package com.campus.lostfound.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class AppConfig {

    /** 统一的 UTC 时钟；需要当前时间的业务注入它，测试时可替换为固定时钟。 */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
