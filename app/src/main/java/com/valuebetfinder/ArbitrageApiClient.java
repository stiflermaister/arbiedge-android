package com.valuebetfinder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class ArbitrageApiClient {
    public static final String DEFAULT_ENDPOINT = "http://10.0.2.2:8000/arbs";
    private static final String[] DEFAULT_ENDPOINTS = {
            "http://10.0.2.2:8000/arbs",
            "http://127.0.0.1:8000/arbs"
    };
    private static final String HISTORY_PATH = "/arb-history-app";
    private static final String NOTIFICATIONS_PATH = "/app-notifications";
    private static final String SCAN_STATS_PATH = "/api/arb-scan-stats";

    private final String endpoint;

    public ArbitrageApiClient() {
        this(DEFAULT_ENDPOINT);
    }

    public ArbitrageApiClient(String endpoint) {
        this.endpoint = endpoint;
    }

    public List<ArbitrageOpportunity> fetchArbitrageOpportunities() throws Exception {
        if (DEFAULT_ENDPOINT.equals(endpoint)) {
            Exception lastException = null;
            for (String candidate : DEFAULT_ENDPOINTS) {
                try {
                    return fetchFromEndpoint(candidate);
                } catch (Exception exception) {
                    lastException = exception;
                }
            }
            throw lastException == null ? new IllegalStateException("No arbitrage endpoints configured") : lastException;
        }
        return fetchFromEndpoint(endpoint);
    }

    public List<ArbitrageOpportunity> fetchArbitrageHistory() throws Exception {
        Exception lastException = null;
        if (DEFAULT_ENDPOINT.equals(endpoint)) {
            for (String candidate : DEFAULT_ENDPOINTS) {
                try {
                    return fetchFromEndpoint(candidate.replace("/arbs", HISTORY_PATH));
                } catch (Exception exception) {
                    lastException = exception;
                }
            }
        } else {
            return fetchFromEndpoint(endpoint.replace("/arbs", HISTORY_PATH));
        }
        throw lastException == null ? new IllegalStateException("No arbitrage history endpoints configured") : lastException;
    }

    public List<AppNotification> fetchAppNotifications() throws Exception {
        Exception lastException = null;
        if (DEFAULT_ENDPOINT.equals(endpoint)) {
            for (String candidate : DEFAULT_ENDPOINTS) {
                try {
                    return parseNotifications(readJson(candidate.replace("/arbs", NOTIFICATIONS_PATH)));
                } catch (Exception exception) {
                    lastException = exception;
                }
            }
        } else {
            return parseNotifications(readJson(endpoint.replace("/arbs", NOTIFICATIONS_PATH)));
        }
        throw lastException == null ? new IllegalStateException("No notification endpoints configured") : lastException;
    }

    public JSONObject fetchAutoStatus() throws Exception {
        Exception lastException = null;
        if (DEFAULT_ENDPOINT.equals(endpoint)) {
            for (String candidate : DEFAULT_ENDPOINTS) {
                try {
                    return new JSONObject(readJson(candidate.replace("/arbs", "/api/auto-big-status")));
                } catch (Exception exception) {
                    lastException = exception;
                }
            }
        } else {
            return new JSONObject(readJson(endpoint.replace("/arbs", "/api/auto-big-status")));
        }
        throw lastException == null ? new IllegalStateException("No auto status endpoints configured") : lastException;
    }

    public JSONObject fetchScanStats() throws Exception {
        Exception lastException = null;
        if (DEFAULT_ENDPOINT.equals(endpoint)) {
            for (String candidate : DEFAULT_ENDPOINTS) {
                try {
                    return new JSONObject(readJson(candidate.replace("/arbs", SCAN_STATS_PATH)));
                } catch (Exception exception) {
                    lastException = exception;
                }
            }
        } else {
            return new JSONObject(readJson(endpoint.replace("/arbs", SCAN_STATS_PATH)));
        }
        throw lastException == null ? new IllegalStateException("No scan stats endpoints configured") : lastException;
    }

    private List<ArbitrageOpportunity> fetchFromEndpoint(String endpointUrl) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpointUrl).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(1200);
        connection.setReadTimeout(3500);

        int statusCode = connection.getResponseCode();
        if (statusCode < 200 || statusCode >= 300) {
            throw new IllegalStateException("Arbitrage endpoint returned HTTP " + statusCode);
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        StringBuilder body = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            body.append(line);
        }
        reader.close();
        connection.disconnect();

        return parse(body.toString(), endpointUrl.contains(HISTORY_PATH));
    }

    private List<ArbitrageOpportunity> parse(String json, boolean historyMode) throws Exception {
        JSONArray items = new JSONArray(json);
        List<ArbitrageOpportunity> opportunities = new ArrayList<>();

        for (int index = 0; index < items.length(); index++) {
            JSONObject item = items.getJSONObject(index);
            JSONArray outcomesJson = item.getJSONArray("outcomes");
            List<ArbitrageOutcome> outcomes = new ArrayList<>();

            for (int outcomeIndex = 0; outcomeIndex < outcomesJson.length(); outcomeIndex++) {
                JSONObject outcomeJson = outcomesJson.getJSONObject(outcomeIndex);
                outcomes.add(new ArbitrageOutcome(
                        outcomeJson.getString("selection"),
                        outcomeJson.getDouble("odds"),
                        outcomeJson.getString("bookmaker"),
                        outcomeJson.optString("url", "")
                ));
            }

            ArbitrageOpportunity opportunity = new ArbitrageOpportunity(
                    item.optString("country", ""),
                    item.optString("league", ""),
                    item.getString("match"),
                    item.getString("market"),
                    item.optString("eventDate", ""),
                    item.optString("foundAt", ""),
                    outcomes,
                    item.optDouble("peakProfitPercent", item.optDouble("profitPercent", 0.0)) / 100.0,
                    item.optString("sourceLabel", "API ARB"),
                    item.optInt("refreshCount", 0),
                    item.optInt("firstLostAfterRefreshes", 0),
                    item.optInt("firstLostAfterMinutes", 0),
                    item.optString("firstLostAt", ""),
                    item.has("preStartProfitPercent") && !item.isNull("preStartProfitPercent")
                            ? item.optDouble("preStartProfitPercent", 0.0) / 100.0 : null,
                    item.optString("preStartAt", "")
            );

            if (historyMode || opportunity.isArbitrage()) {
                opportunities.add(opportunity);
            }
        }

        return opportunities;
    }

    private String readJson(String endpointUrl) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpointUrl).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(1200);
        connection.setReadTimeout(3500);
        int statusCode = connection.getResponseCode();
        if (statusCode < 200 || statusCode >= 300) {
            throw new IllegalStateException("Notification endpoint returned HTTP " + statusCode);
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        StringBuilder body = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) body.append(line);
        reader.close();
        connection.disconnect();
        return body.toString();
    }

    private List<AppNotification> parseNotifications(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        JSONArray items = root.optJSONArray("items");
        List<AppNotification> notifications = new ArrayList<>();
        if (items == null) return notifications;
        for (int index = 0; index < items.length(); index++) {
            JSONObject item = items.getJSONObject(index);
            notifications.add(new AppNotification(
                    item.optString("id", ""),
                    item.optString("title", "ArbiEdge"),
                    item.optString("message", "Nova arbitrazna prilika")
            ));
        }
        return notifications;
    }
}
