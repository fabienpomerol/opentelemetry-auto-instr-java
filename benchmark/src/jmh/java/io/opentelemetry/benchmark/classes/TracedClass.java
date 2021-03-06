package io.opentelemetry.benchmark.classes;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.trace.Tracer;

public class TracedClass extends UntracedClass {
  private static final Tracer TRACER =
      OpenTelemetry.getTracerFactory().get("io.opentelemetry.auto");

  @Override
  public void f() {
    TRACER.spanBuilder("f").startSpan().end();
  }

  @Override
  public void e() {
    TRACER.spanBuilder("e").startSpan().end();
  }

  @Override
  public void d() {
    TRACER.spanBuilder("d").startSpan().end();
  }

  @Override
  public void c() {
    TRACER.spanBuilder("c").startSpan().end();
  }

  @Override
  public void b() {
    TRACER.spanBuilder("b").startSpan().end();
  }

  @Override
  public void a() {
    TRACER.spanBuilder("a").startSpan().end();
  }
}
