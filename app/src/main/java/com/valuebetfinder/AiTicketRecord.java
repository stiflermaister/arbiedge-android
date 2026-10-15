package com.valuebetfinder;

import java.util.List;

public class AiTicketRecord {
    private final String type;
    private final String status;
    private final String createdAt;
    private final double odds;
    private final List<AiPick> picks;

    public AiTicketRecord(String type, String status, String createdAt, double odds, List<AiPick> picks) {
        this.type = type;
        this.status = status;
        this.createdAt = createdAt;
        this.odds = odds;
        this.picks = picks;
    }

    public String getType() {
        return type;
    }

    public String getStatus() {
        return status;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public double getOdds() {
        return odds;
    }

    public List<AiPick> getPicks() {
        return picks;
    }

    public String getOddsLabel() {
        return odds > 1.0 ? String.format(java.util.Locale.US, "%.2f", odds) : "-";
    }
}
