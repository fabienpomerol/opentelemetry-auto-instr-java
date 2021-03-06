package io.opentelemetry.auto.instrumentation.grizzly;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.decorator.HttpServerDecorator;
import io.opentelemetry.trace.Tracer;
import java.net.URI;
import java.net.URISyntaxException;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;

public class GrizzlyDecorator extends HttpServerDecorator<Request, Request, Response> {
  public static final GrizzlyDecorator DECORATE = new GrizzlyDecorator();

  public static final Tracer TRACER =
      OpenTelemetry.getTracerFactory().get("io.opentelemetry.auto.grizzly-2.0");

  @Override
  protected String method(final Request request) {
    return request.getMethod().getMethodString();
  }

  @Override
  protected URI url(final Request request) throws URISyntaxException {
    return new URI(
        request.getScheme(),
        null,
        request.getServerName(),
        request.getServerPort(),
        request.getRequestURI(),
        request.getQueryString(),
        null);
  }

  @Override
  protected String peerHostIP(final Request request) {
    return request.getRemoteAddr();
  }

  @Override
  protected Integer peerPort(final Request request) {
    return request.getRemotePort();
  }

  @Override
  protected Integer status(final Response containerResponse) {
    return containerResponse.getStatus();
  }

  @Override
  protected String getComponentName() {
    return "grizzly";
  }
}
