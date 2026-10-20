package com.lipe.proxy.config;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Map;

/** Configuração sem framework; credenciais ficam fora do record para não aparecer em toString(). */
public record ProxyConfig(Mode mode, InetSocketAddress bindAddress, InetSocketAddress upstream,
                          int timeoutMillis, int maxConnectionsPerIp) {
  public enum Mode { TCP, HTTP }

  public ProxyConfig {
    if (mode == null || bindAddress == null || upstream == null) {
      throw new IllegalArgumentException("Modo e endereços são obrigatórios");
    }
    if (timeoutMillis <= 0 || maxConnectionsPerIp <= 0 || upstream.getPort() == 0) {
      throw new IllegalArgumentException("Timeout, limite e porta de destino devem ser positivos");
    }
  }

  public static ProxyConfig fromEnvironment(Map<String, String> env) {
    Mode mode;
    try {
      mode = Mode.valueOf(value(env, "PROXY_MODE", "tcp").toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("PROXY_MODE deve ser tcp ou http", e);
    }
    return new ProxyConfig(mode,
        new InetSocketAddress(value(env, "PROXY_BIND_ADDRESS", "127.0.0.1"),
            number(env, "PROXY_PORT", 25565, 0, 65535)),
        new InetSocketAddress(value(env, "PROXY_UPSTREAM_HOST", "127.0.0.1"),
            number(env, "PROXY_UPSTREAM_PORT", 20000, 1, 65535)),
        number(env, "PROXY_TIMEOUT_MS", 10000, 1, Integer.MAX_VALUE),
        number(env, "PROXY_MAX_CONNECTIONS_PER_IP", 5, 1, Integer.MAX_VALUE));
  }

  public URI httpUpstream() {
    try {
      return new URI("http", null, upstream.getHostString(), upstream.getPort(), null, null, null);
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("Host de destino HTTP inválido", e);
    }
  }

  private static String value(Map<String, String> env, String key, String fallback) {
    String value = env.getOrDefault(key, fallback).trim();
    if (value.isEmpty()) throw new IllegalArgumentException(key + " não pode ser vazio");
    return value;
  }

  private static int number(Map<String, String> env, String key, int fallback, int min, int max) {
    try {
      int value = Integer.parseInt(value(env, key, Integer.toString(fallback)));
      if (value < min || value > max) throw new NumberFormatException();
      return value;
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(key + " deve estar entre " + min + " e " + max, e);
    }
  }
}
