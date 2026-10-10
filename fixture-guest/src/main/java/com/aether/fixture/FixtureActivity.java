package com.aether.fixture;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public final class FixtureActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        TextView title = new TextView(this);
        title.setText(R.string.fixture_title);
        setContentView(title);
    }
}
