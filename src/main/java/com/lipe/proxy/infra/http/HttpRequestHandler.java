package com.lipe.proxy.infra.http;

import com.lipe.proxy.infra.network.ConnectionHandler;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Objects;
import org.apache.hc.core5.http.*;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.impl.io.DefaultBHttpServerConnection;
import org.apache.hc.core5.http.impl.io.DefaultBHttpServerConnectionFactory;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.protocol.ResponseContent;

/** Uma requisição por conexão. Parsing e framing ficam com o HttpCore. */
public final class HttpRequestHandler implements ConnectionHandler {
  private final HttpForwarder forwarder;
  private final DefaultBHttpServerConnectionFactory connections =
      DefaultBHttpServerConnectionFactory.builder()
          .http1Config(Http1Config.custom().setMaxLineLength(8192).setMaxHeaderCount(100).build())
          .build();

  public HttpRequestHandler(HttpForwarder forwarder) {
    this.forwarder = Objects.requireNonNull(forwarder);
  }

  @Override
  public void handle(Socket client) throws IOException {
    try (var connection = connections.createConnection(client)) {
      ClassicHttpRequest request;
      boolean head = false;
      try {
        request = connection.receiveRequestHeader();
        if (request == null) return;
        head = request.getMethod().equalsIgnoreCase("HEAD");
        if (!HttpVersion.HTTP_1_1.equals(request.getVersion())) {
          sendError(connection, head, 505, "Versão HTTP não suportada");
          return;
        }
        if (request.getMethod().equalsIgnoreCase("CONNECT") || request.containsHeader("Upgrade")) {
          sendError(connection, head, 501, "CONNECT e Upgrade ainda não implementados");
          return;
        }
        String path = request.getRequestUri();
        if (request.getAuthority() != null || !path.startsWith("/") || path.startsWith("//") || path.contains("#")) {
          sendError(connection, head, 400, "Use um caminho HTTP relativo ao destino fixo");
          return;
        }
        if (request.getHeaders("Host").length > 1
            || (HttpVersion.HTTP_1_1.equals(request.getVersion())
                && (!request.containsHeader("Host") || request.getFirstHeader("Host").getValue().isBlank()))
            || (request.containsHeader("Content-Length") && request.containsHeader("Transfer-Encoding"))) {
          sendError(connection, head, 400, "Cabeçalhos HTTP inválidos");
          return;
        }
        connection.receiveRequestEntity(request);
        Header expect = request.getFirstHeader("Expect");
        if (expect != null) {
          if (!"100-continue".equalsIgnoreCase(expect.getValue())) {
            sendError(connection, head, 417, "Expect não suportado");
            return;
          }
          connection.sendResponseHeader(new BasicClassicHttpResponse(100));
          connection.flush();
        }
      } catch (SocketTimeoutException e) {
        sendError(connection, head, 408, "Tempo de leitura da requisição excedido");
        return;
      } catch (HttpException e) {
        sendError(connection, head, 400, "Requisição HTTP inválida");
        return;
      }

      ClassicHttpResponse upstream;
      try {
        upstream = forwarder.forward(request);
      } catch (SocketTimeoutException e) {
        sendError(connection, head, 504, "Tempo de resposta do destino excedido");
        return;
      } catch (HttpException | IOException e) {
        sendError(connection, head, 502, "Falha na comunicação com o destino");
        return;
      }
      // A resposta upstream continua aberta até terminar o envio do corpo ao cliente.
      try (upstream) {
        if (upstream.getCode() < 200) {
          sendError(connection, head, 502, "Resposta do destino não suportada");
          return;
        }
        var response = new BasicClassicHttpResponse(upstream.getCode(), upstream.getReasonPhrase());
        response.setVersion(request.getVersion());
        ProxyHeaders.copyEndToEnd(upstream, response);
        boolean noBody = head || upstream.getCode() == 204 || upstream.getCode() == 304;
        if (!noBody) response.setEntity(upstream.getEntity());
        if (head || upstream.getCode() == 304) {
          Header length = upstream.getFirstHeader("Content-Length");
          if (length != null) response.addHeader(length);
        } else {
          ResponseContent.INSTANCE.process(response, response.getEntity(), HttpCoreContext.create());
        }
        response.setHeader("Connection", "close");
        connection.sendResponseHeader(response);
        if (!noBody) connection.sendResponseEntity(response);
        connection.flush();
      } catch (HttpException e) {
        // Depois dos headers, uma falha encerra o socket; não envia uma segunda resposta.
        throw new IOException("Falha ao enviar resposta HTTP", e);
      }
    }
  }

  private static void sendError(DefaultBHttpServerConnection connection, boolean head, int status, String message)
      throws IOException {
    try (var response = new BasicClassicHttpResponse(status)) {
      response.setVersion(HttpVersion.HTTP_1_1);
      response.setEntity(new StringEntity(message + "\n", ContentType.TEXT_PLAIN));
      ResponseContent.INSTANCE.process(response, response.getEntity(), HttpCoreContext.create());
      response.setHeader("Connection", "close");
      connection.sendResponseHeader(response);
      if (!head) connection.sendResponseEntity(response);
      connection.flush();
    } catch (HttpException e) {
      throw new IOException(e);
    }
  }
}
