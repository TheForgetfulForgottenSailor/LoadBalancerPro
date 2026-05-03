package api;

import java.time.Instant;
import java.util.List;

public record ApiErrorResponse(int status, String error, String message, String path, String timestamp,
                               List<String> details) {
    public ApiErrorResponse(String error, String message, List<String> details) {
        this(0, error, message, "", Instant.now().toString(), details);
    }

    public static ApiErrorResponse badRequest(String message) {
        return badRequest(message, "");
    }

    public static ApiErrorResponse badRequest(String message, String path) {
        return new ApiErrorResponse(400, "bad_request", message, path, Instant.now().toString(), List.of());
    }

    public static ApiErrorResponse validation(List<String> details) {
        return validation(details, "");
    }

    public static ApiErrorResponse validation(List<String> details, String path) {
        return new ApiErrorResponse(400, "validation_failed", "Request validation failed", path,
                Instant.now().toString(), details);
    }

    public static ApiErrorResponse payloadTooLarge(String path, long maxBytes) {
        return new ApiErrorResponse(413, "payload_too_large",
                "Request body exceeds maximum size of " + maxBytes + " bytes",
                path, Instant.now().toString(), List.of());
    }

    public static ApiErrorResponse unauthorized(String path) {
        return unauthorized(path, "Valid API key required for this endpoint");
    }

    public static ApiErrorResponse unauthorized(String path, String message) {
        return new ApiErrorResponse(401, "unauthorized", message, path, Instant.now().toString(), List.of());
    }

    public static ApiErrorResponse forbidden(String path) {
        return new ApiErrorResponse(403, "forbidden",
                "Authenticated principal does not have the required role for this endpoint",
                path, Instant.now().toString(), List.of());
    }

    public static ApiErrorResponse methodNotAllowed(String path) {
        return new ApiErrorResponse(405, "method_not_allowed",
                "HTTP method is not supported for this endpoint",
                path, Instant.now().toString(), List.of());
    }

    public static ApiErrorResponse unsupportedMediaType(String path) {
        return new ApiErrorResponse(415, "unsupported_media_type",
                "Content type is not supported for this endpoint",
                path, Instant.now().toString(), List.of());
    }
}
