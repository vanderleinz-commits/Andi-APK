package id.vanderleinz.lumo;

import android.app.KeyguardManager;
import android.app.Notification;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.content.pm.ApplicationInfo;
import android.graphics.drawable.Drawable;
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.List;

public final class PhoneEvents extends NotificationListenerService {
    private static PhoneEvents connected;
    private static FloatingPetService overlay;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<MediaController> controllers = new ArrayList<>();
    private MediaSessionManager manager;
    private MediaController selected;
    private final java.util.LinkedHashMap<String, RecentMessage> recentMessages = new java.util.LinkedHashMap<>();

    private static final class RecentMessage {
        final String body;
        final long time;

        RecentMessage(String body, long time) {
            this.body = body;
            this.time = time;
        }
    }

    private final MediaController.Callback changes = new MediaController.Callback() {
        @Override public void onMetadataChanged(MediaMetadata value) { publishMusic(); }
        @Override public void onQueueChanged(List<MediaSession.QueueItem> value) { publishMusic(); }
        @Override public void onPlaybackStateChanged(PlaybackState value) { publishMusic(); }
        @Override public void onSessionDestroyed() { publishMusic(); }
    };

    private final MediaSessionManager.OnActiveSessionsChangedListener sessions = this::replaceControllers;

    static void attach(FloatingPetService service) {
        overlay = service;
        if (connected != null) connected.publishMusic();
    }

    static void detach(FloatingPetService service) {
        if (overlay == service) overlay = null;
    }

    @Override public void onListenerConnected() {
        connected = this;
        manager = getSystemService(MediaSessionManager.class);
        ComponentName component = new ComponentName(this, PhoneEvents.class);
        try {
            manager.addOnActiveSessionsChangedListener(sessions, component, main);
            replaceControllers(manager.getActiveSessions(component));
        } catch (SecurityException ignored) {
            clearControllers();
        }
    }

    @Override public void onListenerDisconnected() { disconnect(); }
    @Override public void onDestroy() { disconnect(); super.onDestroy(); }

    private void disconnect() {
        if (manager != null) manager.removeOnActiveSessionsChangedListener(sessions);
        manager = null;
        recentMessages.clear();
        selected = null;
        clearControllers();
        if (connected == this) {
            connected = null;
            if (overlay != null) {
                overlay.clearAlert();
                overlay.showMusic(null, null, null, null, false, 0);
            }
        }
    }

    private void clearControllers() {
        for (MediaController controller : controllers) controller.unregisterCallback(changes);
        controllers.clear();
    }

    private void replaceControllers(List<MediaController> active) {
        clearControllers();
        if (active != null) {
            controllers.addAll(active);
            for (MediaController controller : controllers) controller.registerCallback(changes, main);
        }
        publishMusic();
    }

    @Override public void onNotificationPosted(StatusBarNotification posted) {
        if (overlay == null || posted.getPackageName().equals(getPackageName())) return;
        Notification notification = posted.getNotification();
        if (Notification.CATEGORY_TRANSPORT.equals(notification.category)
                || notification.visibility == Notification.VISIBILITY_SECRET
                || getSystemService(KeyguardManager.class).isKeyguardLocked()) return;
        String source = "aplikasi lain";
        Drawable appIcon = null;
        PackageManager packages = getPackageManager();
        try {
            ApplicationInfo app = packages.getApplicationInfo(posted.getPackageName(), 0);
            source = packages.getApplicationLabel(app).toString();
            appIcon = packages.getApplicationIcon(app);
        } catch (PackageManager.NameNotFoundException ignored) { }
        boolean whatsApp = "com.whatsapp".equals(posted.getPackageName())
                || "com.whatsapp.w4b".equals(posted.getPackageName());
        if (appIcon == null && whatsApp) appIcon = getDrawable(R.drawable.ic_whatsapp_fallback);
        if (appIcon == null && notification.getSmallIcon() != null) {
            try {
                appIcon = notification.getSmallIcon().loadDrawable(this);
            } catch (RuntimeException ignored) { }
        }
        CharSequence body = "";
        CharSequence sender = null;
        try {
            Notification.MessagingStyle.Message message = latestMessage(notification.extras);
            body = message == null ? notificationBody(notification) : message.getText();
            sender = senderName(notification, message, body);
        } catch (RuntimeException error) {
            Log.w("AndiNotifications", "Could not decode notification details", error);
        }
        if (((notification.flags & Notification.FLAG_GROUP_SUMMARY) != 0 || posted.isOngoing())
                && body.length() == 0) return;
        String preview = boundedPreview(body, 180);
        String senderPreview = sender == null ? "" : boundedPreview(sender, 70);
        String fingerprint = senderPreview + "\n" + preview;
        String key = posted.getKey();
        long now = android.os.SystemClock.elapsedRealtime();
        RecentMessage previous = recentMessages.get(key);
        if (previous != null && previous.body.equals(fingerprint) && now - previous.time < 1800) return;
        recentMessages.put(key, new RecentMessage(fingerprint, now));
        if (recentMessages.size() > 32) recentMessages.remove(recentMessages.keySet().iterator().next());
        overlay.showAlert(appIcon, source, senderPreview, preview);
    }

    private Notification.MessagingStyle.Message latestMessage(Bundle extras) {
        if (android.os.Build.VERSION.SDK_INT < 30 || extras == null) return null;
        android.os.Parcelable[] parcels = extras.getParcelableArray(Notification.EXTRA_MESSAGES);
        if (parcels == null) return null;
        List<Notification.MessagingStyle.Message> messages =
                Notification.MessagingStyle.Message.getMessagesFromBundleArray(parcels);
        for (int index = messages.size() - 1; index >= 0; index--) {
            Notification.MessagingStyle.Message message = messages.get(index);
            if (!android.text.TextUtils.isEmpty(message.getText())) return message;
        }
        return null;
    }

