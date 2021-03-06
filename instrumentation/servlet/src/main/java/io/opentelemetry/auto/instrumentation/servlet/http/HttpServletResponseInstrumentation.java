package io.opentelemetry.auto.instrumentation.servlet.http;

import static io.opentelemetry.auto.instrumentation.servlet.http.HttpServletResponseDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.servlet.http.HttpServletResponseDecorator.TRACER;
import static io.opentelemetry.auto.tooling.ByteBuddyElementMatchers.safeHasInterface;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.bootstrap.InstrumentationContext;
import io.opentelemetry.auto.instrumentation.api.MoreTags;
import io.opentelemetry.auto.instrumentation.api.SpanWithScope;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.trace.Span;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class HttpServletResponseInstrumentation extends Instrumenter.Default {
  public HttpServletResponseInstrumentation() {
    super("servlet", "servlet-response");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "io.opentelemetry.auto.decorator.BaseDecorator",
      packageName + ".HttpServletResponseDecorator",
    };
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return not(isInterface())
        .and(safeHasInterface(named("javax.servlet.http.HttpServletResponse")));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(named("sendError").or(named("sendRedirect")), SendAdvice.class.getName());
  }

  @Override
  public Map<String, String> contextStore() {
    return singletonMap(
        "javax.servlet.http.HttpServletResponse", "javax.servlet.http.HttpServletRequest");
  }

  public static class SendAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static SpanWithScope start(
        @Advice.Origin("#m") final String method, @Advice.This final HttpServletResponse resp) {
      if (!TRACER.getCurrentSpan().getContext().isValid()) {
        // Don't want to generate a new top-level span
        return null;
      }

      final HttpServletRequest req =
          InstrumentationContext.get(HttpServletResponse.class, HttpServletRequest.class).get(resp);
      if (req == null) {
        // Missing the response->request linking... probably in a wrapped instance.
        return null;
      }

      final Span span = TRACER.spanBuilder("servlet.response").startSpan();
      DECORATE.afterStart(span);

      span.setAttribute(MoreTags.RESOURCE_NAME, "HttpServletResponse." + method);

      return new SpanWithScope(span, TRACER.withSpan(span));
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Enter final SpanWithScope spanWithScope, @Advice.Thrown final Throwable throwable) {
      if (spanWithScope == null) {
        return;
      }
      final Span span = spanWithScope.getSpan();
      DECORATE.onError(span, throwable);
      DECORATE.beforeFinish(span);
      span.end();
      spanWithScope.closeScope();
    }
  }
}
