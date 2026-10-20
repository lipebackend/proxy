package com.lipe.proxy.infra.http;

import java.io.IOException;
import java.net.URI;
import org.apache.hc.core5.http.*;
import org.apache.hc.core5.http.impl.bootstrap.HttpRequester;
import org.apache.hc.core5.http.impl.bootstrap.RequesterBootstrap;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.util.Timeout;

/** Reverse proxy HTTP/1.1; não escolhe o destino a partir do Host fornecido pelo cliente. */
public final class ApacheHttpForwarder implements HttpForwarder, AutoCloseable {
  private final HttpHost upstream;
  private final HttpRequester requester;
  private final Timeout timeout;

  public ApacheHttpForwarder(URI upstream, int timeoutMillis) {
    if (!"http".equalsIgnoreCase(upstream.getScheme()) || upstream.getHost() == null
        || upstream.getUserInfo() != null || upstream.getRawQuery() != null
        || upstream.getRawFragment() != null
        || !(upstream.getRawPath().isEmpty() || upstream.getRawPath().equals("/"))) {
      throw new IllegalArgumentException("Destino deve ser http://host:porta sem caminho ou credenciais");
    }
    if (timeoutMillis <= 0) throw new IllegalArgumentException("Timeout deve ser positivo");
    this.upstream = new HttpHost("http", upstream.getHost(), upstream.getPort() == -1 ? 80 : upstream.getPort());
    timeout = Timeout.ofMilliseconds(timeoutMillis);
    requester = RequesterBootstrap.bootstrap()
        .setSocketConfig(SocketConfig.custom().setSoTimeout(timeout).build())
        .setConnectionReuseStrategy((request, response, context) -> false)
        .create();
  }

  @Override
  public ClassicHttpResponse forward(ClassicHttpRequest request) throws IOException, HttpException {
    String path = request.getRequestUri();
    if (request.getAuthority() != null || !path.startsWith("/") || path.startsWith("//") || path.contains("#")) {
      throw new ProtocolException("Apenas request-target em origin-form é aceito");
    }
    var outbound = new BasicClassicHttpRequest(request.getMethod(), path);
    outbound.setVersion(HttpVersion.HTTP_1_1);
    ProxyHeaders.copyEndToEnd(request, outbound);
    outbound.removeHeaders("Expect"); // O handler já resolveu 100-continue com o cliente.
    outbound.setHeader("Host", upstream.toHostString());
    outbound.setHeader("Connection", "close");
    outbound.setEntity(request.getEntity()); // Streaming: não carrega o corpo inteiro na memória.
    return requester.execute(upstream, outbound, timeout, HttpCoreContext.create());
  }

  @Override
  public void close() throws IOException { requester.close(); }
}
