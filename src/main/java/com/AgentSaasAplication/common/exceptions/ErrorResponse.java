package com.AgentSaasAplication.common.exceptions;

import java.time.Instant;
import java.util.Map;

public record ErrorResponse(int status, String message, Instant timestamp, Map<String, String> fieldErrors) {

    public static ErrorResponse of(int status, String message) {
        return new ErrorResponse(status, message, Instant.now(), null);
    }

    public static ErrorResponse ofValidation(Map<String, String> fieldErrors) {
        return new ErrorResponse(400, "Doğrulama hatası", Instant.now(), fieldErrors);
    }
}