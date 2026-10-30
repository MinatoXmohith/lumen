package com.mkdziner.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.io.File;

/**
 * A muted, looping video wallpaper that behaves like any other view.
 *
 * - A still frame of the video shows immediately, so there is never a black flash; the video fades in over it once
 *   its first frame is on screen.
 * - It draws through a TextureView, so it fades, moves and transitions with the rest of the screen (a SurfaceView
 *   would stay behind during transitions and tear).
 * - It holds the TV's video decoder only while its window is on screen: when an app opens, playback stops and the
 *   decoder is released; it starts again when Home comes back.
 */
public class LoopingVideoView extends FrameLayout implements TextureView.SurfaceTextureListener {

    private final File file;
    private final ImageView still;
    private final TextureView texture;
    private MediaPlayer player;
    private Surface surface;
    private boolean windowVisible, waiting, coveredByFrame;
    private final Runnable takeOver = () -> { waiting = false; startNow(); };
    /** The one view allowed to hold the video decoder, and where the loop was when it last let go. */
    private static LoopingVideoView owner;

    public LoopingVideoView(Context context, File file) {
        super(context);
        this.file = file;
        setFocusable(false);
        still = new ImageView(context);
        still.setScaleType(ImageView.ScaleType.CENTER_CROP);
        addView(still, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        texture = new TextureView(context);
        texture.setAlpha(0f);
        texture.setSurfaceTextureListener(this);
        addView(texture, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        // Another screen is playing this video right now: start from its current picture (half size is plenty
        // under the fade), so the new screen opens on exactly what was on screen.
        Bitmap now = null;
        if (owner != null && owner.file.equals(file) && owner.texture.isAvailable()) {
            try {
                now = owner.texture.getBitmap(owner.texture.getWidth() / 2, owner.texture.getHeight() / 2);
            } catch (Exception | OutOfMemoryError ignored) {
            }
        }
        Bitmap frame = now != null ? now : Wallpapers.cachedFrame(file);
        if (frame != null) still.setImageBitmap(frame);
        else Wallpapers.loadFrame(context, file, b -> { if (b != null) still.setImageBitmap(b); });
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        windowVisible = visibility == VISIBLE;
        if (windowVisible) startIfReady();
        else stop();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stop();
    }

    private void startIfReady() {
        if (player != null || waiting || !windowVisible || !texture.isAvailable()) return;
        if (owner != null && owner != this) {
            // Another screen is still playing behind the window transition: let the zoom finish on its video,
            // then take the decoder over. Swapping players mid-animation is what made it stutter.
            showFrame(owner.snapshot());
            waiting = true;
            postDelayed(takeOver, Motion.ms(300));
            return;
        }
        if (file.getPath().equals(lastPath)) showFrame(lastFrame);
        startNow();
    }

    /** A half-size copy of what this view is showing, or null. */
    private Bitmap snapshot() {
        if (player == null || !texture.isAvailable()) return null;
        try {
            return texture.getBitmap(texture.getWidth() / 2, texture.getHeight() / 2);
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    /** Covers whatever old frame this view holds with the current one, so coming back never jumps in time. */
    private void showFrame(Bitmap frame) {
        if (frame == null) return;
        still.setImageBitmap(frame);
        texture.animate().cancel();
        texture.setAlpha(0f);
        coveredByFrame = true;
    }

    /** One background thread owns every MediaPlayer: creating and releasing one takes tens of ms on this chip. */
    private static android.os.Handler video;

    private static synchronized android.os.Handler video() {
        if (video == null) {
            android.os.HandlerThread t = new android.os.HandlerThread("wallpaper-video");
            t.start();
            video = new android.os.Handler(t.getLooper());
        }
        return video;
    }

    private static volatile String lastPath;
    private static volatile int lastPosition;
    /** The picture on screen when the video last stopped: where the next screen should carry on from. */
    private static Bitmap lastFrame;

    private void startNow() {
        if (player != null || !windowVisible || !texture.isAvailable()) return;
        // hand-over: the screen that just came up takes the decoder; the one behind it lets go first
        // (both run in order on the video thread, so the old player is gone before the new one opens)
        if (owner != null && owner != this) owner.stop();
        owner = this;
        final Surface sf = new Surface(texture.getSurfaceTexture());
        final MediaPlayer mp = new MediaPlayer();
        player = mp;
        surface = sf;
        final String path = file.getPath();
        video().post(() -> {
            try {
                mp.setSurface(sf);
                mp.setDataSource(path);
                mp.setLooping(true);
                mp.setVolume(0f, 0f);
                mp.setOnPreparedListener(p -> {
                    // carry on from where the previous screen left the loop, so the wallpaper never jumps
                    if (path.equals(lastPath) && lastPosition > 0) {
                        p.setOnSeekCompleteListener(MediaPlayer::start);
                        p.seekTo(lastPosition, MediaPlayer.SEEK_CLOSEST);
                    } else {
                        p.start();
                    }
                });
                mp.setOnInfoListener((p, what, extra) -> {
                    if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                        post(() -> {
                            if (player != mp) return;
                            // Over a picture of the same moment there's nothing to fade: a full-screen blend here
                            // only cost frames. The first start of all has no such picture, so that one does fade.
                            if (coveredByFrame) texture.setAlpha(1f);
                            else texture.animate().alpha(1f).setDuration(Motion.ms(400))
                                    .setInterpolator(Motion.STANDARD).start();
                        });
                    }
                    return false;
                });
                // a bad file keeps the still frame; never show Android's error dialog over Home
                mp.setOnErrorListener((p, what, extra) -> { post(() -> { if (player == mp) stop(); }); return true; });
                mp.prepareAsync();
            } catch (Exception e) {
                post(() -> { if (player == mp) stop(); });
            }
        });
    }

    private void stop() {
        stop(null);
    }

    /** Lets go of the decoder; the release itself runs on the video thread. st, if given, is released after it. */
    private void stop(SurfaceTexture st) {
        removeCallbacks(takeOver);
        waiting = false;
        if (owner == this) owner = null;
        Bitmap frame = snapshot();
        if (frame != null) {
            lastFrame = frame;
            lastPath = file.getPath();
        }
        // the last video frame stays on the TextureView, so letting go of the decoder shows no jump
        final MediaPlayer mp = player;
        final Surface sf = surface;
        player = null;
        surface = null;
        if (mp == null && sf == null && st == null) return;
        final String path = file.getPath();
        video().post(() -> {
            if (mp != null) {
                try {
                    if (mp.isPlaying()) {
                        lastPath = path;
                        lastPosition = mp.getCurrentPosition();
                    }
                } catch (Exception ignored) {
                }
                try {
                    mp.release();
                } catch (Exception ignored) {
                }
            }
            if (sf != null) sf.release();
            if (st != null) st.release();
        });
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int h) {
        startIfReady();
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
        texture.animate().cancel();
        texture.setAlpha(0f);
        stop(st);   // the texture is released after the player that draws into it
        return false;
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) {}

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture st) {}

    /** The video's still frame, for screens that show the wallpaper without playing it. */
    static View still(Context c, File file) {
        ImageView iv = new ImageView(c);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Bitmap frame = Wallpapers.cachedFrame(file);
        if (frame != null) iv.setImageBitmap(frame);
        else Wallpapers.loadFrame(c, file, b -> { if (b != null) iv.setImageBitmap(b); });
        return iv;
    }
}
