package me.vlink102.personal.chess.socketserver;

import me.vlink102.personal.chess.socketserver.ratings.RatingCalculator;
import me.vlink102.personal.chess.socketserver.ratings.RatingPeriodResults;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.HashMap;

public class Main {
    public static MySQLConnection connection;
    public static RatingCalculator calculator;
    public static RatingPeriodResults results;

    private static volatile boolean listening = true;
    public static final HashMap<String, ClientSocket> CONNECTED_SOCKET_MAP = new HashMap<>();

    public static void addCSocket(String uuid, ClientSocket socket) {
        CONNECTED_SOCKET_MAP.put(uuid, socket);
        socket.start();
    }

    public static void removeCSocket(ClientSocket socket) {
        CONNECTED_SOCKET_MAP.remove(socket.getUuid());
    }

    public static void main(String[] args) {
        connection = new MySQLConnection("ulucl02v8dm4l3qm", "bf5v9fiyfc6bqge4qrz1-mysql.services.clever-cloud.com", "bf5v9fiyfc6bqge4qrz1", 3306);
        System.out.println("Socket linked to MySQL successfully!");

        connection.loadData();
        connection.savePlayers();
        calculator = new RatingCalculator();
        results = new RatingPeriodResults();

        try (ServerSocket socket = new ServerSocket(55285)) {
            System.out.println("Server initialised successfully");

            while (listening) {
                Socket connected = socket.accept();
                System.out.println("Client [" + connected.getInetAddress() + ":" + connected.getPort() + "] connected at " + System.currentTimeMillis());

                InputStreamReader in = new InputStreamReader(connected.getInputStream());
                BufferedReader bf = new BufferedReader(in);

                String str = bf.readLine();
                System.out.println("Client [" + connected.getInetAddress() + ":" + connected.getPort() + "]: " + str);

                JSONObject strObj = new JSONObject(str);
                String uuid = strObj.getString("online_uuid");

                PrintWriter pr = new PrintWriter(connected.getOutputStream());
                pr.println("Successfully connected to server");
                pr.flush();

                addCSocket(uuid, new ClientSocket(connected, uuid, in, pr, bf));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}