package com.AgentSaasAplication.common.domain;

import java.util.List;
import java.util.Map;

/** Sağlayıcıdan bağımsız araç tanımı — her connector kendi JSON şemasına çeviriyor. */
public record ToolDefinition(
        String name,
        String description,
        Map<String, ParameterSchema> parameters,
        List<String> required,
        DurationClass durationClass
) {
    public ToolDefinition {
        if (durationClass == null) {
            durationClass = DurationClass.MEDIUM;
        }
    }

    /** Zaman aşımı süresi — connector'ların TOOL_CALL beklerken kullandığı değer. */
    public java.time.Duration timeout() {
        return durationClass.timeout();
    }

    public record ParameterSchema(String type, String description) {}
}
