package com.lipe.proxy.infra;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;


public class ProxyServer {

    private AtomicInteger MAX_CONNECTION_PER_IP = new AtomicInteger(0);
    private RateLimiter rateLimiter = new RateLimiter(MAX_CONNECTION_PER_IP);
    private Logger logger = Logger.getLogger(ProxyServer.class.getName());

    public void start() {

        try (ServerSocket serverSocket = new ServerSocket(0)) {
            logger.info("ProxyServer started on port " + serverSocket.getLocalPort());
            while (true) {
                // Bloqueia até um cliente conectar
                Socket clientSocket = serverSocket.accept();
                logger.info("Accepted connection from " + clientSocket.getRemoteSocketAddress());

                // Dispara uma Virtual Thread instantânea para lidar com este cliente
                Thread.startVirtualThread(() -> handleClient(clientSocket, rateLimiter));
                logger.info("ProxyServer started on port " + clientSocket.getLocalPort());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void handleClient(Socket clientSocket, RateLimiter rateLimiter) {
        try (clientSocket) {
            byte[] rawIp = clientSocket.getInetAddress().getAddress(); // 4 bytes no IPv4
            int ipAsInt = ((rawIp[0] & 0xFF) << 24) |
                    ((rawIp[1] & 0xFF) << 16) |
                    ((rawIp[2] & 0xFF) << 8)  |
                    (rawIp[3] & 0xFF);

            if (!rateLimiter.allowConnection(ipAsInt)) {
                return;
            }

            // TODO: 2. Authentication & Forward


        } catch (IOException e) {
            System.err.println("❌ Erro na conexão: " + e.getMessage());
        }
    }
}