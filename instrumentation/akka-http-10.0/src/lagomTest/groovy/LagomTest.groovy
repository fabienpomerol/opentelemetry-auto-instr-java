import akka.NotUsed
import akka.stream.javadsl.Source
import akka.stream.testkit.TestSubscriber.Probe
import akka.stream.testkit.javadsl.TestSink
import io.opentelemetry.auto.instrumentation.api.MoreTags
import io.opentelemetry.auto.instrumentation.api.SpanTypes
import io.opentelemetry.auto.instrumentation.api.Tags
import io.opentelemetry.auto.test.AgentTestRunner
import play.inject.guice.GuiceApplicationBuilder
import spock.lang.Shared

import java.util.function.Function

import static com.lightbend.lagom.javadsl.testkit.ServiceTest.TestServer
import static com.lightbend.lagom.javadsl.testkit.ServiceTest.defaultSetup
import static com.lightbend.lagom.javadsl.testkit.ServiceTest.startServer
import static io.opentelemetry.trace.Span.Kind.SERVER

class LagomTest extends AgentTestRunner {

  @Shared
  private TestServer server

  def setupSpec() {
    server = startServer(defaultSetup()
      .withCluster(false)
      .withPersistence(false)
      .withCassandra(false)
      .withJdbc(false)
      .configureBuilder(
        new Function<GuiceApplicationBuilder, GuiceApplicationBuilder>() {
          @Override
          GuiceApplicationBuilder apply(GuiceApplicationBuilder builder) {
            return builder
              .bindings(new ServiceTestModule())
          }
        }))
  }

  def cleanupSpec() {
    server.stop()
  }

  def "normal request traces"() {
    setup:
    EchoService service = server.client(EchoService)

    Source<String, NotUsed> input =
      Source.from(Arrays.asList("msg1", "msg2", "msg3"))
        .concat(Source.maybe())
    Source<String, NotUsed> output = service.echo().invoke(input)
      .toCompletableFuture().get()
    Probe<String> probe = output.runWith(TestSink.probe(server.system()), server.materializer())
    probe.request(10)
    probe.expectNext("msg1")
    probe.expectNext("msg2")
    probe.expectNext("msg3")
    probe.cancel()

    expect:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          operationName "akka-http.request"
          spanKind SERVER
          errored false
          tags {
            "$MoreTags.SPAN_TYPE" SpanTypes.HTTP_SERVER
            "$Tags.COMPONENT" "akka-http-server"
            "$Tags.HTTP_URL" "ws://localhost:${server.port()}/echo"
            "$Tags.HTTP_METHOD" "GET"
            "$Tags.HTTP_STATUS" 101
          }
        }
        span(1) {
          childOf span(0)
          operationName 'tracedMethod'
          tags {
          }
        }
      }
    }
  }

  def "error traces"() {
    setup:
    EchoService service = server.client(EchoService)

    Source<String, NotUsed> input =
      Source.from(Arrays.asList("msg1", "msg2", "msg3"))
        .concat(Source.maybe())
    try {
      service.error().invoke(input).toCompletableFuture().get()
    } catch (Exception e) {
    }

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          operationName "akka-http.request"
          spanKind SERVER
          errored true
          tags {
            "$MoreTags.SPAN_TYPE" SpanTypes.HTTP_SERVER
            "$Tags.COMPONENT" "akka-http-server"
            "$Tags.HTTP_URL" "ws://localhost:${server.port()}/error"
            "$Tags.HTTP_METHOD" "GET"
            "$Tags.HTTP_STATUS" 500
          }
        }
      }
    }
  }
}
