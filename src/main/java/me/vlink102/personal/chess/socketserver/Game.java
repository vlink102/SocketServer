package me.vlink102.personal.chess.socketserver;

public class Game {
    private final String opponent;
    private boolean gameOver = false;

    public Game(String opponent) {
        this.opponent = opponent;
    }

    public String getOpponent() {
        return opponent;
    }

    public boolean isGameOver() {
        return gameOver;
    }

    public void setGameOver(boolean gameOver) {
        this.gameOver = gameOver;
    }
}
