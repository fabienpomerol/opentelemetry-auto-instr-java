package io.opentelemetry.auto.instrumentation.http_url_connection;

import static io.opentelemetry.auto.instrumentation.http_url_connection.HttpUrlConnectionDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.http_url_connection.HttpUrlConnectionDecorator.TRACER;
import static io.opentelemetry.trace.Span.Kind.CLIENT;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.is;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.bootstrap.InternalJarURLHandler;
import io.opentelemetry.auto.config.Config;
import io.opentelemetry.auto.instrumentation.api.MoreTags;
import io.opentelemetry.auto.instrumentation.api.SpanTypes;
import io.opentelemetry.auto.instrumentation.api.Tags;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import java.net.URL;
import java.net.URLStreamHandler;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public class UrlInstrumentation extends Instrumenter.Default {

  public static final String COMPONENT = "UrlConnection";

  public UrlInstrumentation() {
    super("urlconnection", "httpurlconnection");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return is(URL.class);
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod().and(isPublic()).and(named("openConnection")),
        UrlInstrumentation.class.getName() + "$ConnectionErrorAdvice");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "io.opentelemetry.auto.decorator.BaseDecorator",
      "io.opentelemetry.auto.decorator.ClientDecorator",
      "io.opentelemetry.auto.decorator.HttpClientDecorator",
      packageName + ".HttpUrlConnectionDecorator",
    };
  }

  public static class ConnectionErrorAdvice {

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void errorSpan(
        @Advice.This final URL url,
        @Advice.Thrown final Throwable throwable,
        @Advice.FieldValue("handler") final URLStreamHandler handler) {
      if (throwable != null) {
        // Various agent components end up calling `openConnection` indirectly
        // when loading classes. Avoid tracing these calls.
        final boolean disableTracing = handler instanceof InternalJarURLHandler;
        if (disableTracing) {
          return;
        }

        String protocol = url.getProtocol();
        protocol = protocol != null ? protocol : "url";

        final Span span = TRACER.spanBuilder(protocol + ".request").setSpanKind(CLIENT).startSpan();
        span.setAttribute(MoreTags.SPAN_TYPE, SpanTypes.HTTP_CLIENT);
        span.setAttribute(Tags.COMPONENT, COMPONENT);

        try (final Scope scope = TRACER.withSpan(span)) {
          span.setAttribute(Tags.HTTP_URL, url.toString());
          span.setAttribute(Tags.PEER_PORT, url.getPort() == -1 ? 80 : url.getPort());
          final String host = url.getHost();
          if (host != null && !host.isEmpty()) {
            span.setAttribute(Tags.PEER_HOSTNAME, host);
            if (Config.get().isHttpClientSplitByDomain()) {
              span.setAttribute(MoreTags.SERVICE_NAME, host);
            }
          }

          DECORATE.onError(span, throwable);
          span.end();
        }
      }
    }
  }
}
