package com.laigeoffer.pmhub.base.security.service;

import com.alibaba.fastjson2.JSONObject;
import com.laigeoffer.pmhub.base.core.config.redis.RedisService;
import com.laigeoffer.pmhub.base.core.constant.CacheConstants;
import com.laigeoffer.pmhub.base.core.constant.Constants;
import com.laigeoffer.pmhub.base.core.constant.SecurityConstants;
import com.laigeoffer.pmhub.base.core.core.domain.model.LoginUser;
import com.laigeoffer.pmhub.base.core.utils.JwtUtils;
import com.laigeoffer.pmhub.base.core.utils.ServletUtils;
import com.laigeoffer.pmhub.base.core.utils.StringUtils;
import com.laigeoffer.pmhub.base.core.utils.ip.AddressUtils;
import com.laigeoffer.pmhub.base.core.utils.ip.IpUtils;
import com.laigeoffer.pmhub.base.core.utils.uuid.IdUtils;
import eu.bitwalker.useragentutils.UserAgent;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * token验证处理
 *
 * @author canghe
 */
@Component
public class TokenService {
    private static final Logger log = LoggerFactory.getLogger(TokenService.class);
    protected static final long MILLIS_SECOND = 1000;
    protected static final long MILLIS_MINUTE = 60 * MILLIS_SECOND;
    private static final Long MILLIS_MINUTE_TEN = 20 * 60 * 1000L;

    private final static String ACCESS_TOKEN = CacheConstants.LOGIN_TOKEN_KEY;

    // 令牌自定义标识
    @Value("${token.header}")
    private String header;
    // 令牌秘钥
    @Value("${token.secret}")
    private String secret;
    // 令牌有效期（默认30分钟）
    @Value("${token.expireTime}")
    private int expireTime;
    @Autowired
    private RedisService redisService;

    /**
     * 生成SecretKey
     *
     * @param secret
     * @return
     */
    private static SecretKey generateKey(String secret) {
        byte[] encodedKey = Base64.decodeBase64(secret);
        return new SecretKeySpec(encodedKey, 0, encodedKey.length, "HmacSHA256");
    }

    /**
     * 获取用户身份信息
     *
     * @return 用户信息
     */
    public LoginUser getLoginUser(HttpServletRequest request) {
        // 1.从请求头中获取 Token 字符串
        String token = getToken(request);
        if (StringUtils.isNotEmpty(token)) {
            try {
                // 2. 解析 JWT，获取 Payload（载荷）
                Claims claims = parseToken(token);
                // 3. 从载荷中取出存入的 UUID (login_user_key)
                String uuid = (String) claims.get(Constants.LOGIN_USER_KEY);
                // 4. 拼接 Redis 的 Key: "login_tokens:" + uuid
                String userKey = getTokenKey(uuid);
                // 5. 去 Redis 查询完整的用户信息对象
                LoginUser user = redisService.getCacheObject(userKey);
                return user;
            } catch (Exception e) {
                // 解析失败（如 Token 篡改、过期）则忽略
            }
        }
        return null;
    }

    /**
     * 获取用户身份信息(适用于没有HttpServletRequest对象的场景下)
     * @param token
     * @return
     */
    public LoginUser getLoginUser(String token)
    {
        LoginUser user = null;
        try
        {
            if (StringUtils.isNotEmpty(token))
            {
                // 1. 使用工具类直接从 Token 字符串中解析出 UUID
                String userkey = JwtUtils.getUserKey(token);
                // 2. 查Redis
                JSONObject jsonObject = redisService.getCacheObject(getTokenKey(userkey));
                // 3.JSON转对象
                user = jsonObject.toJavaObject(LoginUser.class);
                return user;
            }
        }
        catch (Exception e)
        {
            log.error("获取用户信息异常'{}'", e.getMessage());
        }
        return user;
    }

