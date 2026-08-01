package dev.atlas.support;

import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiErrorHandler {

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ApiError> handleApi(ApiException ex) {
    return ResponseEntity.status(ex.status()).body(error(ex.code(), ex.getMessage()));
  }

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<ApiError> handleStatus(ResponseStatusException ex) {
    HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
    String code = switch (status) {
      case NOT_FOUND -> "NOT_FOUND";
      case BAD_REQUEST -> "VALIDATION_ERROR";
      case SERVICE_UNAVAILABLE -> "PROVIDER_UNAVAILABLE";
      case CONFLICT -> "EMBEDDING_CONFIG_MISMATCH";
      default -> "VALIDATION_ERROR";
    };
    String message = ex.getReason() != null ? ex.getReason() : status.getReasonPhrase();
    return ResponseEntity.status(status).body(error(code, message));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
    String message = ex.getBindingResult().getFieldErrors().stream()
        .findFirst()
        .map(err -> err.getField() + " " + err.getDefaultMessage())
        .orElse("Invalid request");
    return ResponseEntity.badRequest().body(error("VALIDATION_ERROR", message));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex) {
    return ResponseEntity.badRequest().body(error("VALIDATION_ERROR", safeMessage(ex.getMessage())));
  }

  private static ApiError error(String code, String message) {
    return new ApiError(code, safeMessage(message), MDC.get("requestId"));
  }

  private static String safeMessage(String message) {
    if (message == null || message.isBlank()) {
      return "Request failed";
    }
    return message;
  }
}
