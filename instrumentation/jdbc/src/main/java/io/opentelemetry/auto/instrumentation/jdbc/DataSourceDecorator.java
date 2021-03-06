package io.opentelemetry.auto.instrumentation.jdbc;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.decorator.BaseDecorator;
import io.opentelemetry.trace.Tracer;

public class DataSourceDecorator extends BaseDecorator {
  public static final DataSourceDecorator DECORATE = new DataSourceDecorator();

  public static final Tracer TRACER =
      OpenTelemetry.getTracerFactory().get("io.opentelemetry.auto.jdbc");

  @Override
  protected String getComponentName() {
    return "java-jdbc-connection";
  }

  @Override
  protected String getSpanType() {
    return null;
  }
}
