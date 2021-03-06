import io.opentelemetry.auto.instrumentation.api.MoreTags
import io.opentelemetry.auto.instrumentation.api.SpanTypes
import io.opentelemetry.auto.instrumentation.api.Tags
import io.opentelemetry.auto.test.AgentTestRunner

import static io.opentelemetry.trace.Span.Kind.CLIENT

class SlickTest extends AgentTestRunner {

  // Can't be @Shared, otherwise the work queue is initialized before the instrumentation is applied
  def database = new SlickUtils()

  def "Basic statement generates spans"() {
    setup:
    def result = database.runQuery(SlickUtils.TestQuery())

    expect:
    result == SlickUtils.TestValue()

    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          operationName "run query"
          parent()
          errored false
          tags {
          }
        }
        span(1) {
          operationName "database.query"
          spanKind CLIENT
          childOf span(0)
          errored false
          tags {
            "$MoreTags.SERVICE_NAME" SlickUtils.Driver()
            "$MoreTags.RESOURCE_NAME" SlickUtils.TestQuery()
            "$MoreTags.SPAN_TYPE" SpanTypes.SQL
            "$Tags.COMPONENT" "java-jdbc-prepared_statement"
            "$Tags.DB_TYPE" SlickUtils.Driver()
            "$Tags.DB_INSTANCE" SlickUtils.Db()
            "$Tags.DB_USER" SlickUtils.Username()
            "$Tags.DB_STATEMENT" SlickUtils.TestQuery()
            "span.origin.type" "org.h2.jdbc.JdbcPreparedStatement"
          }
        }
      }
    }
  }
}
