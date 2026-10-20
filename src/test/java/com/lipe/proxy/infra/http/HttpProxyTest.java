package com.lipe.proxy.infra.http;

import com.lipe.proxy.infra.network.ProxyServer;
import com.lipe.proxy.security.RateLimiter;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.assertThat;

@Timeout(10)
class HttpProxyTest {
  private static final InetAddress LOOPBACK = InetAddress.getLoopbackAddress();

  @Test
  void postPreservesPathBodyAndStatusAndFiltersHopHeadersInBothDirections() throws Exception {
    var observed = new AtomicReference<String>();
    var upstream = HttpServer.create(new InetSocketAddress(LOOPBACK, 0), 0);
    upstream.createContext("/", exchange -> {
      String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      observed.set(exchange.getRequestMethod() + " " + exchange.getRequestURI() + " " + body
          + " host=" + exchange.getRequestHeaders().getFirst("Host")
          + " secret=" + exchange.getRequestHeaders().getFirst("X-Secret")
          + " auth=" + exchange.getRequestHeaders().getFirst("Proxy-Authorization")
          + " end=" + exchange.getRequestHeaders().getFirst("X-End"));
      exchange.getResponseHeaders().add("Connection", "X-Internal");
      exchange.getResponseHeaders().add("X-Internal", "hidden");
      exchange.getResponseHeaders().add("X-Result", "visible");
      byte[] bytes = ("echo:" + body).getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(201, bytes.length);
      try (exchange) { exchange.getResponseBody().write(bytes); }
    });
    upstream.start();
    int port = upstream.getAddress().getPort();
    try (var forwarder = new ApacheHttpForwarder(URI.create("http://localhost:" + port), 2000);
         var proxy = new RunningProxy(new HttpRequestHandler(forwarder))) {
      String response = proxy.exchange("POST /echo%20test?q=1 HTTP/1.1\r\nHost: untrusted.test\r\n"
          + "Content-Length: 5\r\nConnection: X-Secret\r\nX-Secret: hidden\r\n"
          + "Proxy-Authorization: secret\r\nX-End: visible\r\n\r\nhello");
      assertThat(response).startsWith("HTTP/1.1 201").contains("echo:hello");
      assertThat(response.toLowerCase()).contains("x-result: visible").doesNotContain("x-internal");
      assertThat(observed.get()).isEqualTo("POST /echo%20test?q=1 hello host=localhost:" + port
          + " secret=null auth=null end=visible");
    } finally { upstream.stop(0); }
  }

  @Test
  void forwardsChunkedRequestAndResponseWithoutBufferingWholeBody() throws Exception {
    var body = new AtomicReference<String>();
    var upstream = HttpServer.create(new InetSocketAddress(LOOPBACK, 0), 0);
    upstream.createContext("/", exchange -> {
      body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      exchange.sendResponseHeaders(200, 0); // Resposta chunked.
      try (exchange) { exchange.getResponseBody().write("chunk-response".getBytes(StandardCharsets.UTF_8)); }
    });
    upstream.start();
    try (var forwarder = new ApacheHttpForwarder(URI.create("http://localhost:" + upstream.getAddress().getPort()), 2000);
         var proxy = new RunningProxy(new HttpRequestHandler(forwarder))) {
      String response = proxy.exchange("POST / HTTP/1.1\r\nHost: localhost\r\nTransfer-Encoding: chunked\r\n\r\n"
          + "5\r\nhello\r\n6\r\n world\r\n0\r\n\r\n");
      assertThat(body.get()).isEqualTo("hello world");
      assertThat(response).startsWith("HTTP/1.1 200").contains("chunk-response", "Transfer-Encoding: chunked");
    } finally { upstream.stop(0); }
  }

  @Test
  void headPreservesRepresentationLengthAndDoesNotSendBody() throws Exception {
    var upstream = HttpServer.create(new InetSocketAddress(LOOPBACK, 0), 0);
    upstream.createContext("/", exchange -> {
      exchange.getResponseHeaders().set("Content-Length", "42");
      exchange.sendResponseHeaders(200, -1);
      exchange.close();
    });
    upstream.start();
    try (var forwarder = new ApacheHttpForwarder(URI.create("http://localhost:" + upstream.getAddress().getPort()), 2000);
         var proxy = new RunningProxy(new HttpRequestHandler(forwarder))) {
      String response = proxy.exchange("HEAD / HTTP/1.1\r\nHost: localhost\r\n\r\n");
      assertThat(response.toLowerCase()).contains("content-length: 42");
      assertThat(response.substring(response.indexOf("\r\n\r\n") + 4)).isEmpty();
    } finally { upstream.stop(0); }
  }

