package com.mkdziner.tvhome;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Dashboard home (mkdziner design, Frame 1): greeting, time + avatar pill, glass rail, music card,
 * date + weather card, an Apps panel with seven favourites and All apps, and Featured / Movies / Shows,
 * which open {@link BrowseActivity}. The app grid ({@link MainActivity}) is the All apps screen.
 */
public class DashboardActivity extends GlassScreen implements NowPlaying.Listener {

    private static final long WEATHER_EVERY_MS = 30 * 60 * 1000L;

    private FrameLayout root;
    private NowPlaying nowPlaying;
    private TextView musicTitle, musicArtist, weatherNow, weatherCity, weatherTemp, weatherIcon, weatherRange;
    private TextView[] stats;
    private View tabHome;
    private ImageView musicArt, musicPlay;
    private LinearLayout appRow;
    private final Handler main = new Handler(Looper.getMainLooper());
    private long weatherAt;
    private View firstApp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);
        root = findViewById(R.id.dashRoot);
        setUpScreen(root);

        TextView hello = findViewById(R.id.hello);
        hello.setText(cfg.greetingName.isEmpty() ? "HELLO!" : "HELLO! " + cfg.greetingName);

        View status = findViewById(R.id.btnStatus);
        pill(status);
        status.setOnClickListener(v -> showControlPanel(status));
        ((ImageView) findViewById(R.id.avatar)).setImageDrawable(Avatar.make(getResources(),
                getExternalFilesDir(null), cfg.greetingName, hello.getTypeface(), density));

        tabHome = findViewById(R.id.tabHome);
        tabHome.setSelected(true);
        findViewById(R.id.tabHome).setOnClickListener(v -> { bounce(v); focusFirstApp(); });
        findViewById(R.id.tabApps).setOnClickListener(this::openApps);

        glass(findViewById(R.id.musicCard), 24, 0x24000000);
        musicTitle = findViewById(R.id.musicTitle);
        musicArtist = findViewById(R.id.musicArtist);
        musicArt = findViewById(R.id.musicArt);
        musicPlay = findViewById(R.id.musicPlay);
        round(musicArt, 16);
        findViewById(R.id.musicPrev).setOnClickListener(v -> nowPlaying.previous());
        findViewById(R.id.musicNext).setOnClickListener(v -> nowPlaying.next());
        musicPlay.setOnClickListener(v -> {
            if (nowPlaying.hasSession()) nowPlaying.playPause();
            else openPackage(defaultMusicApp());
        });

        glass(findViewById(R.id.weatherMain), 24, 0x2E000000);
        glass(findViewById(R.id.weatherStats), 24, 0x1A000000);
        weatherNow = findViewById(R.id.weatherNow);
        weatherCity = findViewById(R.id.weatherCity);
        weatherTemp = findViewById(R.id.weatherTemp);
        weatherIcon = findViewById(R.id.weatherIcon);
        weatherRange = findViewById(R.id.weatherRange);
        stats = new TextView[]{findViewById(R.id.stat0), findViewById(R.id.stat1), findViewById(R.id.stat2), findViewById(R.id.stat3)};

        appRow = findViewById(R.id.appRow);
        buildApps();

        nowPlaying = new NowPlaying(this, this);
        root.post(this::focusFirstApp);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // A new wallpaper was picked in Launcher settings: rebuild with it.
        if (!Wallpapers.choice(this).equals(appliedWallpaper)) {
            recreate();
            return;
        }
        if (returningFromApps) {
            returningFromApps = false;
            settleIn();
            // Home on the remote starts over at the first app; Back lands where you left, on APPS.
            if (homePressed) focusFirstApp();
            else findViewById(R.id.tabApps).requestFocus();
        }
        homePressed = false;
        nowPlaying.start();
        if (System.currentTimeMillis() - weatherAt > WEATHER_EVERY_MS) {
            loadWeather();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        nowPlaying.stop();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Home pressed while the dashboard is already in front: back to the first app.
        if (intent != null && intent.hasCategory(Intent.CATEGORY_HOME)) {
            homePressed = true;
            focusFirstApp();
        }
    }

    @Override
    public void onBackPressed() {
        // Home has nowhere to go back to.
    }

    private boolean returningFromApps, homePressed;

    @Override
    protected boolean playLiveWallpaper() {
        return true;
    }

    /** APPS: press bounce on the tab, then the grid grows in while home steps back. */
    private void openApps(View tab) {
        tab.animate().scaleX(0.9f).scaleY(0.9f).setDuration(Motion.ms(90)).withEndAction(() -> {
            tab.animate().scaleX(1f).scaleY(1f).setDuration(Motion.ms(140)).start();
            returningFromApps = true;
            startActivity(new Intent(this, MainActivity.class));
            overridePendingTransition(R.anim.apps_enter, R.anim.home_exit);
        }).start();
    }

    private static void bounce(View v) {
        v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(Motion.ms(90))
                .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(Motion.ms(160)).start()).start();
    }

    /**
     * Back on home from APPS (remote Home or Back): the pieces settle in one after another, top to bottom,
     * from slightly larger and transparent, so home answers the grid shrinking away.
     */
    private void settleIn() {
        int[] order = {R.id.hello, R.id.dashDate, R.id.tabs, R.id.btnStatus, R.id.musicCard, R.id.weatherMain,
                R.id.weatherStats, R.id.appRow};
        for (int i = 0; i < order.length; i++) {
            View v = findViewById(order[i]);
            if (v == null) continue;
            v.animate().cancel();
            // fade + a short rise only: scaling nine glass cards over a playing video dropped frames on this TV
            v.setAlpha(0f);
            v.setTranslationY(8 * density);
            v.animate().alpha(1f).translationY(0f).setStartDelay(Motion.ms(i < 4 ? 0 : 30L * (i - 3)))
                    .setDuration(Motion.ms(260)).setInterpolator(Motion.EMPHASIZED).withLayer().start();
        }
    }

    private void focusFirstApp() {
        if (firstApp != null) firstApp.requestFocus();
    }

    // ------------------------------------------------------------------ music
    @Override
    public void onNowPlaying(NowPlaying np) {
        if (!np.allowed()) {
            musicTitle.setText("Music access is off");
            musicArtist.setText("Allow it once over ADB (see HOW-TO-RESTORE.md)");
        } else if (!np.hasSession() || np.title.isEmpty()) {
            musicTitle.setText("Nothing playing");
            musicArtist.setText("Press play to open " + appLabel(defaultMusicApp()));
        } else {
            musicTitle.setText(np.title);
            String sub = np.artist;
            if (!np.appLabel.isEmpty()) sub = sub.isEmpty() ? np.appLabel : sub + "  ·  " + np.appLabel;
            musicArtist.setText(sub);
        }
        if (np.art != null) {
            musicArt.setImageBitmap(np.art);
            musicArt.setPadding(0, 0, 0, 0);
            musicArt.setBackground(null);
        } else {
            musicArt.setImageResource(R.drawable.ic_music);
            int p = (int) (34 * density);
            musicArt.setPadding(p, p, p, p);
            musicArt.setBackground(new ColorDrawable(0x33FFFFFF));
        }
        musicPlay.setImageResource(np.playing ? R.drawable.ic_pause : R.drawable.ic_play);
    }

    private String defaultMusicApp() {
        String pkg = themeJson.optString("music_app", "");
        if (!pkg.isEmpty()) return pkg;
        for (String p : new String[]{"com.spotify.tv.android", "com.google.android.youtube.tvmusic"}) {
            if (getPackageManager().getLeanbackLaunchIntentForPackage(p) != null) return p;
        }
        return "com.google.android.youtube.tvmusic";
    }

    private String appLabel(String pkg) {
        try {
            return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return "music";
        }
    }

    // ------------------------------------------------------------------ weather
    private void loadWeather() {
        String city = themeJson.optString("weather_city", "");
        if (city.isEmpty()) {
            weatherNow.setText("Weather");
            weatherCity.setText("Set weather_city in config.json");
            return;
        }
        weatherCity.setText(city);
        Net.IO.execute(() -> {
            Weather.Now w = Weather.fetch(this, city);
            main.post(() -> {
                if (w == null) {
                    weatherNow.setText("Weather unavailable");
                    return;
                }
                weatherAt = System.currentTimeMillis();
                weatherTemp.setText(w.tempC + "\u00b0");
                weatherIcon.setText(w.symbol);
                weatherNow.setText(w.text);
                weatherRange.setText("Day " + w.maxC + "\u00b0  \u00b7  Night " + w.minC + "\u00b0");
                weatherCity.setText(w.city);
                stats[0].setText(w.feelsC + "\u00b0C");
                stats[1].setText(Weather.uvWord(w.uvMax) + " " + Math.round(w.uvMax));
                stats[2].setText(w.windDir + " " + Math.round(w.windKmh) + "km/h");
                stats[3].setText(w.cloud + "%");
            });
        });
    }

    // ------------------------------------------------------------------ apps
    private void buildApps() {
        PackageManager pm = getPackageManager();
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> apps = new ArrayList<>();
        for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
            String p = ri.activityInfo.packageName;
            if (p.equals(getPackageName()) || cfg.hiddenApps.contains(p)) continue;
            apps.add(ri);
        }
        Collections.sort(apps, (a, b) -> {
            int ia = cfg.dockApps.indexOf(a.activityInfo.packageName), ib = cfg.dockApps.indexOf(b.activityInfo.packageName);
            if (ia < 0) ia = 1000;
            if (ib < 0) ib = 1000;
            if (ia != ib) return ia - ib;
            return String.valueOf(a.loadLabel(pm)).compareToIgnoreCase(String.valueOf(b.loadLabel(pm)));
        });
        appRow.removeAllViews();
        int n = Math.min(5, apps.size());
        int tileW = (int) (136 * density), gap = (int) ((856 - 5 * 136) / 4f * density);
        for (int i = 0; i < n; i++) {
            ResolveInfo ri = apps.get(i);
            FrameLayout tile = new FrameLayout(this);
            tile.setFocusable(true);
            tile.setClickable(true);
            ImageView img = new ImageView(this);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Drawable banner = ri.activityInfo.loadBanner(pm);
            if (banner == null) banner = ri.activityInfo.applicationInfo.loadBanner(pm);
            if (banner != null) {
                img.setImageDrawable(banner);
            } else {
                img.setImageDrawable(ri.loadIcon(pm));
                img.setScaleType(ImageView.ScaleType.FIT_CENTER);
                int p = (int) (16 * density);
                img.setPadding(p, p, p, p);
                img.setBackground(new ColorDrawable(0x40FFFFFF));
            }
            tile.addView(img, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            String pkg = ri.activityInfo.packageName;
            tile.setContentDescription(ri.loadLabel(pm));
            tile.setOnClickListener(v -> openPackage(pkg));
            tile.setForeground(getDrawable(R.drawable.focus_ring));
            round(tile, 14);
            tile.setLayerType(View.LAYER_TYPE_HARDWARE, null);   // springs move a cached picture, not a redraw
            tile.setOnFocusChangeListener((v, f) -> {
                Motion.focus(v, f, 1.07f, 3 * density);
                // tvOS: the focused app stands out, the rest of the row steps back a little.
                appRow.post(this::dimUnfocusedApps);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(tileW, ViewGroup.LayoutParams.MATCH_PARENT);
            if (i > 0) lp.leftMargin = gap;
            appRow.addView(tile, lp);
            if (i == 0) firstApp = tile;
        }
    }

    private void dimUnfocusedApps() {
        View focused = appRow.findFocus();
        for (int i = 0; i < appRow.getChildCount(); i++) {
            View t = appRow.getChildAt(i);
            float target = focused == null || t == focused ? 1f : 0.7f;
            t.animate().alpha(target).setDuration(Motion.ms(170)).start();
        }
    }

    // ------------------------------------------------------------------ control panel
    private void showControlPanel(View anchor) {
        View panel = getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);
        PopupWindow popup = popup(panel, (int) (260 * density));
        row(rows, popup, R.drawable.ic_input, getString(R.string.inputs), () -> showInputs(anchor));
        row(rows, popup, R.drawable.ic_wifi, getString(R.string.wifi), () -> open(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        row(rows, popup, R.drawable.ic_settings, getString(R.string.settings), () -> open(new Intent(Settings.ACTION_SETTINGS)));
        row(rows, popup, R.drawable.ic_home_settings, getString(R.string.launcher_settings),
                () -> startActivity(new Intent(this, LauncherSettingsActivity.class)));
        dropIn(popup, panel, anchor);
        rows.getChildAt(0).requestFocus();
    }

    private void showInputs(View anchor) {
        View panel = getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);
        PopupWindow popup = popup(panel, (int) (260 * density));
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (tim != null) {
            List<TvInputInfo> inputs = new ArrayList<>(tim.getTvInputList());
            Collections.sort(inputs, (a, b) -> String.valueOf(a.loadLabel(this)).compareToIgnoreCase(String.valueOf(b.loadLabel(this))));
            for (TvInputInfo info : inputs) {
                if (!info.isPassthroughInput() || info.isHidden(this)) continue;
                CharSequence custom = info.loadCustomLabel(this);
                String label = custom != null && custom.length() > 0 ? custom.toString() : String.valueOf(info.loadLabel(this));
                String id = info.getId();
                boolean hdmi = info.getType() == TvInputInfo.TYPE_HDMI;
                if (hdmi && !label.toUpperCase(Locale.ROOT).contains("HDMI")) label = "HDMI " + label;
                row(rows, popup, hdmi ? R.drawable.ic_input : R.drawable.ic_av, label, () -> {
                    Uri uri = TvContract.buildChannelUriForPassthroughInput(id);
                    open(new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "vnd.android.cursor.item/channel"));
                });
            }
        }
        if (!cfg.liveTvPackage.isEmpty()) {
            row(rows, popup, R.drawable.ic_livetv, getString(R.string.live_tv), () -> openPackage(cfg.liveTvPackage));
        }
        if (rows.getChildCount() == 0) return;
        dropIn(popup, panel, anchor);
        rows.getChildAt(0).requestFocus();
    }

    /** Glass menus open out of the time pill: fade, grow from its top-right corner. */
    private void dropIn(PopupWindow popup, View panel, View anchor) {
        panel.setAlpha(0f);
        panel.setScaleX(0.92f);
        panel.setScaleY(0.8f);
        panel.post(() -> {
            panel.setPivotX(panel.getWidth());
            panel.setPivotY(0f);
        });
        popup.showAsDropDown(anchor, 0, -anchor.getHeight(), Gravity.END);
        panel.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(Motion.ms(220))
                .setInterpolator(Motion.EMPHASIZED).start();
    }

    private PopupWindow popup(View panel, int width) {
        PopupWindow p = new PopupWindow(panel, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        p.setBackgroundDrawable(new ColorDrawable(0));
        p.setOutsideTouchable(true);
        p.setElevation(0f);
        p.setAnimationStyle(R.style.PopupFade);   // in: dropIn; out: a short fade
        panel.setBackground(new LiquidGlass.PillDrawable(panel, null, density, 24 * density));
        return p;
    }

    private void row(LinearLayout rows, PopupWindow popup, int icon, String title, Runnable action) {
        View row = getLayoutInflater().inflate(R.layout.item_input, rows, false);
        ((ImageView) row.findViewById(R.id.icon)).setImageResource(icon);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        row.findViewById(R.id.status).setVisibility(View.GONE);
        row.findViewById(R.id.dot).setVisibility(View.GONE);
        row.setOnClickListener(v -> {
            popup.dismiss();
            action.run();
        });
        rows.addView(row);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // Media keys on the remote control whatever is playing.
        if (event.getAction() == KeyEvent.ACTION_DOWN && nowPlaying != null && nowPlaying.hasSession()) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE: nowPlaying.playPause(); return true;
                case KeyEvent.KEYCODE_MEDIA_NEXT: nowPlaying.next(); return true;
                case KeyEvent.KEYCODE_MEDIA_PREVIOUS: nowPlaying.previous(); return true;
                default: break;
            }
        }
        return super.dispatchKeyEvent(event);
    }
}
