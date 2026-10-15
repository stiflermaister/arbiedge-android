package com.valuebetfinder;

import android.os.Handler;
import android.os.Looper;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ArbitrageSyncScheduler {
    private static final long ARB_SYNC_INTERVAL_MS = 20L * 60L * 1000L;

    private final ArbitrageApiClient apiClient;
    private final boolean historyMode;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Handler scheduleHandler = new Handler(Looper.getMainLooper());

    private Runnable scheduledSync;

    public ArbitrageSyncScheduler(ArbitrageApiClient apiClient) {
        this(apiClient, false);
    }

    public ArbitrageSyncScheduler(ArbitrageApiClient apiClient, boolean historyMode) {
        this.apiClient = apiClient;
        this.historyMode = historyMode;
    }

    public void start(final ArbitrageSyncCallback callback) {
        stop();
        scheduledSync = new Runnable() {
            @Override
            public void run() {
                refresh(callback);
                scheduleHandler.postDelayed(this, ARB_SYNC_INTERVAL_MS);
            }
        };
        scheduledSync.run();
    }

    public void refresh(final ArbitrageSyncCallback callback) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<ArbitrageOpportunity> opportunities = historyMode
                            ? apiClient.fetchArbitrageHistory()
                            : apiClient.fetchArbitrageOpportunities();
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onSuccess(opportunities);
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

}
