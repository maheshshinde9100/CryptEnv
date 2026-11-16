package com.maheshshinde.CryptEnv.service;

import java.util.List;
import java.util.Map;

public interface AiProvider {
    String generateResponse(String prompt, List<String> allowedTools);
    boolean isAvailable();
    String getProviderName();
}
