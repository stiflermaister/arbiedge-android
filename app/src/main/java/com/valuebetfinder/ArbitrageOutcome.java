package com.valuebetfinder;

public class ArbitrageOutcome {
    private final String selection;
    private final double odds;
    private final String bookmaker;
    private final String url;

    public ArbitrageOutcome(String selection, double odds, String bookmaker) {
        this(selection, odds, bookmaker, "");
    }

    public ArbitrageOutcome(String selection, double odds, String bookmaker, String url) {
        this.selection = selection;
        this.odds = odds;
        this.bookmaker = bookmaker;
        this.url = url == null ? "" : url;
    }

    public String getSelection() {
        return selection;
    }

    public double getOdds() {
        return odds;
    }

    public String getBookmaker() {
        return bookmaker;
    }

    public String getUrl() {
        return url;
    }

    public double getImpliedProbability() {
        return 1.0 / odds;
    }
}
