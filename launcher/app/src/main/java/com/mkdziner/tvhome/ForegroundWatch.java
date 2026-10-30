package com.mkdziner.tvhome;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Notices watching in apps that announce nothing through media sessions, as Netflix, Prime Video and Hotstar do on
 * this TV. {@link HomeKeyService} reports which app is in front; if it's a video app and sound keeps playing, the app
 * goes into Continue watching as "last watched in this app". There is no title or poster: those apps don't share them.
 */
final class ForegroundWatch {

    private static final long SAMPLE_MS = 15_000;
    /** Sound for this long means watching rather than browsing a trailer. */
    private static final long WATCHED_MS = 60_000;
    /** Some TVs send a video app's sound past Android's mixer, so a long stay counts on its own. */
    private static final long STAYED_MS = 180_000;

    private static final Set<String> KNOWN = new HashSet<>(Arrays.asList("com.netflix.ninja",
            "com.amazon.amazonvideo.livingroom", "in.startv.hotstar", "com.google.android.youtube.tv"));
    private static final String[] NAME_HINTS = {"netflix", "amazonvideo", "hotstar", "disney", "zee5", "sonyliv",
            "jiotv", "jiocinema", "mxplayer", "aha", "sunnxt", "plex", "kodi", "vlc", "skypro", "hbo", "apple.atve"};

    private final Context ctx;
    private final Handler main = new Handler(Looper.getMainLooper());
    private String current;
    private long heard, stayed, lastSaved;

    private final Runnable sample = new Runnable() {
        @Override
        public void run() {
            if (current == null) return;
            stayed += SAMPLE_MS;
            AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            if (am.isMusicActive()) heard += SAMPLE_MS;
            if ((heard >= WATCHED_MS || stayed >= STAYED_MS) && stayed - lastSaved >= WATCHED_MS) {
                lastSaved = stayed;
                WatchHistory.Item item = new WatchHistory.Item();
                item.pkg = current;
                item.updated = System.currentTimeMillis();
                Net.IO.execute(() -> WatchHistory.record(ctx, item, null));
            }
            main.postDelayed(this, SAMPLE_MS);
        }
    };

    ForegroundWatch(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    void onWindow(CharSequence packageName) {
        if (packageName == null) return;
        String pkg = packageName.toString();
        if (pkg.equals(current) || !isApp(pkg)) return;   // volume bars and the like aren't a change of app
        main.removeCallbacks(sample);
        current = isVideo(pkg) ? pkg : null;
        heard = stayed = lastSaved = 0;
        if (current != null) main.postDelayed(sample, SAMPLE_MS);
    }

    void stop() {
        main.removeCallbacks(sample);
        current = null;
    }

    private boolean isApp(String pkg) {
        return ctx.getPackageManager().getLeanbackLaunchIntentForPackage(pkg) != null
                || ctx.getPackageManager().getLaunchIntentForPackage(pkg) != null;
    }

    private boolean isVideo(String pkg) {
        if (pkg.equals(ctx.getPackageName()) || WatchHistory.isMusicApp(ctx, pkg)) return false;
        if (KNOWN.contains(pkg)) return true;
        String p = pkg.toLowerCase(Locale.ROOT);
        for (String hint : NAME_HINTS) if (p.contains(hint)) return true;
        try {
            return ctx.getPackageManager().getApplicationInfo(pkg, 0).category == ApplicationInfo.CATEGORY_VIDEO;
        } catch (Exception e) {
            return false;
        }
    }
}
