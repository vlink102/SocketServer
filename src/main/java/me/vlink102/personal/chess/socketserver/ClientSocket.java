package me.vlink102.personal.chess.socketserver;

import org.json.JSONObject;

import javax.swing.*;
import java.io.*;
import java.net.Socket;
import java.util.HashMap;
import java.util.Objects;
import java.util.Set;

public class ClientSocket extends Thread {
    private final Socket socket;
    private final String uuid;
    private final InputStreamReader streamReader;
    private final PrintWriter printWriter;
    private final BufferedReader reader;
    private final DataOutputStream outputStream;

    public ClientSocket(Socket socket, String uuid, InputStreamReader streamReader, PrintWriter printWriter, BufferedReader reader) {
        this.pendingChallenges = new HashMap<>();
        this.socket = socket;
        this.uuid = uuid;
        this.streamReader = streamReader;
        this.printWriter = printWriter;
        this.reader = reader;
        try {
            this.outputStream = new DataOutputStream(socket.getOutputStream());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        System.out.println("Client socket [" + uuid + " (" + Main.connection.nameFromUUID(uuid) + ")] initialised");
    }

    public String getUuid() {
        return uuid;
    }

    public enum PacketType {
        ONLINE,
        MOVE,
        OFFER_DRAW,
        RESIGN,
        ABORT,
        INIT_GAME,
        GAME_OVER,
        ONLINE_REQUEST,
        CHALLENGE,
        ACCEPT_CHALLENGE,
        DECLINE_CHALLENGE
    }

    public PacketType getPacketType(JSONObject object) {
        Set<String> keys = object.keySet();
        if (keys.contains("resign_uuid")) {
            return PacketType.RESIGN;
        }
        if (keys.contains("abort_uuid")) {
            return PacketType.ABORT;
        }
        if (keys.contains("draw_uuid")) {
            return PacketType.OFFER_DRAW;
        }
        if (keys.contains("move")) {
            return PacketType.MOVE;
        }
        if (keys.contains("draw")) {
            return PacketType.GAME_OVER;
        }
        if (keys.contains("online_request_uuid")) {
            return PacketType.ONLINE_REQUEST;
        }
        if (keys.contains("challenge-id")) {
            return PacketType.CHALLENGE;
        }
        if (keys.contains("declined-challenge")) {
            return PacketType.DECLINE_CHALLENGE;
        }
        if (keys.contains("accepted-challenge")) {
            return PacketType.ACCEPT_CHALLENGE;
        }
        return null;
    }

    public JSONObject getOnline(String requestUUID) {
        JSONObject wrapper = new JSONObject();
        JSONObject o = new JSONObject();
        for (String uuid : Main.CONNECTED_SOCKET_MAP.keySet()) {
            if (!(requestUUID.equals(uuid))) {
                o.put(uuid, Main.connection.nameFromUUID(uuid));
            }
        }
        wrapper.put("online_players", o);
        return wrapper;
    }

    private final HashMap<Long, JSONObject> pendingChallenges;

    @Override
    public void run() {
        try {
            String move;
            while ((move = reader.readLine()) != null) {
                JSONObject object = new JSONObject(move);

                PacketType type = getPacketType(object);
                if (type == null) continue;

                System.out.println(move);

                switch (type) {
                    case ONLINE -> {
                        for (String uuid : Main.CONNECTED_SOCKET_MAP.keySet()) {
                            DataOutputStream stream = Main.CONNECTED_SOCKET_MAP.get(uuid).outputStream;
                            stream.writeBytes(getOnline(this.uuid) + "\n");
                            stream.flush();
                        }
                    }
                    case MOVE -> {

                    }
                    case ABORT -> {
                        String uuid = object.getString("abort_uuid");
                    }
                    case RESIGN -> {
                        String uuid = object.getString("resign_uuid");
                    }
                    case OFFER_DRAW -> {
                        String uuid = object.getString("draw_uuid");
                        // get opposition TODO
                    }
                    case INIT_GAME -> {
                        String opponent = object.getString("opponent");
                    }
                    case GAME_OVER -> {

                    }
                    case ONLINE_REQUEST -> {
                        outputStream.writeBytes(getOnline(this.uuid) + "\n");
                        outputStream.flush();
                    }
                    case CHALLENGE -> {
                        String opponentUUID = object.getString("challenged");
                        if (!Objects.equals(object.getString("challenger"), uuid)) {
                            System.out.println("Error: Challenger UUID does not equal ClientSocket saved UUID");
                            break;
                        }
                        ClientSocket opponentSocket = Main.CONNECTED_SOCKET_MAP.get(opponentUUID);
                        if (opponentSocket == null) {
                            System.out.println("Error: Opponent ClientSocket not found");
                            break;
                        }

                        outputStream.writeBytes("Successfully sent challenge to " + Main.connection.nameFromUUID(opponentUUID) + "!\n");
                        outputStream.flush();

                        pendingChallenges.put(object.getLong("challenge-id"), object);

                        opponentSocket.outputStream.writeBytes("Successfully received challenge from " + Main.connection.nameFromUUID(uuid) + "!\n");
                        opponentSocket.outputStream.flush();

                        opponentSocket.outputStream.writeBytes(object + "\n");
                        opponentSocket.outputStream.flush();
                    }
                    case ACCEPT_CHALLENGE -> {
                        Long challengeID = object.getLong("accepted-challenge");
                        String opponentUUID = object.getString("opponent");
                        ClientSocket opponentSocket = Main.CONNECTED_SOCKET_MAP.get(opponentUUID);
                        if (!opponentSocket.pendingChallenges.containsKey(challengeID)) {
                            System.out.println("Error: Client accepted nonexistent challenge");
                            break;
                        }
                        JSONObject acceptedChallenge = opponentSocket.pendingChallenges.get(challengeID);
                        outputStream.writeBytes("Successfully accepted " + Main.connection.nameFromUUID(opponentUUID) + "'s challenge!\n");
                        outputStream.flush();
                        opponentSocket.outputStream.writeBytes(Main.connection.nameFromUUID(uuid) + " accepted your challenge!\n");
                        opponentSocket.outputStream.flush();

                        opponentSocket.pendingChallenges.remove(challengeID);
                    }
                    case DECLINE_CHALLENGE -> {
                        Long challengeID = object.getLong("declined-challenge");
                        String opponentUUID = object.getString("opponent");
                        ClientSocket opponentSocket = Main.CONNECTED_SOCKET_MAP.get(opponentUUID);
                        if (!opponentSocket.pendingChallenges.containsKey(challengeID)) {
                            System.out.println("Error: Client declined nonexistent challenge");
                            break;
                        }
                        JSONObject declinedChallenge = opponentSocket.pendingChallenges.get(challengeID);
                        outputStream.writeBytes("Successfully declined " + Main.connection.nameFromUUID(opponentUUID) + "'s challenge!\n");
                        outputStream.flush();
                        opponentSocket.outputStream.writeBytes(Main.connection.nameFromUUID(uuid) + " declined your challenge.\n");
                        opponentSocket.outputStream.flush();

                        opponentSocket.pendingChallenges.remove(challengeID);
                    }
                }
            }
            this.interrupt();
            System.out.println("Client disconnected: " + uuid + " (" + Main.connection.nameFromUUID(uuid) + ")");
            Main.removeCSocket(this);
        } catch (IOException ex) {
            System.out.println("Server exception: " + ex.getMessage());
            ex.printStackTrace();
            Main.removeCSocket(this);
        }
    }
}