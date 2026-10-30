package com.mkdziner.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.KeyEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The owner's remote button choices (Launcher settings → Remote buttons), carried out by {@link HomeKeyService}.
 * Arrows, OK, Back, Home and Power can't be changed: they are how you get back out of a bad choice.
 */
final class RemoteKeys {

    /** Set while the Remote buttons screen is waiting for a press. Runs on the main thread. */
    interface Capture {
        void onButton(int id, String label);
    }

    static final String NONE = "none", HOME = "home", APPS = "apps", INPUTS = "inputs", SETTINGS = "settings",
            PLAY_PAUSE = "playpause", APP = "app:";

    private static final String PREFS = "remote_keys", LABEL = "label_";
    private static final int SCANCODE_BASE = 100_000;

    static volatile Capture capture;

    private RemoteKeys() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Remotes send keycodes; a brand button Android doesn't know arrives with a scan code only. */
    static int id(KeyEvent e) {
        int code = e.getKeyCode();
        return code != KeyEvent.KEYCODE_UNKNOWN ? code : SCANCODE_BASE + e.getScanCode();
    }

    static boolean locked(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
            case KeyEvent.KEYCODE_HOME:
            case KeyEvent.KEYCODE_POWER:
            case KeyEvent.KEYCODE_TV_POWER:
                return true;
            default:
                return false;
        }
    }

    static String label(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.KEYCODE_UNKNOWN) return "Button " + e.getScanCode();
        String s = KeyEvent.keyCodeToString(e.getKeyCode()).replace("KEYCODE_", "").replace('_', ' ').toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    static void set(Context c, int id, String label, String action) {
        prefs(c).edit().putString(String.valueOf(id), action).putString(LABEL + id, label).apply();
    }

    static void clear(Context c, int id) {
        prefs(c).edit().remove(String.valueOf(id)).remove(LABEL + id).apply();
    }

    static String action(Context c, int id) {
        return prefs(c).getString(String.valueOf(id), null);
    }

    /** id → label of every changed button, in a stable order. */
    static Map<Integer, String> all(Context c) {
        Map<Integer, String> out = new LinkedHashMap<>();
        Map<String, ?> m = new java.util.TreeMap<>(prefs(c).getAll());
        for (String k : m.keySet()) {
            if (k.startsWith(LABEL)) continue;
            try {
                int id = Integer.parseInt(k);
                out.put(id, prefs(c).getString(LABEL + id, k));
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    static boolean serviceOn(Context c) {
        String list = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (list == null) return false;
        ComponentName me = new ComponentName(c, HomeKeyService.class);
        return list.contains(me.flattenToString()) || list.contains(me.flattenToShortString());
    }

    /** Carries out a choice. Returns false when there is nothing to run (the app was uninstalled). */
    static boolean run(Context c, String action) {
        Intent i = null;
        switch (action) {
            case NONE:
                return true;
            case HOME:
                i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(c.getPackageName());
                break;
            case APPS:
                i = new Intent(c, MainActivity.class);
                break;
            case INPUTS:
                i = new Intent("com.android.tv.action.VIEW_INPUTS");
                break;
            case SETTINGS:
                i = new Intent(Settings.ACTION_SETTINGS);
                break;
            case PLAY_PAUSE:
                AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
                long t = SystemClock.uptimeMillis();
                am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0));
                am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0));
                return true;
            default:
                if (action.startsWith(APP)) {
                    String pkg = action.substring(APP.length());
                    i = c.getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
                    if (i == null) i = c.getPackageManager().getLaunchIntentForPackage(pkg);
                }
        }
        if (i == null) return false;
        try {
            c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
