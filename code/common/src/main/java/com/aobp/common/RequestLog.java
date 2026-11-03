package com.aobp.common;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Adds a bounded correlation identifier without logging cookies, tokens, or request bodies. */
@Component
public class RequestLog extends OncePerRequestFilter {
  private static final Logger LOG = LoggerFactory.getLogger(RequestLog.class);

  /** Logs method, path, status, and elapsed time after each request. */
  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    String supplied = req.getHeader("X-Request-ID");
    String id =
        supplied != null && supplied.matches("[a-zA-Z0-9-]{1,64}")
            ? supplied
            : UUID.randomUUID().toString();
    req.setAttribute("requestId", id);
    res.setHeader("X-Request-ID", id);
    MDC.put("requestId", id);
    long start = System.nanoTime();
    try {
      chain.doFilter(req, res);
    } finally {
      LOG.info(
          "http method={} path={} status={} durationMs={}",
          req.getMethod(),
          req.getRequestURI(),
          res.getStatus(),
          (System.nanoTime() - start) / 1_000_000);
      MDC.remove("requestId");
    }
  }
}
