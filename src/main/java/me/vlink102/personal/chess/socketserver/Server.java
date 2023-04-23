package me.vlink102.personal.chess.socketserver;

import me.vlink102.personal.chess.socketserver.ratings.RatingCalculator;
import me.vlink102.personal.chess.socketserver.ratings.RatingPeriodResults;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Server {
    public static final String version = "17.0.1";
    public static final String updateLink = "";

    public static List<String> bannedPlayers;

    public static MySQLConnection connection;
    public static RatingCalculator calculator;
    public static RatingPeriodResults results;

    private static volatile boolean listening = true;
    public static final HashMap<String, ClientSocket> CONNECTED_SOCKET_MAP = new HashMap<>();

    public static void addCSocket(String uuid, ClientSocket socket) {
        CONNECTED_SOCKET_MAP.put(uuid, socket);
        socket.start();
    }

    public static void disconnectPlayer(ClientSocket socket, String title, String reason) {
        JSONObject o = new JSONObject();
        o.put("closed-id", title);
        o.put("reason", reason);
        try {
            socket.getOutputStream().writeBytes(o + "\n");
            socket.getOutputStream().flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void removeCSocket(ClientSocket socket) {
        CONNECTED_SOCKET_MAP.remove(socket.getUuid());
    }

    private static void printProgress(long startTime, long total, long current) {
        long eta = current == 0 ? 0 :
                (total - current) * (System.currentTimeMillis() - startTime) / current;

        String etaHms = current == 0 ? "N/A" :
                String.format("%02d:%02d:%02d", TimeUnit.MILLISECONDS.toHours(eta),
                        TimeUnit.MILLISECONDS.toMinutes(eta) % TimeUnit.HOURS.toMinutes(1),
                        TimeUnit.MILLISECONDS.toSeconds(eta) % TimeUnit.MINUTES.toSeconds(1));

        int percent = (int) (current * 100 / total);
        String string = '\r' +
                String.join("", Collections.nCopies(percent == 0 ? 2 : 2 - (int) (Math.log10(percent)), " ")) +
                String.format(" %d%% [", percent) +
                String.join("", Collections.nCopies(percent, "=")) +
                '>' +
                String.join("", Collections.nCopies(100 - percent, " ")) +
                ']' +
                String.join("", Collections.nCopies(current == 0 ? (int) (Math.log10(total)) : (int) (Math.log10(total)) - (int) (Math.log10(current)), " ")) +
                String.format(" %d/%d, ETA: %s", current, total, etaHms);
        System.out.print(string);
    }

    public static void main(String[] args) {
        bannedPlayers = new ArrayList<>();
        Thread commandListener = new Thread() {
            final Scanner scanner = new Scanner(System.in);
            boolean listening = true;
            @Override
            public void run() {
                while (listening) {
                    String result = scanner.nextLine();

                    final String regex = "/(?>(?<=/)(?<c>(?<=/).*?(?=\\s|$))\\h?(?>(?<n>(?<=\\h?)\\w*?(?=\\s|$))))\\h?(?>(?>(?>!<(?<t>.*?)>)|(?>\\?<(?<r>.*?)>))\\h?)*";

                    final Pattern pattern = Pattern.compile(regex, Pattern.MULTILINE);
                    final Matcher matcher = pattern.matcher(result);

                    if (matcher.find()) {
                        String cmd = matcher.group("c");
                        String name = matcher.group("n");
                        String title = matcher.group("t");
                        String reason = matcher.group("r");

                        if (cmd == null) continue;
                        title = title == null ? "Lost connection" : title;
                        reason = reason == null ? "(No reason provided)" : reason;

                        switch (cmd) {
                            case "ban" -> {
                                String uuid = connection.UUIDfromName(name);
                                if (CONNECTED_SOCKET_MAP.containsKey(uuid)) {
                                    ClientSocket socket = CONNECTED_SOCKET_MAP.get(uuid);
                                    disconnectPlayer(socket, title, reason);
                                    bannedPlayers.add(uuid);
                                    System.out.println("Banned " + name + " (" + uuid + ") successfully!");
                                } else {
                                    bannedPlayers.add(uuid);
                                    System.out.println("Banned offline player " + name + " (" + uuid + ") successfully!");
                                }
                            }
                            case "unban" -> {
                                String uuid = connection.UUIDfromName(name);
                                if (bannedPlayers.contains(uuid)) {
                                    System.out.println("Unbanned " + name + " (" + uuid + ") successfully!");
                                    bannedPlayers.remove(uuid);
                                } else {
                                    System.out.println("User " + name + " (" + uuid + ") is not banned!");
                                }
                            }
                            case "kick" -> {
                                String uuid = connection.UUIDfromName(name);
                                if (CONNECTED_SOCKET_MAP.containsKey(uuid)) {
                                    ClientSocket socket = CONNECTED_SOCKET_MAP.get(uuid);
                                    disconnectPlayer(socket, title, reason);
                                    System.out.println("Kicked " + name + " (" + uuid + ") successfully!");
                                }
                            }
                            case "reload","restart","dev","maintenance","fix" -> {
                                for (ClientSocket socket : CONNECTED_SOCKET_MAP.values()) {
                                    disconnectPlayer(socket, title, reason);
                                }
                                System.out.println("Disconnected all clients successfully! (Manual restart required)");
                            }
                        }
                    }

                    /*
                    String finalReason = "(No reason provided)";
                    if (r.find()) {
                        finalReason = r.group(1);
                    }
                    String finalTitle = null;
                    if (i.find()) {
                        finalTitle = i.group(1);
                    }

                    if (c.find()) {
                        if (n.find()) {
                            switch (c.group(1)) {
                                case "ban" -> {
                                    String uuid = connection.UUIDfromName(n.group(1));
                                    if (CONNECTED_SOCKET_MAP.containsKey(uuid)) {
                                        ClientSocket socket = CONNECTED_SOCKET_MAP.get(uuid);

                                        disconnectPlayer(socket, finalTitle != null ? finalTitle : "Banned", finalReason);
                                        System.out.println("Banned " + n.group(1) + " successfully");
                                    }
                                }
                            }
                        } else {
                            switch (c.group(1)) {
                                case "restart", "reload" -> {
                                    for (ClientSocket socket : CONNECTED_SOCKET_MAP.values()) {
                                        disconnectPlayer(socket, finalTitle != null ? finalTitle : "Server restarting", finalReason);
                                    }
                                }
                                case "dev", "maintenance", "fix" -> {
                                    for (ClientSocket socket : CONNECTED_SOCKET_MAP.values()) {
                                        disconnectPlayer(socket, finalTitle != null ? finalTitle : "Maintenance", finalReason);
                                    }
                                }
                            }
                            System.out.println("Kicked all clients successfully");
                        }
                    }

                     */
                }
            }
        };

        commandListener.start();
        long total = 6;
        long start = System.currentTimeMillis();

        System.out.println("Starting server");

        printProgress(start, total, 0);
        connection = new MySQLConnection("ulucl02v8dm4l3qm", "bf5v9fiyfc6bqge4qrz1-mysql.services.clever-cloud.com", "bf5v9fiyfc6bqge4qrz1", 3306);
        printProgress(start, total, 1);
        connection.loadData();
        printProgress(start, total, 2);
        connection.savePlayers();
        printProgress(start, total, 3);
        calculator = new RatingCalculator();
        printProgress(start, total, 4);
        results = new RatingPeriodResults();
        printProgress(start, total, 5);

        try (ServerSocket socket = new ServerSocket(55285)) {
            printProgress(start, total, 6);
            System.out.println("\nServer successfully started on " + socket.getInetAddress().getHostAddress() + " with port " + socket.getLocalPort() + " (Server version: " + Server.version + ")");
            while (listening) {
                Socket connected = socket.accept();
                System.out.println("Client (" + connected.getInetAddress() + ":" + connected.getPort() + ") connected successfully!");

                InputStreamReader in = new InputStreamReader(connected.getInputStream());
                BufferedReader bf = new BufferedReader(in);

                String str = bf.readLine();

                JSONObject strObj = new JSONObject(str);
                String uuid = strObj.getString("online_uuid");

                PrintWriter pr = new PrintWriter(connected.getOutputStream());
                pr.println("Successfully connected to server");
                pr.flush();

                addCSocket(uuid, new ClientSocket(connected, uuid, bf));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}