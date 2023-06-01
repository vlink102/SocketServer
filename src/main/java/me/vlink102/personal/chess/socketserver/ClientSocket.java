package me.vlink102.personal.chess.socketserver;

import me.vlink102.personal.chess.socketserver.ratings.Rating;
import me.vlink102.personal.chess.socketserver.ratings.RatingCalculator;
import org.json.JSONObject;

import java.io.*;
import java.net.Socket;
import java.util.HashMap;
import java.util.Objects;
import java.util.Set;

public class ClientSocket extends Thread {

    private final String uuid;
    private final BufferedReader reader;
    private final DataOutputStream outputStream;

    public ClientSocket(Socket socket, String uuid, BufferedReader reader) {
        this.pendingChallenges = new HashMap<>();
        this.games = new HashMap<>();
        this.uuid = uuid;
        this.reader = reader;
        try {
            this.outputStream = new DataOutputStream(socket.getOutputStream());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        System.out.println("Client '" + Server.connection.nameFromUUID(uuid) + "' connected successfully (" + uuid + ")");
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
        ONLINE_REQUEST,
        CHALLENGE,
        ACCEPT_CHALLENGE,
        DECLINE_CHALLENGE,
        ACCEPT_DRAW,
        DECLINE_DRAW,
        VERSION_CONTROL,
        CHAT_MESSAGE,
        GAME_END
    }

    public PacketType getPacketType(JSONObject object) {
        Set<String> keys = object.keySet();
        if (keys.contains("version")) {
            return PacketType.VERSION_CONTROL;
        }
        if (keys.contains("end-game-id")) {
            return PacketType.GAME_END;
        }
        if (keys.contains("chat-uuid")) {
            return PacketType.CHAT_MESSAGE;
        }
        if (keys.contains("resign_uuid")) {
            return PacketType.RESIGN;
        }
        if (keys.contains("abort_uuid")) {
            return PacketType.ABORT;
        }
        if (keys.contains("draw_uuid")) {
            return PacketType.OFFER_DRAW;
        }
        if (keys.contains("game-id")) {
            return PacketType.MOVE;
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
        if (keys.contains("declined-draw-game-id")) {
            return PacketType.DECLINE_DRAW;
        }
        if (keys.contains("accepted-draw-game-id")) {
            return PacketType.ACCEPT_DRAW;
        }
        return null;
    }

    public JSONObject getOnline(String requestUUID) {
        JSONObject wrapper = new JSONObject();
        JSONObject o = new JSONObject();
        for (String uuid : Server.CONNECTED_SOCKET_MAP.keySet()) {
            if (!(requestUUID.equals(uuid))) {
                o.put(uuid, Server.connection.nameFromUUID(uuid));
            }
        }
        wrapper.put("online_players", o);
        return wrapper;
    }

    private final HashMap<Long, JSONObject> pendingChallenges;
    private final HashMap<Long, Game> games;

    @Override
    public void run() {
        try {
            String move;
            while ((move = reader.readLine()) != null) {
                JSONObject object = new JSONObject(move);

                System.out.println(move);
                PacketType type = getPacketType(object);
                if (type == null) continue;


                switch (type) {
                    case VERSION_CONTROL -> {
                        JSONObject o = new JSONObject();
                        boolean correctVersion =  (object.getString("version").equals(Server.version));
                        String uuid = object.getString("uuid");
                        o.put("version-control-result", correctVersion);
                        if (!correctVersion) {
                            o.put("correct-version", Server.version);
                            o.put("update-link", Server.updateLink);
                        } else {
                            o.put("banned", Server.bannedPlayers.contains(uuid));
                        }
                        outputStream.writeBytes(o + "\n");
                        outputStream.flush();
                    }
                    case CHAT_MESSAGE -> {
                        long gameID = object.getLong("chat-game-id");
                        if (games.containsKey(gameID)) {
                            Game game = games.get(gameID);
                            String opponentUUID = game.getOpponent();
                            ClientSocket opponentSocket = Server.CONNECTED_SOCKET_MAP.get(opponentUUID);
                            opponentSocket.outputStream.writeBytes(object + "\n");
                            opponentSocket.outputStream.flush();
                        } else {
                            System.out.println("Error: Client sent chat message to nonexistent game: " + gameID);
                        }
                    }
                    case ONLINE -> {
                        for (ClientSocket socket1 : Server.CONNECTED_SOCKET_MAP.values()) {
                            socket1.outputStream.writeBytes(getOnline(this.uuid) + "\n");
                            socket1.outputStream.flush();
                        }
                    }
                    case MOVE -> {
                        long gameID = object.getLong("game-id");
                        if (games.containsKey(gameID)) {
                            Game game = games.get(gameID);
                            String opponentUUID = game.getOpponent();
                            ClientSocket opponentSocket = Server.CONNECTED_SOCKET_MAP.get(opponentUUID);
                            opponentSocket.outputStream.writeBytes(object + "\n");
                            opponentSocket.outputStream.flush();
                        } else {
                            System.out.println("Error: Client sent move to nonexistent game: " + gameID);
                        }
                    }
                    case GAME_END -> {
                        long gameID = object.getLong("end-game-id");
                        if (games.containsKey(gameID)) {
                            Game game = games.get(gameID);
                            String opponentUUID = game.getOpponent();
                            Rating self = MySQLConnection.getPlayer(MySQLConnection.players, uuid);
                            Rating opp = MySQLConnection.getPlayer(MySQLConnection.players, opponentUUID);
                            int clientElo = (int) self.getRating();
                            int opponentElo = (int) opp.getRating();
                            int reason = object.getInt("end-game-reason");
                            switch (reason) {
                                case 0, 50, 99, 5, 6, 3, 4 -> Server.results.addDraw(MySQLConnection.getPlayer(MySQLConnection.players, uuid), MySQLConnection.getPlayer(MySQLConnection.players, opponentUUID));
                                case 1, 101, 7, 9 -> Server.results.addResult(MySQLConnection.getPlayer(MySQLConnection.players, uuid), MySQLConnection.getPlayer(MySQLConnection.players, opponentUUID));
                                case 2, 100, 8, 10 -> Server.results.addResult(MySQLConnection.getPlayer(MySQLConnection.players, opponentUUID), MySQLConnection.getPlayer(MySQLConnection.players, uuid));
                                case 200 -> System.out.println("Error: Illegal position in challenge game");
                            }
                            Server.calculator.updateRatings(Server.results);
                            Server.connection.savePlayers();
                            Server.connection.savePeriod(Server.results);
                            Rating newSelf = MySQLConnection.getPlayer(MySQLConnection.players, uuid);
                            Rating newOpp = MySQLConnection.getPlayer(MySQLConnection.players, opponentUUID);
                            int newClientElo = (int) newSelf.getRating();
                            int newOpponentElo = (int) newOpp.getRating();

                            JSONObject o = new JSONObject();
                            o.put("end-game-id", gameID);
                            o.put("end-game-rating-old", clientElo);
                            o.put("end-game-rating-new", newClientElo);
                            o.put("end-game-reason", reason);
                            outputStream.writeBytes(o + "\n");
                            outputStream.flush();

                            ClientSocket opponentSocket = Server.CONNECTED_SOCKET_MAP.get(opponentUUID);
                            JSONObject s = new JSONObject();
                            s.put("end-game-id", gameID);
                            s.put("end-game-rating-old", opponentElo);
                            s.put("end-game-rating-new", newOpponentElo);
                            s.put("end-game-reason", reason);
                            opponentSocket.outputStream.writeBytes(s + "\n");
                            opponentSocket.outputStream.flush();

                        } else {
                            System.out.println("Error: Client ended nonexistent game: " + gameID);
                        }
                    }
                    case ABORT -> {
                        long gameID = object.getLong("abort-game-id");
                        if (games.containsKey(gameID)) {
                            Game game = games.get(gameID);
                            String opponentUUID = game.getOpponent();
                            ClientSocket opponentSocket = Server.CONNECTED_SOCKET_MAP.get(opponentUUID);
                            opponentSocket.outputStream.writeBytes(object + "\n");
                            opponentSocket.outputStream.flush();
                        } else {
                            System.out.println("Error: Client sent abort to nonexistent game: " + gameID);
                        }
                    }
                    case RESIGN -> {
                        long gameID = object.getLong("resign-game-id");
                        if (games.containsKey(gameID)) {
                            Game game = games.get(gameID);
                            String opponentUUID = game.getOpponent();
                            ClientSocket opponentSocket = Server.CONNECTED_SOCKET_MAP.get(opponentUUID);
                            opponentSocket.outputStream.writeBytes(object + "\n");
                            opponentSocket.outputStream.flush();
                        } else {
                            System.out.println("Error: Client sent resignation to nonexistent game: " + gameID);
                        }
                    }
                    case OFFER_DRAW -> {
                        long gameID = object.getLong("draw-game-id");
                        if (games.containsKey(gameID)) {
                            Game game = games.get(gameID);
                            String opponentUUID = game.getOpponent();
                            ClientSocket opponentSocket = Server.CONNECTED_SOCKET_MAP.get(opponentUUID);
                            opponentSocket.outputStream.writeBytes(object + "\n");
                            opponentSocket.outputStream.flush();
                        } else {
                            System.out.println("Error: Client sent draw to nonexistent game: " + gameID);
                        }
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
                        ClientSocket opponentSocket = Server.CONNECTED_SOCKET_MAP.get(opponentUUID);
                        if (opponentSocket == null) {
                            System.out.println("Error: Opponent ClientSocket not found");
                            break;
                        }

                        outputStream.writeBytes("Successfully sent challenge to " + Server.connection.nameFromUUID(opponentUUID) + "!\n");
                        outputStream.flush();

                        pendingChallenges.put(object.getLong("challenge-id"), object);

                        opponentSocket.outputStream.writeBytes("Successfully received challenge from " + Server.connection.nameFromUUID(uuid) + "!\n");
                        opponentSocket.outputStream.flush();

                        opponentSocket.outputStream.writeBytes(object + "\n");
                        opponentSocket.outputStream.flush();
                    }
                    case ACCEPT_CHALLENGE -> {
                        Long challengeID = object.getLong("accepted-challenge");
                        String opponentUUID = object.getString("opponent");
                        ClientSocket opponentSocket = Server.CONNECTED_SOCKET_MAP.get(opponentUUID);
                        if (!opponentSocket.pendingChallenges.containsKey(challengeID)) {
                            System.out.println("Error: Client accepted nonexistent challenge");
                            break;
                        }
                        JSONObject acceptedChallenge = opponentSocket.pendingChallenges.get(challengeID);
                        outputStream.writeBytes("Successfully accepted " + Server.connection.nameFromUUID(opponentUUID) + "'s challenge!\n");
                        outputStream.flush();
                        JSONObject o = new JSONObject();
                        o.put("accepted-challenge-challenged", acceptedChallenge);
                        o.put("challenged-data", object.getJSONObject("challenged-data"));
                        outputStream.writeBytes(o + "\n");
                        outputStream.flush();
                        opponentSocket.outputStream.writeBytes(Server.connection.nameFromUUID(uuid) + " accepted your challenge!\n");
                        opponentSocket.outputStream.flush();
                        JSONObject acceptedToChallenger = new JSONObject();
                        acceptedToChallenger.put("accepted-challenge-challenger", challengeID);
                        opponentSocket.outputStream.writeBytes(acceptedToChallenger + "\n");
                        opponentSocket.outputStream.flush();

                        opponentSocket.pendingChallenges.remove(challengeID);
                        games.put(challengeID, new Game(opponentUUID));
                        opponentSocket.games.put(challengeID, new Game(uuid));
                    }
                    case DECLINE_CHALLENGE -> {
                        Long challengeID = object.getLong("declined-challenge");
                        String opponentUUID = object.getString("opponent");
                        ClientSocket opponentSocket = Server.CONNECTED_SOCKET_MAP.get(opponentUUID);
                        if (!opponentSocket.pendingChallenges.containsKey(challengeID)) {
                            System.out.println("Error: Client declined nonexistent challenge");
                            break;
                        }
                        JSONObject declinedChallenge = opponentSocket.pendingChallenges.get(challengeID);
                        outputStream.writeBytes("Successfully declined " + Server.connection.nameFromUUID(opponentUUID) + "'s challenge!\n");
                        outputStream.flush();
                        opponentSocket.outputStream.writeBytes(Server.connection.nameFromUUID(uuid) + " declined your challenge.\n");
                        opponentSocket.outputStream.flush();

                        JSONObject declinedChallengeData = new JSONObject();
                        declinedChallengeData.put("declined-challenge", challengeID);
                        opponentSocket.outputStream.writeBytes(declinedChallengeData + "\n");
                        opponentSocket.outputStream.flush();

                        opponentSocket.pendingChallenges.remove(challengeID);
                    }
                    case ACCEPT_DRAW -> {
                        Long gameID = object.getLong("accepted-draw-game-id");
                        String opponentUUID = object.getString("accepted-draw-uuid");
                        ClientSocket opponentSocket = Server.CONNECTED_SOCKET_MAP.get(opponentUUID);
                        if (opponentSocket.games.containsKey(gameID)) {
                            outputStream.writeBytes(object + "\n");
                            outputStream.flush();
                            opponentSocket.outputStream.writeBytes(object + "\n");
                            opponentSocket.outputStream.flush();
                        } else {
                            System.out.println("Error: Client accepted nonexistent draw offer: " + gameID);
                        }
                    }
                    case DECLINE_DRAW -> {
                        Long gameID = object.getLong("declined-draw-game-id");
                        String opponentUUID = object.getString("declined-draw-uuid");
                        ClientSocket opponentSocket = Server.CONNECTED_SOCKET_MAP.get(opponentUUID);
                        if (opponentSocket.games.containsKey(gameID)) {
                            opponentSocket.outputStream.writeBytes(object + "\n");
                            opponentSocket.outputStream.flush();
                        } else {
                            System.out.println("Error: Client declined nonexistent draw offer: " + gameID);
                        }
                    }
                }
            }
            this.interrupt();
            System.out.println("User '" + Server.connection.nameFromUUID(uuid) + "' disconnected (" + uuid + ")");
            Server.removeCSocket(this);
        } catch (IOException ex) {
            System.out.println("Server exception: " + ex.getMessage());
            ex.printStackTrace();
            Server.removeCSocket(this);
        }
    }

    public DataOutputStream getOutputStream() {
        return outputStream;
    }
}