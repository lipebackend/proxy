package com.lipe.proxy.infra.network;

import com.lipe.proxy.security.RateLimiter;
import java.io.IOException;
import java.net.*;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;

/** Listener independente do protocolo. start() bloqueia; close() encerra as conexões. */
public final class ProxyServer implements AutoCloseable {
  private static final Logger logger = Logger.getLogger(ProxyServer.class.getName());
  private final ServerSocket listener;
  private final RateLimiter rateLimiter;
  private final ConnectionHandler handler;
  private final int timeoutMillis;
  private final Set<Socket> clients = new HashSet<>();
  private boolean closed;
  private boolean started;

  public ProxyServer(InetSocketAddress bindAddress, RateLimiter rateLimiter,
                     ConnectionHandler handler, int timeoutMillis) throws IOException {
    this.rateLimiter = Objects.requireNonNull(rateLimiter);
    this.handler = Objects.requireNonNull(handler);
    if (timeoutMillis <= 0) throw new IllegalArgumentException("Timeout deve ser positivo");
    this.timeoutMillis = timeoutMillis;
    listener = new ServerSocket();
    try {
      listener.bind(Objects.requireNonNull(bindAddress));
    } catch (IOException | RuntimeException e) {
      listener.close();
      throw e;
    }
  }

  public int getLocalPort() { return listener.getLocalPort(); }

  public void start() throws IOException {
    synchronized (this) {
      if (started || closed) throw new IllegalStateException("Servidor já iniciado ou fechado");
      started = true;
    }
    logger.info("Proxy escutando em " + listener.getLocalSocketAddress());
    try {
      while (!listener.isClosed()) {
        Socket client = listener.accept();
        synchronized (this) {
          if (closed) {
            closeSocket(client);
            break;
          }
          clients.add(client);
          Thread.startVirtualThread(() -> handleClient(client));
        }
      }
    } catch (SocketException e) {
      synchronized (this) {
        if (!closed) throw e;
      }
    } finally {
      close();
    }
  }

  private void handleClient(Socket client) {
    InetAddress address = client.getInetAddress();
    boolean admitted = false;
    try (client) {
      admitted = rateLimiter.allowConnection(address);
      if (!admitted) return;
      client.setSoTimeout(timeoutMillis);
      handler.handle(client);
    } catch (IOException | RuntimeException e) {
      logger.fine("Conexão encerrada: " + e.getMessage());
    } finally {
      if (admitted) rateLimiter.releaseConnection(address);
      synchronized (this) { clients.remove(client); }
    }
  }

  @Override
  public synchronized void close() {
    if (closed) return;
    closed = true;
    try { listener.close(); } catch (IOException e) { logger.fine(e.getMessage()); }
    clients.forEach(ProxyServer::closeSocket);
  }

  private static void closeSocket(Socket socket) {
    try { socket.close(); } catch (IOException e) { logger.fine(e.getMessage()); }
  }
}
