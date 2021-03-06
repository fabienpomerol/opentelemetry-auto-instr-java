import com.sun.jersey.api.client.Client
import com.sun.jersey.api.client.ClientResponse
import com.sun.jersey.api.client.filter.GZIPContentEncodingFilter
import com.sun.jersey.api.client.filter.LoggingFilter
import io.opentelemetry.auto.instrumentation.jaxrs.v1.JaxRsClientV1Decorator
import io.opentelemetry.auto.test.base.HttpClientTest
import spock.lang.Shared

class JaxRsClientV1Test extends HttpClientTest<JaxRsClientV1Decorator> {

  @Shared
  Client client = Client.create()

  def setupSpec() {
    // Add filters to ensure spans aren't duplicated.
    client.addFilter(new LoggingFilter())
    client.addFilter(new GZIPContentEncodingFilter())
  }

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers, Closure callback) {
    def resource = client.resource(uri).requestBuilder
    headers.each { resource.header(it.key, it.value) }
    def body = BODY_METHODS.contains(method) ? "" : null
    ClientResponse response = resource.method(method, ClientResponse, body)
    callback?.call()

    return response.status
  }

  @Override
  JaxRsClientV1Decorator decorator() {
    return JaxRsClientV1Decorator.DECORATE
  }

  @Override
  String expectedOperationName() {
    return "jax-rs.client.call"
  }

  boolean testCircularRedirects() {
    false
  }
}
