package com.lipe.proxy.infra.network;

import java.io.IOException;
import java.net.Socket;

/** Atende uma conexão. O ProxyServer é responsável por fechar o socket do cliente. */
@FunctionalInterface
public interface ConnectionHandler {
  void handle(Socket client) throws IOException;
}
