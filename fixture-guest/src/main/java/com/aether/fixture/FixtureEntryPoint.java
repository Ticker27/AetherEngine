package com.aether.fixture;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import com.aether.guest.api.GuestEntryPoint;
import com.aether.guest.api.GuestStorage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** First-party cooperative guest: no framework Activity attachment or native loading. */
public final class FixtureEntryPoint implements GuestEntryPoint {
    private GuestStorage storage;
    private int resumeCount;

    @Override
    public View onCreate(Context hostUiContext, GuestStorage guestStorage, Bundle savedState) {
        storage = guestStorage;
        resumeCount = savedState == null ? 0 : savedState.getInt("fixture.resumeCount", 0);
        record("created");
        if (savedState != null) record("restored:" + resumeCount);
        TextView view = new TextView(hostUiContext);
        view.setText("Aether cooperative fixture started");
        view.setContentDescription("aether-fixture-ready");
        return view;
    }

    private void record(String event) {
        if (storage == null) throw new IllegalStateException("Fixture session is detached");
        try (OutputStream out = storage.openOutput("evidence/lifecycle.txt", true)) {
            out.write((event + "\n").getBytes(StandardCharsets.UTF_8));
        } catch (IOException error) {
            throw new IllegalStateException("Fixture evidence write failed", error);
        }
    }

    @Override public void onStart() { record("started"); }
    @Override public void onResume() { resumeCount++; record("resumed"); }
    @Override public void onPause() { record("paused"); }
    @Override public void onStop() { record("stopped"); }
    @Override public void onSaveInstanceState(Bundle out) {
        out.putInt("fixture.resumeCount", resumeCount);
        record("saved");
    }
    @Override public void onNewIntent(Intent intent) { record("new_intent"); }
    @Override public void onDestroy() {
        try { record("destroyed"); } finally { storage = null; }
    }
}
