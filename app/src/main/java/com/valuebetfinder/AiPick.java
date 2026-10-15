package com.valuebetfinder;

import java.util.List;

public class AiPick {
    private final String league;
    private final String date;
    private final String match;
    private final String market;
    private final String selection;
    private final String kickoff;
    private final int confidence;
    private final boolean mvp;
    private final String reason;
    private final List<String> keyFactors;
    private final double odds;
    private final String bookmaker;
    private final String id;
    private final String result;

    public AiPick(
            String league,
            String date,
            String match,
            String market,
            String selection,
            String kickoff,
            int confidence,
            boolean mvp,
            String reason,
            List<String> keyFactors,
            double odds,
            String bookmaker
    ) {
        this(league, date, match, market, selection, kickoff, confidence, mvp, reason,
                keyFactors, odds, bookmaker, "", "PENDING");
    }

    public AiPick(
            String league,
            String date,
            String match,
            String market,
            String selection,
            String kickoff,
            int confidence,
            boolean mvp,
            String reason,
            List<String> keyFactors,
            double odds,
            String bookmaker,
            String id,
            String result
    ) {
        this.league = league;
        this.date = date;
        this.match = match;
        this.market = market;
        this.selection = selection;
        this.kickoff = kickoff;
        this.confidence = confidence;
        this.mvp = mvp;
        this.reason = reason;
        this.keyFactors = keyFactors;
        this.odds = odds;
        this.bookmaker = bookmaker;
        this.id = id;
        this.result = result;
    }

    public String getLeague() {
        return league;
    }

    public String getDate() {
        return date;
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

    public String getKickoff() {
        return kickoff;
    }

    public int getConfidence() {
        return confidence;
    }

    public boolean isMvp() {
        return mvp;
    }

    public String getReason() {
        return reason;
    }

    public List<String> getKeyFactors() {
        return keyFactors;
    }

    public double getOdds() {
        return odds;
    }

    public String getBookmaker() {
        return bookmaker;
    }

    public String getId() { return id; }

    public String getResult() { return result; }

    public String getOddsLabel() {
        return odds > 1.0 ? String.format(java.util.Locale.US, "%.2f", odds) : "-";
    }
}
