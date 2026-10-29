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
    private boolean windowVisible;

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
        Bitmap frame = Wallpapers.cachedFrame(file);
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
        if (player != null || !windowVisible || !texture.isAvailable()) return;
        try {
            surface = new Surface(texture.getSurfaceTexture());
            MediaPlayer mp = new MediaPlayer();
            player = mp;
            mp.setSurface(surface);
            mp.setDataSource(file.getPath());
            mp.setLooping(true);
            mp.setVolume(0f, 0f);
            mp.setOnPreparedListener(MediaPlayer::start);
            mp.setOnInfoListener((p, what, extra) -> {
                if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                    texture.animate().alpha(1f).setDuration(Motion.ms(400)).setInterpolator(Motion.STANDARD).start();
                }
                return false;
            });
            // a bad file keeps the still frame; never show Android's error dialog over Home
            mp.setOnErrorListener((p, what, extra) -> { stop(); return true; });
            mp.prepareAsync();
        } catch (Exception e) {
            stop();
        }
    }

    private void stop() {
        texture.animate().cancel();
        texture.setAlpha(0f);
        if (player != null) {
            try {
                player.release();
            } catch (Exception ignored) {
            }
            player = null;
        }
        if (surface != null) {
            surface.release();
            surface = null;
        }
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int h) {
        startIfReady();
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
        stop();
        return true;
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
