package id.vanderleinz.lumo;

import android.content.Context;
import android.content.SharedPreferences;

final class NotificationPreferences {
    static final String QUIET = "quiet";
    static final String APP_PREFIX = "preview.";
    static final int OFF = 0;
    static final int ICON = 1;
    static final int SENDER = 2;
    static final int MESSAGE = 3;
    static final String[] LABELS = {"Sembunyikan", "Ikon saja", "Nama pengirim", "Nama dan pesan"};

    private NotificationPreferences() { }

    static SharedPreferences get(Context context) {
        return context.getSharedPreferences("notification_preferences", Context.MODE_PRIVATE);
    }

    static int previewFor(SharedPreferences preferences, String packageName) {
        if (preferences.getBoolean(QUIET, false)) return OFF;
        return appPreview(preferences, packageName);
    }

    static int appPreview(SharedPreferences preferences, String packageName) {
        int mode = preferences.getInt(APP_PREFIX + packageName, MESSAGE);
        return mode >= OFF && mode <= MESSAGE ? mode : MESSAGE;
    }
}
