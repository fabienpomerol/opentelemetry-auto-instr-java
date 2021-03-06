import groovy.json.JsonSlurper
import io.opentelemetry.auto.instrumentation.api.MoreTags
import io.opentelemetry.auto.instrumentation.api.SpanTypes
import io.opentelemetry.auto.instrumentation.api.Tags
import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.auto.test.utils.PortUtils
import org.apache.http.HttpHost
import org.apache.http.client.config.RequestConfig
import org.apache.http.util.EntityUtils
import org.elasticsearch.client.Request
import org.elasticsearch.client.Response
import org.elasticsearch.client.RestClient
import org.elasticsearch.client.RestClientBuilder
import org.elasticsearch.common.io.FileSystemUtils
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.env.Environment
import org.elasticsearch.node.InternalSettingsPreparer
import org.elasticsearch.node.Node
import org.elasticsearch.plugins.Plugin
import org.elasticsearch.transport.Netty4Plugin
import spock.lang.Shared

import static io.opentelemetry.trace.Span.Kind.CLIENT

class Elasticsearch6RestClientTest extends AgentTestRunner {
  @Shared
  int httpPort
  @Shared
  int tcpPort
  @Shared
  Node testNode
  @Shared
  File esWorkingDir

  @Shared
  RestClient client

  def setupSpec() {
    httpPort = PortUtils.randomOpenPort()
    tcpPort = PortUtils.randomOpenPort()

    esWorkingDir = File.createTempDir("test-es-working-dir-", "")
    esWorkingDir.deleteOnExit()
    println "ES work dir: $esWorkingDir"

    def settings = Settings.builder()
      .put("path.home", esWorkingDir.path)
      .put("http.port", httpPort)
      .put("transport.tcp.port", tcpPort)
      .put("cluster.name", "test-cluster")
      .build()
    testNode = new TestNode(InternalSettingsPreparer.prepareEnvironment(settings, null), [Netty4Plugin])
    testNode.start()

    client = RestClient.builder(new HttpHost("localhost", httpPort))
      .setMaxRetryTimeoutMillis(Integer.MAX_VALUE)
      .setRequestConfigCallback(new RestClientBuilder.RequestConfigCallback() {
        @Override
        RequestConfig.Builder customizeRequestConfig(RequestConfig.Builder builder) {
          return builder.setConnectTimeout(Integer.MAX_VALUE).setSocketTimeout(Integer.MAX_VALUE)
        }
      })
      .build()

  }

  def cleanupSpec() {
    testNode?.close()
    if (esWorkingDir != null) {
      FileSystemUtils.deleteSubDirectories(esWorkingDir.toPath())
      esWorkingDir.delete()
    }
  }

  def "test elasticsearch status"() {
    setup:
    Request request = new Request("GET", "_cluster/health")
    Response response = client.performRequest(request)

    Map result = new JsonSlurper().parseText(EntityUtils.toString(response.entity))

    expect:
    result.status == "green"

    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          operationName "elasticsearch.rest.query"
          spanKind CLIENT
          parent()
          tags {
            "$MoreTags.SERVICE_NAME" "elasticsearch"
            "$MoreTags.SPAN_TYPE" SpanTypes.ELASTICSEARCH
            "$Tags.COMPONENT" "elasticsearch-java"
            "$Tags.PEER_HOSTNAME" "localhost"
            "$Tags.PEER_PORT" httpPort
            "$Tags.HTTP_URL" "_cluster/health"
            "$Tags.HTTP_METHOD" "GET"
            "$Tags.DB_TYPE" "elasticsearch"
          }
        }
        span(1) {
          operationName "http.request"
          spanKind CLIENT
          childOf span(0)
          tags {
            "$MoreTags.SPAN_TYPE" SpanTypes.HTTP_CLIENT
            "$Tags.COMPONENT" "apache-httpasyncclient"
            "$Tags.HTTP_URL" "_cluster/health"
            "$Tags.HTTP_METHOD" "GET"
            "$Tags.HTTP_STATUS" 200
          }
        }
      }
    }
  }

  static class TestNode extends Node {
    TestNode(Environment environment, Collection<Class<? extends Plugin>> classpathPlugins) {
      super(environment, classpathPlugins, false)
    }

    @Override
    protected void registerDerivedNodeNameWithLogger(String nodeName) {}
  }
}
