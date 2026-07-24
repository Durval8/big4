package com.financedash.dto;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        List<String> messages
) {
    public static ErrorResponse of(int status, String error, List<String> messages) {
        return new ErrorResponse(Instant.now(), status, error, messages);
    }
}
