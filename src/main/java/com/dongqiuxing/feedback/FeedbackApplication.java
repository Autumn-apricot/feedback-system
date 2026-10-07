package com.dongqiuxing.feedback;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

/**
 * 企业内部意见反馈系统 - 启动类
 *
 * <p>@EnableCaching 开启 Spring Cache 注解支持，配合 {@code RedisConfig} 里配置的
 * RedisCacheManager，实现意见列表的缓存与失效。</p>
 */
@SpringBootApplication
@EnableCaching
public class FeedbackApplication {

    public static void main(String[] args) {
        SpringApplication.run(FeedbackApplication.class, args);
        System.out.println("==================================================");
        System.out.println("  企业内部意见反馈系统启动成功");
        System.out.println("  入口地址: http://localhost:8080/");
        System.out.println("==================================================");
    }
}
