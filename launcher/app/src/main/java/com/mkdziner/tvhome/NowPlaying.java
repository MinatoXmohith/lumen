package com.mkdziner.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;

import java.util.List;

/**
 * Follows whichever app is playing (YouTube Music, Spotify, ...) through Android's media sessions and reports
 * title, artist, art and play state. Needs MediaListener enabled; without it {@link #allowed()} is false.
 */
final class NowPlaying {

    interface Listener {
        void onNowPlaying(NowPlaying np);
    }

    private final Context ctx;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private MediaSessionManager msm;
    private MediaController controller;
    private boolean allowed;

    String title = "", artist = "", appLabel = "";
    Bitmap art;
    boolean playing;

    private final MediaController.Callback cb = new MediaController.Callback() {
        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            read();
        }

        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            read();
        }

        @Override
        public void onSessionDestroyed() {
            pick();
        }
    };

    private final MediaSessionManager.OnActiveSessionsChangedListener sessions = list -> pick();

    NowPlaying(Context ctx, Listener listener) {
        this.ctx = ctx;
        this.listener = listener;
    }

    void start() {
        msm = (MediaSessionManager) ctx.getSystemService(Context.MEDIA_SESSION_SERVICE);
        try {
            msm.addOnActiveSessionsChangedListener(sessions, component(), main);
            allowed = true;
        } catch (SecurityException e) {
            allowed = false;
        }
        pick();
    }

    void stop() {
        try {
            if (msm != null) msm.removeOnActiveSessionsChangedListener(sessions);
        } catch (Exception ignored) {
        }
        if (controller != null) controller.unregisterCallback(cb);
        controller = null;
    }

    boolean allowed() {
        return allowed;
    }

    boolean hasSession() {
        return controller != null;
    }

    void playPause() {
        if (controller == null) return;
        if (playing) controller.getTransportControls().pause();
        else controller.getTransportControls().play();
    }

    void next() {
        if (controller != null) controller.getTransportControls().skipToNext();
    }

    void previous() {
        if (controller != null) controller.getTransportControls().skipToPrevious();
    }

    /** The package of the app that is playing, or null. */
    String packageName() {
        return controller == null ? null : controller.getPackageName();
    }

    private ComponentName component() {
        return new ComponentName(ctx, MediaListener.class);
    }

    private void pick() {
        List<MediaController> list = null;
        try {
            if (msm != null) list = msm.getActiveSessions(component());
            allowed = true;
        } catch (SecurityException e) {
            allowed = false;
        }
        MediaController best = null;
        if (list != null) {
            for (MediaController mc : list) {
                PlaybackState st = mc.getPlaybackState();
                if (st != null && st.getState() == PlaybackState.STATE_PLAYING) {
                    best = mc;
                    break;
                }
                if (best == null && mc.getMetadata() != null) best = mc;
            }
        }
        if (controller != null && (best == null || !best.getSessionToken().equals(controller.getSessionToken()))) {
            controller.unregisterCallback(cb);
        }
        if (best != null && (controller == null || !best.getSessionToken().equals(controller.getSessionToken()))) {
            best.registerCallback(cb, main);
        }
        controller = best;
        read();
    }

    private void read() {
        title = "";
        artist = "";
        appLabel = "";
        art = null;
        playing = false;
        if (controller != null) {
            MediaMetadata md = controller.getMetadata();
            if (md != null) {
                title = nz(md.getString(MediaMetadata.METADATA_KEY_TITLE));
                if (title.isEmpty()) title = nz(md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE));
                artist = nz(md.getString(MediaMetadata.METADATA_KEY_ARTIST));
                if (artist.isEmpty()) artist = nz(md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE));
                art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
                if (art == null) art = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
                if (art == null) art = md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
            }
            PlaybackState st = controller.getPlaybackState();
            playing = st != null && st.getState() == PlaybackState.STATE_PLAYING;
            try {
                appLabel = ctx.getPackageManager().getApplicationLabel(
                        ctx.getPackageManager().getApplicationInfo(controller.getPackageName(), 0)).toString();
            } catch (Exception ignored) {
            }
        }
        listener.onNowPlaying(this);
    }

    private static String nz(CharSequence s) {
        return s == null ? "" : s.toString();
    }
}
