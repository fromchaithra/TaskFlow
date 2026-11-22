package com.taskflow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LiveAnalyticsConfig {

    @Bean
    public LiveAnalyticsSettings liveAnalyticsSettings() {
        return LiveAnalyticsSettings.defaults();
    }
}
