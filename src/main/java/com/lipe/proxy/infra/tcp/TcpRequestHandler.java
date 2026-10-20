package com.lipe.proxy.infra.tcp;

import com.lipe.proxy.infra.network.ConnectionHandler;
import com.lipe.proxy.security.Auth;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.logging.Logger;

/** Protocolo de estudo: uma linha AUTH antes do fluxo TCP transparente. */
public final class TcpRequestHandler implements ConnectionHandler {
  private static final Logger logger = Logger.getLogger(TcpRequestHandler.class.getName());
  private final InetSocketAddress upstream;
  private final Auth auth;
  private final int timeoutMillis;

  public TcpRequestHandler(InetSocketAddress upstream, Auth auth, int timeoutMillis) {
    this.upstream = Objects.requireNonNull(upstream);
    this.auth = Objects.requireNonNull(auth);
    if (timeoutMillis <= 0) throw new IllegalArgumentException("Timeout deve ser positivo");
    this.timeoutMillis = timeoutMillis;
  }

  @Override
  public void handle(Socket clientSocket) throws IOException {
    String firstLine = readAuthLine(clientSocket.getInputStream());
    if (!auth.authenticate(firstLine)) {
      PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true, StandardCharsets.UTF_8);
      writer.println("ERRO: Login ou senha invalidos. Desconectando...");
      return;
    }
    try (Socket destination = new Socket()) {
      destination.connect(upstream, timeoutMillis);
      // Limite de inatividade em cada direção; conexões paradas não ficam eternas.
      destination.setSoTimeout(timeoutMillis);
      Thread outbound = Thread.startVirtualThread(() -> forward(clientSocket, destination));
      Thread inbound = Thread.startVirtualThread(() -> forward(destination, clientSocket));
      try {
        outbound.join();
        inbound.join();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        closeSocket(clientSocket);
        closeSocket(destination);
      }
    }
  }

  private static String readAuthLine(InputStream input) throws IOException {
    ByteArrayOutputStream line = new ByteArrayOutputStream();
    int value;
    while ((value = input.read()) != -1) {
      if (value == '\n') {
        String text = line.toString(StandardCharsets.UTF_8);
        return text.endsWith("\r") ? text.substring(0, text.length() - 1) : text;
      }
      if (line.size() >= 4096) {
        throw new IOException("Linha de autenticação excede 4096 bytes");
      }
      line.write(value);
    }
    return null;
  }

  private static void forward(Socket source, Socket destination) {
    try {
      source.getInputStream().transferTo(destination.getOutputStream());
      // EOF encerra apenas este sentido; a resposta ainda pode estar chegando.
      destination.shutdownOutput();
    } catch (IOException e) {
      logger.fine("Encaminhamento encerrado: " + e.getMessage());
      closeSocket(source);
      closeSocket(destination);
    }
  }

  private static void closeSocket(Socket socket) {
    try {
      socket.close();
    } catch (IOException e) {
      logger.fine("Falha ao fechar conexão: " + e.getMessage());
    }
  }
}
