package com.mkdziner.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Sends the remote's Home button to this launcher on remotes that emit KEYCODE_ALL_APPS for Home
 * (Xiaomi sticks and boxes). Android TV 9 routes that key only to the system-image launcher, so once
 * the stock launcher is disabled the button does nothing. Every other key passes through untouched.
 *
 * Enable over adb:
 *   settings put secure enabled_accessibility_services com.mkdziner.tvhome/.HomeKeyService
 *   settings put secure accessibility_enabled 1
 */
public class HomeKeyService extends AccessibilityService {
    // Also carries out the choices made in Launcher settings → Remote buttons (see RemoteKeys).

    /** Remote buttons that should open Settings (config.xml, settings_key_scancodes and settings_key_keycodes). */
    private int[] settingsScancodes = new int[0];
    private int[] settingsKeycodes = new int[0];
    private boolean settingsPanel;
    private ForegroundWatch watch;
    private final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        watch = new ForegroundWatch(this);
        settingsScancodes = getResources().getIntArray(R.array.settings_key_scancodes);
        // Keycodes and the panel choice can also come from a theme; theme.sh restarts this service.
        Config config = Config.load(this);
        settingsKeycodes = config.settingsKeycodes;
        settingsPanel = config.settingsKeyPanel;
    }

    private boolean isSettingsKey(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_SETTINGS) return true;
        for (int code : settingsScancodes) if (event.getScanCode() == code) return true;
        for (int code : settingsKeycodes) if (event.getKeyCode() == code) return true;
        return false;
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        // The Remote buttons screen is waiting for a press: it takes this one.
        RemoteKeys.Capture capture = RemoteKeys.capture;
        if (capture != null) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                int id = RemoteKeys.id(event);
                String label = RemoteKeys.label(event);
                main.post(() -> capture.onButton(id, label));
            }
            return !RemoteKeys.locked(event.getKeyCode());   // arrows, OK and Back keep working on that screen
        }
        // A button the owner changed in Launcher settings → Remote buttons.
        if (!RemoteKeys.locked(event.getKeyCode())) {
            String action = RemoteKeys.action(this, RemoteKeys.id(event));
            if (action != null) {
                if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) RemoteKeys.run(this, action);
                return true;
            }
        }
        // The stock launcher handled the remote's Settings button; with it disabled the button does nothing.
        if (isSettingsKey(event)) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                Intent open = settingsPanel ? new Intent(this, QuickSettingsActivity.class)
                        : new Intent(Settings.ACTION_SETTINGS);
                startActivity(open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
            return true;
        }
        if (event.getKeyCode() != KeyEvent.KEYCODE_ALL_APPS) return false;
        if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
            Intent home = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .setPackage(getPackageName())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(home);
        }
        return true;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (watch != null && event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) watch.onWindow(event.getPackageName());
    }

    @Override
    public void onDestroy() {
        if (watch != null) watch.stop();
        super.onDestroy();
    }

    @Override
    public void onInterrupt() {
    }
}
