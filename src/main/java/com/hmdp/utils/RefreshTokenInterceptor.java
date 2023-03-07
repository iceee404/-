package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

/**
 * @Package:com.hmdp.utils
 * @ClassName:RefreshTokenInterceptor
 * @Auther:iceee
 * @Date:2022/3/26
 * @Description:
 */
public class RefreshTokenInterceptor implements HandlerInterceptor {

    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate=stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //1.从浏览器request请求里的请求头获取token
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)) {
            //token是空，说明没有登录不需要刷新or验证，直接放行
            return true;
        }
        //2.基于请求头里的token获取redis里的用户（redis里的用户存的时候的 key 是 基于token的）
        String key = LOGIN_USER_KEY +token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);
        //3.判断用户是否存在
        if (userMap.isEmpty()) {
            //user是空，说明没有登录不需要刷新or验证，直接放行
            return true;
        }

        //4.将查询到的redis用户 由hash转为userdto
        //执行到这里说明存在
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        //5.把用户信息保存到threadLocaol里
        UserHolder.saveUser(userDTO);
        //6.刷新token有效期
        stringRedisTemplate.expire(key, LOGIN_USER_TTL, TimeUnit.MINUTES);
        //7.放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
    //移除用户:防止内存泄漏，在此业务执行后，执行此后置拦截器
        UserHolder.removeUser();
    }
}
