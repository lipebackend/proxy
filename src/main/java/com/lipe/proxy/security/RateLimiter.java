package com.lipe.proxy.security;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.ConcurrentHashMap;

/** Limita conexões simultâneas por IP; não é um limite de requisições por segundo. */
public final class RateLimiter {
  private final ConcurrentHashMap<InetAddress, Integer> activeConnections = new ConcurrentHashMap<>();
  private final int maxConnectionsPerIp;

  public RateLimiter(int maxConnectionsPerIp) {
    if (maxConnectionsPerIp <= 0) throw new IllegalArgumentException("Limite deve ser positivo");
    this.maxConnectionsPerIp = maxConnectionsPerIp;
  }

  public boolean allowConnection(InetAddress address) {
    boolean[] admitted = {false};
    activeConnections.compute(address, (key, count) -> {
      int current = count == null ? 0 : count;
      if (current >= maxConnectionsPerIp) return count;
      admitted[0] = true;
      return current + 1;
    });
    return admitted[0];
  }

  public void releaseConnection(InetAddress address) {
    activeConnections.computeIfPresent(address, (key, count) -> count <= 1 ? null : count - 1);
  }

  public int getActiveConnectionsCount(InetAddress address) {
    return activeConnections.getOrDefault(address, 0);
  }

  // Compatibilidade com os testes/clientes IPv4 existentes.
  public boolean allowConnection(int address) { return allowConnection(ipv4(address)); }
  public void releaseConnection(int address) { releaseConnection(ipv4(address)); }
  public int getActiveConnectionsCount(int address) { return getActiveConnectionsCount(ipv4(address)); }

  private static InetAddress ipv4(int address) {
    try {
      return InetAddress.getByAddress(new byte[] {
          (byte) (address >>> 24), (byte) (address >>> 16), (byte) (address >>> 8), (byte) address});
    } catch (UnknownHostException impossible) {
      throw new AssertionError(impossible);
    }
  }
}
