package me.vlink102.personal.chess.socketserver.ratings;

public class Result {
    private static final double POINTS_FOR_WIN = 1.0;
    private static final double POINTS_FOR_LOSS = 0.0;
    private static final double POINTS_FOR_DRAW = 0.5;

    private boolean isDraw = false;
    private final Rating winner;
    private final Rating loser;

    /**
     * Record a new result from a match between two players.
     */
    public Result(Rating winner, Rating loser) {
        if ( ! validPlayers(winner, loser) ) {
            throw new IllegalArgumentException();
        }

        this.winner = winner;
        this.loser = loser;
    }


    /**
     * Record a draw between two players.
     *
     * @param player1
     * @param player2
     * @param isDraw (must be set to "true")
     */
    public Result(Rating player1, Rating player2, boolean isDraw) {
        if (! isDraw || ! validPlayers(player1, player2) ) {
            throw new IllegalArgumentException();
        }

        this.winner = player1;
        this.loser = player2;
        this.isDraw = true;
    }


    /**
     * Check that we're not doing anything silly like recording a match with only one player.
     *
     * @param player1
     * @param player2
     * @return
     */
    private boolean validPlayers(Rating player1, Rating player2) {
        return !player1.getUuid().equals(player2.getUuid());
    }


    /**
     * Test whether a particular player participated in the match represented by this result.
     *
     * @param player
     * @return boolean (true if player participated in the match)
     */
    public boolean participated(Rating player) {
        if ( winner.equals(player) || loser.equals(player) ) {
            return true;
        } else {
            return false;
        }
    }


    /**
     * Returns the "score" for a match.
     *
     * @param player
     * @return 1 for a win, 0.5 for a draw and 0 for a loss
     * @throws IllegalArgumentException
     */
    public double getScore(Rating player) throws IllegalArgumentException {
        double score;

        if ( winner.equals(player) ) {
            score = POINTS_FOR_WIN;
        } else if ( loser.equals(player) ) {
            score = POINTS_FOR_LOSS;
        } else {
            throw new IllegalArgumentException("Player " + player.getName() + " (" + player.getUuid() + ") " + " did not participate in match");
        }

        if ( isDraw ) {
            score = POINTS_FOR_DRAW;
        }

        return score;
    }


    /**
     * Given a particular player, returns the opponent.
     *
     * @param player
     * @return opponent
     */
    public Rating getOpponent(Rating player) {
        Rating opponent;

        if ( winner.equals(player) ) {
            opponent = loser;
        } else if ( loser.equals(player) ) {
            opponent = winner;
        } else {
            throw new IllegalArgumentException("Player " + player.getName() + " (" + player.getUuid() + ") " + " did not participate in match");
        }

        return opponent;
    }


    public Rating getWinner() {
        return this.winner;
    }


    public Rating getLoser() {
        return this.loser;
    }

    public int isDraw() {
        return isDraw ? 1 : 0;
    }
}
