package client

import io.opentelemetry.auto.instrumentation.netty40.client.NettyHttpClientDecorator
import io.opentelemetry.auto.test.base.HttpClientTest
import play.libs.ws.WS
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Subject

// Play 2.6+ uses a separately versioned client that shades the underlying dependency
// This means our built in instrumentation won't work.
class PlayWSClientTest extends HttpClientTest<NettyHttpClientDecorator> {
  @Subject
  @Shared
  @AutoCleanup
  def client = WS.newClient(-1)

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers, Closure callback) {
    def request = client.url(uri.toString())
    headers.entrySet().each {
      request.setHeader(it.key, it.value)
    }

    def status = request.execute(method).thenApply {
      callback?.call()
      it
    }.thenApply {
      it.status
    }
    return status.toCompletableFuture().get()
  }

  @Override
  NettyHttpClientDecorator decorator() {
    return NettyHttpClientDecorator.DECORATE
  }

  @Override
  String expectedOperationName() {
    return "netty.client.request"
  }

  @Override
  boolean testRedirects() {
    false
  }

  @Override
  boolean testConnectionFailure() {
    false
  }
}
