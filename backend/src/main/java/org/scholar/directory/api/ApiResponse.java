package org.scholar.directory.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record ApiResponse<T>(boolean success, String code, String message, T data, Map<String, Object> meta) {
    public static <T> ApiResponse<T> ok(T data) {
        return ok(data, Map.of());
    }

    public static <T> ApiResponse<T> ok(T data, Map<String, Object> details) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("traceId", UUID.randomUUID().toString());
        meta.putAll(details);
        return new ApiResponse<>(true, "OK", "", data, meta);
    }

    public static ApiResponse<Void> error(String code, String message) {
        return new ApiResponse<>(false, code, message, null,
                Map.of("traceId", UUID.randomUUID().toString()));
    }
}
