package io.opentelemetry.auto.instrumentation.otapi;

import static io.opentelemetry.auto.instrumentation.otapi.Bridging.toShaded;
import static io.opentelemetry.auto.instrumentation.otapi.Bridging.toShadedOrNull;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import unshaded.io.opentelemetry.trace.AttributeValue;
import unshaded.io.opentelemetry.trace.Link;
import unshaded.io.opentelemetry.trace.Span;
import unshaded.io.opentelemetry.trace.SpanContext;

@Slf4j
public class UnshadedSpanBuilder implements Span.Builder {

  private final io.opentelemetry.trace.Span.Builder shadedBuilder;

  public UnshadedSpanBuilder(final io.opentelemetry.trace.Span.Builder shadedBuilder) {
    this.shadedBuilder = shadedBuilder;
  }

  @Override
  public Span.Builder setParent(final Span parent) {
    if (parent instanceof UnshadedSpan) {
      shadedBuilder.setParent(((UnshadedSpan) parent).getShadedSpan());
    } else {
      log.debug("unexpected parent span: {}", parent);
    }
    return this;
  }

  @Override
  public Span.Builder setParent(final SpanContext remoteParent) {
    shadedBuilder.setParent(toShaded(remoteParent));
    return this;
  }

  @Override
  public Span.Builder setNoParent() {
    shadedBuilder.setNoParent();
    return this;
  }

  @Override
  public Span.Builder addLink(final SpanContext spanContext) {
    shadedBuilder.addLink(toShaded(spanContext));
    return this;
  }

  @Override
  public Span.Builder addLink(
      final SpanContext spanContext, final Map<String, AttributeValue> attributes) {
    shadedBuilder.addLink(toShaded(spanContext));
    return this;
  }

  @Override
  public Span.Builder addLink(final Link link) {
    shadedBuilder.addLink(toShaded(link.getContext()), toShaded(link.getAttributes()));
    return this;
  }

  @Override
  public Span.Builder setSpanKind(final Span.Kind spanKind) {
    final io.opentelemetry.trace.Span.Kind shadedSpanKind = toShadedOrNull(spanKind);
    if (shadedSpanKind != null) {
      shadedBuilder.setSpanKind(shadedSpanKind);
    }
    return this;
  }

  @Override
  public Span.Builder setStartTimestamp(final long startTimestamp) {
    shadedBuilder.setStartTimestamp(startTimestamp);
    return this;
  }

  @Override
  public Span startSpan() {
    return new UnshadedSpan(shadedBuilder.startSpan());
  }
}
