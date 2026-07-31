package dev.atlas.support;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ApiErrorSafetyTest {
  @Test
  void errorBodyOmitsStackTraces() {
    ApiErrorHandler handler = new ApiErrorHandler();
    ResponseEntity<ApiError> response = handler.handleApi(
        new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid request"));
    String message = response.getBody().message();
    assertFalse(message.contains("at dev.atlas"));
    assertFalse(message.contains("Exception"));
    assertFalse(message.toLowerCase().contains("localhost"));
  }
}