    private CharSequence senderName(Notification notification, Notification.MessagingStyle.Message message,
                                    CharSequence body) {
        if (android.os.Build.VERSION.SDK_INT >= 30 && message != null) {
            android.app.Person sender = message.getSenderPerson();
            if (sender != null && !android.text.TextUtils.isEmpty(sender.getName())) return sender.getName();
        }
        Bundle extras = notification.extras;
        if (extras == null || (notification.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return null;
        boolean conversation = message != null || Notification.CATEGORY_MESSAGE.equals(notification.category)
                || extras.containsKey(Notification.EXTRA_MESSAGES)
                || extras.containsKey(Notification.EXTRA_CONVERSATION_TITLE);
        if (!conversation) return null;
        CharSequence title = extras.getCharSequence(Notification.EXTRA_TITLE);
        return android.text.TextUtils.equals(title, body) ? null : title;
    }

    private CharSequence notificationBody(Notification notification) {
        Bundle extras = notification.extras;
        if (extras == null) return "";
        boolean summary = (notification.flags & Notification.FLAG_GROUP_SUMMARY) != 0;
        if (!summary) {
            CharSequence expanded = extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
            if (expanded != null && expanded.length() > 0) return expanded;
            CharSequence text = extras.getCharSequence(Notification.EXTRA_TEXT);
            if (text != null && text.length() > 0) return text;
        }
        CharSequence[] lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        if (lines != null) {
            for (int index = lines.length - 1; index >= 0; index--) {
                if (lines[index] != null && lines[index].length() > 0) return lines[index];
            }
        }
        if (summary) return "";
        CharSequence title = extras.getCharSequence(Notification.EXTRA_TITLE);
        return title == null ? "" : title;
    }

    private String boundedPreview(CharSequence text, int limit) {
        return text.subSequence(0, Math.min(text.length(), limit)).toString();
    }

    private void publishMusic() {
        try {
            refreshMusic();
        } catch (RuntimeException error) {
            Log.w("AndiMusic", "Could not read the current media session", error);
        }
    }

    private void refreshMusic() {
        if (overlay == null || connected != this) return;
        MediaController active = firstSession(PlaybackState.STATE_PLAYING);
        if (active == null && selected != null && controllers.contains(selected)
                && stateOf(selected) == PlaybackState.STATE_PAUSED) active = selected;
        selected = active != null ? active : firstSession(PlaybackState.STATE_PAUSED);
        if (selected != null) {
            MediaController controller = selected;
            PlaybackState state = controller.getPlaybackState();
            MediaMetadata metadata = controller.getMetadata();
            CharSequence title = metadata == null ? null : metadata.getDescription().getTitle();
            if (state != null && title != null && title.length() > 0) {
                publishTrack(controller, state, metadata);
                return;
            }
        }
        overlay.showMusic(null, null, null, null, false, 0);
    }

    private MediaController firstSession(int targetState) {
        for (MediaController controller : controllers) {
            MediaMetadata metadata = controller.getMetadata();
            if (stateOf(controller) == targetState && metadata != null) {
                CharSequence title = metadata.getDescription().getTitle();
                if (title != null && title.length() != 0) return controller;
            }
        }
        return null;
    }

    private int stateOf(MediaController controller) {
        PlaybackState state = controller.getPlaybackState();
        return state == null ? PlaybackState.STATE_NONE : state.getState();
    }

    private void publishTrack(MediaController controller, PlaybackState state, MediaMetadata metadata) {
        MediaDescription description = metadata.getDescription();
        android.graphics.Bitmap art = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (art == null) art = description.getIconBitmap();
        overlay.showMusic(description.getTitle(), description.getSubtitle(), art,
                controller.getQueueTitle(), state.getState() == PlaybackState.STATE_PLAYING, state.getActions());
    }

    static void control(long action) {
        PhoneEvents listener = connected;
        if (listener == null || listener.selected == null) return;
        try {
            listener.sendControl(action);
        } catch (RuntimeException error) {
            Log.w("AndiMusic", "Player rejected the requested control", error);
            listener.publishMusic();
        }
    }

    private void sendControl(long action) {
        MediaController controller = selected;
        PlaybackState state = controller.getPlaybackState();
        if (state == null || (state.getActions() & action) == 0) return;
        MediaController.TransportControls controls = controller.getTransportControls();
        if (action == PlaybackState.ACTION_SKIP_TO_PREVIOUS) {
            controls.skipToPrevious();
        } else if (action == PlaybackState.ACTION_SKIP_TO_NEXT) {
            controls.skipToNext();
        } else {
            boolean playing = state.getState() == PlaybackState.STATE_PLAYING;
            long toggle = playing ? PlaybackState.ACTION_PAUSE : PlaybackState.ACTION_PLAY;
            if ((state.getActions() & toggle) != 0) {
                if (playing) controls.pause();
                else controls.play();
            } else if ((state.getActions() & PlaybackState.ACTION_PLAY_PAUSE) != 0) {
                int key = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
                if (controller.dispatchMediaButtonEvent(new KeyEvent(KeyEvent.ACTION_DOWN, key))) {
                    controller.dispatchMediaButtonEvent(new KeyEvent(KeyEvent.ACTION_UP, key));
                }
            }
        }
    }
}
