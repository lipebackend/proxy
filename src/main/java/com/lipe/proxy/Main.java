package com.lipe.proxy;


import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Logger;

public class Main {
    private static final Logger logger = Logger.getLogger(Main.class.getName());


    public static void main(String[] args) throws Exception {

        try {
            ServerSocket serverSocket = new ServerSocket(port)){
                while (true) {
                    Socket socket = serverSocket.accept();
                    Thread.startVirtualThread(() -> handleClient(clientSocket)) ~
                }
            }
        }
    }
}
