package com.lipe.proxy.config;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class ProxyConfigTest {
  @Test
  void defaultsPreserveTcpAndBindToLoopback() {
    var config = ProxyConfig.fromEnvironment(Map.of());
    assertThat(config.mode()).isEqualTo(ProxyConfig.Mode.TCP);
    assertThat(config.bindAddress().getAddress().isLoopbackAddress()).isTrue();
    assertThat(config.bindAddress().getPort()).isEqualTo(25565);
  }

  @Test
  void acceptsHttpAndEphemeralListenPort() {
    var config = ProxyConfig.fromEnvironment(Map.of("PROXY_MODE", "http", "PROXY_PORT", "0",
        "PROXY_UPSTREAM_HOST", "::1", "PROXY_UPSTREAM_PORT", "8080"));
    assertThat(config.mode()).isEqualTo(ProxyConfig.Mode.HTTP);
    assertThat(config.bindAddress().getPort()).isZero();
    assertThat(config.httpUpstream().getScheme()).isEqualTo("http");
    assertThat(config.httpUpstream().getPort()).isEqualTo(8080);
    assertThat(config.httpUpstream().getHost()).contains(":");
  }

  @Test
  void rejectsInvalidModePortAndTimeoutBeforeStartingListener() {
    assertThatIllegalArgumentException().isThrownBy(() ->
        ProxyConfig.fromEnvironment(Map.of("PROXY_MODE", "udp")));
    assertThatIllegalArgumentException().isThrownBy(() ->
        ProxyConfig.fromEnvironment(Map.of("PROXY_PORT", "65536")));
    assertThatIllegalArgumentException().isThrownBy(() ->
        ProxyConfig.fromEnvironment(Map.of("PROXY_TIMEOUT_MS", "0")));
  }
}
