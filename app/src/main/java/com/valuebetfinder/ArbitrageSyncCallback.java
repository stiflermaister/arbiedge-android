package com.valuebetfinder;

import java.util.List;

public interface ArbitrageSyncCallback {
    void onSuccess(List<ArbitrageOpportunity> opportunities);

    void onError(Exception exception);
}
