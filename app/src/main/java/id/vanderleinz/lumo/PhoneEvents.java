package id.vanderleinz.lumo;

import android.app.KeyguardManager;
import android.app.Notification;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PhoneEvents extends NotificationListenerService {
    private static PhoneEvents connected;
    private static FloatingPetService overlay;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<MediaController> controllers = new ArrayList<>();
    private MediaSessionManager manager;

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
        clearControllers();
        if (connected == this) {
            connected = null;
            if (overlay != null) {
                overlay.clearAlert();
                overlay.showMusic(null, null, null, Collections.emptyList());
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
        if (overlay == null || posted.getPackageName().equals(getPackageName()) || posted.isOngoing()) return;
        Notification notification = posted.getNotification();
        if (Notification.CATEGORY_TRANSPORT.equals(notification.category)
                || notification.visibility == Notification.VISIBILITY_SECRET
                || (notification.flags & Notification.FLAG_GROUP_SUMMARY) != 0
                || getSystemService(KeyguardManager.class).isKeyguardLocked()) return;
        String source = "aplikasi lain";
        PackageManager packages = getPackageManager();
        try {
            source = packages.getApplicationLabel(packages.getApplicationInfo(posted.getPackageName(), 0)).toString();
        } catch (PackageManager.NameNotFoundException ignored) { }
        if (notification.extras == null) {
            overlay.showAlert(source, "");
            return;
        }
        CharSequence title = notification.extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence body = notification.extras.getCharSequence(Notification.EXTRA_TEXT);
        if (body == null || body.length() == 0) {
            body = notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
        }
        String header = source;
        if (title != null && title.length() > 0) {
            header += " · " + boundedPreview(title, 72);
        }
        overlay.showAlert(header, body == null ? "" : boundedPreview(body, 180));
    }

    private String boundedPreview(CharSequence text, int limit) {
        return text.subSequence(0, Math.min(text.length(), limit)).toString();
    }

    private void publishMusic() {
        if (overlay == null || connected != this) return;
        for (MediaController controller : controllers) {
            PlaybackState state = controller.getPlaybackState();
            MediaMetadata metadata = controller.getMetadata();
            if (state == null || state.getState() != PlaybackState.STATE_PLAYING || metadata == null) continue;
            MediaDescription description = metadata.getDescription();
            if (description.getTitle() == null || description.getTitle().length() == 0) continue;
            List<String> queue = new ArrayList<>();
            List<MediaSession.QueueItem> items = controller.getQueue();
            if (items != null) {
                for (MediaSession.QueueItem item : items) {
                    CharSequence title = item.getDescription().getTitle();
                    if (title != null && title.length() > 0) queue.add(title.toString());
                    if (queue.size() == 20) break;
                }
            }
            overlay.showMusic(description.getTitle(), description.getSubtitle(), description.getIconBitmap(), queue);
            return;
        }
        overlay.showMusic(null, null, null, Collections.emptyList());
    }
}
