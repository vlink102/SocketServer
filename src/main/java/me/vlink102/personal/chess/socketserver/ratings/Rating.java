package me.vlink102.personal.chess.socketserver.ratings;

public class Rating {
    private final String name;
    private final String uuid;
    private double rating;
    private double ratingDeviation;
    private double volatility;
    private int numberOfResults = 0;
    private final String pwd;

    private double workingRating;
    private double workingRatingDeviation;
    private double workingVolatility;

    public double getWorkingRating() {
        return workingRating;
    }

    public double getWorkingRatingDeviation() {
        return workingRatingDeviation;
    }

    public double getWorkingVolatility() {
        return workingVolatility;
    }

    public String M() {
        return pwd;
    }

    /**
     *
     * @param uuid           An value through which you want to identify the rating (not actually used by the algorithm)
     * @param ratingSystem  An instance of the RatingCalculator object
     */
    public Rating(String name, String uuid, String pwd, RatingCalculator ratingSystem) {
        this.name = name;
        this.uuid = uuid;
        this.rating = ratingSystem.getDefaultRating();
        this.ratingDeviation = ratingSystem.getDefaultRatingDeviation();
        this.volatility = ratingSystem.getDefaultVolatility();
        this.pwd = pwd;
    }

    public Rating(String name, String uuid, double rating, double ratingDeviation, double volatility, int numberOfResults, double workingRating, double workingRatingDeviation, double workingVolatility, String pwd) {
        this.name = name;
        this.uuid = uuid;
        this.rating = rating;
        this.ratingDeviation = ratingDeviation;
        this.volatility = volatility;
        this.numberOfResults = numberOfResults;
        this.workingVolatility = workingVolatility;
        this.workingRatingDeviation = workingRatingDeviation;
        this.workingRating = workingRating;
        this.pwd = pwd;
    }

    /**
     * Return the average skill value of the player.
     */
    public double getRating() {
        return this.rating;
    }

    public void setRating(double rating) {
        this.rating = rating;
    }

    /**
     * Return the average skill value of the player scaled down
     * to the scale used by the algorithm's internal workings.
     */
    public double getGlicko2Rating() {
        return RatingCalculator.convertRatingToGlicko2Scale(this.rating);
    }

    /**
     * Set the average skill value, taking in a value in Glicko2 scale.
     */
    public void setGlicko2Rating(double rating) {
        this.rating = RatingCalculator.convertRatingToOriginalGlickoScale(rating);
    }

    public double getVolatility() {
        return volatility;
    }

    public void setVolatility(double volatility) {
        this.volatility = volatility;
    }

    public double getRatingDeviation() {
        return ratingDeviation;
    }

    public void setRatingDeviation(double ratingDeviation) {
        this.ratingDeviation = ratingDeviation;
    }

    /**
     * Return the rating deviation of the player scaled down
     * to the scale used by the algorithm's internal workings.
     */
    public double getGlicko2RatingDeviation() {
        return RatingCalculator.convertRatingDeviationToGlicko2Scale( ratingDeviation );
    }

    /**
     * Set the rating deviation, taking in a value in Glicko2 scale.
     */
    public void setGlicko2RatingDeviation(double ratingDeviation) {
        this.ratingDeviation = RatingCalculator.convertRatingDeviationToOriginalGlickoScale( ratingDeviation );
    }

    /**
     * Used by the calculation engine, to move interim calculations into their "proper" places.
     */
    public void finaliseRating() {
        this.setGlicko2Rating(workingRating);
        this.setGlicko2RatingDeviation(workingRatingDeviation);
        this.setVolatility(workingVolatility);

        this.setWorkingRatingDeviation(0);
        this.setWorkingRating(0);
        this.setWorkingVolatility(0);
    }

    /**
     * Returns a formatted rating for inspection
     * @return {ratingUid} / {ratingDeviation} / {volatility} / {numberOfResults}
     */
    @Override
    public String toString() {
        return uuid + " / " +
                rating + " / " +
                ratingDeviation + " / " +
                volatility + " / " +
                numberOfResults;
    }

    public int getNumberOfResults() {
        return numberOfResults;
    }

    public void incrementNumberOfResults(int increment) {
        this.numberOfResults = numberOfResults + increment;
    }

    public String getUuid() {
        return uuid;
    }

    public void setWorkingVolatility(double workingVolatility) {
        this.workingVolatility = workingVolatility;
    }

    public void setWorkingRating(double workingRating) {
        this.workingRating = workingRating;
    }

    public void setWorkingRatingDeviation(double workingRatingDeviation) {
        this.workingRatingDeviation = workingRatingDeviation;
    }

    public String getName() {
        return name;
    }
}
