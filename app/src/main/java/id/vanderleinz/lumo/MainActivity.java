package id.vanderleinz.lumo;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private static final int NAVY = 0xff10192b;
    private static final int WHITE = 0xffedf3ff;
    private static final int MUTED = 0xffb5c2df;
    private static final int COCOA = 0xffe3baa0;
    private Button toggle;
    private TextView status;

    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            renderState();
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(NAVY);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);
        content.setPadding(dp(22), dp(22), dp(22), dp(22));
        scroll.addView(content);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        int width = Math.min(dp(460), Math.max(0, getResources().getDisplayMetrics().widthPixels - dp(44)));
        content.addView(panel, new LinearLayout.LayoutParams(width, -2));

        TextView title = label("Andi", 38, WHITE);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        add(panel, title, -2, 0);
        add(panel, label("Teman kecil di layar kamu", 15, MUTED), -2, 2);

        PetView preview = new PetView(this);
        preview.setBackground(shape(0xff192644, 28));
        preview.setContentDescription("Andi. Ketuk untuk melihat ekspresinya.");
        preview.setOnClickListener(view -> preview.react());
        add(panel, preview, dp(174), 24);

        status = label("", 14, MUTED);
        status.setAccessibilityLiveRegion(TextView.ACCESSIBILITY_LIVE_REGION_POLITE);
        add(panel, status, -2, 16);

        toggle = new Button(this);
        toggle.setAllCaps(false);
        toggle.setTextColor(NAVY);
        toggle.setTextSize(16);
        toggle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        toggle.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x3810192b), shape(COCOA, 18), null));
        toggle.setOnClickListener(view -> toggleOverlay());
        add(panel, toggle, dp(54), 14);

        Button phoneAccess = new Button(this);
        phoneAccess.setAllCaps(false);
        phoneAccess.setText("Akses notifikasi dan musik");
        phoneAccess.setOnClickListener(view -> {
            try {
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            } catch (android.content.ActivityNotFoundException exception) {
                Toast.makeText(this, "Cari Akses notifikasi di Setelan Android.", Toast.LENGTH_LONG).show();
            }
        });
        add(panel, phoneAccess, dp(54), 8);
        add(panel, label("Opsional. Bubble pesan menampilkan nama aplikasi pengirim.", 13, MUTED), -2, 6);

        TextView hint = label("Ketuk untuk menyapa • Geser untuk pindah\nTahan Andi untuk menyembunyikan", 14, MUTED);
        hint.setLineSpacing(dp(3), 1);
        add(panel, hint, -2, 15);
        setContentView(scroll);

        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            scroll.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets safe = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
                return insets;
            });
            scroll.requestApplyInsets();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(FloatingPetService.ACTION_STATE);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(stateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(stateReceiver, filter);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderState();
    }

    @Override
    protected void onStop() {
        unregisterReceiver(stateReceiver);
        super.onStop();
    }

    private void toggleOverlay() {
        Intent service = new Intent(this, FloatingPetService.class);
        if (FloatingPetService.isShowing()) {
            stopService(service);
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.fromParts("package", getPackageName(), null)));
            } catch (android.content.ActivityNotFoundException exception) {
                Toast.makeText(this, "Buka Setelan lalu cari izin tampil di atas aplikasi lain.",
                        Toast.LENGTH_LONG).show();
            }
            return;
        }
        try {
            startForegroundService(service);
            if (Build.VERSION.SDK_INT >= 33
                    && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                android.content.SharedPreferences preferences = getPreferences(MODE_PRIVATE);
                if (!preferences.getBoolean("asked_for_notifications", false)) {
                    preferences.edit().putBoolean("asked_for_notifications", true).apply();
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
                }
            }
        } catch (IllegalStateException | SecurityException exception) {
            Toast.makeText(this, "Andi belum bisa ditampilkan. Coba lagi.", Toast.LENGTH_SHORT).show();
        }
    }

    private void renderState() {
        boolean showing = FloatingPetService.isShowing();
        boolean allowed = Settings.canDrawOverlays(this);
        status.setText(showing ? "Andi sedang melayang" :
                allowed ? "Andi sedang beristirahat" : "Aktifkan izin untuk menampilkan Andi di atas aplikasi lain");
        toggle.setText(showing ? "Sembunyikan Andi" : allowed ? "Tampilkan Andi" : "Buka pengaturan izin");
    }

    private void add(LinearLayout panel, android.view.View view, int height, int topMargin) {
        LinearLayout.LayoutParams entries = new LinearLayout.LayoutParams(-1, height);
        entries.topMargin = dp(topMargin);
        panel.addView(view, entries);
    }

    private TextView label(String text, int size, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER);
        return view;
    }

    private GradientDrawable shape(int color, int radius) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(radius));
        return background;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
