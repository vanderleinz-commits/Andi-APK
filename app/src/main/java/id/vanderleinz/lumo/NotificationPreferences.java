package id.vanderleinz.lumo;

import android.content.Context;
import android.content.SharedPreferences;

final class NotificationPreferences {
    static final String GLASSES = "andi_glasses";
    static final String BUBBLE_MOTION = "bubble_motion";
    static final String SHORTCUTS = "quick_shortcuts";
    static final String LYRICS_CONSENT = "lyrics_consent";
    static final String[] SHORTCUT_LABELS = {"Kalender", "Home", "Senter", "Daya", "Kunci", "Screenshot"};
    static final int ALL_SHORTCUTS = (1 << SHORTCUT_LABELS.length) - 1;
    static final String AUTO_TUCK = "auto_tuck";
    static final String QUIET = "quiet";
    static final String APP_PREFIX = "preview.";
    static final int OFF = 0;
    static final int ICON = 1;
    static final int SENDER = 2;
    static final int MESSAGE = 3;
    static final String[] LABELS = {"Sembunyikan", "Ikon saja", "Nama pengirim (percakapan)", "Pratinjau lengkap"};

    private NotificationPreferences() { }

    static SharedPreferences get(Context context) {
        return context.getSharedPreferences("notification_preferences", Context.MODE_PRIVATE);
    }

    static int appPreview(SharedPreferences preferences, String packageName) {
        int mode = preferences.getInt(APP_PREFIX + packageName, MESSAGE);
        return mode >= OFF && mode <= MESSAGE ? mode : MESSAGE;
    }
}
