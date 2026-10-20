package com.lipe.proxy.infra.network;

import com.lipe.proxy.security.RateLimiter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import java.net.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;

class ProxyLifecycleTest {
  @Test
  @Timeout(10)
  void closeUnblocksListenerAndClientAndReleasesAdmission() throws Exception {
    var entered = new CountDownLatch(1);
    var worker = new java.util.concurrent.atomic.AtomicReference<Thread>();
    var limiter = new RateLimiter(1);
    InetAddress loopback = InetAddress.getLoopbackAddress();
    try (var server = new ProxyServer(new InetSocketAddress(loopback, 0), limiter, socket -> {
      worker.set(Thread.currentThread());
      entered.countDown();
      socket.getInputStream().read();
    }, 3000); var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var running = executor.submit(() -> { server.start(); return null; });
      try (var client = new Socket(loopback, server.getLocalPort())) {
        client.setSoTimeout(3000);
        assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
        server.close();
        assertThat(client.getInputStream().read()).isEqualTo(-1);
        running.get(3, TimeUnit.SECONDS);
        worker.get().join(3000);
        assertThat(worker.get().isAlive()).isFalse();
      }
    }
    assertThat(limiter.getActiveConnectionsCount(loopback)).isZero();
  }
}
