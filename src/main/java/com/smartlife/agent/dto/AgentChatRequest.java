package com.smartlife.agent.dto;

import lombok.Data;

import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
public class AgentChatRequest {
    @NotBlank(message = "message must not be blank")
    @Size(max = 2000, message = "message must not exceed 2000 characters")
    private String message;
    @DecimalMin(value = "-180", message = "longitude must be between -180 and 180")
    @DecimalMax(value = "180", message = "longitude must be between -180 and 180")
    private Double longitude;
    @DecimalMin(value = "-90", message = "latitude must be between -90 and 90")
    @DecimalMax(value = "90", message = "latitude must be between -90 and 90")
    private Double latitude;
    @Size(max = 64, message = "conversationId must not exceed 64 characters")
    private String conversationId;
}
