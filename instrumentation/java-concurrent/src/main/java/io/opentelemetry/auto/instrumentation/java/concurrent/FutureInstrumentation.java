package io.opentelemetry.auto.instrumentation.java.concurrent;

import static io.opentelemetry.auto.tooling.ByteBuddyElementMatchers.safeHasInterface;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.bootstrap.ContextStore;
import io.opentelemetry.auto.bootstrap.InstrumentationContext;
import io.opentelemetry.auto.bootstrap.instrumentation.java.concurrent.State;
import io.opentelemetry.auto.tooling.Instrumenter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.Future;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@Slf4j
@AutoService(Instrumenter.class)
public final class FutureInstrumentation extends Instrumenter.Default {

  /**
   * Only apply executor instrumentation to whitelisted executors. In the future, this restriction
   * may be lifted to include all executors.
   */
  private static final Collection<String> WHITELISTED_FUTURES;

  static {
    final String[] whitelist = {
      "akka.dispatch.forkjoin.ForkJoinTask",
      "akka.dispatch.forkjoin.ForkJoinTask$AdaptedCallable",
      "akka.dispatch.forkjoin.ForkJoinTask$AdaptedRunnable",
      "akka.dispatch.forkjoin.ForkJoinTask$AdaptedRunnableAction",
      "akka.dispatch.ForkJoinExecutorConfigurator$AkkaForkJoinTask",
      "akka.dispatch.Mailbox",
      "com.google.common.util.concurrent.AbstractFuture",
      "com.google.common.util.concurrent.AbstractFuture$TrustedFuture",
      "com.google.common.util.concurrent.ListenableFutureTask",
      "com.google.common.util.concurrent.SettableFuture",
      "io.netty.util.concurrent.CompleteFuture",
      "io.netty.util.concurrent.FailedFuture",
      "io.netty.util.concurrent.ScheduledFutureTask",
      "java.util.concurrent.CompletableFuture$BiApply",
      "java.util.concurrent.CompletableFuture$BiCompletion",
      "java.util.concurrent.CompletableFuture$BiRelay",
      "java.util.concurrent.CompletableFuture$ThreadPerTaskExecutor",
      "java.util.concurrent.CountedCompleter",
      "java.util.concurrent.ExecutorCompletionService$QueueingFuture",
      "java.util.concurrent.ForkJoinTask",
      "java.util.concurrent.ForkJoinTask$AdaptedCallable",
      "java.util.concurrent.ForkJoinTask$RunnableExecuteAction",
      "java.util.concurrent.FutureTask",
      "java.util.concurrent.RecursiveAction",
      "java.util.concurrent.ScheduledThreadPoolExecutor$ScheduledFutureTask",
      "scala.collection.parallel.AdaptiveWorkStealingForkJoinTasks$WrappedTask",
      "scala.concurrent.forkjoin.ForkJoinTask",
      "scala.concurrent.forkjoin.ForkJoinTask$AdaptedCallable",
      "scala.concurrent.forkjoin.ForkJoinTask$AdaptedRunnable",
      "scala.concurrent.forkjoin.ForkJoinTask$AdaptedRunnableAction",
      "scala.concurrent.impl.ExecutionContextImpl$AdaptedForkJoinTask",
    };
    WHITELISTED_FUTURES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(whitelist)));
  }

  public FutureInstrumentation() {
    super(AbstractExecutorInstrumentation.EXEC_NAME);
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return not(isInterface())
        .and(
            new ElementMatcher<TypeDescription>() {
              @Override
              public boolean matches(final TypeDescription target) {
                final boolean whitelisted = WHITELISTED_FUTURES.contains(target.getName());
                if (!whitelisted) {
                  log.debug("Skipping future instrumentation for {}", target.getName());
                }
                return whitelisted;
              }
            })
        .and(safeHasInterface(named(Future.class.getName()))); // Apply expensive matcher last.
  }

  @Override
  public Map<String, String> contextStore() {
    final Map<String, String> map = new HashMap<>();
    map.put(Future.class.getName(), State.class.getName());
    return Collections.unmodifiableMap(map);
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        named("cancel").and(returns(boolean.class)),
        FutureInstrumentation.class.getName() + "$CanceledFutureAdvice");
  }

  public static class CanceledFutureAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void exit(@Advice.This final Future<?> future) {
      // Try to clear parent span even if future was not cancelled:
      // the expectation is that parent span should be cleared after 'cancel'
      // is called, one way or another
      final ContextStore<Future, State> contextStore =
          InstrumentationContext.get(Future.class, State.class);
      final State state = contextStore.get(future);
      if (state != null) {
        state.clearParentSpan();
      }
    }
  }
}
