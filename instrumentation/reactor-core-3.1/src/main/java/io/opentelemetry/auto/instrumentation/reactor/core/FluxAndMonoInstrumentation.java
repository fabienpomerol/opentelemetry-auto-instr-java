package io.opentelemetry.auto.instrumentation.reactor.core;

import static io.opentelemetry.auto.tooling.ByteBuddyElementMatchers.safeExtendsClass;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isAbstract;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.tooling.Instrumenter;
import java.util.Map;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class FluxAndMonoInstrumentation extends Instrumenter.Default {

  public FluxAndMonoInstrumentation() {
    super("reactor-core");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".ReactorCoreAdviceUtils",
      packageName + ".ReactorCoreAdviceUtils$TracingSubscriber",
      "io.opentelemetry.auto.decorator.BaseDecorator",
      packageName + ".ReactorCoreDecorator"
    };
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return not(isAbstract())
        .and(
            safeExtendsClass(
                named("reactor.core.publisher.Mono").or(named("reactor.core.publisher.Flux"))));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod()
            .and(isPublic())
            .and(named("subscribe"))
            .and(takesArgument(0, named("reactor.core.CoreSubscriber")))
            .and(takesArguments(1)),
        // Cannot reference class directly here because it would lead to class load failure on Java7
        packageName + ".FluxAndMonoSubscribeAdvice");
  }
}
