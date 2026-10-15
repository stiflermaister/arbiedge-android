package com.valuebetfinder;

public class AiPickAnalytics {
    private final int total;
    private final int settled;
    private final int pending;
    private final int wins;
    private final int losses;
    private final int voids;
    private final double winRate;
    private final double roi;
    private final double profitUnits;
    private final double mvpWinRate;
    private final double mvpProfitUnits;

    public AiPickAnalytics(int total, int settled, int pending, int wins, int losses, int voids,
                           double winRate, double roi, double profitUnits,
                           double mvpWinRate, double mvpProfitUnits) {
        this.total = total;
        this.settled = settled;
        this.pending = pending;
        this.wins = wins;
        this.losses = losses;
        this.voids = voids;
        this.winRate = winRate;
        this.roi = roi;
        this.profitUnits = profitUnits;
        this.mvpWinRate = mvpWinRate;
        this.mvpProfitUnits = mvpProfitUnits;
    }

    public static AiPickAnalytics empty() {
        return new AiPickAnalytics(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    public int getTotal() { return total; }
    public int getSettled() { return settled; }
    public int getPending() { return pending; }
    public int getWins() { return wins; }
    public int getLosses() { return losses; }
    public int getVoids() { return voids; }
    public double getWinRate() { return winRate; }
    public double getRoi() { return roi; }
    public double getProfitUnits() { return profitUnits; }
    public double getMvpWinRate() { return mvpWinRate; }
    public double getMvpProfitUnits() { return mvpProfitUnits; }
}
