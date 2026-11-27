package id.vanderleinz.lumo;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

public final class QuickActionsService extends AccessibilityService {
    private static QuickActionsService connected;

    @Override protected void onServiceConnected() {
        connected = this;
    }

    @Override public boolean onUnbind(android.content.Intent intent) {
        if (connected == this) connected = null;
        return super.onUnbind(intent);
    }

    @Override public void onDestroy() {
        if (connected == this) connected = null;
        super.onDestroy();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { }

    static boolean isConnected() { return connected != null; }

    static boolean run(int action) {
        return connected != null && connected.performGlobalAction(action);
    }
}
