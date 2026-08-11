package com.laigeoffer.pmhub.base.security.aspect;

import com.laigeoffer.pmhub.base.core.exception.UtilException;
import com.laigeoffer.pmhub.base.core.utils.StringUtils;
import com.laigeoffer.pmhub.base.security.annotation.DistributedLock;
import com.laigeoffer.pmhub.base.security.pojo.ILock;
import com.laigeoffer.pmhub.base.security.service.redisson.IDistributedLock;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.lang.reflect.Method;
import java.util.Objects;

/**
 * @author JingYi
 * @description DistributedLockAspect
 */
@Aspect
@Slf4j
@Component
public class DistributedLockAspect {

    @Resource
    private IDistributedLock distributedLock;

    /**
     * SpEL表达式解析
     */
    private SpelExpressionParser spelExpressionParser = new SpelExpressionParser();

    /**
     * 用于获取方法参数名字
     */
    private DefaultParameterNameDiscoverer nameDiscoverer = new DefaultParameterNameDiscoverer();

    @Pointcut("@annotation(com.laigeoffer.pmhub.base.security.annotation.DistributedLock)")
    public void distributorLock() {
    }

    @Around("distributorLock()")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        // 获取DistributedLock注解
        DistributedLock distributedLock = this.getDistributedLock(pjp);
        // 获取 lockKey
        String lockKey = this.getLockKey(pjp, distributedLock);
        ILock lockObj = null;
        try {
            // 加锁，tryLok = true,并且tryTime > 0时，尝试获取锁，获取不到超时异常
            if (distributedLock.tryLok()) {
                if(distributedLock.tryTime() <= 0){
                    throw new UtilException("tryTime must be greater than 0");
                }
                lockObj = this.distributedLock.tryLock(lockKey, distributedLock.tryTime(), distributedLock.lockTime(), distributedLock.unit(), distributedLock.fair());
            } else {
                lockObj = this.distributedLock.lock(lockKey, distributedLock.lockTime(), distributedLock.unit(), distributedLock.fair());
            }

            if (Objects.isNull(lockObj)) {
                throw new UtilException("Duplicate request for method still in process");
            }

            // 执行业务方法
            return pjp.proceed();
        } catch (Exception e) {
            throw e;
        } finally {
            // 解锁
            this.unLock(lockObj);
        }
    }

    /**
     * @param pjp
     * @return
     * @throws NoSuchMethodException
     */
    private DistributedLock getDistributedLock(ProceedingJoinPoint pjp) throws NoSuchMethodException {
        // 获取调用方法的名称
        String methodName = pjp.getSignature().getName();
        // 获取目标对象的Class对象
        Class clazz = pjp.getTarget().getClass();
        // 获取方法参数的类型数组
        Class<?>[] par = ((MethodSignature) pjp.getSignature()).getParameterTypes();
        // 根据方法名和参数类型获取对应的Method对象
        Method lockMethod = clazz.getMethod(methodName, par);
        // 从Method对象上获取DistributedLock注解
        DistributedLock distributedLock = lockMethod.getAnnotation(DistributedLock.class);
        return distributedLock;
    }

    /**
     * 解锁
     *
     * @param lockObj
     */
    private void unLock(ILock lockObj) {
        if (Objects.isNull(lockObj)) {
            return;
        }

        try {
            this.distributedLock.unLock(lockObj);
        } catch (Exception e) {
            log.error("分布式锁解锁异常", e);
        }
    }

    /**
     * 获取 lockKey
     *
     * @param pjp
     * @param distributedLock
     * @return
     */
    private String getLockKey(ProceedingJoinPoint pjp, DistributedLock distributedLock) {
        String lockKey = distributedLock.key();
        String keyPrefix = distributedLock.keyPrefix();
        if (StringUtils.isBlank(lockKey)) {
            throw new UtilException("Lok key cannot be empty");
        }
        if (lockKey.contains("#")) {
            this.checkSpEL(lockKey);
            MethodSignature methodSignature = (MethodSignature) pjp.getSignature();
            // 获取方法参数值
            Object[] args = pjp.getArgs();
            lockKey = getValBySpEL(lockKey, methodSignature, args);
        }
        lockKey = StringUtils.isBlank(keyPrefix) ? lockKey : keyPrefix + lockKey;
        return lockKey;
    }

    /**
     * 解析spEL表达式
     *
     * @param spEL
     * @param methodSignature
     * @param args
     * @return
     */
    private String getValBySpEL(String spEL, MethodSignature methodSignature, Object[] args) {
        // 获取方法形参名数组
        String[] paramNames = nameDiscoverer.getParameterNames(methodSignature.getMethod());
        if (paramNames == null || paramNames.length < 1) {
            throw new UtilException("Lok key cannot be empty");
        }
        Expression expression = spelExpressionParser.parseExpression(spEL);
        // spring的表达式上下文对象
        EvaluationContext context = new StandardEvaluationContext();
        // 给上下文赋值
        for (int i = 0; i < args.length; i++) {
            context.setVariable(paramNames[i], args[i]);
        }
        Object value = expression.getValue(context);
        if (value == null) {
            throw new UtilException("The parameter value cannot be null");
        }
        return value.toString();
    }

    /**
     * SpEL 表达式校验
     *
     * @param spEL
     * @return
     */
    private void checkSpEL(String spEL) {
        try {
            ExpressionParser parser = new SpelExpressionParser();
            parser.parseExpression(spEL, new TemplateParserContext());
        } catch (Exception e) {
            log.error("spEL表达式解析异常", e);
            throw new UtilException("Invalid SpEL expression [" + spEL + "]");
        }
    }
}
