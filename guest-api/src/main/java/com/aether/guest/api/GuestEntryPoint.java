package com.aether.guest.api;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

/** Cooperative guest UI hosted by a real host Activity, not arbitrary Activity attachment. */
public interface GuestEntryPoint {
    View onCreate(Context hostUiContext, GuestStorage storage, Bundle savedState);
    void onStart();
    void onResume();
    void onPause();
    void onStop();
    void onSaveInstanceState(Bundle outState);
    void onNewIntent(Intent intent);
    void onDestroy();
}
