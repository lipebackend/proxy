package com.lipe.proxy.infra.http;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpMessage;

/** Remove opções válidas apenas em um trecho da conexão. O transporte recria o framing. */
final class ProxyHeaders {
  private static final Set<String> HOP_BY_HOP = Set.of(
      "connection", "keep-alive", "proxy-connection", "proxy-authenticate", "proxy-authorization",
      "te", "trailer", "transfer-encoding", "upgrade", "content-length", "host");

  private ProxyHeaders() {}

  static void copyEndToEnd(HttpMessage source, HttpMessage target) {
    Set<String> excluded = new HashSet<>(HOP_BY_HOP);
    for (Header header : source.getHeaders("Connection")) {
      for (String name : header.getValue().split(",")) {
        excluded.add(name.trim().toLowerCase(Locale.ROOT));
      }
    }
    for (Header header : source.getHeaders()) {
      if (!excluded.contains(header.getName().toLowerCase(Locale.ROOT))) target.addHeader(header);
    }
  }
}
