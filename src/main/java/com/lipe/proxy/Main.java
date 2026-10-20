package com.lipe.proxy;

import com.lipe.proxy.infra.network.ProxyServer;
import com.lipe.proxy.infra.tcp.TcpRequestHandler;
import java.net.InetSocketAddress;
import java.io.IOException;
import com.lipe.proxy.security.Auth;
import com.lipe.proxy.security.RateLimiter;

public class Main {
    public static void main(String[] args) throws IOException {
        int listenPort = 25565;
        int maxConnectionsPerIp = 5;

        RateLimiter rateLimiter = new RateLimiter(maxConnectionsPerIp);

        Auth auth = new Auth();

        auth.registerUser("lipe", "123456");

        ProxyServer proxyServer = new ProxyServer(new InetSocketAddress("127.0.0.1", listenPort), rateLimiter,
            new TcpRequestHandler(new InetSocketAddress("localhost", 20000), auth, 10_000), 10_000);

        Runtime.getRuntime().addShutdownHook(new Thread(proxyServer::close));
        try (proxyServer) { proxyServer.start(); }
    }
}
