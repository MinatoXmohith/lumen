package com.mkdziner.tvhome;

import android.content.Context;
import android.widget.VideoView;

import java.io.File;

/**
 * A muted, looping video wallpaper. It holds the TV's video decoder only while its window is on screen: the
 * moment an app opens (Netflix, YouTube...) playback stops and the decoder is released, and it restarts from the
 * beginning when Home comes back.
 */
public class LoopingVideoView extends VideoView {

    private final File file;

    public LoopingVideoView(Context context, File file) {
        super(context);
        this.file = file;
        setOnPreparedListener(mp -> {
            mp.setLooping(true);
            mp.setVolume(0f, 0f);
        });
        // never show Android's "Can't play this video" dialog over the home screen
        setOnErrorListener((mp, what, extra) -> true);
        setFocusable(false);
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE) {
            setVideoPath(file.getPath());
            start();
        } else {
            stopPlayback();
        }
    }
}
