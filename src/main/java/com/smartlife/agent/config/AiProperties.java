package com.smartlife.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "ai")
public class AiProperties {
    private boolean enabled = true;
    private String baseUrl = "";
    private String apiKey = "";
    private String model = "";
    private int maxToolRounds = 5;
    private int timeoutSeconds = 30;
    private long conversationTtlMinutes = 60;
    private int maxConversationMessages = 20;
}
