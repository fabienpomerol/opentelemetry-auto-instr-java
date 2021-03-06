package io.opentelemetry.auto.instrumentation.springwebflux.client;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.decorator.HttpClientDecorator;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;

@Slf4j
public class SpringWebfluxHttpClientDecorator
    extends HttpClientDecorator<ClientRequest, ClientResponse> {

  public static final Tracer TRACER =
      OpenTelemetry.getTracerFactory().get("io.opentelemetry.auto.spring-webflux-5.0");
  public static final SpringWebfluxHttpClientDecorator DECORATE =
      new SpringWebfluxHttpClientDecorator();

  public void onCancel(final Span span) {
    span.setAttribute("event", "cancelled");
    span.setAttribute("message", "The subscription was cancelled");
  }

  @Override
  protected String getComponentName() {
    return "spring-webflux-client";
  }

  @Override
  protected String method(final ClientRequest httpRequest) {
    return httpRequest.method().name();
  }

  @Override
  protected URI url(final ClientRequest httpRequest) {
    return httpRequest.url();
  }

  @Override
  protected String hostname(final ClientRequest httpRequest) {
    return httpRequest.url().getHost();
  }

  @Override
  protected Integer port(final ClientRequest httpRequest) {
    return httpRequest.url().getPort();
  }

  @Override
  protected Integer status(final ClientResponse httpResponse) {
    return httpResponse.statusCode().value();
  }
}
