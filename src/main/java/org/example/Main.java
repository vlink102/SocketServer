package org.example;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class Main {
    private static volatile boolean listening = true;

    public static void main(String[] args) {
        try {
            System.out.println("Initialising Server...");
            ServerSocket socket = new ServerSocket(55285);
            System.out.println("Server waiting...");

            while (listening) {
                Socket connected = socket.accept();
                System.out.println("Client [" + connected.getInetAddress() + ":" + connected.getPort() + "] connected at " + System.currentTimeMillis());

                InputStreamReader in = new InputStreamReader(connected.getInputStream());
                BufferedReader bf = new BufferedReader(in);

                String str = bf.readLine();
                System.out.println("Client [" + connected.getInetAddress() + ":" + connected.getPort() + "]: " + str);
                PrintWriter pr = new PrintWriter(connected.getOutputStream());
                pr.println("Successfully connected to server");
                pr.flush();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}