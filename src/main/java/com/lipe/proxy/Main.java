package com.lipe.proxy;

import com.lipe.proxy.config.ProxyConfig;
import com.lipe.proxy.infra.http.ApacheHttpForwarder;
import com.lipe.proxy.infra.http.HttpRequestHandler;
import com.lipe.proxy.infra.network.ConnectionHandler;
import com.lipe.proxy.infra.network.ProxyServer;
import com.lipe.proxy.infra.tcp.TcpRequestHandler;
import com.lipe.proxy.security.Auth;
import com.lipe.proxy.security.RateLimiter;
import java.io.IOException;
import java.util.Map;

public class Main {
  public static void main(String[] args) throws IOException {
    Map<String, String> env = System.getenv();
    ProxyConfig config = ProxyConfig.fromEnvironment(env);
    if (config.mode() == ProxyConfig.Mode.HTTP) {
      try (var forwarder = new ApacheHttpForwarder(config.httpUpstream(), config.timeoutMillis())) {
        run(config, new HttpRequestHandler(forwarder));
      }
    } else {
      Auth auth = new Auth();
      String username = required(env, "PROXY_USERNAME");
      String password = required(env, "PROXY_PASSWORD");
      if (username.chars().anyMatch(Character::isWhitespace)
          || password.chars().anyMatch(Character::isWhitespace)) {
        throw new IllegalArgumentException("Credenciais TCP não podem conter espaços");
      }
      auth.registerUser(username, password);
      run(config, new TcpRequestHandler(config.upstream(), auth, config.timeoutMillis()));
    }
  }

  private static void run(ProxyConfig config, ConnectionHandler handler) throws IOException {
    try (var server = new ProxyServer(config.bindAddress(),
        new RateLimiter(config.maxConnectionsPerIp()), handler, config.timeoutMillis())) {
      Thread shutdown = new Thread(server::close, "proxy-shutdown");
      Runtime.getRuntime().addShutdownHook(shutdown);
      try {
        server.start();
      } finally {
        try { Runtime.getRuntime().removeShutdownHook(shutdown); }
        catch (IllegalStateException shuttingDown) { /* JVM já iniciou o shutdown. */ }
      }
    }
  }

  private static String required(Map<String, String> env, String key) {
    String value = env.get(key);
    if (value == null || value.isBlank()) throw new IllegalArgumentException("Defina " + key + " no modo TCP");
    return value;
  }
}
