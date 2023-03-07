package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @Package:com.hmdp.config
 * @ClassName:RedissonConfig
 * @Auther:iceee
 * @Date:2022/3/31
 * @Description:
 */
@Configuration
public class RedissonConfig {
    @Bean
    public RedissonClient redissonClient(){
        //配置类
        Config config = new Config();
        //添加redis地址，这里添加了单点的地址，也可以使用config.useClusterServers()集群地址
        config.useSingleServer()
                .setAddress("redis://192.168.91.111:6379")
                .setPassword("423333");
        //创建客户端

        return Redisson.create(config);
    }
}