    /**
     * 设置用户身份信息
     * @param loginUser
     */
    public void setLoginUser(LoginUser loginUser) {
        if (StringUtils.isNotNull(loginUser) && StringUtils.isNotEmpty(loginUser.getToken())) {
            // 刷新Token有效期
            refreshToken(loginUser);
        }
    }

    /**
     * 删除用户身份信息
     * @param token
     */
    public void delLoginUser(String token) {
        if (StringUtils.isNotEmpty(token)) {
            String userKey = getTokenKey(token);
            // 删除 Redis 中的数据，实现“踢人下线”
            redisService.deleteObject(userKey);
        }
    }

    /**
     * 创建令牌
     * @param loginUser
     * @return
     */
    public String createToken(LoginUser loginUser)
    {
        // 1. 生成一个唯一的 UUID 作为 Token 标识
        String token = IdUtils.fastUUID();
        // 2. 设置用户基本信息
        Long userId = loginUser.getUser().getUserId();
        String userName = loginUser.getUser().getUserName();
        loginUser.setToken(token);
        loginUser.setUserId(userId);
        loginUser.setUsername(userName);
        // 记录登录 IP
        loginUser.setIpaddr(IpUtils.getIpAddr());
        // 3. 将用户完整信息存入 Redis，并设置过期时间
        refreshToken(loginUser);

        // Jwt存储信息
        Map<String, Object> claimsMap = new HashMap<String, Object>();
        claimsMap.put(SecurityConstants.USER_KEY, token);
        claimsMap.put(SecurityConstants.DETAILS_USER_ID, userId);
        claimsMap.put(SecurityConstants.DETAILS_USERNAME, userName);

        // 5. 生成 JWT 字符串并返回
        return JwtUtils.createToken(claimsMap);
    }


    /**
     * 创建长效令牌
     *
     * @param loginUser 用户信息
     * @return 令牌
     */
    public String createLongTimeToken(LoginUser loginUser) {
        String token = IdUtils.fastUUID();
        loginUser.setToken(token);

        // 记录浏览器、OS、IP 等环境信息
        setUserAgent(loginUser);
        // 刷新 Redis，有效期极长（7天）
        refreshLongToken(loginUser);

        Map<String, Object> claims = new HashMap<>();
        claims.put(Constants.LOGIN_USER_KEY, token);
        return createLongTimeToken(claims);
    }

    /**
     * 验证令牌有效期，相差不足20分钟，自动刷新缓存
     *
     * @param loginUser
     * @return 令牌
     */
    public void verifyToken(LoginUser loginUser) {
        long expireTime = loginUser.getExpireTime();
        long currentTime = System.currentTimeMillis();
        // 核心逻辑：如果 (过期时间 - 当前时间) <= 20分钟
        if (expireTime - currentTime <= MILLIS_MINUTE_TEN) {
            // 则自动刷新 Redis 中的过期时间
            refreshToken(loginUser);
        }
    }

    /**
     * 刷新令牌有效期
     *
     * @param loginUser 登录信息
     */
    public void refreshToken(LoginUser loginUser) {
        // 更新登录时间为当前时间
        loginUser.setLoginTime(System.currentTimeMillis());
        // 更新过期时间为：当前时间 + 配置的有效期（如30分钟）
        loginUser.setExpireTime(loginUser.getLoginTime() + expireTime * MILLIS_MINUTE);
        // 根据uuid将loginUser缓存，更新Redis缓存
        String userKey = getTokenKey(loginUser.getToken());
        redisService.setCacheObject(userKey, loginUser, expireTime, TimeUnit.MINUTES);
    }

    /**
     * 刷新长效令牌有效期
     *
     * @param loginUser 登录信息
     */
    public void refreshLongToken(LoginUser loginUser) {
        // 更新登录时间为当前时间
        loginUser.setLoginTime(System.currentTimeMillis());
        // 更新过期时间为七天
        loginUser.setExpireTime(loginUser.getLoginTime() + (7 * 1440 * MILLIS_MINUTE));
        // 根据uuid将loginUser缓存
        String userKey = getTokenKey(loginUser.getToken());
        redisService.setCacheObject(userKey, loginUser, 7 * 1440, TimeUnit.MINUTES);
    }


