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
import java.net.UnknownHostException;
import java.util.HashMap;

public class Main {
    public static MySQLConnection connection;
    public static RatingCalculator calculator;
    public static RatingPeriodResults results;

    private static volatile boolean listening = true;
    public static final HashMap<String, ClientSocket> CONNECTED_SOCKET_MAP = new HashMap<>();

    public record Challenge(String from, String to) {}

    public static final HashMap<Long, Challenge> PENDING_CHALLENGES = new HashMap<>();

    public static ClientSocket getFromUUID(String uuid) {
        return CONNECTED_SOCKET_MAP.get(uuid);
    }

    public static void addPendingChallenge(String from, String to) {
        PENDING_CHALLENGES.put(System.currentTimeMillis(), new Challenge(from, to));
    }

    public static void clearTimedOutChallenges() {
        HashMap<Long, Challenge> newChallenges = new HashMap<>(PENDING_CHALLENGES);
        PENDING_CHALLENGES.forEach((aLong, challenge) -> {
            if (aLong < 60 * 1000) {
                newChallenges.put(aLong, challenge);
            }
        });
        PENDING_CHALLENGES.clear();
        PENDING_CHALLENGES.putAll(newChallenges);
    }

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