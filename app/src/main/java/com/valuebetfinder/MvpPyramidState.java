package com.valuebetfinder;

import java.util.Locale;

public class MvpPyramidState {
    private final boolean active;
    private final int level;
    private final double startStake;
    private final double currentStake;
    private final double bankedProfit;
    private final String lastMatch;
    private final String lastSelection;

    public MvpPyramidState(
            boolean active,
            int level,
            double startStake,
            double currentStake,
            double bankedProfit,
            String lastMatch,
            String lastSelection
    ) {
        this.active = active;
        this.level = level;
        this.startStake = startStake;
        this.currentStake = currentStake;
        this.bankedProfit = bankedProfit;
        this.lastMatch = lastMatch;
        this.lastSelection = lastSelection;
    }

    public static MvpPyramidState empty() {
        return new MvpPyramidState(false, 0, 0.0, 0.0, 0.0, "", "");
    }

    public boolean isActive() {
        return active;
    }

    public int getLevel() {
        return level;
    }

    public double getStartStake() {
        return startStake;
    }

    public double getCurrentStake() {
        return currentStake;
    }

    public double getBankedProfit() {
        return bankedProfit;
    }

    public String getLastMatch() {
        return lastMatch;
    }

    public String getLastSelection() {
        return lastSelection;
    }

    public String currentStakeLabel() {
        return String.format(Locale.US, "%.2f", currentStake);
    }

    public String bankedProfitLabel() {
        return String.format(Locale.US, "%+.2f", bankedProfit);
    }
}
