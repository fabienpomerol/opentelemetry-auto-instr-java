package io.opentelemetry.auto.instrumentation.api;

import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;

// intentionally (for now) not implementing Closeable since not clear what it would close
public class SpanWithScope {
  private final Span span;
  private final Scope scope;

  public SpanWithScope(final Span span, final Scope scope) {
    this.span = span;
    this.scope = scope;
  }

  public Span getSpan() {
    return span;
  }

  public void closeScope() {
    scope.close();
  }
}
