package io.opentelemetry.auto.instrumentation.trace_annotation;

import static io.opentelemetry.auto.instrumentation.trace_annotation.TraceConfigInstrumentation.PACKAGE_CLASS_NAME_REGEX;
import static io.opentelemetry.auto.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.none;

import com.google.auto.service.AutoService;
import com.google.common.collect.Sets;
import io.opentelemetry.auto.config.Config;
import io.opentelemetry.auto.tooling.Instrumenter;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@Slf4j
@AutoService(Instrumenter.class)
public final class TraceAnnotationsInstrumentation extends Instrumenter.Default {

  static final String CONFIG_FORMAT =
      "(?:\\s*"
          + PACKAGE_CLASS_NAME_REGEX
          + "\\s*;)*\\s*"
          + PACKAGE_CLASS_NAME_REGEX
          + "\\s*;?\\s*";

  private static final String[] DEFAULT_ANNOTATIONS =
      new String[] {
        "com.newrelic.api.agent.Trace",
        "kamon.annotation.Trace",
        "com.tracelytics.api.ext.LogMethod",
        "io.opentracing.contrib.dropwizard.Trace",
        "org.springframework.cloud.sleuth.annotation.NewSpan"
      };

  private final Set<String> additionalTraceAnnotations;
  private final ElementMatcher.Junction<NamedElement> methodTraceMatcher;

  public TraceAnnotationsInstrumentation() {
    super("trace", "trace-annotation");

    final String configString = Config.get().getTraceAnnotations();
    if (configString == null) {
      additionalTraceAnnotations =
          Collections.unmodifiableSet(Sets.<String>newHashSet(DEFAULT_ANNOTATIONS));
    } else if (configString.trim().isEmpty()) {
      additionalTraceAnnotations = Collections.emptySet();
    } else if (!configString.matches(CONFIG_FORMAT)) {
      log.warn(
          "Invalid trace annotations config '{}'. Must match 'package.Annotation$Name;*'.",
          configString);
      additionalTraceAnnotations = Collections.emptySet();
    } else {
      final Set<String> annotations = Sets.newHashSet();
      final String[] annotationClasses = configString.split(";", -1);
      for (final String annotationClass : annotationClasses) {
        if (!annotationClass.trim().isEmpty()) {
          annotations.add(annotationClass.trim());
        }
      }
      additionalTraceAnnotations = Collections.unmodifiableSet(annotations);
    }

    if (additionalTraceAnnotations.isEmpty()) {
      methodTraceMatcher = none();
    } else {
      ElementMatcher.Junction<NamedElement> methodTraceMatcher = null;
      for (final String annotationName : additionalTraceAnnotations) {
        if (methodTraceMatcher == null) {
          methodTraceMatcher = named(annotationName);
        } else {
          methodTraceMatcher = methodTraceMatcher.or(named(annotationName));
        }
      }
      this.methodTraceMatcher = methodTraceMatcher;
    }
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return safeHasSuperType(declaresMethod(isAnnotatedWith(methodTraceMatcher)));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "io.opentelemetry.auto.decorator.BaseDecorator", packageName + ".TraceDecorator",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(isAnnotatedWith(methodTraceMatcher), packageName + ".TraceAdvice");
  }
}
