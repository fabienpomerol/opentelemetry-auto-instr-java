package io.opentelemetry.auto.instrumentation.okhttp3;

import io.opentelemetry.auto.decorator.HttpClientDecorator;
import java.net.URI;
import okhttp3.Request;
import okhttp3.Response;

public class OkHttpClientDecorator extends HttpClientDecorator<Request, Response> {
  public static final OkHttpClientDecorator DECORATE = new OkHttpClientDecorator();

  @Override
  protected String service() {
    return null;
  }

  @Override
  protected String getComponentName() {
    return "okhttp";
  }

  @Override
  protected String method(final Request httpRequest) {
    return httpRequest.method();
  }

  @Override
  protected URI url(final Request httpRequest) {
    return httpRequest.url().uri();
  }

  @Override
  protected String hostname(final Request httpRequest) {
    return httpRequest.url().host();
  }

  @Override
  protected Integer port(final Request httpRequest) {
    return httpRequest.url().port();
  }

  @Override
  protected Integer status(final Response httpResponse) {
    return httpResponse.code();
  }
}
