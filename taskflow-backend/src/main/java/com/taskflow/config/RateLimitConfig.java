package com.taskflow.config;

import com.taskflow.security.RateLimitFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class RateLimitConfig {

    @Bean
    public RateLimitFilter rateLimitFilter(RateLimitProperties properties, JsonMapper jsonMapper) {
        return new RateLimitFilter(properties, jsonMapper);
    }

    /** The filter runs inside the Spring Security chain only, not as a servlet filter. */
    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(RateLimitFilter filter) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
