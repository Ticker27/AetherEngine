package com.aether.fixture;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class FixtureReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // The fixture only needs a real manifest-declared receiver for S1 loading tests.
    }
}
