package com.mkdziner.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Follows video apps' media sessions in the background and keeps {@link WatchHistory} current: title, poster and
 * progress of whatever is being watched. It runs inside {@link MediaListener}, which Android keeps bound while
 * other apps are in front. Apps that announce no title still count, as "last watched in this app".
 */
final class WatchTracker {

    private static final long TICK_MS = 15_000, SAVE_EVERY_MS = 30_000;

    private final class Track extends MediaController.Callback {
        final MediaController controller;
        final String pkg;
        String title = "", subtitle = "";
        Bitmap art;
        long duration, position, watched, lastTick, lastSave;
        boolean playing;

        Track(MediaController c) {
            controller = c;
            pkg = c.getPackageName();
            WatchHistory.setLive(pkg, c.getSessionActivity());
            onMetadataChanged(c.getMetadata());
            onPlaybackStateChanged(c.getPlaybackState());
        }

        @Override
        public void onMetadataChanged(MediaMetadata md) {
            if (md == null) return;     // apps often clear this on the way out: keep what we knew
            String t = text(md, MediaMetadata.METADATA_KEY_TITLE, MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
            if (t.isEmpty()) return;
            if (!t.equals(title)) {
                save();                 // the next episode: the last one keeps its own place
                watched = 0;
                art = null;
            }
            title = t;
            subtitle = text(md, MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, MediaMetadata.METADATA_KEY_ARTIST);
            duration = Math.max(0, md.getLong(MediaMetadata.METADATA_KEY_DURATION));
            Bitmap b = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
            if (b == null) b = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            if (b == null) b = md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
            if (b != null) art = b;
        }

        @Override
        public void onPlaybackStateChanged(PlaybackState st) {
            tick();
            boolean now = st != null && st.getState() == PlaybackState.STATE_PLAYING;
            if (st != null) position = Math.max(0, st.getPosition());
            if (playing && !now) save();
            playing = now;
            lastTick = SystemClock.elapsedRealtime();
            schedule();
        }

        @Override
        public void onSessionDestroyed() {
            drop(this);
        }

        void tick() {
            long t = SystemClock.elapsedRealtime();
            if (playing) {
                long d = t - lastTick;
                watched += d;
                position += d;
            }
            lastTick = t;
        }

        void save() {
            if (watched < WatchHistory.MIN_WATCHED_MS) return;
            WatchHistory.Item item = new WatchHistory.Item();
            item.pkg = pkg;
            item.title = title;
            item.subtitle = subtitle;
            item.position = position;
            item.duration = duration;
            item.updated = System.currentTimeMillis();
            Bitmap a = art;
            lastSave = SystemClock.elapsedRealtime();
            Net.IO.execute(() -> WatchHistory.record(ctx, item, a));
        }
    }

    private final Context ctx;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<MediaSession.Token, Track> tracks = new HashMap<>();
    private MediaSessionManager msm;

    private final MediaSessionManager.OnActiveSessionsChangedListener changed = this::sync;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            boolean any = false;
            long now = SystemClock.elapsedRealtime();
            for (Track t : tracks.values()) {
                if (!t.playing) continue;
                any = true;
                t.tick();
                if (now - t.lastSave >= SAVE_EVERY_MS) t.save();
            }
            if (any) main.postDelayed(this, TICK_MS);
        }
    };

    WatchTracker(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    void start() {
        msm = (MediaSessionManager) ctx.getSystemService(Context.MEDIA_SESSION_SERVICE);
        ComponentName me = new ComponentName(ctx, MediaListener.class);
        try {
            msm.addOnActiveSessionsChangedListener(changed, me, main);
            sync(msm.getActiveSessions(me));
        } catch (SecurityException ignored) {
            // music access is off: the row just stays empty
        }
    }

    void stop() {
        main.removeCallbacks(ticker);
        try {
            if (msm != null) msm.removeOnActiveSessionsChangedListener(changed);
        } catch (Exception ignored) {
        }
        for (Track t : new HashSet<>(tracks.values())) drop(t);
    }

    private void sync(List<MediaController> list) {
        Set<MediaSession.Token> alive = new HashSet<>();
        if (list != null) {
            for (MediaController c : list) {
                if (c.getPackageName().equals(ctx.getPackageName()) || WatchHistory.isMusicApp(ctx, c.getPackageName())) continue;
                alive.add(c.getSessionToken());
                if (!tracks.containsKey(c.getSessionToken())) {
                    Track t = new Track(c);
                    tracks.put(c.getSessionToken(), t);
                    c.registerCallback(t, main);
                }
            }
        }
        for (Track t : new HashSet<>(tracks.values())) {
            if (!alive.contains(t.controller.getSessionToken())) drop(t);
        }
        schedule();
    }

    private void drop(Track t) {
        t.tick();
        t.save();
        t.controller.unregisterCallback(t);
        tracks.remove(t.controller.getSessionToken());
        WatchHistory.setLive(t.pkg, null);
    }

    private void schedule() {
        main.removeCallbacks(ticker);
        main.postDelayed(ticker, TICK_MS);
    }

    private static String text(MediaMetadata md, String... keys) {
        for (String k : keys) {
            CharSequence s = md.getText(k);
            if (s != null && s.length() > 0) return s.toString();
        }
        return "";
    }
}
