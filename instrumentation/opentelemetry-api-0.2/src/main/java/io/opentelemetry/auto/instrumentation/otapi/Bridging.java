package io.opentelemetry.auto.instrumentation.otapi;

import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import unshaded.io.opentelemetry.trace.AttributeValue;
import unshaded.io.opentelemetry.trace.EndSpanOptions;
import unshaded.io.opentelemetry.trace.Span;
import unshaded.io.opentelemetry.trace.SpanContext;
import unshaded.io.opentelemetry.trace.SpanId;
import unshaded.io.opentelemetry.trace.Status;
import unshaded.io.opentelemetry.trace.TraceFlags;
import unshaded.io.opentelemetry.trace.TraceId;
import unshaded.io.opentelemetry.trace.Tracestate;

/**
 * This class translates between the (unshaded) OpenTelemetry API that the user brings and the
 * (shaded) OpenTelemetry API that is in the bootstrap class loader and used to report telemetry to
 * the agent.
 *
 * <p>"unshaded.io.opentelemetry.*" refers to the (unshaded) OpenTelemetry API that the user brings
 * (as those references will be translated during the build to remove the "unshaded." prefix).
 *
 * <p>"io.opentelemetry.*" refers to the (shaded) OpenTelemetry API that is in the bootstrap class
 * loader (as those references will later be shaded).
 *
 * <p>Also see comments in this module's gradle file.
 */
@Slf4j
public class Bridging {

  // this is just an optimization to save some byte array allocations
  public static final ThreadLocal<byte[]> BUFFER = new ThreadLocal<>();

  public static SpanContext toUnshaded(final io.opentelemetry.trace.SpanContext shadedContext) {
    if (shadedContext.isRemote()) {
      return SpanContext.createFromRemoteParent(
          toUnshaded(shadedContext.getTraceId()),
          toUnshaded(shadedContext.getSpanId()),
          toUnshaded(shadedContext.getTraceFlags()),
          toUnshaded(shadedContext.getTracestate()));
    } else {
      return SpanContext.create(
          toUnshaded(shadedContext.getTraceId()),
          toUnshaded(shadedContext.getSpanId()),
          toUnshaded(shadedContext.getTraceFlags()),
          toUnshaded(shadedContext.getTracestate()));
    }
  }

  public static io.opentelemetry.trace.SpanContext toShaded(final SpanContext unshadedContext) {
    if (unshadedContext.isRemote()) {
      return io.opentelemetry.trace.SpanContext.createFromRemoteParent(
          toShaded(unshadedContext.getTraceId()),
          toShaded(unshadedContext.getSpanId()),
          toShaded(unshadedContext.getTraceFlags()),
          toShaded(unshadedContext.getTracestate()));
    } else {
      return io.opentelemetry.trace.SpanContext.create(
          toShaded(unshadedContext.getTraceId()),
          toShaded(unshadedContext.getSpanId()),
          toShaded(unshadedContext.getTraceFlags()),
          toShaded(unshadedContext.getTracestate()));
    }
  }

  public static Map<String, io.opentelemetry.trace.AttributeValue> toShaded(
      final Map<String, AttributeValue> unshadedAttributes) {
    final Map<String, io.opentelemetry.trace.AttributeValue> shadedAttributes = new HashMap<>();
    for (final Map.Entry<String, AttributeValue> entry : unshadedAttributes.entrySet()) {
      final AttributeValue value = entry.getValue();
      final io.opentelemetry.trace.AttributeValue shadedValue = toShadedOrNull(value);
      if (shadedValue != null) {
        shadedAttributes.put(entry.getKey(), shadedValue);
      }
    }
    return shadedAttributes;
  }

  public static io.opentelemetry.trace.AttributeValue toShadedOrNull(
      final AttributeValue unshadedValue) {
    switch (unshadedValue.getType()) {
      case STRING:
        return io.opentelemetry.trace.AttributeValue.stringAttributeValue(
            unshadedValue.getStringValue());
      case LONG:
        return io.opentelemetry.trace.AttributeValue.longAttributeValue(
            unshadedValue.getLongValue());
      case BOOLEAN:
        return io.opentelemetry.trace.AttributeValue.booleanAttributeValue(
            unshadedValue.getBooleanValue());
      case DOUBLE:
        return io.opentelemetry.trace.AttributeValue.doubleAttributeValue(
            unshadedValue.getDoubleValue());
      default:
        log.debug("unexpected attribute type: {}", unshadedValue.getType());
        return null;
    }
  }

