package com.lipe.proxy.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import java.net.InetAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterTest {
  @Test
  @Timeout(10)
  void concurrentAdmissionAndReleaseNeverExceedLimit() throws Exception {
    RateLimiter limiter = new RateLimiter(3);
    InetAddress address = InetAddress.getLoopbackAddress();
    AtomicInteger inside = new AtomicInteger();
    AtomicInteger peak = new AtomicInteger();
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      for (int task = 0; task < 32; task++) {
        executor.submit(() -> {
          for (int i = 0; i < 1000; i++) {
            if (limiter.allowConnection(address)) {
              peak.accumulateAndGet(inside.incrementAndGet(), Math::max);
              Thread.yield();
              inside.decrementAndGet();
              limiter.releaseConnection(address);
            }
          }
        });
      }
    }
    assertThat(peak.get()).isBetween(1, 3);
    assertThat(limiter.getActiveConnectionsCount(address)).isZero();
  }

  @Test
  void differentIpv6AddressesHaveIndependentLimits() throws Exception {
    RateLimiter limiter = new RateLimiter(1);
    InetAddress first = InetAddress.getByName("2001:db8::1");
    InetAddress second = InetAddress.getByName("2001:db8::2");
    assertThat(limiter.allowConnection(first)).isTrue();
    assertThat(limiter.allowConnection(second)).isTrue();
    assertThat(limiter.allowConnection(first)).isFalse();
    limiter.releaseConnection(first);
    limiter.releaseConnection(first);
    assertThat(limiter.allowConnection(first)).isTrue();
  }
}
