package com.valuebetfinder;

import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.ColorDrawable;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.TimeZone;

public class ArbOnlyActivity extends Activity {
    public static final String EXTRA_OPEN_SCREEN = "arbiedge_open_screen";
    private static final String MIGRATION_PREFS = "arbiedge_migration";
    private static final String SAVED_PREFS = "arbiedge_saved";
    private static final String SAVED_KEYS = "opportunity_keys";
    private static final String CAPITAL_PREFS = "arbiedge_capital_plan";
    private static final String AI_STATS_PREFS = "arbiedge_ai_stats";
    private static final double AI_STAKE_PERCENT = 1.0;

    private static class AiDaySummary {
        final String date;
        final List<AiPick> picks = new ArrayList<>();

        AiDaySummary(String date) {
            this.date = date;
        }
    }

    private static class ScanStat {
        final String timestamp;
        final String source;
        final int foundCount;
        final int newCount;
        final int refreshedCount;
        final int activeCount;
        final List<Double> edges;
        final int hour;
        final int minute;

        ScanStat(String timestamp, String source, int foundCount, int newCount,
                 int refreshedCount, int activeCount, List<Double> edges, int hour, int minute) {
            this.timestamp = timestamp;
            this.source = source;
            this.foundCount = foundCount;
            this.newCount = newCount;
            this.refreshedCount = refreshedCount;
            this.activeCount = activeCount;
            this.edges = edges;
            this.hour = hour;
            this.minute = minute;
        }
    }

    private final ArbitrageSyncScheduler syncScheduler =
            new ArbitrageSyncScheduler(new ArbitrageApiClient());
    private final ArbitrageSyncScheduler historySyncScheduler =
            new ArbitrageSyncScheduler(new ArbitrageApiClient(), true);

    private LocalTicketStore store;
    private SharedPreferences savedPreferences;
    private LinearLayout content;
    private LinearLayout navigation;
    private TextView syncStatus;
    private SwipeRefreshLayout swipeRefresh;
    private List<ArbitrageOpportunity> opportunities = new ArrayList<>();
    private List<ArbitrageOpportunity> archive = new ArrayList<>();
    private List<AiPick> aiPicks = new ArrayList<>();
    private AiPickAnalytics aiAnalytics = AiPickAnalytics.empty();
    private String screen = "opportunities";
    private String opportunitySource = "all";
    private boolean roiOnly = false;
    private boolean historyDescending = true;
    private String historySortMode = "time_desc";
    private String historyMode = "arb";
    private String historyFilter = "all";
    private String historySource = "all";
    private String historyMonth = "all";
    private String analyticsMode = "arb";
    private String analyticsMonth = "all";
    private String analyticsArbSource = "all";
    private String scanStatsSource = "all";
    private List<ScanStat> scanStats = new ArrayList<>();
    private String scanStatsStartAt = "";
    private String renderedAiDate = "";
    private volatile long nextRefreshAt = 0L;
    private volatile boolean autoStatusLoaded = false;
    private volatile boolean autoRunning = false;
    private volatile boolean apiAutoRunning = false;
    private volatile boolean serbianAutoRunning = false;
    private volatile String lastAutoRun = "";
    private volatile String lastSerbianAutoRun = "";
    private volatile long nextApiRefreshAt = 0L;
    private volatile long nextSerbianRefreshAt = 0L;
    private volatile String autoBookmakers = "";
    private volatile String autoMessage = "";
    private volatile String serbianAutoMessage = "";
    private volatile long lastAutoStatusRequestAt = 0L;
    private volatile long lastAiRefreshRequestAt = 0L;
    private TextView refreshCountdown;
    private final Handler refreshTimerHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshTimer = new Runnable() {
        @Override
        public void run() {
            updateRefreshCountdown();
            refreshTimerHandler.postDelayed(this, 1000L);
        }
    };
    private final Handler notificationHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService notificationExecutor = Executors.newSingleThreadExecutor();
    private final Runnable autoStatusPoller = new Runnable() {
        @Override
        public void run() {
            pollAutoStatus();
            notificationHandler.postDelayed(this, 30000L);
        }
    };
    private final Runnable notificationPoller = new Runnable() {
        @Override
        public void run() {
            pollAppNotifications();
            notificationHandler.postDelayed(this, 5000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        clearLegacyDataOnce();
        store = new LocalTicketStore(this);
        savedPreferences = getSharedPreferences(SAVED_PREFS, Context.MODE_PRIVATE);
        opportunities = store.loadArbLatest();
        archive = store.loadArbArchive();
        aiPicks = store.loadAiLatest();
        aiAnalytics = loadAiAnalytics();
        applyLaunchDestination(getIntent());

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(color(R.color.screen_bg));
        root.addView(appHeader(), blockParams());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(4), dp(16), dp(20));
        scroll.addView(content);
        swipeRefresh = new SwipeRefreshLayout(this);
        swipeRefresh.setColorSchemeResources(R.color.accent, R.color.blue, R.color.orange);
        swipeRefresh.setProgressBackgroundColorSchemeResource(R.color.panel);
        swipeRefresh.addView(scroll, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        swipeRefresh.setOnRefreshListener(this::refreshVisibleScreen);
        root.addView(swipeRefresh, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        navigation = bottomNavigation();
        root.addView(navigation);
        setContentView(root);

        render();
        AppNotificationClient.requestPermissionIfNeeded(this);
        startNotificationPolling();
        startSync();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        applyLaunchDestination(intent);
        if (navigation != null) navigation.setVisibility(View.VISIBLE);
        if (content != null) render();
        startSync();
    }

    private void applyLaunchDestination(Intent intent) {
        if (intent == null) return;
        String destination = intent.getStringExtra(EXTRA_OPEN_SCREEN);
        if ("opportunities".equals(destination)) screen = "opportunities";
    }

    @Override
    protected void onDestroy() {
        syncScheduler.stop();
        historySyncScheduler.stop();
        notificationHandler.removeCallbacks(notificationPoller);
        notificationHandler.removeCallbacks(autoStatusPoller);
        notificationExecutor.shutdownNow();
        refreshTimerHandler.removeCallbacks(refreshTimer);
        super.onDestroy();
    }

    private void startNotificationPolling() {
        notificationHandler.post(autoStatusPoller);
        notificationHandler.post(notificationPoller);
    }

    private void refreshVisibleScreen() {
        if ("graph".equals(screen)) {
            refreshScanStats(true);
            return;
        }
        startSync();
        swipeRefresh.postDelayed(() -> swipeRefresh.setRefreshing(false), 3500L);
    }

    private void pollAppNotifications() {
        long now = System.currentTimeMillis();
        notificationExecutor.execute(() -> {
            try {
                List<AppNotification> notifications = new ArbitrageApiClient().fetchAppNotifications();
                runOnUiThread(() -> {
                    for (AppNotification notification : notifications) {
                        AppNotificationClient.showIfNew(this, notification);
                    }
                });
            } catch (Exception ignored) {
                // The regular ARB sync already reports backend status to the user.
            }
        });
    }

    private void pollAutoStatus() {
        lastAutoStatusRequestAt = System.currentTimeMillis();
        notificationExecutor.execute(() -> {
            try {
                org.json.JSONObject root = new ArbitrageApiClient().fetchAutoStatus();
                org.json.JSONObject auto = root.optJSONObject("auto");
                if (auto == null) return;
                apiAutoRunning = auto.optBoolean("running", false);
                lastAutoRun = auto.optString("last_run_at", "");
                autoMessage = auto.optString("last_message", "");
                org.json.JSONObject config = auto.optJSONObject("config");
                autoBookmakers = config == null ? "" : config.optString("bookmakers", "");
                String nextRun = auto.optString("next_run_at", "");
                nextApiRefreshAt = parseBackendTime(nextRun);

                org.json.JSONObject serbianAuto = root.optJSONObject("serbian_auto");
                serbianAutoRunning = serbianAuto != null && serbianAuto.optBoolean("running", false);
                lastSerbianAutoRun = serbianAuto == null ? "" : serbianAuto.optString("last_run_at", "");
                serbianAutoMessage = serbianAuto == null ? "" : serbianAuto.optString("last_message", "");
                nextSerbianRefreshAt = serbianAuto == null ? 0L
                        : parseBackendTime(serbianAuto.optString("next_run_at", ""));
                autoRunning = apiAutoRunning || serbianAutoRunning;
                nextRefreshAt = nearestFutureTime(nextApiRefreshAt, nextSerbianRefreshAt);
                autoStatusLoaded = true;
                runOnUiThread(() -> {
                    updateRefreshCountdown();
                    if ("opportunities".equals(screen) && opportunities.isEmpty()) render();
                });
            } catch (Exception ignored) {
                // Keep the last known countdown when the PC is temporarily unavailable.
            }
        });
    }

    private long nearestFutureTime(long first, long second) {
        long now = System.currentTimeMillis();
        boolean firstValid = first > now;
        boolean secondValid = second > now;
        if (firstValid && secondValid) return Math.min(first, second);
        if (firstValid) return first;
        if (secondValid) return second;
        return Math.max(first, second);
    }

    private long parseBackendTime(String value) {
        if (value == null || value.trim().isEmpty()) return 0L;
        String normalized = value.trim();
        try {
            int dot = normalized.indexOf('.');
            int zone = dot < 0 ? -1 : normalized.indexOf('+', dot);
            if (dot >= 0 && zone > dot) {
                String fraction = normalized.substring(dot + 1, zone);
                if (fraction.length() > 3) {
                    normalized = normalized.substring(0, dot + 1) + fraction.substring(0, 3) + normalized.substring(zone);
                }
            }
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US);
            Date date = format.parse(normalized);
            return date == null ? 0L : date.getTime();
        } catch (Exception ignored) {
            try {
                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US);
                Date date = format.parse(normalized);
                return date == null ? 0L : date.getTime();
            } catch (Exception ignoredAgain) {
                return 0L;
            }
        }
    }

    private void clearLegacyDataOnce() {
        SharedPreferences migration = getSharedPreferences(MIGRATION_PREFS, Context.MODE_PRIVATE);
        if (!migration.getBoolean("arb_only_v1", false)) {
            getSharedPreferences("valuebet_local_store", Context.MODE_PRIVATE).edit().clear().commit();
            getSharedPreferences(SAVED_PREFS, Context.MODE_PRIVATE).edit().clear().commit();
            migration.edit().putBoolean("arb_only_v1", true).apply();
        }
        if (!migration.getBoolean("ai_removed_v1", false)) {
            getSharedPreferences("valuebet_local_store", Context.MODE_PRIVATE).edit()
                    .remove("ai_latest")
                    .remove("ai_history")
                    .remove("ai_parlay_history")
                    .remove("ai_mvp_history")
                    .remove("mvp_pyramid")
                    .apply();
            getSharedPreferences("arbiedge_ai_stats", Context.MODE_PRIVATE).edit().clear().apply();
            migration.edit().putBoolean("ai_removed_v1", true).apply();
        }
    }

    private void startSync() {
        syncScheduler.start(new ArbitrageSyncCallback() {
            @Override
            public void onSuccess(List<ArbitrageOpportunity> fresh) {
                opportunities = fresh;
                store.saveArbLatest(fresh);
                render();
                setStatus("Osvezeno sa API i domaceg skenera");
            }

            @Override
            public void onError(Exception exception) {
                setStatus("Backend nije dostupan - prikazujem poslednji lokalni zapis");
            }
        });
        historySyncScheduler.start(new ArbitrageSyncCallback() {
            @Override
            public void onSuccess(List<ArbitrageOpportunity> history) {
                archive = history;
                store.saveArbArchive(history);
                if ("history".equals(screen) || "capital".equals(screen)) {
                    render();
                    setStatus("Istorija azurirana sa PC-a");
                }
            }

            @Override
            public void onError(Exception exception) {
                if ("history".equals(screen)) {
                    setStatus("PC nije dostupan - prikazujem lokalno sacuvanu istoriju");
                }
            }
        });
    }

    private void render() {
        content.removeAllViews();
        navigation.setVisibility(View.VISIBLE);
        if ("ai".equals(screen)) {
            screen = "opportunities";
        }
        if ("history".equals(screen)) {
            addHistoryScreen();
        } else if ("graph".equals(screen)) {
            addGraphScreen();
        } else if ("saved".equals(screen)) {
            addSavedScreen();
        } else if ("analytics".equals(screen)) {
            addAnalyticsScreen();
        } else if ("capital".equals(screen)) {
            addCapitalScreen();
        } else {
            addOpportunitiesScreen();
        }
        updateNavigation();
    }

    private LinearLayout appHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(6), dp(16), dp(4));

        ImageView menu = icon(R.drawable.header_menu, "Meni");
        menu.setOnClickListener(v -> Toast.makeText(this, "ArbiEdge", Toast.LENGTH_SHORT).show());
        header.addView(menu, new LinearLayout.LayoutParams(dp(30), dp(30)));

        LinearLayout brand = new LinearLayout(this);
        brand.setGravity(Gravity.CENTER);
        ImageView mark = icon(R.drawable.arbiedge_mark, "ArbiEdge");
        brand.addView(mark, new LinearLayout.LayoutParams(dp(25), dp(25)));
        TextView name = text("Arbi", 17, R.color.ink, Typeface.BOLD);
        TextView edge = text("Edge", 17, R.color.accent, Typeface.BOLD);
        brand.addView(name);
        brand.addView(edge);
        header.addView(brand, weightedParams());

