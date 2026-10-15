package com.valuebetfinder;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class LocalTicketStore {
    private static final String PREFS = "valuebet_local_store";
    private static final String AI_LATEST = "ai_latest";
    private static final String AI_HISTORY = "ai_history";
    private static final String AI_PARLAY_HISTORY = "ai_parlay_history";
    private static final String AI_MVP_HISTORY = "ai_mvp_history";
    private static final String ARB_LATEST = "arb_latest";
    private static final String ARB_HISTORY = "arb_history";
    private static final String ARB_ARCHIVE = "arb_archive";
    private static final String MVP_PYRAMID = "mvp_pyramid";
    private static final int HISTORY_LIMIT = 30;

    private final SharedPreferences preferences;

    public LocalTicketStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public List<AiPick> loadAiLatest() {
        return parseAiPicks(preferences.getString(AI_LATEST, "[]"));
    }

    public void rolloverAiLatestIfOld() {
        List<AiPick> picks = loadAiLatest();
        if (picks.isEmpty()) {
            return;
        }
        String date = picks.get(0).getDate();
        if (!date.isEmpty() && date.compareTo(today()) < 0) {
            preferences.edit().putString(AI_LATEST, "[]").apply();
        }
    }

    public void clearAiLatestOnly() {
        preferences.edit().putString(AI_LATEST, "[]").apply();
    }

    public List<List<AiPick>> loadAiHistory() {
        return parseAiHistory(preferences.getString(AI_HISTORY, "[]"));
    }

    public void saveAiLatest(List<AiPick> picks) {
        String json = aiPicksToJson(picks).toString();
        preferences.edit().putString(AI_LATEST, json).apply();
        appendHistory(AI_HISTORY, json);
    }

    public void saveAiParlayResult(List<AiPick> picks, String status) {
        saveAiTicketResult(AI_PARLAY_HISTORY, "PARLAY", status, picks);
    }

    public void saveAiMvpResult(AiPick pick, String status) {
        List<AiPick> picks = new ArrayList<>();
        if (pick != null) {
            picks.add(pick);
        }
        saveAiTicketResult(AI_MVP_HISTORY, "MVP", status, picks);
    }

    public List<AiTicketRecord> loadAiParlayHistory() {
        return parseAiTicketHistory(preferences.getString(AI_PARLAY_HISTORY, "[]"));
    }

    public List<AiTicketRecord> loadAiMvpHistory() {
        return parseAiTicketHistory(preferences.getString(AI_MVP_HISTORY, "[]"));
    }

    public List<ArbitrageOpportunity> loadArbLatest() {
        return parseArbs(preferences.getString(ARB_LATEST, "[]"));
    }

    public List<List<ArbitrageOpportunity>> loadArbHistory() {
        return parseArbHistory(preferences.getString(ARB_HISTORY, "[]"));
    }

    public List<ArbitrageOpportunity> loadArbArchive() {
        return parseArbs(preferences.getString(ARB_ARCHIVE, "[]"));
    }

    public void saveArbArchive(List<ArbitrageOpportunity> opportunities) {
        preferences.edit().putString(ARB_ARCHIVE, arbsToJson(opportunities).toString()).apply();
    }

    public void saveArbLatest(List<ArbitrageOpportunity> opportunities) {
        String json = arbsToJson(opportunities).toString();
        preferences.edit().putString(ARB_LATEST, json).apply();
        appendHistory(ARB_HISTORY, json);
    }

    public MvpPyramidState loadMvpPyramid() {
        try {
            JSONObject item = new JSONObject(preferences.getString(MVP_PYRAMID, "{}"));
            return new MvpPyramidState(
                    item.optBoolean("active", false),
                    item.optInt("level", 0),
                    item.optDouble("start_stake", 0.0),
                    item.optDouble("current_stake", 0.0),
                    item.optDouble("banked_profit", 0.0),
                    item.optString("last_match", ""),
                    item.optString("last_selection", "")
            );
        } catch (Exception ignored) {
            return MvpPyramidState.empty();
        }
    }

    public void saveMvpPyramid(MvpPyramidState state) {
        JSONObject item = new JSONObject();
        try {
            item.put("active", state.isActive());
            item.put("level", state.getLevel());
            item.put("start_stake", state.getStartStake());
            item.put("current_stake", state.getCurrentStake());
            item.put("banked_profit", state.getBankedProfit());
            item.put("last_match", state.getLastMatch());
            item.put("last_selection", state.getLastSelection());
        } catch (Exception ignored) {
        }
        preferences.edit().putString(MVP_PYRAMID, item.toString()).apply();
    }

    public void resetMvpPyramid() {
        preferences.edit().putString(MVP_PYRAMID, "{}").apply();
    }

    private void appendHistory(String key, String snapshotJson) {
        if ("[]".equals(snapshotJson)) {
            return;
        }
        try {
            JSONArray history = new JSONArray(preferences.getString(key, "[]"));
            if (history.length() > 0 && snapshotJson.equals(history.optString(history.length() - 1, ""))) {
                return;
            }
            history.put(snapshotJson);
            while (history.length() > HISTORY_LIMIT) {
                JSONArray trimmed = new JSONArray();
                for (int index = 1; index < history.length(); index++) {
                    trimmed.put(history.get(index));
                }
                history = trimmed;
            }
            preferences.edit().putString(key, history.toString()).apply();
        } catch (Exception ignored) {
            preferences.edit().putString(key, new JSONArray().put(snapshotJson).toString()).apply();
        }
    }

    private void saveAiTicketResult(String key, String type, String status, List<AiPick> picks) {
        if (picks == null || picks.isEmpty()) {
            return;
        }
        JSONObject item = new JSONObject();
        try {
            item.put("type", type);
            item.put("status", status);
            item.put("created_at", new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date()));
            item.put("odds", combinedOdds(picks));
            item.put("picks", aiPicksToJson(picks));
            appendHistory(key, item.toString());
        } catch (Exception ignored) {
        }
    }

    private double combinedOdds(List<AiPick> picks) {
        double combined = 1.0;
        int count = 0;
        for (AiPick pick : picks) {
            if (pick.getOdds() > 1.0) {
                combined *= pick.getOdds();
                count++;
            }
        }
        return count == 0 ? 0.0 : combined;
    }

    private JSONArray aiPicksToJson(List<AiPick> picks) {
        JSONArray array = new JSONArray();
        for (AiPick pick : picks) {
            JSONObject item = new JSONObject();
            try {
                item.put("league", pick.getLeague());
                item.put("date", pick.getDate());
                item.put("match", pick.getMatch());
                item.put("market", pick.getMarket());
                item.put("selection", pick.getSelection());
                item.put("kickoff", pick.getKickoff());
                item.put("confidence", pick.getConfidence());
                item.put("mvp", pick.isMvp());
                item.put("reason", pick.getReason());
                item.put("odds", pick.getOdds());
                item.put("bookmaker", pick.getBookmaker());
                item.put("id", pick.getId());
                item.put("result", pick.getResult());
                JSONArray factors = new JSONArray();
                for (String factor : pick.getKeyFactors()) {
                    factors.put(factor);
                }
                item.put("key_factors", factors);
                array.put(item);
            } catch (Exception ignored) {
            }
        }
        return array;
    }

    private List<AiPick> parseAiPicks(String json) {
        List<AiPick> picks = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(json);
            for (int index = 0; index < array.length(); index++) {
                JSONObject item = array.getJSONObject(index);
                picks.add(new AiPick(
                        item.optString("league", ""),
                        item.optString("date", ""),
                        item.optString("match", ""),
                        item.optString("market", ""),
                        item.optString("selection", ""),
                        item.optString("kickoff", ""),
                        item.optInt("confidence", 0),
                        item.optBoolean("mvp", false),
                        item.optString("reason", ""),
                        parseStringArray(item.optJSONArray("key_factors")),
                        item.optDouble("odds", 0.0),
                        item.optString("bookmaker", ""),
                        item.optString("id", ""),
                        item.optString("result", "PENDING")
                ));
            }
        } catch (Exception ignored) {
        }
        return picks;
    }

    private List<List<AiPick>> parseAiHistory(String json) {
        List<List<AiPick>> history = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(json);
            for (int index = array.length() - 1; index >= 0; index--) {
                history.add(parseAiPicks(array.optString(index, "[]")));
            }
        } catch (Exception ignored) {
        }
        return history;
    }

    private List<AiTicketRecord> parseAiTicketHistory(String json) {
        List<AiTicketRecord> history = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(json);
            for (int index = array.length() - 1; index >= 0; index--) {
                JSONObject item = new JSONObject(array.optString(index, "{}"));
                history.add(new AiTicketRecord(
                        item.optString("type", ""),
                        item.optString("status", ""),
                        item.optString("created_at", ""),
                        item.optDouble("odds", 0.0),
                        parseAiPicks(item.optJSONArray("picks") == null ? "[]" : item.optJSONArray("picks").toString())
                ));
            }
        } catch (Exception ignored) {
        }
        return history;
    }

    private JSONArray arbsToJson(List<ArbitrageOpportunity> opportunities) {
        JSONArray array = new JSONArray();
        for (ArbitrageOpportunity opportunity : opportunities) {
            JSONObject item = new JSONObject();
            try {
                item.put("country", opportunity.getCountry());
                item.put("league", opportunity.getLeague());
                item.put("match", opportunity.getMatch());
                item.put("market", opportunity.getMarket());
                item.put("eventDate", opportunity.getEventDate());
                item.put("foundAt", opportunity.getFoundAt());
                item.put("sourceLabel", opportunity.getSourceLabel());
                item.put("peakProfitPercent", opportunity.getPeakProfitPercent() * 100.0);
                item.put("refreshCount", opportunity.getRefreshCount());
                item.put("firstLostAfterRefreshes", opportunity.getFirstLostAfterRefreshes());
                item.put("firstLostAfterMinutes", opportunity.getFirstLostAfterMinutes());
                item.put("firstLostAt", opportunity.getFirstLostAt());
                if (opportunity.hasPreStartProfit()) {
                    item.put("preStartProfitPercent", opportunity.getPreStartProfitPercent() * 100.0);
                    item.put("preStartAt", opportunity.getPreStartAt());
                }
                JSONArray outcomes = new JSONArray();
                for (ArbitrageOutcome outcome : opportunity.getOutcomes()) {
                    JSONObject out = new JSONObject();
                    out.put("selection", outcome.getSelection());
                    out.put("odds", outcome.getOdds());
                    out.put("bookmaker", outcome.getBookmaker());
                    outcomes.put(out);
                }
                item.put("outcomes", outcomes);
                array.put(item);
            } catch (Exception ignored) {
            }
        }
        return array;
    }

    private List<ArbitrageOpportunity> parseArbs(String json) {
        List<ArbitrageOpportunity> opportunities = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(json);
            for (int index = 0; index < array.length(); index++) {
                JSONObject item = array.getJSONObject(index);
                JSONArray outcomesJson = item.optJSONArray("outcomes");
                List<ArbitrageOutcome> outcomes = new ArrayList<>();
                if (outcomesJson != null) {
                    for (int outcomeIndex = 0; outcomeIndex < outcomesJson.length(); outcomeIndex++) {
                        JSONObject out = outcomesJson.getJSONObject(outcomeIndex);
                        outcomes.add(new ArbitrageOutcome(
                                out.optString("selection", ""),
                                out.optDouble("odds", 0.0),
                                out.optString("bookmaker", "")
                        ));
                    }
                }
                opportunities.add(new ArbitrageOpportunity(
                        item.optString("country", ""),
                        item.optString("league", ""),
                        item.optString("match", ""),
                        item.optString("market", ""),
                        item.optString("eventDate", ""),
                        item.optString("foundAt", ""),
                        outcomes,
                        item.optDouble("peakProfitPercent", 0.0) / 100.0,
                        item.optString("sourceLabel", "API ARB"),
                        item.optInt("refreshCount", 0),
                        item.optInt("firstLostAfterRefreshes", 0),
                        item.optInt("firstLostAfterMinutes", 0),
                        item.optString("firstLostAt", ""),
                        item.has("preStartProfitPercent") && !item.isNull("preStartProfitPercent")
                                ? item.optDouble("preStartProfitPercent", 0.0) / 100.0 : null,
                        item.optString("preStartAt", "")
                ));
            }
        } catch (Exception ignored) {
        }
        return opportunities;
    }

    private List<List<ArbitrageOpportunity>> parseArbHistory(String json) {
        List<List<ArbitrageOpportunity>> history = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(json);
            for (int index = array.length() - 1; index >= 0; index--) {
                history.add(parseArbs(array.optString(index, "[]")));
            }
        } catch (Exception ignored) {
        }
        return history;
    }

    private List<String> parseStringArray(JSONArray array) {
        List<String> values = new ArrayList<>();
        if (array == null) {
            return values;
        }
        for (int index = 0; index < array.length(); index++) {
            String value = array.optString(index, "").trim();
            if (!value.isEmpty()) {
                values.add(value);
            }
        }
        return values;
    }

    private String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }
}
