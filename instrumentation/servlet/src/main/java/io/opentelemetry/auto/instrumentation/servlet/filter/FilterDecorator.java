package io.opentelemetry.auto.instrumentation.servlet.filter;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.decorator.BaseDecorator;
import io.opentelemetry.trace.Tracer;

public class FilterDecorator extends BaseDecorator {
  public static final FilterDecorator DECORATE = new FilterDecorator();
  public static final Tracer TRACER = OpenTelemetry.getTracerFactory().get("io.opentelemetry.auto");

  @Override
  protected String getSpanType() {
    return null;
  }

  @Override
  protected String getComponentName() {
    return "java-web-servlet-filter";
  }
}