  public static io.opentelemetry.trace.Status toShadedOrNull(final Status unshadedStatus) {
    final io.opentelemetry.trace.Status.CanonicalCode canonicalCode;
    try {
      canonicalCode =
          io.opentelemetry.trace.Status.CanonicalCode.valueOf(
              unshadedStatus.getCanonicalCode().name());
    } catch (final IllegalArgumentException e) {
      log.debug("unexpected status canonical code: {}", unshadedStatus.getCanonicalCode().name());
      return null;
    }
    return canonicalCode.toStatus().withDescription(unshadedStatus.getDescription());
  }

  public static io.opentelemetry.trace.Span.Kind toShadedOrNull(final Span.Kind unshadedSpanKind) {
    try {
      return io.opentelemetry.trace.Span.Kind.valueOf(unshadedSpanKind.name());
    } catch (final IllegalArgumentException e) {
      log.debug("unexpected span kind: {}", unshadedSpanKind.name());
      return null;
    }
  }

  public static io.opentelemetry.trace.EndSpanOptions toShaded(
      final EndSpanOptions unshadedEndSpanOptions) {
    return io.opentelemetry.trace.EndSpanOptions.builder()
        .setEndTimestamp(unshadedEndSpanOptions.getEndTimestamp())
        .build();
  }

  private static TraceId toUnshaded(final io.opentelemetry.trace.TraceId shadedTraceId) {
    final byte[] bytes = getBuffer();
    shadedTraceId.copyBytesTo(bytes, 0);
    return TraceId.fromBytes(bytes, 0);
  }

  private static SpanId toUnshaded(final io.opentelemetry.trace.SpanId shadedSpanId) {
    final byte[] bytes = getBuffer();
    shadedSpanId.copyBytesTo(bytes, 0);
    return SpanId.fromBytes(bytes, 0);
  }

  private static TraceFlags toUnshaded(final io.opentelemetry.trace.TraceFlags shadedTraceFlags) {
    return TraceFlags.fromByte(shadedTraceFlags.getByte());
  }

  private static Tracestate toUnshaded(final io.opentelemetry.trace.Tracestate shadedTraceState) {
    final Tracestate.Builder builder = Tracestate.builder();
    for (final io.opentelemetry.trace.Tracestate.Entry entry : shadedTraceState.getEntries()) {
      builder.set(entry.getKey(), entry.getValue());
    }
    return builder.build();
  }

  private static io.opentelemetry.trace.TraceId toShaded(final TraceId unshadedTraceId) {
    final byte[] bytes = getBuffer();
    unshadedTraceId.copyBytesTo(bytes, 0);
    return io.opentelemetry.trace.TraceId.fromBytes(bytes, 0);
  }

  private static io.opentelemetry.trace.SpanId toShaded(final SpanId unshadedSpanId) {
    final byte[] bytes = getBuffer();
    unshadedSpanId.copyBytesTo(bytes, 0);
    return io.opentelemetry.trace.SpanId.fromBytes(bytes, 0);
  }

  private static io.opentelemetry.trace.TraceFlags toShaded(final TraceFlags unshadedTraceFlags) {
    return io.opentelemetry.trace.TraceFlags.fromByte(unshadedTraceFlags.getByte());
  }

  private static io.opentelemetry.trace.Tracestate toShaded(final Tracestate unshadedTraceState) {
    final io.opentelemetry.trace.Tracestate.Builder builder =
        io.opentelemetry.trace.Tracestate.builder();
    for (final Tracestate.Entry entry : unshadedTraceState.getEntries()) {
      builder.set(entry.getKey(), entry.getValue());
    }
    return builder.build();
  }

  private static byte[] getBuffer() {
    byte[] bytes = BUFFER.get();
    if (bytes == null) {
      bytes = new byte[16];
      BUFFER.set(bytes);
    }
    return bytes;
  }
}
