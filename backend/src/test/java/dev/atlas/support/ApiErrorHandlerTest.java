package dev.atlas.support;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

class ApiErrorHandlerTest {
  private final ApiErrorHandler handler = new ApiErrorHandler();

  @Test
  void mapsApiExceptionToStableBody() {
    MDC.put("requestId", "req-123");
    try {
      ResponseEntity<ApiError> response = handler.handleApi(
          new ApiException(HttpStatus.BAD_REQUEST, "UPLOAD_TOO_LARGE", "Upload a non-empty file smaller than 80 MB"));
      assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
      assertNotNull(response.getBody());
      assertEquals("UPLOAD_TOO_LARGE", response.getBody().code());
      assertTrue(response.getBody().message().contains("80 MB"));
      assertEquals("req-123", response.getBody().requestId());
    } finally {
      MDC.remove("requestId");
    }
  }

  @Test
  void mapsNotFoundStatusException() {
    ResponseEntity<ApiError> response = handler.handleStatus(
        new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found"));
    assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    assertEquals("NOT_FOUND", response.getBody().code());
  }
}
