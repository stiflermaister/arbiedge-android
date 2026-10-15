package com.valuebetfinder;

import java.util.List;

public interface AiPicksSyncCallback {
    void onSuccess(List<AiPick> picks);

    void onError(Exception exception);
}
