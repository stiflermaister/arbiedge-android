package com.valuebetfinder;

import java.util.List;
import java.util.Locale;

public class ArbitrageOpportunity {
    private final String country;
    private final String league;
    private final String match;
    private final String market;
    private final String eventDate;
    private final String foundAt;
    private final String sourceLabel;
    private final double peakProfitPercent;
    private final int refreshCount;
    private final int firstLostAfterRefreshes;
    private final int firstLostAfterMinutes;
    private final String firstLostAt;
    private final Double preStartProfitPercent;
    private final String preStartAt;
    private final List<ArbitrageOutcome> outcomes;

    public ArbitrageOpportunity(
            String country,
            String league,
            String match,
            String market,
            List<ArbitrageOutcome> outcomes
    ) {
        this(country, league, match, market, "", "", outcomes, 0.0);
    }

    public ArbitrageOpportunity(
            String country,
            String league,
            String match,
            String market,
            String eventDate,
            String foundAt,
            List<ArbitrageOutcome> outcomes
    ) {
        this(country, league, match, market, eventDate, foundAt, outcomes, 0.0, "API ARB");
    }

    public ArbitrageOpportunity(
            String country,
            String league,
            String match,
            String market,
            String eventDate,
            String foundAt,
            List<ArbitrageOutcome> outcomes,
            double peakProfitPercent
    ) {
        this(country, league, match, market, eventDate, foundAt, outcomes, peakProfitPercent, "API ARB");
    }

    public ArbitrageOpportunity(
            String country,
            String league,
            String match,
            String market,
            String eventDate,
            String foundAt,
            List<ArbitrageOutcome> outcomes,
            double peakProfitPercent,
            String sourceLabel
    ) {
        this(country, league, match, market, eventDate, foundAt, outcomes,
                peakProfitPercent, sourceLabel, 0, 0, 0, "");
    }

    public ArbitrageOpportunity(
            String country,
            String league,
            String match,
            String market,
            String eventDate,
            String foundAt,
            List<ArbitrageOutcome> outcomes,
            double peakProfitPercent,
            String sourceLabel,
            int refreshCount,
            int firstLostAfterRefreshes,
            int firstLostAfterMinutes,
            String firstLostAt
    ) {
        this(country, league, match, market, eventDate, foundAt, outcomes,
                peakProfitPercent, sourceLabel, refreshCount, firstLostAfterRefreshes,
                firstLostAfterMinutes, firstLostAt, null, "");
    }

    public ArbitrageOpportunity(
            String country, String league, String match, String market,
            String eventDate, String foundAt, List<ArbitrageOutcome> outcomes,
            double peakProfitPercent, String sourceLabel, int refreshCount,
            int firstLostAfterRefreshes, int firstLostAfterMinutes, String firstLostAt,
            Double preStartProfitPercent, String preStartAt
    ) {
        this.country = country;
        this.league = league;
        this.match = match;
        this.market = market;
        this.eventDate = eventDate;
        this.foundAt = foundAt;
        this.sourceLabel = sourceLabel == null || sourceLabel.trim().isEmpty() ? "API ARB" : sourceLabel.trim();
        this.outcomes = outcomes;
        this.peakProfitPercent = peakProfitPercent;
        this.refreshCount = Math.max(0, refreshCount);
        this.firstLostAfterRefreshes = Math.max(0, firstLostAfterRefreshes);
        this.firstLostAfterMinutes = Math.max(0, firstLostAfterMinutes);
        this.firstLostAt = firstLostAt == null ? "" : firstLostAt;
        this.preStartProfitPercent = preStartProfitPercent;
        this.preStartAt = preStartAt == null ? "" : preStartAt;
    }

    public String getCountry() {
        return country;
    }

    public String getLeague() {
        return league;
    }

    public String getMatch() {
        return match;
    }

    public String getMarket() {
        return market;
    }

    public String getEventDate() {
        return eventDate;
    }

    public String getFoundAt() {
        return foundAt;
    }

    public String getSourceLabel() {
        return sourceLabel;
    }

    public List<ArbitrageOutcome> getOutcomes() {
        return outcomes;
    }

    public double getBookPercent() {
        double sum = 0.0;
        for (ArbitrageOutcome outcome : outcomes) {
            sum += outcome.getImpliedProbability();
        }
        return sum;
    }

    public double getProfitPercent() {
        double bookPercent = getBookPercent();
        return bookPercent <= 0.0 ? 0.0 : (1.0 / bookPercent) - 1.0;
    }

    public double getAnalyticsProfitPercent() {
        return Math.max(getProfitPercent(), peakProfitPercent);
    }

    public double getPeakProfitPercent() {
        return peakProfitPercent;
    }

    public int getRefreshCount() {
        return refreshCount;
    }

    public int getFirstLostAfterRefreshes() {
        return firstLostAfterRefreshes;
    }

    public int getFirstLostAfterMinutes() {
        return firstLostAfterMinutes;
    }

    public String getFirstLostAt() {
        return firstLostAt;
    }

    public boolean hasPreStartProfit() {
        return preStartProfitPercent != null;
    }

    public double getPreStartProfitPercent() {
        return preStartProfitPercent == null ? 0.0 : preStartProfitPercent;
    }

    public String getPreStartProfitLabel() {
        return String.format(Locale.US, "%+.2f%%", getPreStartProfitPercent() * 100.0);
    }

    public String getPreStartAt() {
        return preStartAt;
    }

    public String getPeakProfitLabel() {
        return String.format(Locale.US, "%+.2f%%", peakProfitPercent * 100.0);
    }

    public String getLifecycleLabel() {
        if (firstLostAfterRefreshes > 0) {
            String refreshWord = firstLostAfterRefreshes == 1 ? "refresh-a" : "refresh-a";
            String time = firstLostAfterMinutes > 0 ? " · " + firstLostAfterMinutes + " min" : "";
            return "Nestao posle " + firstLostAfterRefreshes + " " + refreshWord + time;
        }
        if (refreshCount > 0) {
            return "Nije nestao · " + refreshCount + " provera";
        }
        return "Praćenje počinje od sledećeg refresh-a";
    }

    public boolean isArbitrage() {
        return getBookPercent() < 1.0;
    }

    public String getProfitLabel() {
        return String.format(Locale.US, "%+.2f%%", getProfitPercent() * 100.0);
    }

    public String getBookPercentLabel() {
        return String.format(Locale.US, "%.2f%%", getBookPercent() * 100.0);
    }
}
