package com.smartlife.agent.client;

import com.smartlife.agent.client.model.AiModelRequest;
import com.smartlife.agent.client.model.AiModelResponse;

public interface AiModelClient {
    AiModelResponse respond(AiModelRequest request);
}
