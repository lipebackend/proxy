package com.lipe.proxy;

import com.lipe.proxy.infra.network.ProxyServer;
import com.lipe.proxy.security.Auth;
import com.lipe.proxy.security.RateLimiter;

public class Main {
    public static void main(String[] args) {
        int listenPort = 25565;
        int maxConnectionsPerIp = 5;

        RateLimiter rateLimiter = new RateLimiter(maxConnectionsPerIp);

        Auth auth = new Auth();

        auth.registerUser("lipe", "123456");

        ProxyServer proxyServer = new ProxyServer(listenPort, rateLimiter, auth);

        proxyServer.start();
    }
}
