package net.ltgt.gwt.devserver;

import com.google.gwt.dev.util.arg.ArgHandlerBindAddress;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.mockwebserver.*;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

public class ProxyTest {
  @ClassRule public static MockWebServer mockCodeServer = new MockWebServer();
  @ClassRule public static MockWebServer mockWebServer = new MockWebServer();

  static {
    mockCodeServer.setDispatcher(new Dispatcher() {
      @Override
      public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
        return new MockResponse()
            .setBody("From code server: " + request.getPath());
      }
    });

    mockWebServer.setDispatcher(new Dispatcher() {
      @Override
      public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
        return new MockResponse()
            .setBody("From web server: " + request.getPath());
      }
    });
  }

  private final OkHttpClient client = new OkHttpClient.Builder().build();

  @Test public void testProxy() throws Exception {
    DevServer.Options options = new DevServer.Options();
    options.bindAddress = ArgHandlerBindAddress.DEFAULT_BIND_ADDRESS;
    options.connectAddress = ArgHandlerBindAddress.DEFAULT_BIND_ADDRESS;
    options.codeServerPort = mockCodeServer.getPort();
    options.proxyTo = mockWebServer.url("").toString();
    options.moduleNames.add("net.ltgt.gwt.devserver.GWTTestCase");
    Server server = DevServer.start(options);
    try {
      final HttpUrl baseUrl = new HttpUrl.Builder()
          .scheme("http")
          .host(options.connectAddress)
          .port(((ServerConnector) server.getConnectors()[0]).getLocalPort())
          .build();
      assertStubNocacheJs(baseUrl, "tests/tests.nocache.js");
      assertFromCodeServer(baseUrl, "tests/whatever", "/tests/whatever");
      assertFromWebServer(baseUrl, "whatever", "/whatever");
    } finally {
      server.stop();
    }
  }

  @Test public void testModulePathPrefix() throws Exception {
    DevServer.Options options = new DevServer.Options();
    options.bindAddress = ArgHandlerBindAddress.DEFAULT_BIND_ADDRESS;
    options.connectAddress = ArgHandlerBindAddress.DEFAULT_BIND_ADDRESS;
    options.codeServerPort = mockCodeServer.getPort();
    options.proxyTo = mockWebServer.url("").toString();
    options.modulePathPrefix = "prefix";
    options.moduleNames.add("net.ltgt.gwt.devserver.GWTTestCase");
    Server server = DevServer.start(options);
    try {
      final HttpUrl baseUrl = new HttpUrl.Builder()
          .scheme("http")
          .host(options.connectAddress)
          .port(((ServerConnector) server.getConnectors()[0]).getLocalPort())
          .build();
      assertFromWebServer(baseUrl, "tests/tests.nocache.js", "/tests/tests.nocache.js");
      assertFromWebServer(baseUrl, "tests/whatever", "/tests/whatever");
      assertFromWebServer(baseUrl, "whatever", "/whatever");
      assertStubNocacheJs(baseUrl, "prefix/tests/tests.nocache.js");
      assertFromCodeServer(baseUrl, "prefix/tests/whatever", "/tests/whatever");
      assertFromWebServer(baseUrl, "prefix/whatever", "/prefix/whatever");
    } finally {
      server.stop();
    }
  }

  private void assertStubNocacheJs(HttpUrl baseUrl, String pathSegments) throws IOException {
    try (Response response = client.newCall(new Request.Builder()
        .get()
        .url(baseUrl.newBuilder().addPathSegments(pathSegments).build())
        .build())
        .execute()) {
      assertThat(response.header("Content-Type")).contains("javascript");
      final String body = response.body().string();
      assertThat(body).isNotEqualTo("From code server");
      assertThat(body).isNotEqualTo("From file system");
    }
  }

  private void assertFromCodeServer(HttpUrl baseUrl, String relativePath, String codeServerPath) throws IOException {
    try (Response response = client.newCall(new Request.Builder()
        .get()
        .url(baseUrl.newBuilder().addPathSegments(relativePath).build())
        .build())
        .execute()) {
      assertThat(response.body().string()).isEqualTo("From code server: " + codeServerPath);
    }
  }

  private void assertFromWebServer(HttpUrl baseUrl, String relativePath, String webServerPath) throws IOException {
    try (Response response = client.newCall(new Request.Builder()
        .get()
        .url(baseUrl.newBuilder().addPathSegments(relativePath).build())
        .build())
        .execute()) {
      assertThat(response.body().string()).isEqualTo("From web server: " + webServerPath);
    }
  }
}
