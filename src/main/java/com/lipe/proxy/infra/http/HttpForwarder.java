package com.lipe.proxy.infra.http;

import java.io.IOException;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;

/** Transporte HTTP para um destino fixo. Quem chama deve fechar a resposta após enviá-la. */
@FunctionalInterface
public interface HttpForwarder {
  ClassicHttpResponse forward(ClassicHttpRequest request) throws IOException, HttpException;
}
