package io.opentelemetry.auto.test.asserts

import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import io.opentelemetry.sdk.trace.SpanData
import io.opentelemetry.trace.Span
import io.opentelemetry.trace.Status

import static TagsAssert.assertTags
import static io.opentelemetry.auto.test.asserts.EventAssert.assertEvent

class SpanAssert {
  private final SpanData span
  private final checked = [:]

  private final Set<Integer> assertedEventIndexes = new HashSet<>()

  private SpanAssert(span) {
    this.span = span
  }

  static void assertSpan(SpanData span,
                         @ClosureParams(value = SimpleType, options = ['io.opentelemetry.auto.test.asserts.SpanAssert'])
                         @DelegatesTo(value = SpanAssert, strategy = Closure.DELEGATE_FIRST) Closure spec) {
    def asserter = new SpanAssert(span)
    asserter.assertSpan spec
    asserter.assertEventsAllVerified()
  }

  void assertSpan(
    @ClosureParams(value = SimpleType, options = ['io.opentelemetry.auto.test.asserts.SpanAssert'])
    @DelegatesTo(value = SpanAssert, strategy = Closure.DELEGATE_FIRST) Closure spec) {
    def clone = (Closure) spec.clone()
    clone.delegate = this
    clone.resolveStrategy = Closure.DELEGATE_FIRST
    clone(this)
    assertDefaults()
  }

  void event(int index, @ClosureParams(value = SimpleType, options = ['io.opentelemetry.auto.test.asserts.EventAssert']) @DelegatesTo(value = EventAssert, strategy = Closure.DELEGATE_FIRST) Closure spec) {
    if (index >= span.timedEvents.size()) {
      throw new ArrayIndexOutOfBoundsException(index)
    }
    assertedEventIndexes.add(index)
    assertEvent(span.timedEvents.get(index), spec)
  }

  def assertSpanNameContains(String spanName, String... shouldContainArr) {
    for (String shouldContain : shouldContainArr) {
      assert spanName.contains(shouldContain)
    }
  }

  def operationName(String name) {
    assert span.name == name
    checked.name = true
  }

  def operationNameContains(String... operationNameParts) {
    assertSpanNameContains(span.name, operationNameParts)
    checked.name = true
  }

  def spanKind(Span.Kind spanKind) {
    assert span.kind == spanKind
    checked.kind = true
  }

  def parent() {
    assert !span.parentSpanId.isValid()
    checked.parentSpanId = true
  }

  def parentId(String parentId) {
    assert span.parentSpanId.toLowerBase16() == parentId
    checked.parentId = true
  }

  def traceId(String traceId) {
    assert span.traceId.toLowerBase16() == traceId
    checked.traceId = true
  }

  def childOf(SpanData parent) {
    parentId(parent.spanId.toLowerBase16())
    traceId(parent.traceId.toLowerBase16())
  }

  def hasLink(SpanData linked) {
    def found
    for (def link : span.links) {
      if (link.context.traceId == linked.traceId && link.context.spanId == linked.spanId) {
        found = true
        break
      }
    }
    assert found
  }

  def status(Status status) {
    assert span.status == status
    checked.status = true
  }

  def errored(boolean errored) {
    if (errored) {
      assert span.status != Status.OK
    } else {
      assert span.status == Status.OK
    }
    checked.status = true
  }

  void assertDefaults() {
    if (!checked.status) {
      errored(false)
    }
  }

  void tags(@ClosureParams(value = SimpleType, options = ['io.opentelemetry.auto.test.asserts.TagsAssert'])
            @DelegatesTo(value = TagsAssert, strategy = Closure.DELEGATE_FIRST) Closure spec) {
    assertTags(span.attributes, spec)
  }

  void assertEventsAllVerified() {
    assert assertedEventIndexes.size() == span.timedEvents.size()
  }
}