    /**
     * 更新用户token信息
     * 场景：管理员修改了用户权限或信息，需要实时更新该用户所有在线会话的数据。
     *
     * @param loginUser 登录信息
     */
    public void updateToken(LoginUser loginUser) {
        // 模糊查询 Redis 中所有 login_tokens:* 的 key
        Map<String,Object> tokensMap = redisService.getCacheKv("login_tokens:*");
        tokensMap.forEach((key, value) -> {
            // 遍历找到该用户ID对应的Token
            if (Objects.equals(((JSONObject) value).getLong("userId"), loginUser.getUserId())){
                String token = key.replace(CacheConstants.LOGIN_TOKEN_KEY,"");
                // 刷新该 Token 的信息
                loginUser.setToken(token);
                refreshToken(loginUser);
            }
        });
    }


    /**
     * 设置用户代理信息
     *
     * @param loginUser 登录信息
     */
    public void setUserAgent(LoginUser loginUser) {
        // 解析 User-Agent 头
        UserAgent userAgent = UserAgent.parseUserAgentString(ServletUtils.getRequest().getHeader("User-Agent"));
        String ip = IpUtils.getIpAddr(ServletUtils.getRequest());
        // 设置 IP、地理位置、浏览器、操作系统
        loginUser.setIpaddr(ip);
        loginUser.setLoginLocation(AddressUtils.getRealAddressByIP(ip));
        loginUser.setBrowser(userAgent.getBrowser().getName());
        loginUser.setOs(userAgent.getOperatingSystem().getName());
    }

    /**
     * 从数据声明生成令牌
     *
     * @param claims 数据声明
     * @return 令牌
     */
    private String createToken(Map<String, Object> claims) {
        String token = Jwts.builder()
                .setClaims(claims)  // 设置载荷
                .signWith(generateKey(secret), SignatureAlgorithm.HS512)
                .compact();
        return token;
    }

    /**
     * 从数据声明生成长效令牌
     *
     * @param claims 数据声明
     * @return 令牌
     */
    private String createLongTimeToken(Map<String, Object> claims) {
        Date expirationDate = new Date(System.currentTimeMillis() + (24*3600*1000));   // 24小时后过期
        String token = Jwts.builder()
                .setClaims(claims)
                .setExpiration(expirationDate)   // 显式设置过期时间
                .signWith(generateKey(secret), SignatureAlgorithm.HS512)
                .compact();
        return token;
    }

    /**
     * 从令牌中获取数据声明
     *
     * @param token 令牌
     * @return 数据声明
     */
    private Claims parseToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(Base64.decodeBase64(secret))
                .build()
                .parseClaimsJws(token)  // 解析 token
                .getBody();  // 获取载荷 Body
    }

    /**
     * 从令牌中获取用户名
     *
     * @param token 令牌
     * @return 用户名
     */
    public String getUsernameFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.getSubject();
    }

    /**
     * 获取请求token
     *
     * 这里的HTTP请求头(Header)格式为Authorization: <type> <credentials>
     * 例如Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
     * @param request
     * @return token
     */
    private String getToken(HttpServletRequest request) {
        String token = request.getHeader(header);  // 获取 Header 中的值
        // 如果不为空且以 "Bearer " 开头
        if (StringUtils.isNotEmpty(token) && token.startsWith(Constants.TOKEN_PREFIX)) {
            // 去掉 "Bearer " 前缀，只保留后面的 Token 字符串
            token = token.replace(Constants.TOKEN_PREFIX, "");
        }
        return token;
    }

    private String getTokenKey(String token) {
        return ACCESS_TOKEN + token;
    }
}
