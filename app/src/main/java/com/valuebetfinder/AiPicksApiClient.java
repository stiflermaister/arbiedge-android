package com.valuebetfinder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class AiPicksApiClient {
    public static final String DEFAULT_ENDPOINT = "http://10.0.2.2:8000/ai-picks";
    private static final String[] DEFAULT_ENDPOINTS = {
            "http://10.0.2.2:8000/ai-picks",
            "http://127.0.0.1:8000/ai-picks"
    };
    private static final String[] DEFAULT_RUN_ENDPOINTS = {
            "http://10.0.2.2:8000/api/run-ai-picks",
            "http://127.0.0.1:8000/api/run-ai-picks"
    };
    private static final String[] DEFAULT_ODDS_ENDPOINTS = {
            "http://10.0.2.2:8000/api/update-ai-odds",
            "http://127.0.0.1:8000/api/update-ai-odds"
    };
    private static final String[] DEFAULT_ANALYSIS_ENDPOINTS = {
            "http://10.0.2.2:8000/api/update-ai-analysis",
            "http://127.0.0.1:8000/api/update-ai-analysis"
    };
    private static final String[] DEFAULT_CLEAR_ENDPOINTS = {
            "http://10.0.2.2:8000/api/clear-ai-picks",
            "http://127.0.0.1:8000/api/clear-ai-picks"
    };

    private final String endpoint;

    public AiPicksApiClient() {
        this(DEFAULT_ENDPOINT);
    }

    public AiPicksApiClient(String endpoint) {
        this.endpoint = endpoint;
    }

    public List<AiPick> fetchAiPicks() throws Exception {
        if (DEFAULT_ENDPOINT.equals(endpoint)) {
            Exception lastException = null;
            for (String candidate : DEFAULT_ENDPOINTS) {
                try {
                    return fetchFromEndpoint(candidate);
                } catch (Exception exception) {
                    lastException = exception;
                }
            }
            throw lastException == null ? new IllegalStateException("No AI picks endpoints configured") : lastException;
        }
        return fetchFromEndpoint(endpoint);
    }

    public List<AiPick> runAiPicksAndFetch() throws Exception {
        return runAiPicksAndFetch("");
    }

    public List<AiPick> runAiPicksAndFetch(String targetDate) throws Exception {
        if (DEFAULT_ENDPOINT.equals(endpoint)) {
            Exception lastException = null;
            for (int index = 0; index < DEFAULT_RUN_ENDPOINTS.length; index++) {
                try {
                    postRunRequest(DEFAULT_RUN_ENDPOINTS[index], targetDate);
                    return fetchFromEndpoint(DEFAULT_ENDPOINTS[index]);
                } catch (Exception exception) {
                    lastException = exception;
                }
            }
            throw lastException == null ? new IllegalStateException("No AI run endpoints configured") : lastException;
        }

        String runEndpoint = endpoint.replace("/ai-picks", "/api/run-ai-picks");
        postRunRequest(runEndpoint, targetDate);
        return fetchFromEndpoint(endpoint);
    }

    public List<AiPick> updateOddsAndFetch() throws Exception {
        if (DEFAULT_ENDPOINT.equals(endpoint)) {
            Exception lastException = null;
            for (int index = 0; index < DEFAULT_ODDS_ENDPOINTS.length; index++) {
                try {
                    postRunRequest(DEFAULT_ODDS_ENDPOINTS[index]);
                    return fetchFromEndpoint(DEFAULT_ENDPOINTS[index]);
                } catch (Exception exception) {
                    lastException = exception;
                }
            }
            throw lastException == null ? new IllegalStateException("No AI odds endpoints configured") : lastException;
        }

        String oddsEndpoint = endpoint.replace("/ai-picks", "/api/update-ai-odds");
        postRunRequest(oddsEndpoint);
        return fetchFromEndpoint(endpoint);
    }

    public List<AiPick> updateAnalysisAndFetch() throws Exception {
        if (DEFAULT_ENDPOINT.equals(endpoint)) {
            Exception lastException = null;
            for (int index = 0; index < DEFAULT_ANALYSIS_ENDPOINTS.length; index++) {
                try {
                    postRunRequest(DEFAULT_ANALYSIS_ENDPOINTS[index]);
                    return fetchFromEndpoint(DEFAULT_ENDPOINTS[index]);
                } catch (Exception exception) {
                    lastException = exception;
                }
            }
            throw lastException == null ? new IllegalStateException("No AI analysis endpoints configured") : lastException;
        }

        String analysisEndpoint = endpoint.replace("/ai-picks", "/api/update-ai-analysis");
        postRunRequest(analysisEndpoint);
        return fetchFromEndpoint(endpoint);
    }

    public List<AiPick> clearAiPicksAndFetch() throws Exception {
        if (DEFAULT_ENDPOINT.equals(endpoint)) {
            Exception lastException = null;
            for (int index = 0; index < DEFAULT_CLEAR_ENDPOINTS.length; index++) {
                try {
                    postRunRequest(DEFAULT_CLEAR_ENDPOINTS[index]);
                    return fetchFromEndpoint(DEFAULT_ENDPOINTS[index]);
                } catch (Exception exception) {
                    lastException = exception;
                }
            }
            throw lastException == null ? new IllegalStateException("No AI clear endpoints configured") : lastException;
        }

        String clearEndpoint = endpoint.replace("/ai-picks", "/api/clear-ai-picks");
        postRunRequest(clearEndpoint);
        return fetchFromEndpoint(endpoint);
    }

    private void postRunRequest(String endpointUrl) throws Exception {
        postRunRequest(endpointUrl, "");
    }

    private void postRunRequest(String endpointUrl, String targetDate) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpointUrl).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(1200);
        connection.setReadTimeout(120000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");

        String safeDate = targetDate == null ? "" : targetDate.replace("\\", "").replace("\"", "").trim();
        byte[] body = ("{\"target_date\":\"" + safeDate + "\"}").getBytes("UTF-8");
        OutputStream outputStream = connection.getOutputStream();
        outputStream.write(body);
        outputStream.close();

        int statusCode = connection.getResponseCode();
        if (statusCode < 200 || statusCode >= 300) {
            StringBuilder errorBody = new StringBuilder();
            if (connection.getErrorStream() != null) {
                BufferedReader errorReader = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
                String errorLine;
                while ((errorLine = errorReader.readLine()) != null) {
                    errorBody.append(errorLine);
                }
                errorReader.close();
            }
            throw new IllegalStateException("AI run HTTP " + statusCode + ": " + errorBody.toString());
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();
        connection.disconnect();

        JSONObject payload = new JSONObject(response.toString());
        if (!payload.optBoolean("ok", false)) {
            throw new IllegalStateException(payload.optString("message", "AI run failed"));
        }
    }

    private List<AiPick> fetchFromEndpoint(String endpointUrl) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpointUrl).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(1200);
        connection.setReadTimeout(3500);

        int statusCode = connection.getResponseCode();
        if (statusCode < 200 || statusCode >= 300) {
            throw new IllegalStateException("AI picks endpoint returned HTTP " + statusCode);
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        StringBuilder body = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            body.append(line);
        }
        reader.close();
        connection.disconnect();

        return parse(body.toString());
    }

    private List<AiPick> parse(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        JSONArray items = root.optJSONArray("picks");
        List<AiPick> picks = new ArrayList<>();
        if (items == null) {
            return picks;
        }

        for (int index = 0; index < items.length(); index++) {
            JSONObject item = items.getJSONObject(index);
            picks.add(new AiPick(
                    item.optString("competition", ""),
                    root.optString("date", ""),
                    item.optString("match", ""),
                    item.optString("market", ""),
                    item.optString("selection", ""),
                    item.optString("kickoff_time_local", ""),
                    item.optInt("confidence", 0),
                    item.optBoolean("is_mvp", false),
                    item.optString("short_reason", ""),
                    parseStringArray(item.optJSONArray("key_factors")),
                    item.optDouble("decimal_odds", 0.0),
                    item.optString("bookmaker", "")
            ));
        }
        return picks;
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
}
