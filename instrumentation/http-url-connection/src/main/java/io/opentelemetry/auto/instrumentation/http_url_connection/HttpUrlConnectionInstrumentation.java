package io.opentelemetry.auto.instrumentation.http_url_connection;

import static io.opentelemetry.auto.instrumentation.http_url_connection.HeadersInjectAdapter.SETTER;
import static io.opentelemetry.auto.instrumentation.http_url_connection.HttpUrlConnectionDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.http_url_connection.HttpUrlConnectionDecorator.TRACER;
import static io.opentelemetry.auto.tooling.ByteBuddyElementMatchers.safeExtendsClass;
import static io.opentelemetry.trace.Span.Kind.CLIENT;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.bootstrap.CallDepthThreadLocalMap;
import io.opentelemetry.auto.bootstrap.ContextStore;
import io.opentelemetry.auto.bootstrap.InstrumentationContext;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import java.net.HttpURLConnection;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

@AutoService(Instrumenter.class)
public class HttpUrlConnectionInstrumentation extends Instrumenter.Default {

  public HttpUrlConnectionInstrumentation() {
    super("httpurlconnection");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return nameStartsWith("java.net.")
        .or(ElementMatchers.<TypeDescription>nameStartsWith("sun.net"))
        // This class is a simple delegator. Skip because it does not update its `connected` field.
        .and(not(named("sun.net.www.protocol.https.HttpsURLConnectionImpl")))
        .and(safeExtendsClass(named("java.net.HttpURLConnection")));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "io.opentelemetry.auto.decorator.BaseDecorator",
      "io.opentelemetry.auto.decorator.ClientDecorator",
      "io.opentelemetry.auto.decorator.HttpClientDecorator",
      packageName + ".HttpUrlConnectionDecorator",
      packageName + ".HeadersInjectAdapter",
      HttpUrlConnectionInstrumentation.class.getName() + "$HttpUrlState",
      HttpUrlConnectionInstrumentation.class.getName() + "$HttpUrlState$1",
    };
  }

  @Override
  public Map<String, String> contextStore() {
    return singletonMap("java.net.HttpURLConnection", getClass().getName() + "$HttpUrlState");
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod()
            .and(isPublic())
            .and(named("connect").or(named("getOutputStream")).or(named("getInputStream"))),
        HttpUrlConnectionInstrumentation.class.getName() + "$HttpUrlConnectionAdvice");
  }

  public static class HttpUrlConnectionAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static HttpUrlState methodEnter(
        @Advice.This final HttpURLConnection thiz,
        @Advice.FieldValue("connected") final boolean connected) {

      final int callDepth = CallDepthThreadLocalMap.incrementCallDepth(HttpURLConnection.class);
      if (callDepth > 0) {
        return null;
      }

      final ContextStore<HttpURLConnection, HttpUrlState> contextStore =
          InstrumentationContext.get(HttpURLConnection.class, HttpUrlState.class);
      final HttpUrlState state = contextStore.putIfAbsent(thiz, HttpUrlState.FACTORY);

      synchronized (state) {
        if (!state.hasSpan() && !state.isFinished()) {
          final Span span = state.start(thiz);
          if (!connected) {
            TRACER.getHttpTextFormat().inject(span.getContext(), thiz, SETTER);
          }
        }
        return state;
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Enter final HttpUrlState state,
        @Advice.FieldValue("responseCode") final int responseCode,
        @Advice.Thrown final Throwable throwable,
        @Advice.Origin("#m") final String methodName) {

      if (state == null) {
        return;
      }
      CallDepthThreadLocalMap.reset(HttpURLConnection.class);

      synchronized (state) {
        if (state.hasSpan() && !state.isFinished()) {
          if (throwable != null) {
            state.finishSpan(throwable);
          } else if ("getInputStream".equals(methodName)) {
            state.finishSpan(responseCode);
          }
        }
      }
    }
  }

  public static class HttpUrlState {

    public static final String OPERATION_NAME = "http.request";

    public static final ContextStore.Factory<HttpUrlState> FACTORY =
        new ContextStore.Factory<HttpUrlState>() {
          @Override
          public HttpUrlState create() {
            return new HttpUrlState();
          }
        };

    private volatile Span span = null;
    private volatile boolean finished = false;

    public Span start(final HttpURLConnection connection) {
      span = TRACER.spanBuilder(OPERATION_NAME).setSpanKind(CLIENT).startSpan();
      try (final Scope scope = TRACER.withSpan(span)) {
        DECORATE.afterStart(span);
        DECORATE.onRequest(span, connection);
        return span;
      }
    }

    public boolean hasSpan() {
      return span != null;
    }

    public boolean isFinished() {
      return finished;
    }

    public void finish() {
      finished = true;
    }

    public void finishSpan(final Throwable throwable) {
      try (final Scope scope = TRACER.withSpan(span)) {
        DECORATE.onError(span, throwable);
        DECORATE.beforeFinish(span);
        span.end();
        span = null;
        finished = true;
      }
    }

    public void finishSpan(final int responseCode) {
      /*
       * responseCode field is sometimes not populated.
       * We can't call getResponseCode() due to some unwanted side-effects
       * (e.g. breaks getOutputStream).
       */
      if (responseCode > 0) {
        try (final Scope scope = TRACER.withSpan(span)) {
          DECORATE.onResponse(span, responseCode);
          DECORATE.beforeFinish(span);
          span.end();
          span = null;
          finished = true;
        }
      }
    }
  }
}
