package io.opentelemetry.auto.instrumentation.jul;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.config.Config;
import io.opentelemetry.trace.AttributeValue;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JavaUtilLoggingEvents {

  private static final Tracer TRACER =
      OpenTelemetry.getTracerFactory().get("io.opentelemetry.auto.java-util-logging-events");

  private static final Formatter FORMATTER = new AccessibleFormatter();

  public static void capture(final Logger logger, final LogRecord logRecord) {

    final Level level = logRecord.getLevel();
    if (!logger.isLoggable(level)) {
      // this is already checked in most cases, except if Logger.log(LogRecord) was called directly
      return;
    }
    if (level.intValue() < getThreshold().intValue()) {
      return;
    }
    final Span currentSpan = TRACER.getCurrentSpan();
    if (!currentSpan.getContext().isValid()) {
      return;
    }

    final Throwable t = logRecord.getThrown();
    final Map<String, AttributeValue> attributes = new HashMap<>(t == null ? 2 : 3);
    attributes.put("level", newAttributeValue(level.getName()));
    attributes.put("loggerName", newAttributeValue(logger.getName()));
    if (t != null) {
      attributes.put("error.stack", newAttributeValue(toString(t)));
    }
    currentSpan.addEvent(FORMATTER.formatMessage(logRecord), attributes);
  }

  private static AttributeValue newAttributeValue(final String stringValue) {
    return AttributeValue.stringAttributeValue(stringValue);
  }

  private static String toString(final Throwable t) {
    final StringWriter out = new StringWriter();
    t.printStackTrace(new PrintWriter(out));
    return out.toString();
  }

  private static Level getThreshold() {
    final String level = Config.get().getLogsEventsThreshold();
    if (level == null) {
      return Level.OFF;
    }
    switch (level) {
      case "OFF":
        return Level.OFF;
      case "FATAL":
      case "ERROR":
      case "SEVERE":
        return Level.SEVERE;
      case "WARN":
      case "WARNING":
        return Level.WARNING;
      case "INFO":
        return Level.INFO;
      case "CONFIG":
        return Level.CONFIG;
      case "DEBUG":
      case "FINE":
        return Level.FINE;
      case "FINER":
        return Level.FINER;
      case "TRACE":
      case "FINEST":
        return Level.FINEST;
      case "ALL":
        return Level.ALL;
      default:
        log.error("unexpected value for {}: {}", Config.LOGS_EVENTS_THRESHOLD, level);
        return Level.OFF;
    }
  }

  // this is just needed for calling formatMessage in abstract super class
  public static class AccessibleFormatter extends Formatter {

    @Override
    public String format(final LogRecord record) {
      throw new UnsupportedOperationException();
    }
  }
}
