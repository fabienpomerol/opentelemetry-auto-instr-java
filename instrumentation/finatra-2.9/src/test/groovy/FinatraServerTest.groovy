import com.twitter.finatra.http.HttpServer
import com.twitter.util.Await
import com.twitter.util.Closable
import com.twitter.util.Duration
import io.opentelemetry.auto.instrumentation.api.MoreTags
import io.opentelemetry.auto.instrumentation.api.SpanTypes
import io.opentelemetry.auto.instrumentation.api.Tags
import io.opentelemetry.auto.instrumentation.finatra.FinatraDecorator
import io.opentelemetry.auto.test.asserts.TraceAssert
import io.opentelemetry.auto.test.base.HttpServerTest
import io.opentelemetry.sdk.trace.SpanData

import java.util.concurrent.TimeoutException

import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.ERROR
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.SUCCESS
import static io.opentelemetry.trace.Span.Kind.INTERNAL
import static io.opentelemetry.trace.Span.Kind.SERVER

class FinatraServerTest extends HttpServerTest<HttpServer, FinatraDecorator> {
  private static final Duration TIMEOUT = Duration.fromSeconds(5)
  private static final long STARTUP_TIMEOUT = 40 * 1000

  static closeAndWait(Closable closable) {
    if (closable != null) {
      Await.ready(closable.close(), TIMEOUT)
    }
  }

  @Override
  HttpServer startServer(int port) {
    HttpServer testServer = new FinatraServer()

    // Starting the server is blocking so start it in a separate thread
    Thread startupThread = new Thread({
      testServer.main("-admin.port=:0", "-http.port=:" + port)
    })
    startupThread.setDaemon(true)
    startupThread.start()

    long startupDeadline = System.currentTimeMillis() + STARTUP_TIMEOUT
    while (!testServer.started()) {
      if (System.currentTimeMillis() > startupDeadline) {
        throw new TimeoutException("Timed out waiting for server startup")
      }
    }

    return testServer
  }

  @Override
  boolean hasHandlerSpan() {
    return true
  }

  @Override
  void stopServer(HttpServer httpServer) {
    Await.ready(httpServer.close(), TIMEOUT)
  }

  @Override
  FinatraDecorator decorator() {
    return FinatraDecorator.DECORATE
  }

  @Override
  String expectedOperationName() {
    return "finatra.request"
  }

  @Override
  void handlerSpan(TraceAssert trace, int index, Object parent, String method = "GET", ServerEndpoint endpoint = SUCCESS) {
    def errorEndpoint = endpoint == EXCEPTION || endpoint == ERROR
    trace.span(index) {
      operationName "finatra.controller"
      spanKind INTERNAL
      errored errorEndpoint
      childOf(parent as SpanData)
      tags {
        "$MoreTags.RESOURCE_NAME" "FinatraController"
        "$MoreTags.SPAN_TYPE" "web"
        "$Tags.COMPONENT" FinatraDecorator.DECORATE.getComponentName()

        // Finatra doesn't propagate the stack trace or exception to the instrumentation
        // so the normal errorTags() method can't be used
      }
    }
  }

  // need to override in order to add RESOURCE_NAME
  @Override
  void serverSpan(TraceAssert trace, int index, String traceID = null, String parentID = null, String method = "GET", ServerEndpoint endpoint = SUCCESS) {
    trace.span(index) {
      operationName expectedOperationName()
      spanKind SERVER
      errored endpoint.errored
      if (parentID != null) {
        traceId traceID
        parentId parentID
      } else {
        parent()
      }
      tags {
        "$MoreTags.RESOURCE_NAME" "$method ${endpoint.resolve(address).path}"
        "$MoreTags.SPAN_TYPE" SpanTypes.HTTP_SERVER
        "$Tags.COMPONENT" serverDecorator.getComponentName()
        "$Tags.PEER_PORT" Long
        "$Tags.PEER_HOST_IPV4" { it == null || it == "127.0.0.1" } // Optional
        "$Tags.HTTP_URL" "${endpoint.resolve(address)}"
        "$Tags.HTTP_METHOD" method
        "$Tags.HTTP_STATUS" endpoint.status
        if (endpoint.query) {
          "$MoreTags.HTTP_QUERY" endpoint.query
        }
      }
    }
  }
}
