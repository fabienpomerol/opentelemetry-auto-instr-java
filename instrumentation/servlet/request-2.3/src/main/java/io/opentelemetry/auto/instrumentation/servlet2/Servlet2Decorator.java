package io.opentelemetry.auto.instrumentation.servlet2;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.decorator.HttpServerDecorator;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import java.net.URI;
import java.net.URISyntaxException;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

public class Servlet2Decorator
    extends HttpServerDecorator<HttpServletRequest, HttpServletRequest, ServletResponse> {
  public static final Servlet2Decorator DECORATE = new Servlet2Decorator();
  public static final Tracer TRACER =
      OpenTelemetry.getTracerFactory().get("io.opentelemetry.auto.servlet-2.3");

  @Override
  protected String getComponentName() {
    return "java-web-servlet";
  }

  @Override
  protected String method(final HttpServletRequest httpServletRequest) {
    return httpServletRequest.getMethod();
  }

  @Override
  protected URI url(final HttpServletRequest httpServletRequest) throws URISyntaxException {
    return new URI(
        httpServletRequest.getScheme(),
        null,
        httpServletRequest.getServerName(),
        httpServletRequest.getServerPort(),
        httpServletRequest.getRequestURI(),
        httpServletRequest.getQueryString(),
        null);
  }

  @Override
  protected String peerHostIP(final HttpServletRequest httpServletRequest) {
    return httpServletRequest.getRemoteAddr();
  }

  @Override
  protected Integer peerPort(final HttpServletRequest httpServletRequest) {
    // HttpServletResponse doesn't have accessor for remote port.
    return null;
  }

  @Override
  protected Integer status(final ServletResponse httpServletResponse) {
    if (httpServletResponse instanceof StatusSavingHttpServletResponseWrapper) {
      return ((StatusSavingHttpServletResponseWrapper) httpServletResponse).status;
    } else {
      // HttpServletResponse doesn't have accessor for status code.
      return null;
    }
  }

  @Override
  public Span onRequest(final Span span, final HttpServletRequest request) {
    assert span != null;
    if (request != null) {
      final String sc = request.getContextPath();
      if (sc != null && !sc.isEmpty()) {
        span.setAttribute("servlet.context", sc);
      }
      final String sp = request.getServletPath();
      if (sp != null && !sc.isEmpty()) {
        span.setAttribute("servlet.path", sp);
      }
    }
    return super.onRequest(span, request);
  }
}
