package com.lipe.proxy.infra.network;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

import com.lipe.proxy.security.Auth;
import com.lipe.proxy.security.RateLimiter;

public class ProxyServer {
  private static final Logger logger = Logger.getLogger(ProxyServer.class.getName());
  private final int listenPort;
  private final RateLimiter rateLimiter;
  private final Auth auth;

  public ProxyServer(int listenPort, RateLimiter rateLimiter, Auth auth) {
    this.listenPort = listenPort;
    this.rateLimiter = rateLimiter;
    this.auth = auth;
  }

  public void start() {
    try (ServerSocket serverSocket = new ServerSocket(listenPort)) {
      logger.info("ProxyServer iniciado e escutando na porta " + serverSocket.getLocalPort());

      while (true) {
        Socket clientSocket = serverSocket.accept();
        logger.info("Conexão recebida de " + clientSocket.getRemoteSocketAddress());

        Thread.startVirtualThread(() -> handleClient(clientSocket, rateLimiter, auth));
      }
    } catch (IOException e) {
      throw new RuntimeException("Falha fatal ao executar o ProxyServer", e);
    }
  }

  private static void handleClient(Socket clientSocket, RateLimiter rateLimiter, Auth auth) {
    try (clientSocket) {
      clientSocket.setSoTimeout(10_000);

      byte[] rawIp = clientSocket.getInetAddress().getAddress();
      int ipAsInt = ((rawIp[0] & 0xFF) << 24) |
          ((rawIp[1] & 0xFF) << 16) |
          ((rawIp[2] & 0xFF) << 8) |
          (rawIp[3] & 0xFF);

      if (!rateLimiter.allowConnection(ipAsInt)) {
        logger.warning("Conexão recusada por limite de IP excedido: " + clientSocket.getInetAddress());
        return;
      }

      try {
        String firstLine = readAuthLine(clientSocket.getInputStream());

        if (!auth.authenticate(firstLine)) {
          PrintWriter writer = new PrintWriter(
              clientSocket.getOutputStream(), true, StandardCharsets.UTF_8);
          writer.println("ERRO: Login ou senha invalidos. Desconectando...");
          logger.warning("Falha de login vinda de " + clientSocket.getRemoteSocketAddress());
          return;
        }

        try (Socket serverSocket = new Socket("localhost", 20000)) {
          clientSocket.setSoTimeout(0);
          Thread clientToServer = Thread.startVirtualThread(
              () -> forward(clientSocket, serverSocket));
          Thread serverToClient = Thread.startVirtualThread(
              () -> forward(serverSocket, clientSocket));

          try {
            clientToServer.join();
            serverToClient.join();
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        }
      } finally {
        rateLimiter.releaseConnection(ipAsInt);
      }
    } catch (IOException e) {
      logger.warning("Erro de I/O na comunicação do proxy: " + e.getMessage());
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
