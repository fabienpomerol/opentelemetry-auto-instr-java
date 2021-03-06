package io.opentelemetry.auto.decorator;

import io.opentelemetry.auto.instrumentation.api.MoreTags;
import io.opentelemetry.auto.instrumentation.api.Tags;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Status;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutionException;

public abstract class BaseDecorator {

  protected BaseDecorator() {}

  protected abstract String getSpanType();

  protected abstract String getComponentName();

  public Span afterStart(final Span span) {
    assert span != null;
    final String spanType = getSpanType();
    if (spanType != null) {
      span.setAttribute(MoreTags.SPAN_TYPE, spanType);
    }
    final String component = getComponentName();
    if (component != null) {
      span.setAttribute(Tags.COMPONENT, component);
    }
    return span;
  }

  public Span beforeFinish(final Span span) {
    assert span != null;
    return span;
  }

  public Span onError(final Span span, final Throwable throwable) {
    assert span != null;
    if (throwable != null) {
      span.setStatus(Status.UNKNOWN);
      addThrowable(
          span, throwable instanceof ExecutionException ? throwable.getCause() : throwable);
    }
    return span;
  }

  public Span onPeerConnection(final Span span, final InetSocketAddress remoteConnection) {
    assert span != null;
    if (remoteConnection != null) {
      onPeerConnection(span, remoteConnection.getAddress());

      span.setAttribute(Tags.PEER_HOSTNAME, remoteConnection.getHostName());
      span.setAttribute(Tags.PEER_PORT, remoteConnection.getPort());
    }
    return span;
  }

  public Span onPeerConnection(final Span span, final InetAddress remoteAddress) {
    assert span != null;
    if (remoteAddress != null) {
      span.setAttribute(Tags.PEER_HOSTNAME, remoteAddress.getHostName());
      if (remoteAddress instanceof Inet4Address) {
        span.setAttribute(Tags.PEER_HOST_IPV4, remoteAddress.getHostAddress());
      } else if (remoteAddress instanceof Inet6Address) {
        span.setAttribute(Tags.PEER_HOST_IPV6, remoteAddress.getHostAddress());
      }
    }
    return span;
  }

  public static void addThrowable(final Span span, final Throwable throwable) {
    final String message = throwable.getMessage();
    if (message != null) {
      span.setAttribute(MoreTags.ERROR_MSG, message);
    }
    span.setAttribute(MoreTags.ERROR_TYPE, throwable.getClass().getName());

    final StringWriter errorString = new StringWriter();
    throwable.printStackTrace(new PrintWriter(errorString));
    span.setAttribute(MoreTags.ERROR_STACK, errorString.toString());
  }

  /**
   * This method is used to generate an acceptable span (operation) name based on a given method
   * reference. Anonymous classes are named based on their parent.
   *
   * @param method
   * @return
   */
  public String spanNameForMethod(final Method method) {
    return spanNameForClass(method.getDeclaringClass()) + "." + method.getName();
  }

  /**
   * This method is used to generate an acceptable span (operation) name based on a given class
   * reference. Anonymous classes are named based on their parent.
   *
   * @param clazz
   * @return
   */
  public String spanNameForClass(final Class clazz) {
    if (!clazz.isAnonymousClass()) {
      return clazz.getSimpleName();
    }
    String className = clazz.getName();
    if (clazz.getPackage() != null) {
      final String pkgName = clazz.getPackage().getName();
      if (!pkgName.isEmpty()) {
        className = clazz.getName().replace(pkgName, "").substring(1);
      }
    }
    return className;
  }
}
