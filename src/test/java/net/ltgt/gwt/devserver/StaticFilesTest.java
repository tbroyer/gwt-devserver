package net.ltgt.gwt.devserver;

import com.google.common.io.Files;
import com.google.gwt.dev.util.arg.ArgHandlerBindAddress;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.mockwebserver.*;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static com.google.common.truth.Truth.assertThat;

public class StaticFilesTest {
  @ClassRule public static TemporaryFolder baseDir = new TemporaryFolder();

  @ClassRule public static MockWebServer mockCodeServer = new MockWebServer();

  static {
    mockCodeServer.setDispatcher(new Dispatcher() {
      @Override
      public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
        return new MockResponse()
            .setBody("From code server: " + request.getPath());
      }
    });
  }

  @BeforeClass public static void setUpClass() throws Exception {
    Files.write("From file system: index.html", baseDir.newFile("index.html"), StandardCharsets.UTF_8);
    baseDir.newFolder("tests");
    Files.write("From file system: tests/index.html", baseDir.newFile("tests/index.html"), StandardCharsets.UTF_8);
    Files.write("From file system: tests/tests.nocache.js", baseDir.newFile("tests/tests.nocache.js"), StandardCharsets.UTF_8);
    baseDir.newFolder("prefix");
    Files.write("From file system: prefix/index.html", baseDir.newFile("prefix/index.html"), StandardCharsets.UTF_8);
    baseDir.newFolder("prefix", "tests");
    Files.write("From file system: prefix/tests/index.html", baseDir.newFile("prefix/tests/index.html"), StandardCharsets.UTF_8);
    Files.write("From file system: prefix/tests/tests.nocache.js", baseDir.newFile("prefix/tests/tests.nocache.js"), StandardCharsets.UTF_8);
  }

  private final OkHttpClient client = new OkHttpClient.Builder().build();

  @Test public void testStaticFiles() throws Exception {
    DevServer.Options options = new DevServer.Options();
    options.bindAddress = ArgHandlerBindAddress.DEFAULT_BIND_ADDRESS;
    options.connectAddress = ArgHandlerBindAddress.DEFAULT_BIND_ADDRESS;
    options.codeServerPort = mockCodeServer.getPort();
    options.baseDir = baseDir.getRoot();
    options.moduleNames.add("net.ltgt.gwt.devserver.GWTTestCase");
    Server server = DevServer.start(options);
    try {
      final HttpUrl baseUrl = new HttpUrl.Builder()
          .scheme("http")
          .host(options.connectAddress)
          .port(((ServerConnector) server.getConnectors()[0]).getLocalPort())
          .build();
      assertStubNocacheJs(baseUrl, "tests/tests.nocache.js");
      assertFromCodeServer(baseUrl, "tests/index.html", "/tests/index.html");
      assertFromFileSystem(baseUrl, "index.html", "index.html");
    } finally {
      server.stop();
    }
  }

  @Test public void testModulePathPrefix() throws Exception {
    DevServer.Options options = new DevServer.Options();
    options.bindAddress = ArgHandlerBindAddress.DEFAULT_BIND_ADDRESS;
    options.connectAddress = ArgHandlerBindAddress.DEFAULT_BIND_ADDRESS;
    options.codeServerPort = mockCodeServer.getPort();
    options.baseDir = baseDir.getRoot();
    options.modulePathPrefix = "prefix";
    options.moduleNames.add("net.ltgt.gwt.devserver.GWTTestCase");
    Server server = DevServer.start(options);
    try {
      final HttpUrl baseUrl = new HttpUrl.Builder()
          .scheme("http")
          .host(options.connectAddress)
          .port(((ServerConnector) server.getConnectors()[0]).getLocalPort())
          .build();
      assertFromFileSystem(baseUrl, "tests/tests.nocache.js", "tests/tests.nocache.js");
      assertFromFileSystem(baseUrl, "tests/index.html", "tests/index.html");
      assertFromFileSystem(baseUrl, "index.html", "index.html");
      assertStubNocacheJs(baseUrl, "prefix/tests/tests.nocache.js");
      assertFromCodeServer(baseUrl, "prefix/tests/index.html", "/tests/index.html");
      assertFromFileSystem(baseUrl, "prefix/index.html", "prefix/index.html");
    } finally {
      server.stop();
    }
  }

  @Test public void testContextPath() throws Exception {
    DevServer.Options options = new DevServer.Options();
    options.bindAddress = ArgHandlerBindAddress.DEFAULT_BIND_ADDRESS;
    options.connectAddress = ArgHandlerBindAddress.DEFAULT_BIND_ADDRESS;
    options.codeServerPort = mockCodeServer.getPort();
    options.baseDir = baseDir.getRoot();
    options.contextPath = "context";
    options.moduleNames.add("net.ltgt.gwt.devserver.GWTTestCase");
    Server server = DevServer.start(options);
    try {
      final HttpUrl baseUrl = new HttpUrl.Builder()
          .scheme("http")
          .host(options.connectAddress)
          .port(((ServerConnector) server.getConnectors()[0]).getLocalPort())
          .build();
      assertOutsideOfContext(baseUrl, "tests/tests.nocache.js");
      assertOutsideOfContext(baseUrl, "tests/index.html");
      assertOutsideOfContext(baseUrl, "index.html");
      assertStubNocacheJs(baseUrl, "context/tests/tests.nocache.js");
      assertFromCodeServer(baseUrl, "context/tests/index.html", "/tests/index.html");
      assertFromFileSystem(baseUrl, "context/index.html", "index.html");
    } finally {
      server.stop();
    }
  }

  @Test public void testContextPathModulePathPrefix() throws Exception {
    DevServer.Options options = new DevServer.Options();
    options.bindAddress = ArgHandlerBindAddress.DEFAULT_BIND_ADDRESS;
    options.connectAddress = ArgHandlerBindAddress.DEFAULT_BIND_ADDRESS;
    options.codeServerPort = mockCodeServer.getPort();
    options.baseDir = baseDir.getRoot();
    options.contextPath = "context";
    options.modulePathPrefix = "prefix";
    options.moduleNames.add("net.ltgt.gwt.devserver.GWTTestCase");
    Server server = DevServer.start(options);
    try {
      final HttpUrl baseUrl = new HttpUrl.Builder()
          .scheme("http")
          .host(options.connectAddress)
          .port(((ServerConnector) server.getConnectors()[0]).getLocalPort())
          .build();
      assertOutsideOfContext(baseUrl, "tests/tests.nocache.js");
      assertOutsideOfContext(baseUrl, "tests/index.html");
      assertOutsideOfContext(baseUrl, "index.html");
      assertOutsideOfContext(baseUrl, "prefix/tests/tests.nocache.js");
      assertOutsideOfContext(baseUrl, "prefix/tests/index.html");
      assertOutsideOfContext(baseUrl, "prefix/index.html");
      assertFromFileSystem(baseUrl, "context/tests/tests.nocache.js", "tests/tests.nocache.js");
      assertFromFileSystem(baseUrl, "context/tests/index.html", "tests/index.html");
      assertFromFileSystem(baseUrl, "context/index.html", "index.html");
      assertStubNocacheJs(baseUrl, "context/prefix/tests/tests.nocache.js");
      assertFromCodeServer(baseUrl, "context/prefix/tests/index.html", "/tests/index.html");
      assertFromFileSystem(baseUrl, "context/prefix/index.html", "prefix/index.html");
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

  private void assertFromFileSystem(HttpUrl baseUrl, String relativePath, String fileSystemPath) throws IOException {
    try (Response response = client.newCall(new Request.Builder()
        .get()
        .url(baseUrl.newBuilder().addPathSegments(relativePath).build())
        .build())
        .execute()) {
      assertThat(response.body().string()).isEqualTo("From file system: " + fileSystemPath);
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

  private void assertOutsideOfContext(HttpUrl baseUrl, String relativePath) throws IOException {
    try (Response response = client.newCall(new Request.Builder()
        .get()
        .url(baseUrl.newBuilder().addPathSegments(relativePath).build())
        .build())
        .execute()) {
      assertThat(response.code()).isEqualTo(404);
      assertThat(response.header("Content-Type")).contains("html");
      assertThat(response.body().string()).contains("Powered by Jetty://");
    }
  }
}
