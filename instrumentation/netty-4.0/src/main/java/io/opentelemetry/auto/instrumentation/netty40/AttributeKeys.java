package io.opentelemetry.auto.instrumentation.netty40;

import io.netty.util.AttributeKey;
import io.opentelemetry.auto.bootstrap.WeakMap;
import io.opentelemetry.auto.instrumentation.netty40.client.HttpClientTracingHandler;
import io.opentelemetry.auto.instrumentation.netty40.server.HttpServerTracingHandler;
import io.opentelemetry.trace.Span;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AttributeKeys {

  private static final WeakMap<ClassLoader, Map<String, AttributeKey<?>>> map =
      WeakMap.Implementation.DEFAULT.get();

  private static final WeakMap.ValueSupplier<ClassLoader, Map<String, AttributeKey<?>>>
      mapSupplier =
          new WeakMap.ValueSupplier<ClassLoader, Map<String, AttributeKey<?>>>() {
            @Override
            public Map<String, AttributeKey<?>> get(final ClassLoader ignored) {
              return new ConcurrentHashMap<>();
            }
          };

  public static final AttributeKey<Span> PARENT_CONNECT_SPAN_ATTRIBUTE_KEY =
      attributeKey("io.opentelemetry.auto.instrumentation.netty40.parent.connect.span");

  public static final AttributeKey<Span> SERVER_ATTRIBUTE_KEY =
      attributeKey(HttpServerTracingHandler.class.getName() + ".span");

  public static final AttributeKey<Span> CLIENT_ATTRIBUTE_KEY =
      attributeKey(HttpClientTracingHandler.class.getName() + ".span");

  public static final AttributeKey<Span> CLIENT_PARENT_ATTRIBUTE_KEY =
      attributeKey(HttpClientTracingHandler.class.getName() + ".parent");

  /**
   * Generate an attribute key or reuse the one existing in the global app map. This implementation
   * creates attributes only once even if the current class is loaded by several class loaders and
   * prevents an issue with Apache Atlas project were this class loaded by multiple class loaders,
   * while the Attribute class is loaded by a third class loader and used internally for the
   * cassandra driver.
   */
  private static <T> AttributeKey<T> attributeKey(final String key) {
    final Map<String, AttributeKey<?>> classLoaderMap =
        map.computeIfAbsent(AttributeKey.class.getClassLoader(), mapSupplier);
    if (classLoaderMap.containsKey(key)) {
      return (AttributeKey<T>) classLoaderMap.get(key);
    }

    final AttributeKey<T> value = new AttributeKey<>(key);
    classLoaderMap.put(key, value);
    return value;
  }
}
