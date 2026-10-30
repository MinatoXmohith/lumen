package com.mkdziner.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Launcher settings → Remote buttons: press a button, pick what it does. */
public class RemoteButtonsActivity extends Activity {

    private static final long CAPTURE_MS = 12_000;

    private float density;
    private LinearLayout col;
    private ScrollView scroll;
    private Typeface semibold, medium, regular;
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean picking;
    private final Runnable captureTimeout = () -> {
        RemoteKeys.capture = null;
        showList();
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        density = getResources().getDisplayMetrics().density;
        semibold = getResources().getFont(R.font.outfit_semibold);
        medium = getResources().getFont(R.font.outfit_medium);
        regular = getResources().getFont(R.font.outfit_regular);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0B0E12);
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int side = dp(180);
        col.setPadding(side, dp(36), side, dp(30));
        scroll.addView(col);
        root.addView(scroll);
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!picking) showList();   // also picks up the service being switched on in Android's settings
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopCapture();
    }

    private void stopCapture() {
        RemoteKeys.capture = null;
        main.removeCallbacks(captureTimeout);
    }

    @Override
    public void onBackPressed() {
        if (picking || RemoteKeys.capture != null) {
            stopCapture();
            picking = false;
            showList();
            return;
        }
        super.onBackPressed();
        overridePendingTransition(0, R.anim.popup_exit);
    }

    private void showList() {
        picking = false;
        col.removeAllViews();
        col.addView(text(getString(R.string.remote_buttons), 28, semibold, 0xFFFFFFFF));
        TextView sub = text(getString(R.string.remote_buttons_hint), 15, regular, 0xB3FFFFFF);
        sub.setPadding(0, dp(2), 0, dp(14));
        col.addView(sub);
        View first;
        if (!RemoteKeys.serviceOn(this)) {
            TextView off = text(getString(R.string.remote_service_off), 15, regular, 0xFFFFBE5C);
            off.setPadding(0, 0, 0, dp(10));
            col.addView(off);
            first = row(getString(R.string.remote_turn_on), null, () -> {
                try {
                    startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                } catch (Exception e) {
                    Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
                }
            });
        } else {
            first = row(getString(R.string.remote_add), null, this::startCapture);
            for (Map.Entry<Integer, String> e : RemoteKeys.all(this).entrySet()) {
                int id = e.getKey();
                String label = e.getValue();
                row(label, describe(RemoteKeys.action(this, id)), () -> showPicker(id, label));
            }
        }
        first.requestFocus();
        scroll.scrollTo(0, 0);
    }

    private void startCapture() {
        col.removeAllViews();
        col.addView(text(getString(R.string.remote_press), 28, semibold, 0xFFFFFFFF));
        TextView sub = text(getString(R.string.remote_press_hint), 15, regular, 0xB3FFFFFF);
        sub.setPadding(0, dp(6), 0, 0);
        col.addView(sub);
        RemoteKeys.capture = (id, label) -> {
            stopCapture();
            if (id < 100_000 && RemoteKeys.locked(id)) {
                Toast.makeText(this, R.string.remote_locked, Toast.LENGTH_LONG).show();
                showList();
                return;
            }
            showPicker(id, label);
        };
        main.postDelayed(captureTimeout, CAPTURE_MS);
    }

    private void showPicker(int id, String label) {
        picking = true;
        col.removeAllViews();
        col.addView(text(label, 28, semibold, 0xFFFFFFFF));
        TextView sub = text(getString(R.string.remote_choose), 15, regular, 0xB3FFFFFF);
        sub.setPadding(0, dp(2), 0, dp(14));
        col.addView(sub);
        View first = choice(id, label, RemoteKeys.HOME);
        choice(id, label, RemoteKeys.APPS);
        choice(id, label, RemoteKeys.INPUTS);
        choice(id, label, RemoteKeys.SETTINGS);
        choice(id, label, RemoteKeys.PLAY_PAUSE);
        choice(id, label, RemoteKeys.NONE);
        if (RemoteKeys.action(this, id) != null) {
            row(getString(R.string.remote_original), null, () -> {
                RemoteKeys.clear(this, id);
                showList();
            });
        }
        PackageManager pm = getPackageManager();
        List<ResolveInfo> apps = new ArrayList<>(pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0));
        Collections.sort(apps, (a, b) -> String.valueOf(a.loadLabel(pm)).compareToIgnoreCase(String.valueOf(b.loadLabel(pm))));
        for (ResolveInfo ri : apps) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;
            choice(id, label, RemoteKeys.APP + pkg);
        }
        first.requestFocus();
        scroll.scrollTo(0, 0);
    }

    private View choice(int id, String label, String action) {
        return row(describe(action), null, () -> {
            RemoteKeys.set(this, id, label, action);
            showList();
        });
    }

    private String describe(String action) {
        if (action == null) return "";
        switch (action) {
            case RemoteKeys.NONE: return getString(R.string.remote_do_nothing);
            case RemoteKeys.HOME: return getString(R.string.remote_go_home);
            case RemoteKeys.APPS: return getString(R.string.remote_all_apps);
            case RemoteKeys.INPUTS: return getString(R.string.inputs);
            case RemoteKeys.SETTINGS: return getString(R.string.settings);
            case RemoteKeys.PLAY_PAUSE: return getString(R.string.remote_play_pause);
            default:
                String pkg = action.substring(RemoteKeys.APP.length());
                try {
                    PackageManager pm = getPackageManager();
                    return getString(R.string.remote_open_app, pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
                } catch (PackageManager.NameNotFoundException e) {
                    return pkg;
                }
        }
    }

    private View row(String title, String value, Runnable action) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setBackgroundResource(R.drawable.settings_choice_bg);
        r.setPadding(dp(18), 0, dp(18), 0);
        r.setFocusable(true);
        r.setClickable(true);
        r.setOnClickListener(v -> action.run());
        TextView t = text(title, 17, medium, 0xFFFFFFFF);
        t.setDuplicateParentStateEnabled(true);
        r.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (value != null) {
            TextView v = text(value, 15, regular, 0xCCFFFFFF);
            v.setDuplicateParentStateEnabled(true);
            r.addView(v);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        lp.bottomMargin = dp(8);
        col.addView(r, lp);
        return r;
    }

    private TextView text(String s, float sp, Typeface tf, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(tf);
        t.setTextColor(color);
        return t;
    }

    private int dp(float v) {
        return Math.round(v * density);
    }
}
