package com.aobp.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientResponseException;

/** Converts expected application failures into RFC 9457 problem responses. */
@RestControllerAdvice
public class ApiErrors {
  /** Preserves a downstream status without exposing its internal response body. */
  @ExceptionHandler(RestClientResponseException.class)
  ResponseEntity<ProblemDetail> downstream(
      RestClientResponseException e, HttpServletRequest request) {
    return problem(e.getStatusCode(), "Downstream request failed", request);
  }

  /** Preserves deliberate application statuses such as 401, 403, 404, and 503. */
  @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
  ResponseEntity<ProblemDetail> expected(
      org.springframework.web.server.ResponseStatusException e, HttpServletRequest request) {
    return problem(
        e.getStatusCode(), e.getReason() == null ? "Request rejected" : e.getReason(), request);
  }

  /** Distinguishes dependency outages from authorization denials. */
  @ExceptionHandler({
    org.springframework.dao.DataAccessException.class,
    org.springframework.web.client.ResourceAccessException.class
  })
  ResponseEntity<ProblemDetail> unavailable(Exception e, HttpServletRequest request) {
    return problem(HttpStatus.SERVICE_UNAVAILABLE, "Required dependency unavailable", request);
  }

  /** Keeps unexpected exception details out of public responses. */
  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> unexpected(Exception e, HttpServletRequest request) {
    org.slf4j.LoggerFactory.getLogger(getClass())
        .error("Request failed; requestId={}", request.getAttribute("requestId"), e);
    return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error", request);
  }

  /** Builds a safe, correlatable error document. */
  public static ResponseEntity<ProblemDetail> problem(
      HttpStatusCode status, String detail, HttpServletRequest request) {
    var p = ProblemDetail.forStatusAndDetail(status, detail);
    p.setProperty("requestId", request.getAttribute("requestId"));
    return ResponseEntity.status(status).body(p);
  }
}
