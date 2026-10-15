package com.valuebetfinder;

import java.util.Locale;

public class ValueBet {
    private final String country;
    private final String league;
    private final String match;
    private final String market;
    private final String selection;
    private final double odds;
    private final double modelProbability;
    private final String bookmaker;

    public ValueBet(
            String country,
            String league,
            String match,
            String market,
            String selection,
            double odds,
            double modelProbability,
            String bookmaker
    ) {
        this.league = league;
        this.country = country;
        this.match = match;
        this.market = market;
        this.selection = selection;
        this.odds = odds;
        this.modelProbability = modelProbability;
        this.bookmaker = bookmaker;
    }

    public String getLeague() {
        return league;
    }

    public String getCountry() {
        return country;
    }

    public String getMatch() {
        return match;
    }

    public String getMarket() {
        return market;
    }

    public String getSelection() {
        return selection;
    }

    public double getOdds() {
        return odds;
    }

    public double getModelProbability() {
        return modelProbability;
    }

    public String getBookmaker() {
        return bookmaker;
    }

    public double getImpliedProbability() {
        return 1.0 / odds;
    }

    public double getEdge() {
        return modelProbability - getImpliedProbability();
    }

    public double getExpectedValue() {
        return (modelProbability * odds) - 1.0;
    }

    public String getEdgeLabel() {
        return formatPercent(getEdge());
    }

    public String getModelProbabilityLabel() {
        return formatPercent(modelProbability);
    }

    public String getImpliedProbabilityLabel() {
        return formatPercent(getImpliedProbability());
    }

    public String getExpectedValueLabel() {
        return formatPercent(getExpectedValue());
    }

    private String formatPercent(double value) {
        return String.format(Locale.US, "%+.1f%%", value * 100.0);
    }
}
