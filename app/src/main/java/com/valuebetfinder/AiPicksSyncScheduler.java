package com.valuebetfinder;

import android.os.Handler;
import android.os.Looper;

import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AiPicksSyncScheduler {
    private static final int DAILY_SYNC_HOUR = 9;

    private final AiPicksApiClient apiClient;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Handler scheduleHandler = new Handler(Looper.getMainLooper());

    private Runnable scheduledSync;

    public AiPicksSyncScheduler(AiPicksApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public void start(final AiPicksSyncCallback callback) {
        stop();
        scheduledSync = new Runnable() {
            @Override
            public void run() {
                refresh(callback);
                scheduleHandler.postDelayed(this, delayUntilNextDailySync());
            }
        };
        scheduledSync.run();
    }

    public void refresh(final AiPicksSyncCallback callback) {
        execute(callback, false);
    }

    public void runAiPicks(final AiPicksSyncCallback callback) {
        execute(callback, true);
    }

    public void runAiPicks(final String targetDate, final AiPicksSyncCallback callback) {
        execute(callback, true, false, false, false, targetDate);
    }

    public void updateOdds(final AiPicksSyncCallback callback) {
        execute(callback, false, true);
    }

    public void updateAnalysis(final AiPicksSyncCallback callback) {
        execute(callback, false, false, false, true);
    }

    public void clearAiPicks(final AiPicksSyncCallback callback) {
        execute(callback, false, false, true, false);
    }

    private void execute(final AiPicksSyncCallback callback, final boolean runGenerator) {
        execute(callback, runGenerator, false, false, false);
    }

    private void execute(final AiPicksSyncCallback callback, final boolean runGenerator, final boolean updateOdds) {
        execute(callback, runGenerator, updateOdds, false, false);
    }

    private void execute(final AiPicksSyncCallback callback, final boolean runGenerator, final boolean updateOdds, final boolean clearAiPicks) {
        execute(callback, runGenerator, updateOdds, clearAiPicks, false);
    }

    private void execute(final AiPicksSyncCallback callback, final boolean runGenerator, final boolean updateOdds, final boolean clearAiPicks, final boolean updateAnalysis) {
        execute(callback, runGenerator, updateOdds, clearAiPicks, updateAnalysis, "");
    }

    private void execute(final AiPicksSyncCallback callback, final boolean runGenerator, final boolean updateOdds, final boolean clearAiPicks, final boolean updateAnalysis, final String targetDate) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<AiPick> picks;
                    if (runGenerator) {
                        picks = apiClient.runAiPicksAndFetch(targetDate);
                    } else if (updateOdds) {
                        picks = apiClient.updateOddsAndFetch();
                    } else if (clearAiPicks) {
                        picks = apiClient.clearAiPicksAndFetch();
                    } else if (updateAnalysis) {
                        picks = apiClient.updateAnalysisAndFetch();
                    } else {
                        picks = apiClient.fetchAiPicks();
                    }
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onSuccess(picks);
                        }
                    });
                } catch (final Exception exception) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onError(exception);
                        }
                    });
                }
            }
        });
    }

    public void stop() {
        if (scheduledSync != null) {
            scheduleHandler.removeCallbacks(scheduledSync);
            scheduledSync = null;
        }
    }

    private long delayUntilNextDailySync() {
        Calendar now = Calendar.getInstance();
        Calendar next = Calendar.getInstance();
        next.set(Calendar.HOUR_OF_DAY, DAILY_SYNC_HOUR);
        next.set(Calendar.MINUTE, 0);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        if (!next.after(now)) {
            next.add(Calendar.DAY_OF_YEAR, 1);
        }
        return next.getTimeInMillis() - now.getTimeInMillis();
    }
}
