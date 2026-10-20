package com.lipe.proxy.security;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class RateLimiter {
    private final ConcurrentHashMap<Integer, AtomicInteger> activeConnections = new ConcurrentHashMap<>();
    private final int maxConnectionsPerIp;

    public RateLimiter(int maxConnectionsPerIp) {
        if (maxConnectionsPerIp <= 0) {
            throw new IllegalArgumentException("O limite de conexões por IP deve ser maior que zero.");
        }
        this.maxConnectionsPerIp = maxConnectionsPerIp;
    }

    public boolean allowConnection(int ipAsInt) {
        AtomicInteger count = activeConnections.computeIfAbsent(ipAsInt, k -> new AtomicInteger(0));

        int current = count.incrementAndGet();

        if (current > maxConnectionsPerIp) {
            count.decrementAndGet();
            return false;
        }
        return true;
    }

    public void releaseConnection(int ipAsInt) {
        AtomicInteger count = activeConnections.get(ipAsInt);
        if (count != null && count.decrementAndGet() <= 0) {
            activeConnections.remove(ipAsInt, count);
        }
    }

    public int getActiveConnectionsCount(int ipAsInt) {
        AtomicInteger count = activeConnections.get(ipAsInt);
        return count == null ? 0 : count.get();
    }
}
