package com.valuebetfinder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PdfAiPicksApiClient {
    private static volatile String preferredEndpoint = "";
    private static final String[] ENDPOINTS = {
            "http://10.0.2.2:8000/pdf-ai-picks",
            "http://127.0.0.1:8000/pdf-ai-picks"
    };

    public List<AiPick> fetch() throws Exception {
        Exception last = null;
        for (String endpoint : orderedEndpoints()) {
            try {
                List<AiPick> picks = fetchFrom(endpoint);
                preferredEndpoint = endpoint;
                return picks;
            } catch (Exception exception) {
                last = exception;
            }
        }
        throw last == null ? new IllegalStateException("AI Picks PDF endpoint nije dostupan") : last;
    }

    public AiPickAnalytics fetchAnalytics() throws Exception {
        return fetchAnalytics("all");
    }

    public AiPickAnalytics fetchAnalytics(String month) throws Exception {
        Exception last = null;
        for (String endpoint : orderedEndpoints()) {
            try {
                AiPickAnalytics analytics = parseAnalytics(readBody(endpoint), month);
                preferredEndpoint = endpoint;
                return analytics;
            } catch (Exception exception) {
                last = exception;
            }
        }
        throw last == null ? new IllegalStateException("AI statistika nije dostupna") : last;
    }

    private List<AiPick> fetchFrom(String endpoint) throws Exception {
        return parse(readBody(endpoint));
    }

    private List<String> orderedEndpoints() {
        List<String> endpoints = new ArrayList<>();
        if (preferredEndpoint != null && !preferredEndpoint.isEmpty()) endpoints.add(preferredEndpoint);
        for (String endpoint : ENDPOINTS) {
            if (!endpoint.equals(preferredEndpoint)) endpoints.add(endpoint);
        }
        return endpoints;
    }

    private String readBody(String endpoint) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(1200);
        connection.setReadTimeout(3500);
        int status = connection.getResponseCode();
        if (status < 200 || status >= 300) {
            throw new IllegalStateException("AI Picks PDF HTTP " + status);
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        StringBuilder body = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) body.append(line);
        reader.close();
        connection.disconnect();
        return body.toString();
    }

    private List<AiPick> parse(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        JSONArray items = root.optJSONArray("all_picks");
        if (items == null) items = root.optJSONArray("picks");
        List<AiPick> picks = new ArrayList<>();
        if (items == null) return picks;
        for (int index = 0; index < items.length(); index++) {
            JSONObject item = items.getJSONObject(index);
            picks.add(new AiPick(
                    item.optString("competition", ""),
                    item.optString("date", ""),
                    item.optString("match", ""),
                    item.optString("market", ""),
                    item.optString("selection", ""),
                    item.optString("kickoff_time_local", ""),
                    item.optInt("confidence", 0),
                    item.optBoolean("is_mvp", false),
                    item.optString("short_reason", ""),
                    stringArray(item.optJSONArray("key_factors")),
                    item.optDouble("decimal_odds", 0.0),
                    item.optString("bookmaker", ""),
                    item.optString("id", ""),
                    item.optString("result", "PENDING")
            ));
        }
        return picks;
    }

    private AiPickAnalytics parseAnalytics(String json, String month) throws Exception {
        JSONObject root = new JSONObject(json);
        JSONArray history = root.optJSONArray("all_picks");
        if (history != null && !"all".equals(month)) {
            return calculateAnalytics(history, month);
        }
        JSONObject stats = root.optJSONObject("stats");
        JSONObject all = stats == null ? null : stats.optJSONObject("all");
        JSONObject mvp = stats == null ? null : stats.optJSONObject("mvp");
        if (all == null) return AiPickAnalytics.empty();
        return new AiPickAnalytics(
                all.optInt("total", 0), all.optInt("settled", 0), all.optInt("pending", 0),
                all.optInt("wins", 0), all.optInt("losses", 0), all.optInt("voids", 0),
                all.optDouble("win_rate", 0), all.optDouble("roi", 0),
                all.optDouble("profit_units", 0),
                mvp == null ? 0 : mvp.optDouble("win_rate", 0),
                mvp == null ? 0 : mvp.optDouble("profit_units", 0)
        );
    }

    private AiPickAnalytics calculateAnalytics(JSONArray items, String month) {
        int total = 0;
        int settled = 0;
        int pending = 0;
        int wins = 0;
        int losses = 0;
        int voids = 0;
        int mvpSettled = 0;
        int mvpWins = 0;
        double profit = 0.0;
        double mvpProfit = 0.0;

        for (int index = 0; index < items.length(); index++) {
            JSONObject pick = items.optJSONObject(index);
            if (pick == null || !matchesMonth(pick.optString("date", ""), month)) continue;
            total++;
            String result = pick.optString("result", "PENDING").toUpperCase(Locale.US);
            boolean isSettled = "WIN".equals(result) || "LOSS".equals(result) || "VOID".equals(result);
            if (!isSettled) {
                pending++;
                continue;
            }
            settled++;
            double pickProfit = 0.0;
            if ("WIN".equals(result)) {
                wins++;
                pickProfit = pick.optDouble("decimal_odds", 0.0) - 1.0;
            } else if ("LOSS".equals(result)) {
                losses++;
                pickProfit = -1.0;
            } else {
                voids++;
            }
            profit += pickProfit;
            if (pick.optBoolean("is_mvp", false)) {
                mvpSettled++;
                if ("WIN".equals(result)) mvpWins++;
                mvpProfit += pickProfit;
            }
        }

        int decided = wins + losses;
        double winRate = decided == 0 ? 0.0 : wins * 100.0 / decided;
        double roi = settled == 0 ? 0.0 : profit * 100.0 / settled;
        double mvpWinRate = mvpSettled == 0 ? 0.0 : mvpWins * 100.0 / mvpSettled;
        return new AiPickAnalytics(total, settled, pending, wins, losses, voids,
                winRate, roi, profit, mvpWinRate, mvpProfit);
    }

    private boolean matchesMonth(String date, String month) {
        String normalized = date == null ? "" : date.trim();
        if (normalized.startsWith(month)) return true;
        String[] parts = normalized.split("[./-]");
        return parts.length >= 3 && (parts[2] + "-" + String.format(Locale.US, "%02d", parseInt(parts[1])))
                .equals(month);
    }

    private int parseInt(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return -1;
        }
    }

    private List<String> stringArray(JSONArray array) {
        List<String> values = new ArrayList<>();
        if (array == null) return values;
        for (int index = 0; index < array.length(); index++) {
            String value = array.optString(index, "").trim();
            if (!value.isEmpty()) values.add(value);
        }
        return values;
    }
}
