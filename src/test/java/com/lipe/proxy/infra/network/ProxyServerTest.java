package com.lipe.proxy.infra.network;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ProxyServerTest {
  @Test
  void authenticationLeavesFollowingBytesUntouched() throws Exception {
    InputStream input = new ByteArrayInputStream(
        "AUTH lipe senha\r\npayload".getBytes(StandardCharsets.UTF_8));
    Method readLine = ProxyServer.class.getDeclaredMethod("readAuthLine", InputStream.class);
    readLine.setAccessible(true);

    assertThat(readLine.invoke(null, input)).isEqualTo("AUTH lipe senha");
    assertThat(new String(input.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("payload");
  }

  @Test
  @Timeout(10)
  void forwardsBothWaysAndAllowsResponseAfterClientEof() throws Exception {
    Method forward = ProxyServer.class.getDeclaredMethod("forward", Socket.class, Socket.class);
    forward.setAccessible(true);
    try (ServerSocket clientListener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
         ServerSocket destinationListener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
         Socket client = new Socket(InetAddress.getLoopbackAddress(), clientListener.getLocalPort());
         Socket proxyClient = clientListener.accept();
         Socket proxyDestination = new Socket(InetAddress.getLoopbackAddress(), destinationListener.getLocalPort());
         Socket destination = destinationListener.accept()) {
      client.setSoTimeout(3000);
      destination.setSoTimeout(3000);
      Thread outbound = Thread.startVirtualThread(() -> invokeForward(forward, proxyClient, proxyDestination));
      Thread inbound = Thread.startVirtualThread(() -> invokeForward(forward, proxyDestination, proxyClient));

      client.getOutputStream().write("request".getBytes(StandardCharsets.UTF_8));
      client.shutdownOutput();
      assertThat(new String(destination.getInputStream().readAllBytes(), StandardCharsets.UTF_8))
          .isEqualTo("request");

      destination.getOutputStream().write("response".getBytes(StandardCharsets.UTF_8));
      destination.shutdownOutput();
      assertThat(new String(client.getInputStream().readAllBytes(), StandardCharsets.UTF_8))
          .isEqualTo("response");
      outbound.join(3000);
      inbound.join(3000);
      assertThat(outbound.isAlive()).isFalse();
      assertThat(inbound.isAlive()).isFalse();
    }
  }

  private static void invokeForward(Method method, Socket source, Socket destination) {
    try {
      method.invoke(null, source, destination);
    } catch (ReflectiveOperationException e) {
      throw new AssertionError(e);
    }
  }
}
