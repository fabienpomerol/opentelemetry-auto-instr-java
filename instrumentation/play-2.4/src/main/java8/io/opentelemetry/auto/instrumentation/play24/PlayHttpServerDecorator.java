package io.opentelemetry.auto.instrumentation.play24;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.decorator.HttpServerDecorator;
import io.opentelemetry.auto.instrumentation.api.MoreTags;
import io.opentelemetry.auto.instrumentation.api.Tags;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Status;
import io.opentelemetry.trace.Tracer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.UndeclaredThrowableException;
import java.net.URI;
import java.net.URISyntaxException;
import lombok.extern.slf4j.Slf4j;
import play.api.mvc.Request;
import play.api.mvc.Result;
import scala.Option;

@Slf4j
public class PlayHttpServerDecorator extends HttpServerDecorator<Request, Request, Result> {
  public static final PlayHttpServerDecorator DECORATE = new PlayHttpServerDecorator();

  public static final Tracer TRACER =
      OpenTelemetry.getTracerFactory().get("io.opentelemetry.auto.play-2.4");

  @Override
  protected String getComponentName() {
    return "play-action";
  }

  @Override
  protected String method(final Request httpRequest) {
    return httpRequest.method();
  }

  @Override
  protected URI url(final Request request) throws URISyntaxException {
    return new URI((request.secure() ? "https://" : "http://") + request.host() + request.uri());
  }

  @Override
  protected String peerHostIP(final Request request) {
    return request.remoteAddress();
  }

  @Override
  protected Integer peerPort(final Request request) {
    return null;
  }

  @Override
  protected Integer status(final Result httpResponse) {
    return httpResponse.header().status();
  }

  @Override
  public Span onRequest(final Span span, final Request request) {
    super.onRequest(span, request);
    if (request != null) {
      // more about routes here:
      // https://github.com/playframework/playframework/blob/master/documentation/manual/releases/release26/migration26/Migration26.md#router-tags-are-now-attributes
      final Option pathOption = request.tags().get("ROUTE_PATTERN");
      if (!pathOption.isEmpty()) {
        final String path = (String) pathOption.get();
        span.setAttribute(MoreTags.RESOURCE_NAME, request.method() + " " + path);
      }
    }
    return span;
  }

  @Override
  public Span onError(final Span span, Throwable throwable) {
    span.setAttribute(Tags.HTTP_STATUS, 500);
    span.setStatus(Status.UNKNOWN);
    if (throwable != null
        // This can be moved to instanceof check when using Java 8.
        && throwable.getClass().getName().equals("java.util.concurrent.CompletionException")
        && throwable.getCause() != null) {
      throwable = throwable.getCause();
    }
    while ((throwable instanceof InvocationTargetException
            || throwable instanceof UndeclaredThrowableException)
        && throwable.getCause() != null) {
      throwable = throwable.getCause();
    }
    return super.onError(span, throwable);
  }
}
