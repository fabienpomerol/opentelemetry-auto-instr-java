package io.opentelemetry.auto.decorator


import io.opentelemetry.auto.instrumentation.api.MoreTags
import io.opentelemetry.auto.instrumentation.api.Tags
import io.opentelemetry.trace.Span

class ServerDecoratorTest extends BaseDecoratorTest {

  def span = Mock(Span)

  def "test afterStart"() {
    def decorator = newDecorator()
    when:
    decorator.afterStart(span)

    then:
    1 * span.setAttribute(Tags.COMPONENT, "test-component")
    1 * span.setAttribute(MoreTags.SPAN_TYPE, decorator.getSpanType())
    0 * _
  }

  def "test beforeFinish"() {
    when:
    newDecorator().beforeFinish(span)

    then:
    0 * _
  }

  @Override
  def newDecorator() {
    return new ServerDecorator() {

      @Override
      protected String getSpanType() {
        return "test-type"
      }

      @Override
      protected String getComponentName() {
        return "test-component"
      }
    }
  }
}
