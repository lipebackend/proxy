package com.lipe.proxy.infra;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class RateLimiter {
    private final ConcurrentHashMap<Integer, AtomicInteger> activeConnections = new ConcurrentHashMap<>();
    private final int maxConnectionsPerIp;

    public RateLimiter(AtomicInteger maxConnectionPerIp) {
        this.maxConnectionsPerIp = maxConnectionPerIp;
    }

    public boolean allowConnection(int ipAsInt) {
        AtomicInteger count = activeConnections.computeIfAbsent(ipAsInt, k -> new AtomicInteger(0));
        return count.incrementAndGet() <= maxConnectionsPerIp;
    }

    public void releaseConnection(int ipAsInt) {
        AtomicInteger count = activeConnections.get(ipAsInt);
        if (count != null && count.decrementAndGet() <= 0) {
            activeConnections.remove(ipAsInt, count);
        }
    }
}