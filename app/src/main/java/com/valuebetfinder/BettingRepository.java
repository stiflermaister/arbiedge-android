package com.valuebetfinder;

import java.util.ArrayList;
import java.util.List;

public class BettingRepository {
    public List<AiPick> getAiDailyPicks() {
        return new ArrayList<>();
    }

    public List<ValueBet> getTopValueBets() {
        return new ArrayList<>();
    }

    public List<ArbitrageOpportunity> getArbitrageOpportunities() {
        return new ArrayList<>();
    }
}
