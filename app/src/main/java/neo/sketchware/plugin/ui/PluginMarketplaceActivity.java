package neo.sketchware.plugin.ui;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

/** Backwards-compatible entry point; the main plugin marketplace now lives in ManagePluginsActivity. */
public final class PluginMarketplaceActivity extends AppCompatActivity {
    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startActivity(new Intent(this, ManagePluginsActivity.class));
        finish();
    }
}