        ImageView bell = icon(R.drawable.header_bell, "Notifikacije");
        refreshCountdown = text("", 10, R.color.muted, Typeface.BOLD);
        refreshCountdown.setGravity(Gravity.CENTER);
        refreshCountdown.setVisibility(View.GONE);
        bell.setOnClickListener(v -> {
            refreshCountdown.setVisibility(View.VISIBLE);
            pollAutoStatus();
            updateRefreshCountdown();
            String message = autoStatusLoaded
                    ? "Sledeci refresh: " + refreshCountdown.getText()
                    : "Ucitavam vreme sa sajta...";
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        });
        header.addView(bell, new LinearLayout.LayoutParams(dp(30), dp(30)));
        header.addView(refreshCountdown, new LinearLayout.LayoutParams(dp(54), dp(30)));
        refreshTimerHandler.post(refreshTimer);
        return header;
    }

    private void updateRefreshCountdown() {
        if (refreshCountdown == null || refreshCountdown.getVisibility() != View.VISIBLE) return;
        if (!autoStatusLoaded) {
            refreshCountdown.setText("...");
            return;
        }
        if (nextRefreshAt <= 0L) {
            refreshCountdown.setText(autoRunning ? "SKEN" : "OFF");
            return;
        }
        long remaining = Math.max(0L, nextRefreshAt - System.currentTimeMillis());
        if (remaining == 0L) {
            refreshCountdown.setText(autoRunning ? "SKEN" : "00:00");
            if (System.currentTimeMillis() - lastAutoStatusRequestAt >= 5000L) {
                pollAutoStatus();
            }
            return;
        }
        long totalSeconds = (remaining + 999L) / 1000L;
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        refreshCountdown.setText(String.format(Locale.US, "%02d:%02d", minutes, seconds));
    }

    private void addOpportunitiesScreen() {
        addPageTitle("Prilike");
        addStatsStrip();
        addFilters();
        addSyncStatus();

        List<ArbitrageOpportunity> visible = filteredOpportunities();
        if (visible.isEmpty()) {
            content.addView(scannerStatusCard(), topParams(14));
            return;
        }
        for (ArbitrageOpportunity opportunity : visible) {
            content.addView(opportunityCard(opportunity), topParams(14));
        }
    }

    private void addGraphScreen() {
        addPageTitle("Graf skenova");
        addSyncStatus();

        LinearLayout sourceRow = new LinearLayout(this);
        sourceRow.setOrientation(LinearLayout.HORIZONTAL);
        sourceRow.addView(filterButton("SVE", "all".equals(scanStatsSource), v -> {
            scanStatsSource = "all";
            render();
        }), weightedParams());
        LinearLayout.LayoutParams apiParams = weightedParams();
        apiParams.leftMargin = dp(8);
        sourceRow.addView(filterButton("API ARB", "api".equals(scanStatsSource), v -> {
            scanStatsSource = "api";
            render();
        }), apiParams);
        LinearLayout.LayoutParams srParams = weightedParams();
        srParams.leftMargin = dp(8);
        sourceRow.addView(filterButton("SR ARB", "sr".equals(scanStatsSource), v -> {
            scanStatsSource = "sr";
            render();
        }), srParams);
        content.addView(sourceRow, topParams(12));

        List<ScanStat> visible = filteredScanStats();
        if (visible.isEmpty()) {
            content.addView(emptyCard("Nema podataka o skenovima", "Statistika se puni od prvog novog ciklusa."), topParams(18));
            content.addView(text("Pocetak merenja: " + (scanStatsStartAt.isEmpty() ? "nije poznat" : scanStatsStartAt),
                    11, R.color.muted, Typeface.NORMAL), topParams(12));
            return;
        }

        content.addView(scanStatsSummary(visible), topParams(14));
        content.addView(hourScanGraph(visible), topParams(12));
        content.addView(scanQuarterDetails(visible), topParams(12));
        content.addView(scanLifespanSummary(), topParams(14));
        content.addView(text("Klikni na sat da vidis blokove od 15 minuta.", 11, R.color.muted, Typeface.NORMAL), topParams(10));
    }

    private void refreshScanStats(final boolean showMessage) {
        notificationExecutor.execute(() -> {
            try {
                JSONObject root = new ArbitrageApiClient().fetchScanStats();
                JSONArray items = root.optJSONArray("items");
                List<ScanStat> parsed = new ArrayList<>();
                if (items != null) {
                    for (int index = 0; index < items.length(); index++) {
                        JSONObject item = items.optJSONObject(index);
                        if (item == null) continue;
                        String timestamp = item.optString("scan_at", "");
                        int[] time = scanHourMinute(timestamp);
                        if (time[0] < 0) continue;
                        JSONArray edgeJson = item.optJSONArray("edges");
                        List<Double> edges = new ArrayList<>();
                        if (edgeJson != null) {
                            for (int edgeIndex = 0; edgeIndex < edgeJson.length(); edgeIndex++) {
                                edges.add(edgeJson.optDouble(edgeIndex, 0.0));
                            }
                        }
                        parsed.add(new ScanStat(
                                timestamp,
                                item.optString("source", "API ARB"),
                                item.optInt("found_count", edges.size()),
                                item.optInt("new_count", item.optInt("found_count", edges.size())),
                                item.optInt("refreshed_count", 0),
                                item.optInt("active_count", item.optInt("found_count", edges.size())),
                                edges,
                                time[0],
                                time[1]
                        ));
                    }
                }
                scanStatsStartAt = root.optString("startAt", "");
                runOnUiThread(() -> {
                    scanStats = parsed;
                    if ("graph".equals(screen)) render();
                    if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                    if (showMessage) setStatus("Graf skenova azuriran");
                });
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                    if (showMessage) setStatus("Graf nije dostupan - prikazan je poslednji lokalni zapis");
                });
            }
        });
    }

    private List<ScanStat> filteredScanStats() {
        List<ScanStat> filtered = new ArrayList<>();
        for (ScanStat stat : scanStats) {
            String source = stat.source.toUpperCase(Locale.US);
            if ("api".equals(scanStatsSource) && !source.contains("API")) continue;
            if ("sr".equals(scanStatsSource) && !source.contains("SR")) continue;
            filtered.add(stat);
        }
        return filtered;
    }

    private LinearLayout scanStatsSummary(List<ScanStat> stats) {
        int positive = 0;
        int found = 0;
        int refreshed = 0;
        int[] hourlyNew = new int[24];
        List<Double>[] hourlyEdges = new ArrayList[24];
        for (int hour = 0; hour < 24; hour++) hourlyEdges[hour] = new ArrayList<>();
        List<Double> edges = new ArrayList<>();
        for (ScanStat stat : stats) {
            if (stat.activeCount > 0) positive++;
            found += stat.newCount;
            refreshed += stat.refreshedCount;
            hourlyNew[stat.hour] += stat.newCount;
            hourlyEdges[stat.hour].addAll(stat.edges);
            edges.addAll(stat.edges);
        }
        int bestHour = 0;
        double bestMedian = -1.0;
        for (int hour = 0; hour < 24; hour++) {
            double hourMedian = hourlyEdges[hour].isEmpty() ? 0.0 : median(hourlyEdges[hour]);
            if (hourlyNew[hour] > hourlyNew[bestHour]
                    || (hourlyNew[hour] == hourlyNew[bestHour] && hourMedian > bestMedian)) {
                bestHour = hour;
                bestMedian = hourMedian;
            }
        }
        LinearLayout card = card();
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(15), dp(14), dp(15), dp(14));
        card.addView(text("STATISTIKA SKENOVA", 12, R.color.muted, Typeface.BOLD));
        card.addView(text(found + " novih arbova", 26, R.color.accent, Typeface.BOLD), topParams(5));
        card.addView(text(refreshed + " refreshovano  ·  " + positive + "/" + stats.size() + " pozitivnih skenova  ·  " +
                (edges.isEmpty() ? "medijana 0.00%" : "medijana " + scanPercent(median(edges))),
                11, R.color.muted, Typeface.NORMAL), topParams(3));
        card.addView(text("Najbolji termin za igru: " + String.format(Locale.US, "%02d:00", bestHour)
                        + " · " + hourlyNew[bestHour] + " novih · medijana " + scanPercent(bestMedian),
                11, R.color.ink, Typeface.BOLD), topParams(7));
        return card;
    }

    private LinearLayout hourScanGraph(List<ScanStat> stats) {
        int[] counts = new int[24];
        int[] refreshed = new int[24];
        int[] cycles = new int[24];
        int[] positive = new int[24];
        for (ScanStat stat : stats) {
            cycles[stat.hour]++;
            counts[stat.hour] += stat.newCount;
            refreshed[stat.hour] += stat.refreshedCount;
            if (stat.activeCount > 0) positive[stat.hour]++;
        }
        int max = 1;
        for (int count : counts) max = Math.max(max, count);
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout chart = new LinearLayout(this);
        chart.setGravity(Gravity.BOTTOM);
        chart.setPadding(dp(4), dp(8), dp(4), dp(4));
        for (int hour = 0; hour < 24; hour++) {
            final int selectedHour = hour;
            LinearLayout column = new LinearLayout(this);
            column.setOrientation(LinearLayout.VERTICAL);
            column.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            TextView count = text(positive[hour] + "/" + cycles[hour] + " sken.", 8, R.color.muted, Typeface.BOLD);
            count.setGravity(Gravity.CENTER);
            column.addView(count, new LinearLayout.LayoutParams(dp(54), dp(18)));
            TextView bar = text("", 1, R.color.accent, Typeface.NORMAL);
            bar.setBackgroundColor(positive[hour] > 0 ? color(R.color.accent) : color(R.color.muted));
            LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(dp(24), dp(12 + Math.round(120f * counts[hour] / max)));
            barParams.gravity = Gravity.CENTER_HORIZONTAL;
            column.addView(bar, barParams);
            TextView label = text(String.format(Locale.US, "%02d:00", hour), 9, R.color.muted, Typeface.BOLD);
            label.setGravity(Gravity.CENTER);
            column.addView(label, new LinearLayout.LayoutParams(dp(44), dp(22)));
            column.setContentDescription(counts[hour] + " novih, " + refreshed[hour] + " refreshovanih, " +
                    positive[hour] + " od " + cycles[hour] + " pozitivnih skenova");
            column.setOnClickListener(v -> showQuarterDetails(selectedHour, filteredScanStats()));
            LinearLayout.LayoutParams columnParams = new LinearLayout.LayoutParams(dp(58), dp(170));
            columnParams.leftMargin = dp(2);
            columnParams.rightMargin = dp(2);
            chart.addView(column, columnParams);
        }
        scroll.addView(chart);
        return wrapSection("ARBOVI PO CELIM SATIMA", scroll);
    }

    private LinearLayout scanQuarterDetails(List<ScanStat> stats) {
        LinearLayout panel = card();
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(12), dp(14), dp(12));
        panel.setTag("quarter_details");
        panel.addView(text("DETALJI PO 15 MINUTA", 11, R.color.muted, Typeface.BOLD));
        panel.addView(text("Izaberi sat na grafiku.", 12, R.color.muted, Typeface.NORMAL), topParams(7));
        return panel;
    }

    private void showQuarterDetails(int hour, List<ScanStat> stats) {
        for (int index = 0; index < content.getChildCount(); index++) {
            View view = content.getChildAt(index);
            if (!(view instanceof LinearLayout) || !"quarter_details".equals(view.getTag())) continue;
            LinearLayout panel = (LinearLayout) view;
            panel.removeAllViews();
            panel.setPadding(dp(14), dp(12), dp(14), dp(12));
            panel.addView(text(String.format(Locale.US, "DETALJI %02d:00-%02d:00", hour, (hour + 1) % 24), 11, R.color.muted, Typeface.BOLD));
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            for (int quarter = 0; quarter < 4; quarter++) {
                List<ScanStat> bucket = new ArrayList<>();
                for (ScanStat stat : stats) {
                    if (stat.hour == hour && stat.minute / 15 == quarter) bucket.add(stat);
                }
                int found = 0;
                int refreshed = 0;
                int positive = 0;
                List<Double> edges = new ArrayList<>();
                for (ScanStat stat : bucket) {
                    found += stat.newCount;
                    refreshed += stat.refreshedCount;
                    if (stat.activeCount > 0) positive++;
                    edges.addAll(stat.edges);
                }
                String label = String.format(Locale.US, "%02d:%02d-%02d:%02d", hour, quarter * 15,
                        (quarter == 3 ? (hour + 1) % 24 : hour), quarter == 3 ? 0 : quarter * 15 + 15);
                row.addView(text(label + "   " + positive + "/" + bucket.size() + " pozitivnih · " + found + " novih · " + refreshed + " refreshovano · medijana " + scanPercent(median(edges)),
                        11, R.color.ink, Typeface.NORMAL), topParams(7));
            }
            panel.addView(row, topParams(5));
        }
    }

    private LinearLayout scanLifespanSummary() {
        List<Integer> lost = new ArrayList<>();
        int tracked = 0;
        for (ArbitrageOpportunity opportunity : archive) {
            if (opportunity.getRefreshCount() <= 0) continue;
            tracked++;
            if (opportunity.getFirstLostAfterMinutes() > 0) lost.add(opportunity.getFirstLostAfterMinutes());
        }
        LinearLayout card = card();
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.addView(text("TRAJANJE ARBOVA", 11, R.color.muted, Typeface.BOLD));
        if (tracked == 0) {
            card.addView(text("Još nema dovoljno praćenih arbova.", 12, R.color.muted, Typeface.NORMAL), topParams(7));
            return card;
        }
        int average = 0;
        for (Integer value : lost) average += value;
        average = lost.isEmpty() ? 0 : Math.round((float) average / lost.size());
        card.addView(text("Praćeno " + tracked + " · nestalo " + lost.size() + " · prosek " + average + " min",
                12, R.color.ink, Typeface.NORMAL), topParams(7));
        return card;
    }

    private LinearLayout wrapSection(String title, View body) {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.addView(text(title, 11, R.color.muted, Typeface.BOLD));
        section.addView(body, topParams(7));
        return section;
    }

    private int[] scanHourMinute(String value) {
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US);
            format.setTimeZone(TimeZone.getTimeZone("Europe/Belgrade"));
            Date date = format.parse(value);
            Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Europe/Belgrade"));
            calendar.setTime(date);
            return new int[]{calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE)};
        } catch (Exception ignored) {
            try {
                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US);
                format.setTimeZone(TimeZone.getTimeZone("Europe/Belgrade"));
                Date date = format.parse(value);
                Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Europe/Belgrade"));
                calendar.setTime(date);
                return new int[]{calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE)};
            } catch (Exception ignoredAgain) {
                return new int[]{-1, -1};
            }
        }
    }

    private double median(List<Double> values) {
        if (values == null || values.isEmpty()) return 0.0;
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 0 ? (sorted.get(middle - 1) + sorted.get(middle)) / 2.0 : sorted.get(middle);
    }

    private String scanPercent(double value) {
        return String.format(Locale.US, "+%.2f%%", value);
    }

    private void refreshPdfAiPicks(final boolean showMessage) {
        lastAiRefreshRequestAt = System.currentTimeMillis();
        final String requestedMonth = analyticsMonth;
        notificationExecutor.execute(() -> {
            try {
                PdfAiPicksApiClient client = new PdfAiPicksApiClient();
                List<AiPick> fresh = client.fetch();
                AiPickAnalytics analytics = client.fetchAnalytics(requestedMonth);
                runOnUiThread(() -> {
                    aiPicks = fresh;
                    store.saveAiLatest(fresh);
                    if (requestedMonth.equals(analyticsMonth)) {
                        aiAnalytics = analytics;
                        if ("all".equals(requestedMonth)) saveAiAnalytics(analytics);
                    }
                    if ("ai".equals(screen)
                            || ("history".equals(screen) && "ai".equals(historyMode))
                            || ("analytics".equals(screen) && "ai".equals(analyticsMode))) render();
                    if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                    if (showMessage) setStatus("AI Picks ucitani sa PDF-a na sajtu");
                });
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                    if (showMessage) setStatus("PDF AI Picks nisu dostupni - prikazujem lokalni zapis");
                });
            }
        });
    }

    private void addAiPicksScreen() {
        addPageTitle("AI PICKS");
        TextView refresh = filterButton("OSVEZI SA SAJTA", true, v -> {
            setStatus("Ucitavam AI Picks PDF...");
            refreshPdfAiPicks(true);
        });
        content.addView(refresh, topParams(16));
        addSyncStatus();
        String today = belgradeToday();
        renderedAiDate = today;
        List<AiPick> todayPicks = new ArrayList<>();
        for (AiPick pick : aiPicks) {
            if (today.equals(normalizeAiDate(pick.getDate()))) todayPicks.add(pick);
        }
        if (todayPicks.isEmpty()) {
            content.addView(emptyCard(
                    "Nema AI pickova za danas",
                    "Pickovi za naredne dane su sacuvani i prikazace se automatski posle ponoci."
            ), topParams(18));
            return;
        }
        TextView date = text("DANAS  ·  " + aiDateLabel(today), 11, R.color.muted, Typeface.BOLD);
        content.addView(date, topParams(16));
        int number = 0;
        for (AiPick pick : todayPicks) {
            number++;
            content.addView(aiPdfPickCard(pick.isMvp() ? "MVP PICK" : "PICK " + number, pick), topParams(10));
        }
    }

    private void refreshAiScreenAfterMidnight() {
        if (!"ai".equals(screen)) return;
        String today = belgradeToday();
        if (!today.equals(renderedAiDate)) render();
    }

    private String belgradeToday() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("Europe/Belgrade"));
        return format.format(new Date());
    }

    private String normalizeAiDate(String value) {
        String raw = value == null ? "" : value.trim();
        if (raw.matches("\\d{4}-\\d{2}-\\d{2}")) return raw;
        String[] patterns = {"dd.MM.yyyy", "dd.MM.yyyy.", "dd/MM/yyyy"};
        for (String pattern : patterns) {
            try {
                Date parsed = new SimpleDateFormat(pattern, Locale.US).parse(raw);
                if (parsed != null) return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(parsed);
            } catch (Exception ignored) { }
        }
        return raw;
    }

    private LinearLayout aiPdfPickCard(String label, final AiPick pick) {
        LinearLayout card = card();
        card.setBackgroundResource(pick.isMvp()
                ? R.drawable.mvp_card_selector
                : R.drawable.ai_card_selector);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setClickable(true);
        card.setFocusable(true);

        TextView pickLabel = text(pick.isMvp() ? "★  MVP PICK" : label, 10,
                pick.isMvp() ? R.color.orange : R.color.muted, Typeface.BOLD);
        card.addView(pickLabel);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(0, dp(7), 0, 0);
        top.addView(text(pick.getMatch(), 16, R.color.ink, Typeface.BOLD), weightedParams());
        TextView odds = text(pick.getOddsLabel(), 15, R.color.ink, Typeface.BOLD);
        odds.setGravity(Gravity.CENTER);
        odds.setBackgroundResource(R.drawable.chip_bg);
        top.addView(odds, new LinearLayout.LayoutParams(dp(62), dp(34)));
        card.addView(top);

        LinearLayout marketRow = new LinearLayout(this);
        marketRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView selection = text(pick.getSelection(), 13, R.color.ink, Typeface.BOLD);
        selection.setPadding(0, dp(5), dp(8), 0);
        marketRow.addView(selection, weightedParams());
        String resultValue = pick.getResult();
        int resultColor = "WIN".equalsIgnoreCase(resultValue) ? R.color.accent
                : "LOSS".equalsIgnoreCase(resultValue) ? R.color.danger : R.color.muted;
        TextView result = text(aiResultLabel(resultValue), 10, resultColor, Typeface.BOLD);
        result.setGravity(Gravity.RIGHT);
        marketRow.addView(result);
        card.addView(marketRow);

        String meta = cleanMeta(pick.getLeague(), pick.getKickoff());
        TextView metaView = text(meta, 11, R.color.muted, Typeface.NORMAL);
        metaView.setPadding(0, dp(4), 0, 0);
        card.addView(metaView);

        LinearLayout footer = new LinearLayout(this);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setPadding(0, dp(8), 0, 0);
        String confidence = pick.getConfidence() > 0 ? pick.getConfidence() + "% procena" : "Procena nije navedena";
        String bookmaker = pick.getBookmaker().isEmpty() ? "Kladionica nije navedena" : displayBookmaker(pick.getBookmaker());
        footer.addView(text(confidence + "  ·  " + bookmaker, 10, R.color.muted, Typeface.NORMAL), weightedParams());
        TextView details = text("Detalji  ›", 10,
                pick.isMvp() ? R.color.orange : R.color.ink, Typeface.BOLD);
        details.setGravity(Gravity.RIGHT);
        details.setPadding(dp(8), 0, 0, 0);
        footer.addView(details);
        card.addView(footer);
        card.setOnClickListener(v -> showPdfAiPickDetail(pick));
        return card;
    }

    private String cleanMeta(String league, String kickoff) {
        String left = league == null ? "" : league.trim();
        String right = kickoff == null ? "" : kickoff.trim();
        if (left.isEmpty()) return right;
        if (right.isEmpty()) return left;
        return left + "  ·  " + right;
    }

    private String aiDateLabel(String value) {
        try {
            Date date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(value);
            if (date != null) return new SimpleDateFormat("EEEE, dd.MM.yyyy", new Locale("sr", "RS")).format(date).toUpperCase();
        } catch (Exception ignored) { }
        return value;
    }

    private void showPdfAiPickDetail(AiPick pick) {
        Dialog dialog = new Dialog(this);
        LinearLayout panel = card();
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(18), dp(18), dp(16));

        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout headingCopy = new LinearLayout(this);
        headingCopy.setOrientation(LinearLayout.VERTICAL);
        headingCopy.addView(text(pick.isMvp() ? "MVP PICK" : "AI PICK", 11,
                pick.isMvp() ? R.color.orange : R.color.accent, Typeface.BOLD));
        headingCopy.addView(text(pick.getMatch(), 20, R.color.ink, Typeface.BOLD), topParams(4));
        heading.addView(headingCopy, weightedParams());
        TextView odds = text(pick.getOddsLabel(), 16, R.color.ink, Typeface.BOLD);
        odds.setGravity(Gravity.CENTER);
        odds.setBackgroundResource(R.drawable.chip_bg);
        heading.addView(odds, new LinearLayout.LayoutParams(dp(64), dp(38)));
        panel.addView(heading);

        panel.addView(text(pick.getSelection(), 14, R.color.ink, Typeface.BOLD), topParams(10));
        panel.addView(text(cleanMeta(pick.getLeague(), pick.getKickoff()) + "  ·  "
                + (pick.getBookmaker().isEmpty() ? "Bez kladionice" : displayBookmaker(pick.getBookmaker())),
                11, R.color.muted, Typeface.NORMAL), topParams(3));

        LinearLayout confidenceBlock = card();
        confidenceBlock.setOrientation(LinearLayout.VERTICAL);
        confidenceBlock.setPadding(dp(12), dp(11), dp(12), dp(11));
        LinearLayout confidenceTop = new LinearLayout(this);
        confidenceTop.setGravity(Gravity.CENTER_VERTICAL);
        confidenceTop.addView(text("PROCENA MODELA", 10, R.color.muted, Typeface.BOLD), weightedParams());
        confidenceTop.addView(rightText(pick.getConfidence() > 0 ? pick.getConfidence() + "%" : "N/A",
                16, R.color.ink, Typeface.BOLD));
        confidenceBlock.addView(confidenceTop);
        ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgress(Math.max(0, Math.min(100, pick.getConfidence())));
        progress.setProgressDrawable(getResources().getDrawable(R.drawable.confidence_track));
        confidenceBlock.addView(progress, topParams(8));
        panel.addView(confidenceBlock, topParams(14));

        panel.addView(detailTextBlock("ZASTO SE IGRA", pick.getReason().trim().isEmpty()
                ? "Obrazlozenje nije navedeno u PDF analizi." : pick.getReason()), topParams(10));
        if (!pick.getKeyFactors().isEmpty()) {
            StringBuilder factors = new StringBuilder();
            for (String factor : pick.getKeyFactors()) {
                String clean = factor == null ? "" : factor.trim();
                if (clean.isEmpty()) continue;
                if (factors.length() > 0) factors.append("\n");
                factors.append("✓  ").append(clean);
            }
            if (factors.length() > 0) panel.addView(detailTextBlock("KLJUCNI FAKTORI", factors.toString()), topParams(10));
        }

        TextView close = filterButton("ZATVORI", false, v -> dialog.dismiss());
        panel.addView(close, topParams(18));
        ScrollView dialogScroll = new ScrollView(this);
        dialogScroll.setFillViewport(true);
        dialogScroll.addView(panel);
        dialog.setContentView(dialogScroll);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
            window.setDimAmount(0.68f);
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.90f),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private LinearLayout detailTextBlock(String label, String body) {
        LinearLayout block = card();
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(dp(12), dp(11), dp(12), dp(11));
        block.addView(text(label, 10, R.color.muted, Typeface.BOLD));
        TextView copy = text(body, 13, R.color.ink, Typeface.NORMAL);
        copy.setLineSpacing(0, 1.12f);
        block.addView(copy, topParams(6));
        return block;
    }

    private String aiResultLabel(String result) {
        if ("WIN".equalsIgnoreCase(result)) return "PROSAO";
        if ("LOSS".equalsIgnoreCase(result)) return "PAO";
        if ("VOID".equalsIgnoreCase(result)) return "VOID";
        return "CEKA";
    }

    private void addStatsStrip() {
        LinearLayout strip = new LinearLayout(this);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setPadding(0, dp(6), 0, dp(10));

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.addView(text("Aktivne prilike", 12, R.color.muted, Typeface.NORMAL));
        left.addView(text(String.valueOf(opportunities.size()), 22, R.color.ink, Typeface.BOLD));
        strip.addView(left, weightedParams());

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.RIGHT);
        right.addView(rightText("Najbolji ROI", 12, R.color.muted, Typeface.NORMAL));
        right.addView(rightText(bestRoiLabel(), 22, R.color.accent, Typeface.BOLD));
        strip.addView(right, weightedParams());
        content.addView(strip);
        addDivider(content, 0, 14);
    }

    private void addFilters() {
        LinearLayout sources = new LinearLayout(this);
        sources.setOrientation(LinearLayout.HORIZONTAL);
        sources.addView(filterButton("SVE", "all".equals(opportunitySource), v -> {
            opportunitySource = "all";
            render();
        }), weightedParams());

        LinearLayout.LayoutParams apiParams = weightedParams();
        apiParams.leftMargin = dp(8);
        sources.addView(filterButton("API ARB", "api".equals(opportunitySource), v -> {
            opportunitySource = "api";
            render();
        }), apiParams);

        LinearLayout.LayoutParams srParams = weightedParams();
        srParams.leftMargin = dp(8);
        sources.addView(filterButton("SR ARB", "sr".equals(opportunitySource), v -> {
            opportunitySource = "sr";
            render();
        }), srParams);
        content.addView(sources);

        LinearLayout filters = new LinearLayout(this);
        filters.setOrientation(LinearLayout.HORIZONTAL);
        filters.addView(filterButton("SVI ROI", !roiOnly, v -> {
            roiOnly = false;
            render();
        }), new LinearLayout.LayoutParams(dp(78), ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        right.leftMargin = dp(8);
        filters.addView(filterButton("ROI 2%+", roiOnly, v -> {
            roiOnly = true;
            render();
        }), right);
        content.addView(filters, topParams(8));
    }

    private LinearLayout scannerStatusCard() {
        LinearLayout card = card();
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(18), dp(16), dp(18));

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView dot = text(autoRunning ? "●" : "○", 16,
                autoRunning ? R.color.accent : R.color.muted, Typeface.BOLD);
        if (autoRunning) {
            AlphaAnimation pulse = new AlphaAnimation(1.0f, 0.28f);
            pulse.setDuration(850L);
            pulse.setRepeatMode(Animation.REVERSE);
            pulse.setRepeatCount(Animation.INFINITE);
            dot.startAnimation(pulse);
        }
        statusRow.addView(dot);
        TextView status = text(autoRunning ? "Skener je aktivan" : "Skener nije aktivan",
                15, R.color.ink, Typeface.BOLD);
        status.setPadding(dp(7), 0, 0, 0);
        statusRow.addView(status, weightedParams());
        TextView state = text(autoRunning ? "ONLINE" : "OFFLINE", 10,
                autoRunning ? R.color.accent : R.color.muted, Typeface.BOLD);
        statusRow.addView(state);
        card.addView(statusRow);

        addDivider(card, 14, 10);
        scannerInfoRow(card, "API skener",
                scannerStateLabel(apiAutoRunning, nextApiRefreshAt));
        scannerInfoRow(card, "Domaci skener",
                scannerStateLabel(serbianAutoRunning, nextSerbianRefreshAt));
        scannerInfoRow(card, "Poslednji API sken",
                scannerTimeLabel(lastAutoRun, "Nije jos pokrenut"));
        scannerInfoRow(card, "Poslednji domaci sken",
                scannerTimeLabel(lastSerbianAutoRun, "Nije jos pokrenut"));
        scannerInfoRow(card, "Kladionice", bookmakerCountLabel() + " + MaxBet/SoccerBet/MerkurXtip");
        if (!autoMessage.trim().isEmpty()) {
            TextView activity = text(autoMessage, 11, R.color.muted, Typeface.NORMAL);
            activity.setPadding(0, dp(12), 0, 0);
            card.addView(activity);
        }
        if (!serbianAutoMessage.trim().isEmpty()) {
            TextView activity = text(serbianAutoMessage, 11, R.color.muted, Typeface.NORMAL);
            activity.setPadding(0, dp(5), 0, 0);
            card.addView(activity);
        }
        TextView empty = text("Trenutno nema aktivnih arbitraza. Nova prilika ce se pojaviti automatski.",
                12, R.color.muted, Typeface.NORMAL);
        empty.setPadding(0, dp(14), 0, 0);
        card.addView(empty);
        return card;
    }

    private String scannerStateLabel(boolean running, long nextRun) {
        if (!running) return "OFFLINE";
        return "ONLINE  ·  " + scannerTimeLabel(nextRun, "Uskoro");
    }

    private void scannerInfoRow(LinearLayout parent, String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));
        row.addView(text(label, 12, R.color.muted, Typeface.NORMAL), weightedParams());
        row.addView(rightText(value, 12, R.color.ink, Typeface.BOLD));
        parent.addView(row);
    }

    private String scannerTimeLabel(String raw, String fallback) {
        long value = parseBackendTime(raw);
        return scannerTimeLabel(value, fallback);
    }

    private String scannerTimeLabel(long value, String fallback) {
        if (value <= 0L) return fallback;
        return new SimpleDateFormat("dd.MM. HH:mm", new Locale("sr", "RS")).format(new Date(value));
    }

    private String bookmakerCountLabel() {
        String value = autoBookmakers == null ? "" : autoBookmakers.trim();
        if (value.isEmpty()) return "Nije dostupno";
        int count = 0;
        for (String bookmaker : value.split(",")) {
            if (!bookmaker.trim().isEmpty()) count++;
        }
        return count + (count == 1 ? " kladionica" : " kladionice");
    }

    private TextView filterButton(String label, boolean active, View.OnClickListener listener) {
        TextView button = text(label, 11, active ? android.R.color.white : R.color.muted, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(11), dp(7), dp(11), dp(7));
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setBackgroundResource(active ? R.drawable.green_button_bg : R.drawable.chip_bg);
        button.setOnClickListener(listener);
        return button;
    }

    private List<ArbitrageOpportunity> filteredOpportunities() {
        List<ArbitrageOpportunity> filtered = new ArrayList<>();
        for (ArbitrageOpportunity opportunity : opportunities) {
            boolean isSrArb = "SR ARB".equalsIgnoreCase(opportunity.getSourceLabel());
            boolean sourceMatches = "all".equals(opportunitySource)
                    || ("sr".equals(opportunitySource) && isSrArb)
                    || ("api".equals(opportunitySource) && !isSrArb);
            boolean roiMatches = !roiOnly || opportunity.getProfitPercent() >= 0.02;
            if (sourceMatches && roiMatches) {
                filtered.add(opportunity);
            }
        }
        return filtered;
    }

    private LinearLayout opportunityCard(final ArbitrageOpportunity opportunity) {
        LinearLayout card = card();
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        ImageView ball = icon(R.drawable.ai_picks_icon_nav, "Fudbal");
        header.addView(ball, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout title = new LinearLayout(this);
        title.setOrientation(LinearLayout.VERTICAL);
        title.setPadding(dp(8), 0, dp(5), 0);
        title.addView(text(opportunity.getSourceLabel(), 10,
                "SR ARB".equalsIgnoreCase(opportunity.getSourceLabel()) ? R.color.blue : R.color.accent,
                Typeface.BOLD));
        title.addView(text(opportunity.getMatch(), 16, R.color.ink, Typeface.BOLD));
        title.addView(text(marketLabel(opportunity), 12, R.color.muted, Typeface.NORMAL));
        String eventTime = formatEventDate(opportunity.getEventDate());
        if (!eventTime.isEmpty()) {
            title.addView(text(eventTime, 11, R.color.accent, Typeface.NORMAL));
        }
        header.addView(title, weightedParams());

        LinearLayout roi = new LinearLayout(this);
        roi.setOrientation(LinearLayout.VERTICAL);
        roi.setGravity(Gravity.RIGHT);
        boolean historyCard = "history".equals(screen) && "arb".equals(historyMode);
        if (historyCard) {
            long eventTimestamp = parseBackendTime(opportunity.getEventDate());
            boolean eventFinished = eventTimestamp > 0L && eventTimestamp <= System.currentTimeMillis();
            roi.addView(rightText("PEAK " + opportunity.getPeakProfitLabel(), 15,
                    R.color.accent, Typeface.BOLD));
            roi.addView(rightText("(" + (eventFinished ? "poslednji refresh " : "sada ") + opportunity.getProfitLabel() + ")", 10,
                    opportunity.getProfitPercent() >= 0 ? R.color.muted : R.color.danger,
                    Typeface.NORMAL));
            if (opportunity.hasPreStartProfit()) {
                roi.addView(rightText("pre pocetak " + opportunity.getPreStartProfitLabel(), 10,
                        R.color.blue, Typeface.NORMAL));
            }
        } else {
            roi.addView(rightText(opportunity.getProfitLabel(), 15,
                    opportunity.getProfitPercent() >= 0 ? R.color.accent : R.color.danger,
                    Typeface.BOLD));
            roi.addView(rightText("ROI", 10, R.color.muted, Typeface.BOLD));
        }
        header.addView(roi);
        card.addView(header);

        addDivider(card, 12, 10);
        LinearLayout odds = new LinearLayout(this);
        odds.setOrientation(LinearLayout.HORIZONTAL);
        List<ArbitrageOutcome> shown = firstOutcomes(opportunity, 2);
        for (int i = 0; i < shown.size(); i++) {
            odds.addView(outcomeColumn(shown.get(i), i == 0 ? R.color.blue : R.color.accent), weightedParams());
            if (i == 0 && shown.size() > 1) {
                LinearLayout divider = new LinearLayout(this);
                divider.setBackgroundColor(color(R.color.line));
                odds.addView(divider, new LinearLayout.LayoutParams(dp(1), dp(62)));
            }
        }
        card.addView(odds);
        if (historyCard) {
            addDivider(card, 10, 8);
            TextView lifecycle = text(opportunity.getLifecycleLabel(), 11, R.color.muted, Typeface.NORMAL);
            lifecycle.setGravity(Gravity.RIGHT);
            card.addView(lifecycle);
        }
        card.setOnClickListener(v -> showDetail(opportunity));
        return card;
    }

    private LinearLayout outcomeColumn(ArbitrageOutcome outcome, int accentColor) {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER);
        column.setPadding(dp(5), 0, dp(5), 0);
            column.addView(bookmakerLine(outcome));
        TextView selection = text(outcome.getSelection(), 11, R.color.muted, Typeface.NORMAL);
        selection.setGravity(Gravity.CENTER);
        column.addView(selection);
        TextView odds = text(String.format(Locale.US, "%.2f", outcome.getOdds()), 21, accentColor, Typeface.NORMAL);
        odds.setGravity(Gravity.CENTER);
        column.addView(odds);
        return column;
    }

    private void showDetail(final ArbitrageOpportunity opportunity) {
        content.removeAllViews();
        navigation.setVisibility(View.GONE);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("<", 30, R.color.ink, Typeface.NORMAL);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> render());
        top.addView(back, new LinearLayout.LayoutParams(dp(42), dp(42)));
        TextView title = text("Detalji prilike", 18, R.color.ink, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        top.addView(title, weightedParams());
        TextView save = text(isSaved(opportunity) ? "★" : "☆", 28, R.color.accent, Typeface.NORMAL);
        save.setGravity(Gravity.CENTER);
        save.setOnClickListener(v -> {
            toggleSaved(opportunity);
            showDetail(opportunity);
        });
        top.addView(save, new LinearLayout.LayoutParams(dp(42), dp(42)));
        content.addView(top);

        content.addView(opportunityCard(opportunity), topParams(16));
        content.addView(sectionTitle("POREDJENJE KVOTA"), topParams(22));

        LinearLayout oddsRow = new LinearLayout(this);
        oddsRow.setOrientation(LinearLayout.HORIZONTAL);
        List<ArbitrageOutcome> shown = firstOutcomes(opportunity, 2);
        for (int i = 0; i < shown.size(); i++) {
            oddsRow.addView(largeOutcomeCard(shown.get(i), i == 0 ? R.color.blue : R.color.accent), weightedParams());
            if (i == 0 && shown.size() > 1) {
                LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(dp(10), 1);
                oddsRow.addView(new View(this), gap);
            }
        }
        content.addView(oddsRow, topParams(10));
        content.addView(calculatorCard(opportunity), topParams(18));
    }

    private LinearLayout largeOutcomeCard(ArbitrageOutcome outcome, int accentColor) {
        LinearLayout box = card();
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(8), dp(14), dp(8), dp(14));
            box.addView(bookmakerLine(outcome));
        TextView selection = text(outcome.getSelection(), 11, R.color.muted, Typeface.NORMAL);
        selection.setGravity(Gravity.CENTER);
        box.addView(selection);
        TextView odds = text(String.format(Locale.US, "%.2f", outcome.getOdds()), 25, accentColor, Typeface.NORMAL);
        odds.setGravity(Gravity.CENTER);
        box.addView(odds);
        return box;
    }

    private LinearLayout calculatorCard(final ArbitrageOpportunity opportunity) {
        LinearLayout card = card();
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.addView(text("UKUPAN BANKROLL", 13, R.color.muted, Typeface.BOLD));

        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText("1000");
        input.setTextSize(20);
        input.setTextColor(color(R.color.ink));
        input.setHintTextColor(color(R.color.muted));
        input.setHint("Ukupan ulog");
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setSelectAllOnFocus(true);
        card.addView(input, topParams(8));

        final LinearLayout result = new LinearLayout(this);
        result.setOrientation(LinearLayout.VERTICAL);
        card.addView(result, topParams(12));
        updateCalculator(result, opportunity, 1000);

        TextView calculate = actionButton("IZRACUNAJ RASPODELU", v ->
                updateCalculator(result, opportunity, parseMoney(input.getText().toString(), 1000)));
        card.addView(calculate, topParams(14));
        return card;
    }

    private void updateCalculator(LinearLayout result, ArbitrageOpportunity opportunity, double totalStake) {
        result.removeAllViews();
        double book = opportunity.getBookPercent();
        List<ArbitrageOutcome> outcomes = opportunity.getOutcomes();
        if (totalStake <= 0 || book <= 0 || outcomes == null || outcomes.isEmpty()) {
            result.addView(text("Unesi validan bankroll.", 13, R.color.muted, Typeface.NORMAL));
            return;
        }
        long roundedTotal = Math.round(totalStake);
        List<Long> roundedStakes = new ArrayList<>();
        long allocated = 0;
        for (int index = 0; index < outcomes.size(); index++) {
            ArbitrageOutcome outcome = outcomes.get(index);
            long stake = index == outcomes.size() - 1
                    ? roundedTotal - allocated
                    : Math.round(roundedTotal * (1.0 / outcome.getOdds()) / book);
            stake = Math.max(0, stake);
            roundedStakes.add(stake);
            allocated += stake;
        }

        double guaranteedReturn = Double.MAX_VALUE;
        for (int index = 0; index < outcomes.size(); index++) {
            ArbitrageOutcome outcome = outcomes.get(index);
            long stake = roundedStakes.get(index);
            guaranteedReturn = Math.min(guaranteedReturn, stake * outcome.getOdds());
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout left = new LinearLayout(this);
            left.setOrientation(LinearLayout.VERTICAL);
            left.addView(bookmakerLine(outcome.getBookmaker()));
            left.addView(text(outcome.getSelection() + " @ " + String.format(Locale.US, "%.2f", outcome.getOdds()),
                    11, R.color.muted, Typeface.NORMAL));
            row.addView(left, weightedParams());
            row.addView(rightText(wholeMoney(stake), 17, R.color.ink, Typeface.BOLD));
            result.addView(row, topParams(9));
        }
        addDivider(result, 14, 10);
        summaryRow(result, "Ukupan ulog", wholeMoney(roundedTotal), R.color.ink);
        summaryRow(result, "Garantovani povrat", wholeMoney(guaranteedReturn), R.color.accent);
        summaryRow(result, "Neto profit", "+" + wholeMoney(guaranteedReturn - roundedTotal), R.color.accent);
    }

    private void summaryRow(LinearLayout parent, String label, String value, int valueColor) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, dp(6));
        row.addView(text(label, 13, R.color.muted, Typeface.NORMAL), weightedParams());
        row.addView(rightText(value, 14, valueColor, Typeface.BOLD));
        parent.addView(row);
    }

    private void addSavedScreen() {
        addPageTitle("Sacuvane prilike");
        Set<String> saved = savedKeys();
        List<ArbitrageOpportunity> matches = new ArrayList<>();
        for (ArbitrageOpportunity opportunity : opportunities) {
            if (saved.contains(opportunityKey(opportunity))) {
                matches.add(opportunity);
            }
        }
        if (matches.isEmpty()) {
            LinearLayout empty = card();
            empty.setOrientation(LinearLayout.VERTICAL);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(20), dp(34), dp(20), dp(34));
            TextView star = text("☆", 52, R.color.muted, Typeface.NORMAL);
            star.setGravity(Gravity.CENTER);
            empty.addView(star);
            TextView title = text("Nema sacuvanih prilika", 16, R.color.ink, Typeface.BOLD);
            title.setGravity(Gravity.CENTER);
            empty.addView(title, topParams(12));
            TextView subtitle = text("Otvori arb i pritisni zvezdicu da ga sacuvas.",
                    12, R.color.muted, Typeface.NORMAL);
            subtitle.setGravity(Gravity.CENTER);
            empty.addView(subtitle, topParams(7));
            content.addView(empty, topParams(42));
            return;
        }
        for (ArbitrageOpportunity opportunity : matches) {
            content.addView(opportunityCard(opportunity), topParams(14));
        }
    }

    private void addHistoryScreen() {
        addPageTitle("Istorija");
        LinearLayout modes = new LinearLayout(this);
        modes.setOrientation(LinearLayout.HORIZONTAL);
        modes.addView(filterButton("ARB", "arb".equals(historyMode), v -> {
            historyMode = "arb";
            render();
        }), weightedParams());
        content.addView(modes, topParams(12));
        addSyncStatus();
        if (archive.isEmpty()) {
            content.addView(emptyCard(
                    "Nema istorije",
                    "Pokreni start_surebet.bat da aplikacija preuzme dosadasnje arbove sa sajta."
            ), topParams(18));
            return;
        }
        LinearLayout sortRow = new LinearLayout(this);
        sortRow.setOrientation(LinearLayout.HORIZONTAL);
        sortRow.setGravity(Gravity.RIGHT);
        String sortLabel;
        if ("time_desc".equals(historySortMode)) {
            sortLabel = "Sortiraj: vreme ↓";
        } else if ("time_asc".equals(historySortMode)) {
            sortLabel = "Sortiraj: vreme ↑";
        } else {
            sortLabel = historyDescending ? "Sortiraj: ROI ↓" : "Sortiraj: ROI ↑";
        }
        TextView sort = filterButton(sortLabel, false, null);
        sort.setOnClickListener(v -> showHistorySortMenu(sort));
        sortRow.addView(sort, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        content.addView(sortRow, topParams(12));

        LinearLayout sourceFilters = new LinearLayout(this);
        sourceFilters.setOrientation(LinearLayout.HORIZONTAL);
        sourceFilters.addView(historySourceButton("SVE ARBOVE", "all"), weightedParams());
        LinearLayout.LayoutParams apiSourceParams = weightedParams();
        apiSourceParams.leftMargin = dp(8);
        sourceFilters.addView(historySourceButton("API ARB", "api"), apiSourceParams);
        LinearLayout.LayoutParams srSourceParams = weightedParams();
        srSourceParams.leftMargin = dp(8);
        sourceFilters.addView(historySourceButton("SR ARB", "sr"), srSourceParams);
        content.addView(sourceFilters, topParams(10));

        LinearLayout filters = new LinearLayout(this);
        filters.setOrientation(LinearLayout.HORIZONTAL);
        filters.addView(historyFilterButton("SVE", "all"), weightedParams());
        filters.addView(historyFilterButton("ISHOD", "result"), weightedParams());
        filters.addView(historyFilterButton("GOLOVI", "goals"), weightedParams());
        filters.addView(historyFilterButton("KORNERI", "corners"), weightedParams());
        content.addView(filters, topParams(10));
        addHistoryMonthSelector();

        List<ArbitrageOpportunity> visible = filteredAndSortedHistory();
        TextView count = text("Prikazano: " + visible.size() + " / " + archive.size(), 12, R.color.muted, Typeface.NORMAL);
        count.setGravity(Gravity.CENTER);
        content.addView(count, topParams(10));
        for (ArbitrageOpportunity opportunity : visible) {
            content.addView(opportunityCard(opportunity), topParams(14));
        }
    }

    private void addAiHistoryContent() {
        addHistoryMonthSelector();
        List<AiPick> history = new ArrayList<>();
        String today = belgradeToday();
        for (AiPick pick : aiPicks) {
            String date = normalizeAiDate(pick.getDate());
            boolean previousDay = !date.isEmpty() && date.compareTo(today) < 0;
            boolean monthMatches = "all".equals(historyMonth) || date.startsWith(historyMonth);
            if (previousDay && monthMatches) history.add(pick);
        }
        Collections.sort(history, new Comparator<AiPick>() {
            @Override
            public int compare(AiPick left, AiPick right) {
                int date = normalizeAiDate(right.getDate()).compareTo(normalizeAiDate(left.getDate()));
                if (date != 0) return date;
                return right.getKickoff().compareTo(left.getKickoff());
            }
        });

        if (history.isEmpty()) {
            content.addView(emptyCard(
                    "Nema AI Picks istorije",
                    "Zavrseni dnevni pickovi ce se ovde pojaviti posle ponoci."
            ), topParams(16));
            return;
        }

        TextView count = text("Prikazano: " + history.size(), 11, R.color.muted, Typeface.NORMAL);
        count.setGravity(Gravity.CENTER);
        content.addView(count, topParams(10));
        String currentDate = "";
        int index = 0;
        for (AiPick pick : history) {
            String date = normalizeAiDate(pick.getDate());
            if (!date.equals(currentDate)) {
                currentDate = date;
                index = 0;
                TextView heading = text(aiDateLabel(date), 11, R.color.muted, Typeface.BOLD);
                content.addView(heading, topParams(18));
                AiDaySummary day = findAiDaySummary(history, date);
                if (day != null) content.addView(aiDaySummaryCard(day), topParams(8));
            }
            index++;
            content.addView(aiPdfPickCard(pick.isMvp() ? "MVP PICK" : "PICK " + index, pick), topParams(8));
        }
    }

    private AiDaySummary findAiDaySummary(List<AiPick> source, String date) {
        AiDaySummary summary = new AiDaySummary(date);
        for (AiPick pick : source) {
            if (date.equals(normalizeAiDate(pick.getDate()))) summary.picks.add(pick);
        }
        return summary.picks.isEmpty() ? null : summary;
    }

    private LinearLayout aiDaySummaryCard(AiDaySummary day) {
        LinearLayout card = card();
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.addView(text("DNEVNI REZIME", 10, R.color.muted, Typeface.BOLD));
        addAiBetSummaryRow(card, "PARLAY · " + day.picks.size() + " PARA",
                combinedAiOdds(day), parlayResult(day), parlayProfitUnits(day));
        addDivider(card, 10, 7);
        AiPick mvp = mvpPick(day);
        addAiBetSummaryRow(card, "MVP SINGLE",
                mvp == null ? 0.0 : mvp.getOdds(),
                mvp == null ? "PENDING" : mvp.getResult(),
                mvpProfitUnits(day));
        return card;
    }

    private void addAiBetSummaryRow(LinearLayout parent, String label, double odds,
                                    String result, double profitUnits) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text(label, 12, R.color.ink, Typeface.BOLD));
        copy.addView(text("Kvota " + aiOddsLabel(odds) + "  ·  Ulog "
                + String.format(Locale.US, "%.0f%% bankrolla", AI_STAKE_PERCENT),
                10, R.color.muted, Typeface.NORMAL));
        row.addView(copy, weightedParams());

        LinearLayout outcome = new LinearLayout(this);
        outcome.setOrientation(LinearLayout.VERTICAL);
        outcome.setGravity(Gravity.RIGHT);
        int color = aiResultColor(result);
        outcome.addView(rightText(aiResultLabel(result), 10, color, Typeface.BOLD));
        if (!"PENDING".equalsIgnoreCase(result)) {
            outcome.addView(rightText(formatAiProfit(profitUnits), 12,
                    profitUnits >= 0 ? R.color.accent : R.color.danger, Typeface.BOLD));
        }
        row.addView(outcome);
        parent.addView(row, topParams(9));
    }

    private TextView historyFilterButton(String label, final String filter) {
        TextView button = filterButton(label, filter.equals(historyFilter), v -> {
            historyFilter = filter;
            render();
        });
        button.setTextSize(10);
        return button;
    }

    private TextView historySourceButton(String label, final String source) {
        TextView button = filterButton(label, source.equals(historySource), v -> {
            historySource = source;
            render();
        });
        button.setTextSize(10);
        return button;
    }

    private void showHistorySortMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("ROI: najveci prvo");
        menu.getMenu().add("ROI: najmanji prvo");
        menu.getMenu().add("Vreme: najskorije prvo");
        menu.getMenu().add("Vreme: najstarije prvo");
        menu.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();
            if (title.contains("najskorije")) {
                historySortMode = "time_desc";
            } else if (title.contains("najstarije")) {
                historySortMode = "time_asc";
            } else if (title.contains("najveci")) {
                historySortMode = "roi_desc";
                historyDescending = true;
            } else {
                historySortMode = "roi_asc";
                historyDescending = false;
            }
            render();
            return true;
        });
        menu.show();
    }

    private List<ArbitrageOpportunity> filteredAndSortedHistory() {
        List<ArbitrageOpportunity> visible = new ArrayList<>();
        for (ArbitrageOpportunity opportunity : archive) {
            boolean categoryMatches = "all".equals(historyFilter)
                    || historyFilter.equals(historyCategory(opportunity));
            String sourceLabel = opportunity.getSourceLabel().toUpperCase(Locale.US);
            boolean sourceMatches = "all".equals(historySource)
                    || ("api".equals(historySource) && sourceLabel.contains("API"))
                    || ("sr".equals(historySource) && sourceLabel.contains("SR"));
            boolean monthMatches = "all".equals(historyMonth)
                    || matchesAnalyticsMonth(opportunity.getEventDate(), historyMonth);
            if (categoryMatches && sourceMatches && monthMatches) {
                visible.add(opportunity);
            }
        }
        Collections.sort(visible, new Comparator<ArbitrageOpportunity>() {
            @Override
            public int compare(ArbitrageOpportunity left, ArbitrageOpportunity right) {
                if (historySortMode.startsWith("time")) {
                    int result = Long.compare(historyTime(left), historyTime(right));
                    return "time_desc".equals(historySortMode) ? -result : result;
                }
                int result = Double.compare(left.getPeakProfitPercent(), right.getPeakProfitPercent());
                return historyDescending ? -result : result;
            }
        });
        return visible;
    }

    private long historyTime(ArbitrageOpportunity opportunity) {
        String found = opportunity.getFoundAt();
        long parsed = parseBackendTime(found);
        if (parsed > 0L) return parsed;
        return parseBackendTime(opportunity.getEventDate());
    }

    private void addHistoryMonthSelector() {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout months = new LinearLayout(this);
        months.setOrientation(LinearLayout.HORIZONTAL);
        months.addView(historyMonthButton("SVE VREME", "all"));

        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Europe/Belgrade"));
        String[] labels = {"JAN", "FEB", "MAR", "APR", "MAJ", "JUN", "JUL", "AVG", "SEP", "OKT", "NOV", "DEC"};
        for (int index = 0; index < 12; index++) {
            int month = calendar.get(Calendar.MONTH) + 1;
            int year = calendar.get(Calendar.YEAR);
            String key = String.format(Locale.US, "%04d-%02d", year, month);
            TextView button = historyMonthButton(labels[month - 1] + " " + year, key);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(34));
            params.leftMargin = dp(8);
            months.addView(button, params);
            calendar.add(Calendar.MONTH, -1);
        }
        scroll.addView(months);
        content.addView(scroll, topParams(10));
    }

    private TextView historyMonthButton(String label, final String month) {
        return filterButton(label, month.equals(historyMonth), v -> {
            historyMonth = month;
            render();
        });
    }

    private String historyCategory(ArbitrageOpportunity opportunity) {
        StringBuilder value = new StringBuilder(opportunity.getMarket());
        for (ArbitrageOutcome outcome : opportunity.getOutcomes()) {
            value.append(' ').append(outcome.getSelection());
        }
        String text = value.toString().toLowerCase(Locale.US);
        if (text.contains("corner") || text.contains("korner")) {
            return "corners";
        }
        if (text.contains("total") || text.contains("over") || text.contains("under")
                || text.contains("golov") || text.contains("btts") || text.contains("oba tima")) {
            return "goals";
        }
        return "result";
    }

    private void addCapitalScreen() {
        addPageTitle("Plan kapitala");
        TextView subtitle = text("Raspodela online bankrolla samo za 1xBet i Superbet.",
                12, R.color.muted, Typeface.NORMAL);
        subtitle.setGravity(Gravity.CENTER);
        content.addView(subtitle, topParams(7));
        addSyncStatus();

        SharedPreferences preferences = getSharedPreferences(CAPITAL_PREFS, Context.MODE_PRIVATE);
        int periodDays = preferences.getInt("period_days", 30);
        int bufferPercent = preferences.getInt("buffer_percent", 15);
        double bankroll = Double.longBitsToDouble(preferences.getLong(
                "bankroll_bits", Double.doubleToLongBits(100000.0)));

        LinearLayout periodRow = new LinearLayout(this);
        periodRow.setOrientation(LinearLayout.HORIZONTAL);
        periodRow.addView(capitalChoiceButton("7 DANA", periodDays == 7, () -> {
            preferences.edit().putInt("period_days", 7).apply();
            render();
        }), weightedParams());
        LinearLayout.LayoutParams periodGap = weightedParams();
        periodGap.leftMargin = dp(8);
        periodRow.addView(capitalChoiceButton("30 DANA", periodDays == 30, () -> {
            preferences.edit().putInt("period_days", 30).apply();
            render();
        }), periodGap);
        content.addView(periodRow, topParams(14));

        LinearLayout inputCard = card();
        inputCard.setOrientation(LinearLayout.VERTICAL);
        inputCard.setPadding(dp(14), dp(13), dp(14), dp(14));
        inputCard.addView(text("UKUPAN ONLINE BANKROLL", 11, R.color.muted, Typeface.BOLD));
        final EditText bankrollInput = new EditText(this);
        bankrollInput.setSingleLine(true);
        bankrollInput.setText(wholeMoney(bankroll).replace(".", ""));
        bankrollInput.setSelectAllOnFocus(true);
        bankrollInput.setTextSize(22);
        bankrollInput.setTextColor(color(R.color.ink));
        bankrollInput.setHintTextColor(color(R.color.muted));
        bankrollInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        inputCard.addView(bankrollInput, topParams(4));

        TextView bufferLabel = text("SIGURNOSNA REZERVA", 11, R.color.muted, Typeface.BOLD);
        inputCard.addView(bufferLabel, topParams(10));
        LinearLayout bufferRow = new LinearLayout(this);
        bufferRow.setOrientation(LinearLayout.HORIZONTAL);
        int[] bufferOptions = {10, 15, 20};
        for (int index = 0; index < bufferOptions.length; index++) {
            final int option = bufferOptions[index];
            LinearLayout.LayoutParams optionParams = weightedParams();
            if (index > 0) optionParams.leftMargin = dp(7);
            bufferRow.addView(capitalChoiceButton(option + "%", bufferPercent == option, () -> {
                preferences.edit().putInt("buffer_percent", option).apply();
                render();
            }), optionParams);
        }
        inputCard.addView(bufferRow, topParams(8));
        inputCard.addView(actionButton("IZRACUNAJ PLAN", v -> {
            double entered = Math.max(1000.0,
                    parseMoney(bankrollInput.getText().toString(), 100000.0));
            preferences.edit()
                    .putLong("bankroll_bits", Double.doubleToLongBits(entered))
                    .apply();
            render();
        }), topParams(13));
        content.addView(inputCard, topParams(12));

        final double planningStake = 10000.0;
        CapitalPlanCalculator.Plan baselinePlan = CapitalPlanCalculator.calculate(
                archive, opportunities, periodDays, bankroll, bufferPercent, planningStake);
        double averagePeakSlots = baselinePlan.averagePeakLocked / planningStake;
        int plannedSlots = Math.max(1, (int) Math.ceil(Math.max(
                baselinePlan.averageArbsPerDay, averagePeakSlots)) + 1);
        double stakePerArb = Math.floor(
                bankroll / (plannedSlots * (1.0 + bufferPercent / 100.0)) / 100.0) * 100.0;
        stakePerArb = Math.max(100.0, Math.min(bankroll, stakePerArb));
        CapitalPlanCalculator.Plan plan = CapitalPlanCalculator.calculate(
                archive, opportunities, periodDays, bankroll, bufferPercent, stakePerArb);
        content.addView(capitalUsageCard(plan, stakePerArb, plannedSlots), topParams(14));
        content.addView(capitalSplitCard(plan), topParams(10));
        content.addView(capitalBookCard("1xBet", R.drawable.xbet_logo,
                plan.oneXBet, R.color.blue), topParams(10));
        content.addView(capitalBookCard("Superbet", R.drawable.superbet_logo,
                plan.superbet, R.color.accent), topParams(10));

        LinearLayout note = card();
        note.setOrientation(LinearLayout.VERTICAL);
        note.setPadding(dp(14), dp(13), dp(14), dp(13));
        String leading = plan.oneXBet.targetBalance >= plan.superbet.targetBalance
                ? "1xBet" : "Superbet";
        note.addView(text("PREPORUKA", 11, R.color.accent, Typeface.BOLD));
        note.addView(text("Drzi vise novca na " + leading + ". Uzorak " + plan.sampleArbs
                        + " arbova, pouzdanost " + plan.confidence + ".",
                13, R.color.ink, Typeface.NORMAL), topParams(6));
        int reserveColor = bankroll >= plan.minimumReserve ? R.color.accent : R.color.danger;
        note.addView(text("Preporuceni kapital za P90 dan: "
                        + wholeMoney(plan.minimumReserve) + " din",
                14, reserveColor, Typeface.BOLD), topParams(9));
        if (bankroll < plan.minimumReserve) {
            note.addView(text("Ovo nije obavezan minimum. Sa unetim bankrollom mozes pokriti "
                            + plan.coveredSimultaneousArbs + " arba istovremeno po "
                            + wholeMoney(stakePerArb) + " din.",
                    11, R.color.danger, Typeface.NORMAL), topParams(5));
        }
        content.addView(note, topParams(10));
    }

    private TextView capitalChoiceButton(String label, boolean active, final Runnable action) {
        return filterButton(label, active, v -> action.run());
    }

    private LinearLayout capitalUsageCard(CapitalPlanCalculator.Plan plan, double stakePerArb,
                                          int plannedSlots) {
        LinearLayout card = card();
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(13), dp(14), dp(13));
        card.addView(text("KOLIKO KAPITALA REALNO RADI", 11, R.color.muted, Typeface.BOLD));
        summaryRow(card, "Preporuceni ulog po arbu",
                wholeMoney(stakePerArb) + " din", R.color.accent);
        summaryRow(card, "Prosecno novih arbova dnevno",
                String.format(Locale.US, "%.1f", plan.averageArbsPerDay), R.color.ink);
        summaryRow(card, "Prosecan dnevni promet",
                wholeMoney(plan.averageDailyTurnover) + " din", R.color.ink);
        summaryRow(card, "Prosecno zakljucano u trenutku",
                wholeMoney(plan.averageLockedCapital) + " din", R.color.ink);
        summaryRow(card, "Prosecan dnevni maksimum",
                wholeMoney(plan.averagePeakLocked) + " din", R.color.accent);
        summaryRow(card, "Prosecno vreme do oslobadjanja",
                String.format(Locale.US, "%.1f h", plan.averageLockHours), R.color.ink);
        summaryRow(card, "Tvoj bankroll pokriva",
                plan.coveredSimultaneousArbs + " arba po " + wholeMoney(stakePerArb) + " din",
                R.color.accent);
        TextView basis = text("Plan cuva prostor za " + plannedSlots
                        + " arbova, ukljucujuci jedan dodatni preko istorijskog proseka. Racunato kroz "
                        + plan.observedDays + " dana; kapital se oslobadja oko 2 sata posle pocetka meca.",
                10, R.color.muted, Typeface.NORMAL);
        card.addView(basis, topParams(7));
        return card;
    }

    private LinearLayout capitalSplitCard(CapitalPlanCalculator.Plan plan) {
        LinearLayout card = card();
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(13), dp(14), dp(13));
        LinearLayout labels = new LinearLayout(this);
        labels.setGravity(Gravity.CENTER_VERTICAL);
        labels.addView(text("1xBet  " + String.format(Locale.US, "%.1f%%",
                plan.oneXBet.share * 100.0), 12, R.color.blue, Typeface.BOLD), weightedParams());
        labels.addView(rightText("Superbet  " + String.format(Locale.US, "%.1f%%",
                plan.superbet.share * 100.0), 12, R.color.accent, Typeface.BOLD), weightedParams());
        card.addView(labels);

        LinearLayout meter = new LinearLayout(this);
        meter.setOrientation(LinearLayout.HORIZONTAL);
        View one = new View(this);
        one.setBackgroundColor(color(R.color.blue));
        View superbet = new View(this);
        superbet.setBackgroundColor(color(R.color.accent));
        meter.addView(one, new LinearLayout.LayoutParams(0, dp(10),
                (float) Math.max(0.01, plan.oneXBet.share)));
        meter.addView(superbet, new LinearLayout.LayoutParams(0, dp(10),
                (float) Math.max(0.01, plan.superbet.share)));
        card.addView(meter, topParams(10));
        return card;
    }

    private LinearLayout capitalBookCard(String title, int logoResource,
                                         CapitalPlanCalculator.BookPlan plan, int accentColor) {
        LinearLayout card = card();
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(13), dp(14), dp(13));
        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = icon(logoResource, title);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        heading.addView(logo, new LinearLayout.LayoutParams(dp(62), dp(30)));
        LinearLayout headingCopy = new LinearLayout(this);
        headingCopy.setOrientation(LinearLayout.VERTICAL);
        headingCopy.setPadding(dp(10), 0, 0, 0);
        headingCopy.addView(text(title, 15, R.color.ink, Typeface.BOLD));
        headingCopy.addView(text(String.format(Locale.US, "%.1f%% bankrolla", plan.share * 100.0),
                11, R.color.muted, Typeface.NORMAL), topParams(2));
        heading.addView(headingCopy, weightedParams());
        heading.addView(rightText(wholeMoney(plan.targetBalance) + " din",
                20, accentColor, Typeface.BOLD));
        card.addView(heading);
        addDivider(card, 12, 6);
        summaryRow(card, "Trenutno zakljucano", wholeMoney(plan.currentLocked) + " din", R.color.ink);
        summaryRow(card, "Prosecan ulog po legu", wholeMoney(plan.averageStake) + " din", R.color.ink);
        summaryRow(card, "P90 istovremeno zauzeto", wholeMoney(plan.p90DailyNeed) + " din", accentColor);
        summaryRow(card, "Istorijskih uplata", String.valueOf(plan.stakeCount), R.color.ink);
        return card;
    }

    private void addAnalyticsScreen() {
        addPageTitle("Analitika");
        LinearLayout modes = new LinearLayout(this);
        modes.setOrientation(LinearLayout.HORIZONTAL);
        modes.addView(filterButton("ARB", "arb".equals(analyticsMode), v -> {
            analyticsMode = "arb";
            render();
        }), weightedParams());
        content.addView(modes, topParams(14));
        addAnalyticsMonthSelector();
        addArbAnalyticsSourceSelector();
        List<List<ArbitrageOpportunity>> history = store.loadArbHistory();
        List<ArbitrageOpportunity> periodArbs = analyticsArbsForMonth();
        double totalRoi = 0;
        double best = 0;
        int apiCount = 0;
        int srCount = 0;
        int lostFirstRefresh = 0;
        int lostLater = 0;
        int neverLost = 0;
        for (ArbitrageOpportunity opportunity : periodArbs) {
            double roi = opportunity.getAnalyticsProfitPercent();
            totalRoi += roi;
            best = Math.max(best, roi);
            if ("SR ARB".equalsIgnoreCase(opportunity.getSourceLabel())) srCount++;
            else apiCount++;
            if (opportunity.getFirstLostAfterRefreshes() == 1) lostFirstRefresh++;
            else if (opportunity.getFirstLostAfterRefreshes() > 1) lostLater++;
            else if (opportunity.getRefreshCount() > 0) neverLost++;
        }
        double average = periodArbs.isEmpty() ? 0 : totalRoi / periodArbs.size();
        double potential = totalRoi * 10000;

        LinearLayout hero = card();
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(16), dp(16), dp(16), dp(16));
        hero.addView(text("Profit po peak kvotama", 13, R.color.muted, Typeface.NORMAL));
        hero.addView(text("+" + wholeMoney(potential) + " din", 32, R.color.accent, Typeface.BOLD));
        hero.addView(text(periodArbs.size() + " prilika  ·  Prosecan ROI " + percent(average),
                11, R.color.muted, Typeface.NORMAL));
        hero.addView(text("API ARB " + apiCount + "  ·  SR ARB " + srCount,
                10, R.color.muted, Typeface.NORMAL), topParams(3));
        hero.addView(text("Racunato po peak kvotama i ulogu 10.000 din po arbu",
                10, R.color.muted, Typeface.NORMAL), topParams(3));
        content.addView(hero, topParams(14));

        LinearLayout kpis = new LinearLayout(this);
        kpis.setOrientation(LinearLayout.HORIZONTAL);
        kpis.addView(compactMetricCard("PRILIKE", String.valueOf(periodArbs.size()), R.color.ink), weightedParams());
        LinearLayout.LayoutParams middle = weightedParams();
        middle.leftMargin = dp(8);
        kpis.addView(compactMetricCard("NAJBOLJI ROI", percent(best), R.color.accent), middle);
        LinearLayout.LayoutParams right = weightedParams();
        right.leftMargin = dp(8);
        kpis.addView(compactMetricCard("SACUVANO", String.valueOf(savedKeys().size()), R.color.ink), right);
        content.addView(kpis, topParams(10));

        LinearLayout lifetime = card();
        lifetime.setOrientation(LinearLayout.VERTICAL);
        lifetime.setPadding(dp(14), dp(12), dp(14), dp(12));
        lifetime.addView(text("TRAJANJE ARBOVA", 11, R.color.muted, Typeface.BOLD));
        lifetime.addView(text(
                "Nestao posle 1 refresh-a: " + lostFirstRefresh
                        + "  ·  kasnije: " + lostLater
                        + "  ·  nije nestao: " + neverLost,
                11, R.color.ink, Typeface.NORMAL), topParams(7));
        content.addView(lifetime, topParams(10));

        TextView refreshes = text("Lokalna osvezavanja: " + history.size(), 10, R.color.muted, Typeface.NORMAL);
        refreshes.setGravity(Gravity.CENTER);
        content.addView(refreshes, topParams(10));
    }

    private void addArbAnalyticsSourceSelector() {
        LinearLayout filters = new LinearLayout(this);
        filters.setOrientation(LinearLayout.HORIZONTAL);
        filters.addView(filterButton("SVE", "all".equals(analyticsArbSource), v -> {
            analyticsArbSource = "all";
            render();
        }), weightedParams());
        LinearLayout.LayoutParams apiParams = weightedParams();
        apiParams.leftMargin = dp(8);
        filters.addView(filterButton("API ARB", "api".equals(analyticsArbSource), v -> {
            analyticsArbSource = "api";
            render();
        }), apiParams);
        LinearLayout.LayoutParams srParams = weightedParams();
        srParams.leftMargin = dp(8);
        filters.addView(filterButton("SR ARB", "sr".equals(analyticsArbSource), v -> {
            analyticsArbSource = "sr";
            render();
        }), srParams);
        content.addView(filters, topParams(10));
    }

    private void addAnalyticsMonthSelector() {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout months = new LinearLayout(this);
        months.setOrientation(LinearLayout.HORIZONTAL);
        months.addView(analyticsMonthButton("SVE VREME", "all"));

        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Europe/Belgrade"));
        String[] labels = {"JAN", "FEB", "MAR", "APR", "MAJ", "JUN", "JUL", "AVG", "SEP", "OKT", "NOV", "DEC"};
        for (int index = 0; index < 12; index++) {
            int month = calendar.get(Calendar.MONTH) + 1;
            int year = calendar.get(Calendar.YEAR);
            String key = String.format(Locale.US, "%04d-%02d", year, month);
            TextView button = analyticsMonthButton(labels[month - 1] + " " + year, key);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(34));
            params.leftMargin = dp(8);
            months.addView(button, params);
            calendar.add(Calendar.MONTH, -1);
        }
        scroll.addView(months);
        content.addView(scroll, topParams(12));
    }

    private TextView analyticsMonthButton(String label, final String month) {
        return filterButton(label, month.equals(analyticsMonth), v -> {
            if (month.equals(analyticsMonth)) return;
            analyticsMonth = month;
            if ("ai".equals(analyticsMode)) {
                aiAnalytics = "all".equals(month) ? loadAiAnalytics() : AiPickAnalytics.empty();
                refreshPdfAiPicks(false);
            }
            render();
        });
    }

    private List<ArbitrageOpportunity> analyticsArbsForMonth() {
        Map<String, ArbitrageOpportunity> unique = new LinkedHashMap<>();
        for (ArbitrageOpportunity opportunity : archive) {
            unique.put(opportunityKey(opportunity), opportunity);
        }
        for (ArbitrageOpportunity opportunity : opportunities) {
            String key = opportunityKey(opportunity);
            ArbitrageOpportunity existing = unique.get(key);
            if (existing == null
                    || opportunity.getAnalyticsProfitPercent() > existing.getAnalyticsProfitPercent()) {
                unique.put(key, opportunity);
            }
        }
        List<ArbitrageOpportunity> source = new ArrayList<>(unique.values());
        List<ArbitrageOpportunity> filtered = new ArrayList<>();
        for (ArbitrageOpportunity opportunity : source) {
            boolean isSerbian = "SR ARB".equalsIgnoreCase(opportunity.getSourceLabel());
            if ("api".equals(analyticsArbSource) && isSerbian) continue;
            if ("sr".equals(analyticsArbSource) && !isSerbian) continue;
            if ("all".equals(analyticsMonth)) {
                filtered.add(opportunity);
                continue;
            }
            String date = opportunity.getFoundAt();
            if (date == null || date.trim().isEmpty()) date = opportunity.getEventDate();
            if (matchesAnalyticsMonth(date, analyticsMonth)) filtered.add(opportunity);
        }
        return filtered;
    }

    private boolean matchesAnalyticsMonth(String rawDate, String month) {
        String value = rawDate == null ? "" : rawDate.trim();
        if (value.startsWith(month)) return true;
        String[] parts = value.split("[./-]");
        if (parts.length < 3) return false;
        try {
            return String.format(Locale.US, "%04d-%02d",
                    Integer.parseInt(parts[2].substring(0, 4)), Integer.parseInt(parts[1])).equals(month);
        } catch (Exception ignored) {
            return false;
        }
    }

    private List<AiDaySummary> aiDaySummaries(String month, boolean completedDaysOnly) {
        Map<String, AiDaySummary> grouped = new LinkedHashMap<>();
        String today = belgradeToday();
        for (AiPick pick : aiPicks) {
            String date = normalizeAiDate(pick.getDate());
            if (date.isEmpty()) continue;
            if (!"all".equals(month) && !date.startsWith(month)) continue;
            if (completedDaysOnly && date.compareTo(today) >= 0) continue;
            AiDaySummary day = grouped.get(date);
            if (day == null) {
                day = new AiDaySummary(date);
                grouped.put(date, day);
            }
            day.picks.add(pick);
        }
        List<AiDaySummary> summaries = new ArrayList<>(grouped.values());
        Collections.sort(summaries, new Comparator<AiDaySummary>() {
            @Override
            public int compare(AiDaySummary left, AiDaySummary right) {
                return right.date.compareTo(left.date);
            }
        });
        return summaries;
    }

    private AiPick mvpPick(AiDaySummary day) {
        for (AiPick pick : day.picks) {
            if (pick.isMvp()) return pick;
        }
        return null;
    }

    private double combinedAiOdds(AiDaySummary day) {
        if (day.picks.isEmpty()) return 0.0;
        double odds = 1.0;
        for (AiPick pick : day.picks) {
            if (pick.getOdds() <= 1.0) return 0.0;
            odds *= pick.getOdds();
        }
        return odds;
    }

    private String parlayResult(AiDaySummary day) {
        if (day.picks.size() < 3) return "PENDING";
        boolean pending = false;
        for (AiPick pick : day.picks) {
            String result = pick.getResult();
            if ("LOSS".equalsIgnoreCase(result)) return "LOSS";
            if (!"WIN".equalsIgnoreCase(result) && !"VOID".equalsIgnoreCase(result)) pending = true;
        }
        return pending ? "PENDING" : "WIN";
    }

    private double parlayProfitUnits(AiDaySummary day) {
        String result = parlayResult(day);
        if ("LOSS".equals(result)) return -1.0;
        if (!"WIN".equals(result)) return 0.0;
        double settledOdds = 1.0;
        for (AiPick pick : day.picks) {
            if ("VOID".equalsIgnoreCase(pick.getResult())) continue;
            if (pick.getOdds() <= 1.0) return 0.0;
            settledOdds *= pick.getOdds();
        }
        return settledOdds - 1.0;
    }

    private double mvpProfitUnits(AiDaySummary day) {
        AiPick mvp = mvpPick(day);
        if (mvp == null) return 0.0;
        if ("WIN".equalsIgnoreCase(mvp.getResult())) return Math.max(0.0, mvp.getOdds() - 1.0);
        if ("LOSS".equalsIgnoreCase(mvp.getResult())) return -1.0;
        return 0.0;
    }

    private boolean isSettledAiResult(String result) {
        return "WIN".equalsIgnoreCase(result)
                || "LOSS".equalsIgnoreCase(result)
                || "VOID".equalsIgnoreCase(result);
    }

    private int aiResultColor(String result) {
        if ("WIN".equalsIgnoreCase(result)) return R.color.accent;
        if ("LOSS".equalsIgnoreCase(result)) return R.color.danger;
        return R.color.muted;
    }

    private String aiOddsLabel(double odds) {
        return odds > 1.0 ? String.format(Locale.US, "%.2f", odds) : "-";
    }

    private String formatAiProfit(double units) {
        return String.format(Locale.US, "%+.2f%%", units * AI_STAKE_PERCENT);
    }

    private String mvpStreakLabel() {
        List<AiPick> mvps = new ArrayList<>();
        for (AiPick pick : aiPicks) {
            if (pick.isMvp() && isSettledAiResult(pick.getResult())) mvps.add(pick);
        }
        Collections.sort(mvps, new Comparator<AiPick>() {
            @Override
            public int compare(AiPick left, AiPick right) {
                return normalizeAiDate(right.getDate()).compareTo(normalizeAiDate(left.getDate()));
            }
        });
        if (mvps.isEmpty()) return "Nema zavrsenih MVP tipova";
        StringBuilder streak = new StringBuilder();
        int limit = Math.min(5, mvps.size());
        for (int index = 0; index < limit; index++) {
            if (index > 0) streak.append("   ");
            String result = mvps.get(index).getResult();
            streak.append("WIN".equalsIgnoreCase(result) ? "✓" : "✕");
        }
        return streak.toString();
    }

    private void addAiAnalyticsContent() {
        List<AiDaySummary> days = aiDaySummaries(analyticsMonth, false);
        double parlayUnits = 0.0;
        double mvpUnits = 0.0;
        int parlayWins = 0;
        int parlayLosses = 0;
        int mvpWins = 0;
        int mvpLosses = 0;
        int pendingBets = 0;
        for (AiDaySummary day : days) {
            String parlay = parlayResult(day);
            if ("WIN".equals(parlay)) {
                parlayWins++;
                parlayUnits += parlayProfitUnits(day);
            } else if ("LOSS".equals(parlay)) {
                parlayLosses++;
                parlayUnits -= 1.0;
            } else {
                pendingBets++;
            }
            AiPick mvp = mvpPick(day);
            String mvpResult = mvp == null ? "PENDING" : mvp.getResult();
            if ("WIN".equalsIgnoreCase(mvpResult)) {
                mvpWins++;
                mvpUnits += mvpProfitUnits(day);
            } else if ("LOSS".equalsIgnoreCase(mvpResult)) {
                mvpLosses++;
                mvpUnits -= 1.0;
            } else if (!"VOID".equalsIgnoreCase(mvpResult)) {
                pendingBets++;
            }
        }
        double totalUnits = parlayUnits + mvpUnits;
        double profitPercent = totalUnits * AI_STAKE_PERCENT;
        int settledBets = parlayWins + parlayLosses + mvpWins + mvpLosses;
        double roi = settledBets == 0 ? 0.0 : totalUnits * 100.0 / settledBets;

        LinearLayout hero = card();
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(16), dp(16), dp(16), dp(16));
        LinearLayout heroLabel = new LinearLayout(this);
        heroLabel.setGravity(Gravity.CENTER_VERTICAL);
        heroLabel.addView(text("Promena bankrolla", 13, R.color.muted, Typeface.NORMAL), weightedParams());
        heroLabel.addView(rightText(analyticsPeriodLabel(), 10, R.color.muted, Typeface.BOLD));
        hero.addView(heroLabel);
        String profit = String.format(Locale.US, "%+.2f%%", profitPercent);
        hero.addView(text(profit, 32,
                profitPercent >= 0 ? R.color.accent : R.color.danger, Typeface.BOLD));
        hero.addView(text(days.size() + " dnevnih tiketa  ·  ROI "
                + String.format(Locale.US, "%+.1f%%", roi),
                11, R.color.muted, Typeface.NORMAL));
        hero.addView(text("Baza: 1% bankrolla na parlay + 1% na MVP single",
                10, R.color.muted, Typeface.NORMAL), topParams(3));
        content.addView(hero, topParams(14));

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.addView(compactMetricCard("ROI", String.format(Locale.US, "%+.1f%%", roi),
                roi >= 0 ? R.color.accent : R.color.danger), weightedParams());
        LinearLayout.LayoutParams center = weightedParams();
        center.leftMargin = dp(8);
        row1.addView(compactMetricCard("RESENO", String.valueOf(settledBets), R.color.ink), center);
        LinearLayout.LayoutParams pending = weightedParams();
        pending.leftMargin = dp(8);
        row1.addView(compactMetricCard("CEKA", String.valueOf(pendingBets), R.color.ink), pending);
        content.addView(row1, topParams(10));

        LinearLayout systems = new LinearLayout(this);
        systems.setOrientation(LinearLayout.HORIZONTAL);
        systems.addView(compactMetricCard("PARLAY · " + parlayWins + "W/" + parlayLosses + "L",
                formatAiProfit(parlayUnits), parlayUnits >= 0 ? R.color.accent : R.color.danger), weightedParams());
        LinearLayout.LayoutParams mvpParams = weightedParams();
        mvpParams.leftMargin = dp(8);
        systems.addView(compactMetricCard("MVP · " + mvpWins + "W/" + mvpLosses + "L",
                formatAiProfit(mvpUnits), mvpUnits >= 0 ? R.color.orange : R.color.danger), mvpParams);
        content.addView(systems, topParams(10));

        LinearLayout streak = card();
        streak.setOrientation(LinearLayout.VERTICAL);
        streak.setPadding(dp(14), dp(12), dp(14), dp(12));
        streak.addView(text("POSLEDNJIH 5 MVP REZULTATA", 9, R.color.muted, Typeface.NORMAL));
        TextView streakValue = text(mvpStreakLabel(), 20, R.color.orange, Typeface.BOLD);
        streakValue.setPadding(0, dp(7), 0, 0);
        streak.addView(streakValue);
        content.addView(streak, topParams(10));
    }

    private AiPickAnalytics loadAiAnalytics() {
        SharedPreferences prefs = getSharedPreferences(AI_STATS_PREFS, Context.MODE_PRIVATE);
        return new AiPickAnalytics(
                prefs.getInt("total", 0), prefs.getInt("settled", 0), prefs.getInt("pending", 0),
                prefs.getInt("wins", 0), prefs.getInt("losses", 0), prefs.getInt("voids", 0),
                Double.longBitsToDouble(prefs.getLong("win_rate", Double.doubleToLongBits(0))),
                Double.longBitsToDouble(prefs.getLong("roi", Double.doubleToLongBits(0))),
                Double.longBitsToDouble(prefs.getLong("profit", Double.doubleToLongBits(0))),
                Double.longBitsToDouble(prefs.getLong("mvp_win_rate", Double.doubleToLongBits(0))),
                Double.longBitsToDouble(prefs.getLong("mvp_profit", Double.doubleToLongBits(0)))
        );
    }

    private void saveAiAnalytics(AiPickAnalytics analytics) {
        getSharedPreferences(AI_STATS_PREFS, Context.MODE_PRIVATE).edit()
                .putInt("total", analytics.getTotal()).putInt("settled", analytics.getSettled())
                .putInt("pending", analytics.getPending()).putInt("wins", analytics.getWins())
                .putInt("losses", analytics.getLosses()).putInt("voids", analytics.getVoids())
                .putLong("win_rate", Double.doubleToRawLongBits(analytics.getWinRate()))
                .putLong("roi", Double.doubleToRawLongBits(analytics.getRoi()))
                .putLong("profit", Double.doubleToRawLongBits(analytics.getProfitUnits()))
                .putLong("mvp_win_rate", Double.doubleToRawLongBits(analytics.getMvpWinRate()))
                .putLong("mvp_profit", Double.doubleToRawLongBits(analytics.getMvpProfitUnits()))
                .apply();
    }

    private LinearLayout metricCard(String label, String value, int valueColor) {
        LinearLayout card = card();
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(16), dp(14), dp(16));
        card.addView(text(label, 12, R.color.muted, Typeface.NORMAL));
        TextView metric = text(value, 23, valueColor, Typeface.BOLD);
        metric.setPadding(0, dp(8), 0, 0);
        card.addView(metric);
        return card;
    }

    private LinearLayout compactMetricCard(String label, String value, int valueColor) {
        LinearLayout card = card();
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(10), dp(11), dp(10), dp(11));
        TextView labelView = text(label, 9, R.color.muted, Typeface.NORMAL);
        labelView.setSingleLine(false);
        card.addView(labelView);
        TextView valueView = text(value, 16, valueColor, Typeface.BOLD);
        valueView.setPadding(0, dp(5), 0, 0);
        card.addView(valueView);
        return card;
    }

    private String analyticsPeriodLabel() {
        if ("all".equals(analyticsMonth)) return "SVE VREME";
        try {
            Date date = new SimpleDateFormat("yyyy-MM", Locale.US).parse(analyticsMonth);
            if (date != null) {
                return new SimpleDateFormat("MMM yyyy", new Locale("sr", "RS"))
                        .format(date).replace(".", "").toUpperCase();
            }
        } catch (Exception ignored) { }
        return analyticsMonth;
    }

    private void addPageTitle(String title) {
        TextView view = text(title, 20, R.color.ink, Typeface.BOLD);
        view.setGravity(Gravity.CENTER);
        content.addView(view, topParams(2));
    }

    private void addSyncStatus() {
        syncStatus = text("", 11, R.color.muted, Typeface.NORMAL);
        syncStatus.setGravity(Gravity.CENTER);
        content.addView(syncStatus, topParams(12));
    }

    private LinearLayout bottomNavigation() {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(8), dp(5), dp(8), dp(7));
        nav.setBackgroundColor(color(R.color.panel));
        return nav;
    }

    private void updateNavigation() {
        navigation.removeAllViews();
        navigation.addView(navItem(R.drawable.nav_search, "Prilike", "opportunities"), weightedParams());
        navigation.addView(navItem(R.drawable.nav_chart, "Graf", "graph"), weightedParams());
        navigation.addView(navItem(R.drawable.nav_star, "Sacuvano", "saved"), weightedParams());
        navigation.addView(navItem(R.drawable.nav_history, "Istorija", "history"), weightedParams());
        navigation.addView(navItem(R.drawable.nav_chart, "Analitika", "analytics"), weightedParams());
        navigation.addView(navItem(R.drawable.nav_wallet, "Kapital", "capital"), weightedParams());
    }

    private LinearLayout navItem(int iconRes, String label, final String target) {
        boolean active = target.equals(screen);
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        item.setPadding(dp(4), 0, dp(4), 0);
        ImageView icon = icon(iconRes, label);
        icon.setColorFilter(color(active ? R.color.accent : R.color.muted));
        item.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24)));
        TextView text = text(label, 9, active ? R.color.accent : R.color.muted, Typeface.NORMAL);
        text.setGravity(Gravity.CENTER);
        text.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        text.setPadding(0, dp(2), 0, 0);
        item.addView(text, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        item.setOnClickListener(v -> {
            screen = target;
            render();
            if ("graph".equals(target)) refreshScanStats(false);
        });
        return item;
    }

    private LinearLayout bookmakerLine(String rawBookmaker) {
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.VERTICAL);
        line.setGravity(Gravity.CENTER);
        int logo = bookmakerLogo(rawBookmaker);
        if (logo != 0) {
            ImageView image = icon(logo, displayBookmaker(rawBookmaker));
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            if (isDomesticBookmakerLogo(logo)) {
                image.setBackgroundResource(R.drawable.bookmaker_logo_plate);
                image.setPadding(dp(4), dp(3), dp(4), dp(3));
                line.addView(image, new LinearLayout.LayoutParams(dp(72), dp(32)));
            } else {
                line.addView(image, new LinearLayout.LayoutParams(dp(58), dp(26)));
            }
        }
        TextView name = text(displayBookmaker(rawBookmaker), 12, R.color.ink, Typeface.BOLD);
        name.setGravity(Gravity.CENTER);
        if (logo != 0) {
            name.setPadding(0, dp(3), 0, 0);
        }
        line.addView(name, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return line;
    }

    private LinearLayout bookmakerLine(ArbitrageOutcome outcome) {
        LinearLayout line = bookmakerLine(outcome.getBookmaker());
        String url = localizedBookmakerUrl(outcome.getBookmaker(), outcome.getUrl());
        if (!url.isEmpty()) {
            line.setClickable(true);
            line.setFocusable(true);
            line.setContentDescription("Otvori kvotu u " + displayBookmaker(outcome.getBookmaker()));
            line.setOnClickListener(v -> openBookmakerUrl(url));
        }
        return line;
    }

    private String localizedBookmakerUrl(String bookmaker, String rawUrl) {
        String url = rawUrl == null ? "" : rawUrl.trim();
        String lowerBookmaker = bookmaker == null ? "" : bookmaker.toLowerCase(Locale.US);
        if (url.startsWith("http://") || url.startsWith("https://")) {
            try {
                Uri parsed = Uri.parse(url);
                String host = parsed.getHost() == null ? "" : parsed.getHost().toLowerCase(Locale.US);
                String domain = lowerBookmaker.contains("superbet") || host.contains("superbet")
                        ? "superbet.rs" : lowerBookmaker.contains("1xbet") || host.contains("1xbet")
                        ? "1xbet.rs" : "";
                if (!domain.isEmpty()) {
                    Uri.Builder builder = parsed.buildUpon().scheme("https").authority(domain);
                    return builder.build().toString();
                }
            } catch (Exception ignored) {
                // Fall through to the bookmaker homepage.
            }
        }
        if (lowerBookmaker.contains("superbet")) return "https://superbet.rs";
        if (lowerBookmaker.contains("1xbet")) return "https://1xbet.rs";
        return url;
    }

    private void openBookmakerUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception exception) {
            Toast.makeText(this, "Ne mogu da otvorim sajt kladionice", Toast.LENGTH_SHORT).show();
        }
    }

    private int bookmakerLogo(String bookmaker) {
        String normalized = displayBookmaker(bookmaker).toLowerCase(Locale.US).replace(" ", "");
        if ("1xbet".equals(normalized)) return R.drawable.xbet_logo;
        if ("superbet".equals(normalized)) return R.drawable.superbet_logo;
        if ("maxbet".equals(normalized)) return R.drawable.maxbet_logo;
        if ("soccerbet".equals(normalized)) return R.drawable.soccerbet_logo;
        if ("merkurxtip".equals(normalized)) return R.drawable.merkurxtip_logo;
        return 0;
    }

    private boolean isDomesticBookmakerLogo(int logo) {
        return logo == R.drawable.maxbet_logo
                || logo == R.drawable.soccerbet_logo
                || logo == R.drawable.merkurxtip_logo;
    }

    private String displayBookmaker(String bookmaker) {
        String value = bookmaker == null ? "" : bookmaker.trim();
        String normalized = value.toLowerCase(Locale.US).replace(" ", "").replace("-", "");
        if (normalized.equals("1xbet")) return "1xBet";
        if (normalized.equals("superbet")) return "Superbet";
        if (normalized.equals("maxbet")) return "MaxBet";
        if (normalized.equals("soccerbet")) return "SoccerBet";
        if (normalized.equals("merkurxtip") || normalized.equals("xtip")) return "MerkurXtip";
        if (normalized.equals("meridian") || normalized.equals("meridianbet")) return "Meridianbet";
        if (normalized.equals("mozzart") || normalized.equals("mozzartbet")) return "Mozzart";
        if (value.isEmpty() || normalized.equals("booka") || normalized.equals("bookb")) return "Nepoznata";
        return value;
    }

    private boolean isSaved(ArbitrageOpportunity opportunity) {
        return savedKeys().contains(opportunityKey(opportunity));
    }

    private void toggleSaved(ArbitrageOpportunity opportunity) {
        Set<String> keys = new HashSet<>(savedKeys());
        String key = opportunityKey(opportunity);
        if (!keys.add(key)) {
            keys.remove(key);
        }
        savedPreferences.edit().putStringSet(SAVED_KEYS, keys).apply();
    }

    private Set<String> savedKeys() {
        return new HashSet<>(savedPreferences.getStringSet(SAVED_KEYS, new HashSet<>()));
    }

    private String opportunityKey(ArbitrageOpportunity opportunity) {
        return opportunity.getMatch() + "|" + opportunity.getMarket() + "|" + opportunity.getSourceLabel();
    }

    private String bestRoiLabel() {
        double best = 0;
        for (ArbitrageOpportunity opportunity : opportunities) {
            best = Math.max(best, opportunity.getProfitPercent());
        }
        return percent(best);
    }

    private String percent(double value) {
        return String.format(Locale.US, "+%.2f%%", value * 100);
    }

    private String marketLabel(ArbitrageOpportunity opportunity) {
        String league = opportunity.getLeague().isEmpty() ? "Football" : opportunity.getLeague();
        return league + "  |  " + opportunity.getMarket();
    }

    private List<ArbitrageOutcome> firstOutcomes(ArbitrageOpportunity opportunity, int limit) {
        List<ArbitrageOutcome> out = new ArrayList<>();
        for (ArbitrageOutcome outcome : opportunity.getOutcomes()) {
            out.add(outcome);
            if (out.size() == limit) break;
        }
        return out;
    }

    private double parseMoney(String value, double fallback) {
        try {
            return Double.parseDouble(value.replace(",", ".").trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String wholeMoney(double value) {
        return String.format(Locale.US, "%,d", Math.round(value)).replace(",", ".");
    }

    private String formatEventDate(String rawValue) {
        String raw = rawValue == null ? "" : rawValue.trim();
        if (raw.isEmpty()) {
            return "";
        }
        if (raw.matches("\\d{4}-\\d{2}-\\d{2}")) {
            try {
                Date date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(raw);
                return date == null ? raw : new SimpleDateFormat("dd.MM.yyyy.", new Locale("sr", "RS")).format(date);
            } catch (Exception ignored) {
                return raw;
            }
        }
        String[] patterns = {
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd HH:mm:ss"
        };
        for (String pattern : patterns) {
            try {
                SimpleDateFormat parser = new SimpleDateFormat(pattern, Locale.US);
                parser.setLenient(false);
                if (pattern.endsWith("'Z'")) {
                    parser.setTimeZone(TimeZone.getTimeZone("UTC"));
                }
                Date date = parser.parse(raw);
                if (date == null) {
                    continue;
                }
                SimpleDateFormat output = new SimpleDateFormat("dd.MM.yyyy.  HH:mm", new Locale("sr", "RS"));
                output.setTimeZone(TimeZone.getDefault());
                return output.format(date);
            } catch (Exception ignored) {
            }
        }
        return raw;
    }

    private LinearLayout emptyCard(String title, String subtitle) {
        LinearLayout card = card();
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(20), dp(28), dp(20), dp(28));
        TextView titleView = text(title, 17, R.color.ink, Typeface.BOLD);
        titleView.setGravity(Gravity.CENTER);
        card.addView(titleView);
        TextView body = text(subtitle, 13, R.color.muted, Typeface.NORMAL);
        body.setGravity(Gravity.CENTER);
        body.setPadding(0, dp(8), 0, 0);
        card.addView(body);
        return card;
    }

    private TextView sectionTitle(String value) {
        return text(value, 13, R.color.muted, Typeface.BOLD);
    }

    private TextView actionButton(String label, View.OnClickListener listener) {
        TextView button = text(label, 14, android.R.color.white, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(14), dp(13), dp(14), dp(13));
        button.setBackgroundResource(R.drawable.green_button_bg);
        button.setOnClickListener(listener);
        return button;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setBackgroundResource(R.drawable.card_bg);
        return card;
    }

    private ImageView icon(int resource, String description) {
        ImageView image = new ImageView(this);
        image.setImageResource(resource);
        image.setContentDescription(description);
        image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        return image;
    }

    private TextView text(String value, int size, int colorId, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color(colorId));
        view.setTypeface(Typeface.DEFAULT, style);
        return view;
    }

    private TextView rightText(String value, int size, int colorId, int style) {
        TextView view = text(value, size, colorId, style);
        view.setGravity(Gravity.RIGHT);
        return view;
    }

    private void addDivider(LinearLayout parent, int top, int bottom) {
        View divider = new View(this);
        divider.setBackgroundColor(color(R.color.line));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        params.setMargins(0, dp(top), 0, dp(bottom));
        parent.addView(divider, params);
    }

    private LinearLayout.LayoutParams blockParams() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams weightedParams() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    private LinearLayout.LayoutParams topParams(int margin) {
        LinearLayout.LayoutParams params = blockParams();
        params.topMargin = dp(margin);
        return params;
    }

    private int color(int resource) {
        return getResources().getColor(resource);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void setStatus(String value) {
        if (syncStatus != null) {
            syncStatus.setText(value);
        }
    }
}
