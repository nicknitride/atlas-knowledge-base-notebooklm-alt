package dev.atlas.support;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class NotFoundErrorContractTest {
  @Test
  void notFoundUsesStableCode() {
    ApiErrorHandler handler = new ApiErrorHandler();
    ResponseEntity<ApiError> response = handler.handleApi(
        new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Document not found in workspace"));
    assertEquals(404, response.getStatusCode().value());
    assertEquals("NOT_FOUND", response.getBody().code());
    assertFalse(response.getBody().message().contains("Exception"));
  }
}
