package io.opentelemetry.auto.instrumentation.hibernate.core.v4_0;

import static io.opentelemetry.auto.instrumentation.hibernate.HibernateDecorator.DECORATOR;
import static io.opentelemetry.auto.instrumentation.hibernate.SessionMethodUtils.SCOPE_ONLY_METHODS;
import static io.opentelemetry.auto.tooling.ByteBuddyElementMatchers.safeHasInterface;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.bootstrap.ContextStore;
import io.opentelemetry.auto.bootstrap.InstrumentationContext;
import io.opentelemetry.auto.instrumentation.api.SpanWithScope;
import io.opentelemetry.auto.instrumentation.hibernate.SessionMethodUtils;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.trace.Span;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;
import org.hibernate.Criteria;
import org.hibernate.Query;
import org.hibernate.SharedSessionContract;
import org.hibernate.Transaction;

@AutoService(Instrumenter.class)
public class SessionInstrumentation extends AbstractHibernateInstrumentation {

  @Override
  public Map<String, String> contextStore() {
    final Map<String, String> map = new HashMap<>();
    map.put("org.hibernate.SharedSessionContract", Span.class.getName());
    map.put("org.hibernate.Query", Span.class.getName());
    map.put("org.hibernate.Transaction", Span.class.getName());
    map.put("org.hibernate.Criteria", Span.class.getName());
    return Collections.unmodifiableMap(map);
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return not(isInterface()).and(safeHasInterface(named("org.hibernate.SharedSessionContract")));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    final Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
    transformers.put(
        isMethod().and(named("close")).and(takesArguments(0)),
        SessionInstrumentation.class.getName() + "$SessionCloseAdvice");

    // Session synchronous methods we want to instrument.
    transformers.put(
        isMethod()
            .and(
                named("save")
                    .or(named("replicate"))
                    .or(named("saveOrUpdate"))
                    .or(named("update"))
                    .or(named("merge"))
                    .or(named("persist"))
                    .or(named("lock"))
                    .or(named("refresh"))
                    .or(named("insert"))
                    .or(named("delete"))
                    // Iterator methods.
                    .or(named("iterate"))
                    // Lazy-load methods.
                    .or(named("immediateLoad"))
                    .or(named("internalLoad"))),
        SessionInstrumentation.class.getName() + "$SessionMethodAdvice");
    // Handle the non-generic 'get' separately.
    transformers.put(
        isMethod()
            .and(named("get"))
            .and(returns(named("java.lang.Object")))
            .and(takesArgument(0, named("java.lang.String"))),
        SessionInstrumentation.class.getName() + "$SessionMethodAdvice");

    // These methods return some object that we want to instrument, and so the Advice will pin the
    // current Span to the returned object using a ContextStore.
    transformers.put(
        isMethod()
            .and(named("beginTransaction").or(named("getTransaction")))
            .and(returns(named("org.hibernate.Transaction"))),
        SessionInstrumentation.class.getName() + "$GetTransactionAdvice");

    transformers.put(
        isMethod().and(returns(safeHasInterface(named("org.hibernate.Query")))),
        SessionInstrumentation.class.getName() + "$GetQueryAdvice");

    transformers.put(
        isMethod().and(returns(safeHasInterface(named("org.hibernate.Criteria")))),
        SessionInstrumentation.class.getName() + "$GetCriteriaAdvice");

    return transformers;
  }

  public static class SessionCloseAdvice extends V4Advice {

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void closeSession(
        @Advice.This final SharedSessionContract session,
        @Advice.Thrown final Throwable throwable) {

      final ContextStore<SharedSessionContract, Span> contextStore =
          InstrumentationContext.get(SharedSessionContract.class, Span.class);
      final Span sessionSpan = contextStore.get(session);
      if (sessionSpan == null) {
        return;
      }

      DECORATOR.onError(sessionSpan, throwable);
      DECORATOR.beforeFinish(sessionSpan);
      sessionSpan.end();
    }
  }

  public static class SessionMethodAdvice extends V4Advice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static SpanWithScope startMethod(
        @Advice.This final SharedSessionContract session,
        @Advice.Origin("#m") final String name,
        @Advice.Argument(0) final Object entity) {

      final boolean startSpan = !SCOPE_ONLY_METHODS.contains(name);
      final ContextStore<SharedSessionContract, Span> contextStore =
          InstrumentationContext.get(SharedSessionContract.class, Span.class);
      return SessionMethodUtils.startScopeFrom(
          contextStore, session, "hibernate." + name, entity, startSpan);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void endMethod(
        @Advice.Enter final SpanWithScope spanWithScope,
        @Advice.Thrown final Throwable throwable,
        @Advice.Return(typing = Assigner.Typing.DYNAMIC) final Object returned) {

      SessionMethodUtils.closeScope(spanWithScope, throwable, returned);
    }
  }

  public static class GetQueryAdvice extends V4Advice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void getQuery(
        @Advice.This final SharedSessionContract session, @Advice.Return final Query query) {

      final ContextStore<SharedSessionContract, Span> sessionContextStore =
          InstrumentationContext.get(SharedSessionContract.class, Span.class);
      final ContextStore<Query, Span> queryContextStore =
          InstrumentationContext.get(Query.class, Span.class);

      SessionMethodUtils.attachSpanFromStore(
          sessionContextStore, session, queryContextStore, query);
    }
  }

  public static class GetTransactionAdvice extends V4Advice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void getTransaction(
        @Advice.This final SharedSessionContract session,
        @Advice.Return final Transaction transaction) {

      final ContextStore<SharedSessionContract, Span> sessionContextStore =
          InstrumentationContext.get(SharedSessionContract.class, Span.class);
      final ContextStore<Transaction, Span> transactionContextStore =
          InstrumentationContext.get(Transaction.class, Span.class);

      SessionMethodUtils.attachSpanFromStore(
          sessionContextStore, session, transactionContextStore, transaction);
    }
  }

  public static class GetCriteriaAdvice extends V4Advice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void getCriteria(
        @Advice.This final SharedSessionContract session, @Advice.Return final Criteria criteria) {

      final ContextStore<SharedSessionContract, Span> sessionContextStore =
          InstrumentationContext.get(SharedSessionContract.class, Span.class);
      final ContextStore<Criteria, Span> criteriaContextStore =
          InstrumentationContext.get(Criteria.class, Span.class);

      SessionMethodUtils.attachSpanFromStore(
          sessionContextStore, session, criteriaContextStore, criteria);
    }
  }
}
