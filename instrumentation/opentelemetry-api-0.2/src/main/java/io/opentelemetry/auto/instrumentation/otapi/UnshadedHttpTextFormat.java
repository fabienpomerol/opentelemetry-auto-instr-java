package io.opentelemetry.auto.instrumentation.otapi;

import static io.opentelemetry.auto.instrumentation.otapi.Bridging.toShaded;
import static io.opentelemetry.auto.instrumentation.otapi.Bridging.toUnshaded;

import java.util.List;
import unshaded.io.opentelemetry.context.propagation.HttpTextFormat;
import unshaded.io.opentelemetry.trace.SpanContext;

public class UnshadedHttpTextFormat implements HttpTextFormat<SpanContext> {

  private final io.opentelemetry.context.propagation.HttpTextFormat<
          io.opentelemetry.trace.SpanContext>
      shadedHttpTextFormat;

  public UnshadedHttpTextFormat(
      final io.opentelemetry.context.propagation.HttpTextFormat<io.opentelemetry.trace.SpanContext>
          shadedHttpTextFormat) {
    this.shadedHttpTextFormat = shadedHttpTextFormat;
  }

  @Override
  public List<String> fields() {
    return shadedHttpTextFormat.fields();
  }

  @Override
  public <C> SpanContext extract(final C carrier, final HttpTextFormat.Getter<C> getter) {
    return toUnshaded(shadedHttpTextFormat.extract(carrier, new UnshadedGetter<>(getter)));
  }

  @Override
  public <C> void inject(
      final SpanContext value, final C carrier, final HttpTextFormat.Setter<C> setter) {
    shadedHttpTextFormat.inject(toShaded(value), carrier, new UnshadedSetter<>(setter));
  }

  public static class UnshadedGetter<C>
      implements io.opentelemetry.context.propagation.HttpTextFormat.Getter<C> {

    private final HttpTextFormat.Getter<C> shadedGetter;

    public UnshadedGetter(final HttpTextFormat.Getter<C> shadedGetter) {
      this.shadedGetter = shadedGetter;
    }

    @Override
    public String get(final C carrier, final String key) {
      return shadedGetter.get(carrier, key);
    }
  }

  public static class UnshadedSetter<C>
      implements io.opentelemetry.context.propagation.HttpTextFormat.Setter<C> {

    private final HttpTextFormat.Setter<C> shadedSetter;

    public UnshadedSetter(final Setter<C> shadedSetter) {
      this.shadedSetter = shadedSetter;
    }

    @Override
    public void put(final C carrier, final String key, final String value) {
      shadedSetter.put(carrier, key, value);
    }
  }
}
