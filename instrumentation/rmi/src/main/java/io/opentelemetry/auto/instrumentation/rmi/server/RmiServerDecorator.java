package io.opentelemetry.auto.instrumentation.rmi.server;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.decorator.ServerDecorator;
import io.opentelemetry.auto.instrumentation.api.SpanTypes;
import io.opentelemetry.trace.Tracer;

public class RmiServerDecorator extends ServerDecorator {
  public static final RmiServerDecorator DECORATE = new RmiServerDecorator();

  public static final Tracer TRACER =
      OpenTelemetry.getTracerFactory().get("io.opentelemetry.instrumentation.auto.rmi");

  @Override
  protected String getSpanType() {
    return SpanTypes.RPC;
  }

  @Override
  protected String getComponentName() {
    return "rmi-server";
  }
}
