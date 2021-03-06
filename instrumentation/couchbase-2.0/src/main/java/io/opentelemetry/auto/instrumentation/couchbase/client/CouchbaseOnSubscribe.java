package io.opentelemetry.auto.instrumentation.couchbase.client;

import static io.opentelemetry.auto.instrumentation.couchbase.client.CouchbaseClientDecorator.DECORATE;
import static io.opentelemetry.trace.Span.Kind.CLIENT;

import io.opentelemetry.auto.instrumentation.api.MoreTags;
import io.opentelemetry.auto.instrumentation.rxjava.TracedOnSubscribe;
import io.opentelemetry.trace.Span;
import java.lang.reflect.Method;
import rx.Observable;

public class CouchbaseOnSubscribe extends TracedOnSubscribe {
  private final String resourceName;
  private final String bucket;

  public CouchbaseOnSubscribe(
      final Observable originalObservable, final Method method, final String bucket) {
    super(originalObservable, "couchbase.call", DECORATE, CLIENT);

    final Class<?> declaringClass = method.getDeclaringClass();
    final String className =
        declaringClass.getSimpleName().replace("CouchbaseAsync", "").replace("DefaultAsync", "");
    resourceName = className + "." + method.getName();
    this.bucket = bucket;
  }

  @Override
  protected void afterStart(final Span span) {
    super.afterStart(span);

    span.setAttribute(MoreTags.RESOURCE_NAME, resourceName);

    if (bucket != null) {
      span.setAttribute("bucket", bucket);
    }
  }
}
