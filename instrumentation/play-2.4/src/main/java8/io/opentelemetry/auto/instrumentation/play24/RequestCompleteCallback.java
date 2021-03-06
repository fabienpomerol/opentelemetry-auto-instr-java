package io.opentelemetry.auto.instrumentation.play24;

import static io.opentelemetry.auto.instrumentation.play24.PlayHttpServerDecorator.DECORATE;

import io.opentelemetry.trace.Span;
import lombok.extern.slf4j.Slf4j;
import play.api.mvc.Result;
import scala.util.Try;

@Slf4j
public class RequestCompleteCallback extends scala.runtime.AbstractFunction1<Try<Result>, Object> {
  private final Span span;

  public RequestCompleteCallback(final Span span) {
    this.span = span;
  }

  @Override
  public Object apply(final Try<Result> result) {
    try {
      if (result.isFailure()) {
        DECORATE.onError(span, result.failed().get());
      } else {
        DECORATE.onResponse(span, result.get());
      }
      DECORATE.beforeFinish(span);
    } catch (final Throwable t) {
      log.debug("error in play instrumentation", t);
    } finally {
      span.end();
    }
    return null;
  }
}
