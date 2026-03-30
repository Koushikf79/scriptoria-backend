package com.scriptoria.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * RestTemplate configured for quality-first, long-script use:
 * - Connect timeout: 30s
 * - Read timeout: 5 min (120+ scene scripts with reasoning_effort=high can take 2-3 min per chunk)
 */
@Configuration
public class OpenAiConfig {

    @Bean
    public RestTemplate openRouterRestTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(30))
                .setReadTimeout(Duration.ofMinutes(5))
                .build();
    }
}