  @Test
  void rejectsTunnelsAbsoluteTargetsAndAmbiguousFramingWithoutContactingUpstream() throws Exception {
    try (var proxy = new RunningProxy(new HttpRequestHandler(request -> {
      throw new AssertionError("Não deve encaminhar requisição inválida");
    }))) {
      assertThat(proxy.exchange("CONNECT example.com:443 HTTP/1.1\r\nHost: example.com\r\n\r\n"))
          .startsWith("HTTP/1.1 501");
      assertThat(proxy.exchange("GET http://other.test/ HTTP/1.1\r\nHost: other.test\r\n\r\n"))
          .startsWith("HTTP/1.1 400");
      assertThat(proxy.exchange("POST / HTTP/1.1\r\nHost: localhost\r\nContent-Length: 1\r\n"
          + "Transfer-Encoding: chunked\r\n\r\n")).startsWith("HTTP/1.1 400");
      assertThat(proxy.exchange("GET / HTTP/1.1\r\n\r\n")).startsWith("HTTP/1.1 400");
    }
  }

  @Test
  void mapsTransportFailuresTo502AndTimeoutsTo504() throws Exception {
    try (var proxy = new RunningProxy(new HttpRequestHandler(request -> { throw new IOException("offline"); }))) {
      assertThat(proxy.exchange("GET / HTTP/1.1\r\nHost: localhost\r\n\r\n")).startsWith("HTTP/1.1 502");
    }
    try (var proxy = new RunningProxy(new HttpRequestHandler(request -> { throw new SocketTimeoutException(); }))) {
      assertThat(proxy.exchange("GET / HTTP/1.1\r\nHost: localhost\r\n\r\n")).startsWith("HTTP/1.1 504");
    }
  }

  @Test
  void headErrorsHaveNoBodyAndHttp10IsExplicitlyUnsupported() throws Exception {
    try (var proxy = new RunningProxy(new HttpRequestHandler(request -> { throw new IOException(); }))) {
      String response = proxy.exchange("HEAD / HTTP/1.1\r\nHost: localhost\r\n\r\n");
      assertThat(response).startsWith("HTTP/1.1 502");
      assertThat(response.substring(response.indexOf("\r\n\r\n") + 4)).isEmpty();
      assertThat(proxy.exchange("GET / HTTP/1.0\r\n\r\n")).startsWith("HTTP/1.1 505");
    }
  }

  @Test
  void expectContinueDoesNotDeadlockWaitingForRequestBody() throws Exception {
    try (var proxy = new RunningProxy(new HttpRequestHandler(request -> {
      assertThat(new String(request.getEntity().getContent().readAllBytes(), StandardCharsets.UTF_8))
          .isEqualTo("hello");
      return new org.apache.hc.core5.http.message.BasicClassicHttpResponse(204);
    })); var socket = new Socket(LOOPBACK, proxy.server.getLocalPort())) {
      socket.setSoTimeout(3000);
      socket.getOutputStream().write(("POST / HTTP/1.1\r\nHost: localhost\r\n"
          + "Content-Length: 5\r\nExpect: 100-continue\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
      var interim = new StringBuilder();
      while (!interim.toString().endsWith("\r\n\r\n")) {
        int value = socket.getInputStream().read();
        if (value == -1) throw new EOFException();
        interim.append((char) value);
      }
      assertThat(interim.toString()).startsWith("HTTP/1.1 100");
      socket.getOutputStream().write("hello".getBytes(StandardCharsets.US_ASCII));
      String response = new String(socket.getInputStream().readAllBytes(), StandardCharsets.US_ASCII);
      assertThat(response).startsWith("HTTP/1.1 204").doesNotContain("Content-Length", "Transfer-Encoding");
    }
  }

  private static final class RunningProxy implements AutoCloseable {
    private final ProxyServer server;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Future<?> running;

    RunningProxy(HttpRequestHandler handler) throws IOException {
      server = new ProxyServer(new InetSocketAddress(LOOPBACK, 0), new RateLimiter(5), handler, 2000);
      running = executor.submit(() -> { server.start(); return null; });
    }

    String exchange(String request) throws IOException {
      try (var socket = new Socket(LOOPBACK, server.getLocalPort())) {
        socket.setSoTimeout(3000);
        socket.getOutputStream().write(request.getBytes(StandardCharsets.ISO_8859_1));
        socket.getOutputStream().flush();
        return new String(socket.getInputStream().readAllBytes(), StandardCharsets.ISO_8859_1);
      }
    }

    @Override public void close() throws Exception {
      server.close();
      try { running.get(3, TimeUnit.SECONDS); } finally { executor.close(); }
    }
  }
}
