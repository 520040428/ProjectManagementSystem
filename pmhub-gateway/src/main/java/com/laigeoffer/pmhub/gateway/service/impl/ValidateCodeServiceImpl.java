package com.laigeoffer.pmhub.gateway.service.impl;

import com.google.code.kaptcha.Producer;
import com.laigeoffer.pmhub.base.core.config.redis.RedisService;
import com.laigeoffer.pmhub.base.core.constant.CacheConstants;
import com.laigeoffer.pmhub.base.core.constant.Constants;
import com.laigeoffer.pmhub.base.core.core.domain.AjaxResult;
import com.laigeoffer.pmhub.base.core.exception.user.CaptchaException;
import com.laigeoffer.pmhub.base.core.utils.StringUtils;
import com.laigeoffer.pmhub.base.core.utils.sign.Base64;
import com.laigeoffer.pmhub.base.core.utils.uuid.IdUtils;
import com.laigeoffer.pmhub.gateway.config.properties.CaptchaProperties;
import com.laigeoffer.pmhub.gateway.service.ValidateCodeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.FastByteArrayOutputStream;

import javax.annotation.Resource;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * 验证码实现处理
 *
 * @author JingYi
 */
@Service
public class ValidateCodeServiceImpl implements ValidateCodeService
{
    // 从Spring容器中找到名为"captchaProducer"的Bean对象，验证码生成器
    @Resource(name = "captchaProducer")
    private Producer captchaProducer;

    // 同上，数字验证码生成器
    @Resource(name = "captchaProducerMath")
    private Producer captchaProducerMath;

    @Autowired
    private RedisService redisService;

    @Autowired
    private CaptchaProperties captchaProperties;

    /**
     * 生成验证码
     */
    @Override
    public AjaxResult createCaptcha() throws IOException, CaptchaException
    {
        AjaxResult ajax = AjaxResult.success();
        boolean captchaEnabled = captchaProperties.getEnabled();
        ajax.put("captchaEnabled", captchaEnabled);
        // 没开启，直接返回结果，告诉前端"不需要验证码"
        if (!captchaEnabled)
        {
            return ajax;
        }

        // 保存验证码信息
        // 生成一个唯一的ID(UUID)
        String uuid = IdUtils.simpleUUID();
        // redis key为"captcha_codes:" + uuid
        String verifyKey = CacheConstants.CAPTCHA_CODE_KEY + uuid;

        String capStr = null, code = null;
        BufferedImage image = null;

        // 从配置中得到我们验证码的类型
        String captchaType = captchaProperties.getType();
        // 生成验证码
        // 数字验证码
        if ("math".equals(captchaType))
        {
            // 数字验证码生成器生成我们的验证码和答案，格式为类似"1+2=@3"
            String capText = captchaProducerMath.createText();
            // capStr是capText前面的验证码,比如"1+2="
            capStr = capText.substring(0, capText.lastIndexOf("@"));
            // code是capText后面的答案，比如"3"
            code = capText.substring(capText.lastIndexOf("@") + 1);
            // 生成验证码图片
            image = captchaProducerMath.createImage(capStr);
        }
        else if ("char".equals(captchaType))
        {
            capStr = code = captchaProducer.createText();
            image = captchaProducer.createImage(capStr);
        }

        // 将验证码存入redis，倒数第二个是有效期两分钟，最后是时间单位
        redisService.setCacheObject(verifyKey, code, Constants.CAPTCHA_EXPIRATION, TimeUnit.MINUTES);
        // 转换流信息写出
        FastByteArrayOutputStream os = new FastByteArrayOutputStream();
        try
        {
            ImageIO.write(image, "jpg", os);
        }
        catch (IOException e)
        {
            return AjaxResult.error(e.getMessage());
        }

        // 返回 UUID。前端在提交表单时必须把这个 UUID 带回来，否则后端不知道去 Redis 里查哪个验证码。
        ajax.put("uuid", uuid);
        // 图片的二进制数据编码成 Base64 字符串。这样前端可以直接用 <img src="data:image/jpeg;base64,..."> 显示图片
        ajax.put("img", Base64.encode(os.toByteArray()));
        return ajax;
    }

    /**
     * 校验验证码
     */
    @Override
    public void checkCaptcha(String code, String uuid) throws CaptchaException
    {
        if (StringUtils.isEmpty(code))
        {
            throw new CaptchaException("验证码不能为空");
        }
        if (StringUtils.isEmpty(uuid))
        {
            throw new CaptchaException("验证码已失效");
        }
        String verifyKey = CacheConstants.CAPTCHA_CODE_KEY + uuid;
        String captcha = redisService.getCacheObject(verifyKey);
        // 拿出来redis就删，验证码只能用一次
        redisService.deleteObject(verifyKey);

        if (!code.equalsIgnoreCase(captcha))
        {
            throw new CaptchaException("验证码错误");
        }
    }
}
