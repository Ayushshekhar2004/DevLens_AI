package com.devlensai.backend.config;

import com.devlensai.backend.ai.AiCodeReviewProvider;
import com.devlensai.backend.ai.MockAiCodeReviewProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiProviderConfig {

    @Bean
    @ConditionalOnMissingBean(AiCodeReviewProvider.class)
    public AiCodeReviewProvider mockAiCodeReviewProvider() {
        return new MockAiCodeReviewProvider();
    }
}
