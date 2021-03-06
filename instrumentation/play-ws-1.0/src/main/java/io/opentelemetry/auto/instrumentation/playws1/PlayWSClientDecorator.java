package io.opentelemetry.auto.instrumentation.playws1;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.decorator.HttpClientDecorator;
import io.opentelemetry.trace.Tracer;
import java.net.URI;
import java.net.URISyntaxException;
import play.shaded.ahc.org.asynchttpclient.Request;
import play.shaded.ahc.org.asynchttpclient.Response;

public class PlayWSClientDecorator extends HttpClientDecorator<Request, Response> {
  public static final PlayWSClientDecorator DECORATE = new PlayWSClientDecorator();

  public static final Tracer TRACER =
      OpenTelemetry.getTracerFactory().get("io.opentelemetry.auto.play-ws-1.0");

  @Override
  protected String method(final Request request) {
    return request.getMethod();
  }

  @Override
  protected URI url(final Request request) throws URISyntaxException {
    return request.getUri().toJavaNetURI();
  }

  @Override
  protected String hostname(final Request request) {
    return request.getUri().getHost();
  }

  @Override
  protected Integer port(final Request request) {
    return request.getUri().getPort();
  }

  @Override
  protected Integer status(final Response response) {
    return response.getStatusCode();
  }

  @Override
  protected String getComponentName() {
    return "play-ws";
  }
}
