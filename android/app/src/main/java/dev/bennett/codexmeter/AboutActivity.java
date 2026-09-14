package dev.bennett.codexmeter;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Compatibility stub for old internal navigation targets.
 * The About screen was removed; diagnostics is now a normal Settings page.
 */
public final class AboutActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        startActivity(SettingsActivity.diagnosticsIntent(this));
        finish();
    }
}
