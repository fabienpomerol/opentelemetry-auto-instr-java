package io.opentelemetry.auto.tooling

import io.opentelemetry.auto.common.exec.CommonTaskExecutor
import io.opentelemetry.auto.util.gc.GCUtils
import io.opentelemetry.auto.util.test.AgentSpecification
import spock.lang.Subject

import java.lang.ref.WeakReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

import static java.util.concurrent.TimeUnit.MILLISECONDS

class CleanerTest extends AgentSpecification {

  @Subject
  def cleaner = new Cleaner()

  def "test scheduling"() {
    setup:
    def latch = new CountDownLatch(2)
    def target = new Object()
    def action = new Cleaner.Adapter<Object>() {
      @Override
      void clean(Object t) {
        latch.countDown()
      }
    }

    expect:
    !CommonTaskExecutor.INSTANCE.isShutdown()

    when:
    cleaner.scheduleCleaning(target, action, 10, MILLISECONDS)

    then:
    latch.await(200, MILLISECONDS)
  }

  def "test canceling"() {
    setup:
    def callCount = new AtomicInteger()
    def target = new WeakReference(new Object())
    def action = new Cleaner.Adapter<Object>() {
      @Override
      void clean(Object t) {
        callCount.incrementAndGet()
      }
    }

    expect:
    !CommonTaskExecutor.INSTANCE.isShutdown()

    when:
    cleaner.scheduleCleaning(target.get(), action, 10, MILLISECONDS)
    GCUtils.awaitGC(target)
    Thread.sleep(1)
    def snapshot = callCount.get()
    Thread.sleep(11)

    then:
    snapshot == callCount.get()
  }

  def "test null target"() {
    setup:
    def callCount = new AtomicInteger()
    def action = new Cleaner.Adapter<Object>() {
      @Override
      void clean(Object t) {
        callCount.incrementAndGet()
      }
    }

    expect:
    !CommonTaskExecutor.INSTANCE.isShutdown()

    when:
    cleaner.scheduleCleaning(null, action, 10, MILLISECONDS)
    Thread.sleep(11)

    then:
    callCount.get() == 0
  }
}
