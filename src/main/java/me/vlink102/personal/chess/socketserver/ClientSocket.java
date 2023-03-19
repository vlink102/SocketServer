package me.vlink102.personal.chess.socketserver;

import org.json.JSONObject;

import java.io.*;
import java.net.Socket;
import java.util.Set;

public class ClientSocket extends Thread {
    private final Socket socket;
    private boolean playing = false;
    private final String uuid;
    private final InputStreamReader streamReader;
    private final PrintWriter printWriter;
    private final BufferedReader reader;
    private final DataOutputStream outputStream;

    public ClientSocket(Socket socket, String uuid, InputStreamReader streamReader, PrintWriter printWriter, BufferedReader reader) {
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

    public void setPlaying(boolean playing) {
        this.playing = playing;
    }

    public boolean isPlaying() {
        return playing;
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
        CHALLENGE
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
        if (keys.contains("opponent")) {
            return PacketType.INIT_GAME;
        }
        if (keys.contains("draw")) {
            return PacketType.GAME_OVER;
        }
        if (keys.contains("online_request_uuid")) {
            return PacketType.ONLINE_REQUEST;
        }
        if (keys.contains("challenger")) {
            return PacketType.CHALLENGE;
        }
        return null;
    }

    @Override
    public void run() {
        try {
            String move;
            while ((move = reader.readLine()) != null) {
                JSONObject object = new JSONObject(move);

                PacketType type = getPacketType(object);
                if (type == null) continue;


                switch (type) {
                    case MOVE -> {

                    }
                    case ABORT -> {
                        String uuid = object.getString("abort_uuid");
                        setPlaying(false);
                    }
                    case RESIGN -> {
                        String uuid = object.getString("resign_uuid");
                        setPlaying(false);
                    }
                    case OFFER_DRAW -> {
                        String uuid = object.getString("draw_uuid");
                        // get opposition TODO
                        setPlaying(false);
                    }
                    case INIT_GAME -> {
                        String opponent = object.getString("opponent");

                        setPlaying(true);
                    }
                    case GAME_OVER -> {
                        setPlaying(false);
                    }
                    case ONLINE_REQUEST -> {
                        JSONObject wrapper = new JSONObject();
                        JSONObject o = new JSONObject();
                        for (String uuid : Main.CONNECTED_SOCKET_MAP.keySet()) {
                            if (!Main.CONNECTED_SOCKET_MAP.get(uuid).isPlaying() && !(object.getString("online_request_uuid").equals(uuid))) {
                                o.put(uuid, Main.connection.nameFromUUID(uuid));
                            }
                        }
                        wrapper.put("online_players", o);
                        outputStream.writeBytes(wrapper + "\n");
                        outputStream.flush();
                    }
                    case CHALLENGE -> {
                        String to = object.getString("challenged");
                        Main.addPendingChallenge(uuid, to);
                        JSONObject o = new JSONObject();
                        o.put("incoming_challenge", uuid);
                        ClientSocket opponentSocket = Main.getFromUUID(to);
                        opponentSocket.outputStream.writeBytes(o + "\n");
                        opponentSocket.outputStream.flush();
                        outputStream.writeBytes("Successfully sent challenge to " + Main.connection.nameFromUUID(to) + "!\n");
                        outputStream.flush(); // TODO convert all messages to jsonobjects (INCLUDING CONNECT SERVER)
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