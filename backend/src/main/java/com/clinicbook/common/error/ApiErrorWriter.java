package com.clinicbook.common.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

/**
 * Serialises the standard error shape from places that run before the MVC layer,
 * such as security filters.
 */
@Component
public class ApiErrorWriter {

    private final ObjectMapper objectMapper;

    public ApiErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletResponse response, HttpStatus status, String code, String message, String path)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ApiError body = new ApiError(Instant.now(), status.value(), code, message, path, List.of());
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
