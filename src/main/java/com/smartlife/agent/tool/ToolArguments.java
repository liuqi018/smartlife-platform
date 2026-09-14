package com.smartlife.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartlife.agent.exception.AgentException;

final class ToolArguments {
    private ToolArguments() {}

    static long requiredPositiveLong(JsonNode args, String name) {
        JsonNode value = args.get(name);
        if (value == null || !value.canConvertToLong() || value.asLong() <= 0) throw invalid(name);
        return value.asLong();
    }

    static int intInRange(JsonNode args, String name, int defaultValue, int min, int max) {
        JsonNode value = args.get(name);
        int result = value == null || value.isNull() ? defaultValue : value.asInt(Integer.MIN_VALUE);
        if (result < min || result > max) throw invalid(name);
        return result;
    }

    static double doubleInRange(JsonNode args, String name, Double fallback, double min, double max) {
        JsonNode value = args.get(name);
        if ((value == null || value.isNull()) && fallback == null) throw invalid(name);
        double result = value == null || value.isNull() ? fallback : value.asDouble(Double.NaN);
        if (Double.isNaN(result) || result < min || result > max) throw invalid(name);
        return result;
    }

    static String optionalText(JsonNode args, String name, int maxLength) {
        JsonNode value = args.get(name);
        if (value == null || value.isNull()) return null;
        if (!value.isTextual() || value.asText().length() > maxLength) throw invalid(name);
        String text = value.asText().trim();
        return text.isEmpty() ? null : text;
    }

    private static AgentException invalid(String name) {
        return new AgentException("invalid tool argument: " + name);
    }
}
