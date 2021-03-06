package io.opentelemetry.auto.instrumentation.springwebflux.server;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.decorator.ServerDecorator;
import io.opentelemetry.auto.instrumentation.api.SpanTypes;
import io.opentelemetry.trace.Tracer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SpringWebfluxHttpServerDecorator extends ServerDecorator {
  public static final SpringWebfluxHttpServerDecorator DECORATE =
      new SpringWebfluxHttpServerDecorator();
  public static final Tracer TRACER =
      OpenTelemetry.getTracerFactory()
          .get("io.opentelemetry.instrumentation.auto.spring-webflux-5.0");

  @Override
  protected String getSpanType() {
    return SpanTypes.HTTP_SERVER;
  }

  @Override
  protected String getComponentName() {
    return "spring-webflux-controller";
  }
}
