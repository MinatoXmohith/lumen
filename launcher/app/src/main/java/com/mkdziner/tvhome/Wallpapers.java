package com.mkdziner.tvhome;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Mood wallpapers picked on the TV (Launcher settings → Wallpapers), in two groups: Static and Live.
 *
 * Your own wallpapers come from the theme folder: wallpapers/wallpapers.json lists static images and live videos
 * (file, thumbnail, mood, name), pushed with tools/wallpapers.sh. Without that folder the launcher falls back to its
 * built-in set: five images in the APK and five scenes drawn by {@link LiveWallpaperView}.
 * "theme" means the theme's own wallpaper.jpg, as before.
 */
final class Wallpapers {

    static final class Entry {
        String id, mood, name;
        boolean live;
        int res;            // built-in static image, or 0
        int scene = -1;     // built-in live scene, or -1
        File file, thumb;   // user wallpaper and its thumbnail
    }

    private static final String PREFS = "wallpaper_choice", KEY = "wallpaper";
    static final String THEME = "theme";

    private Wallpapers() {}

    static String choice(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, THEME);
    }

    static void set(Context c, String value) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, value).apply();
    }

    static String key(Entry e) {
        return (e.live ? "live:" : "static:") + e.id;
    }

    static List<Entry> list(Context c, boolean live) {
        List<Entry> out = fromFolder(c, live);
        if (!out.isEmpty()) return out;
        String[] ids = live ? new String[]{"aurora", "nightsky", "ocean", "fireflies", "neon"}
                : new String[]{"calm", "cozy", "focus", "dreamy", "fresh"};
        String[] moods = live ? new String[]{"Calm", "Sleepy", "Chill", "Cozy", "Party"}
                : new String[]{"Calm", "Cozy", "Focus", "Dreamy", "Fresh"};
        String[] names = live ? new String[]{"Aurora", "Starry night", "Ocean waves", "Fireflies", "Neon flow"}
                : new String[]{"Blue-hour sea", "Amber dusk", "Graphite light", "Pastel clouds", "Misty pines"};
        int[] res = {R.drawable.wall_calm, R.drawable.wall_cozy, R.drawable.wall_focus, R.drawable.wall_dreamy, R.drawable.wall_fresh};
        for (int i = 0; i < 5; i++) {
            Entry e = new Entry();
            e.id = "builtin-" + ids[i];
            e.mood = moods[i];
            e.name = names[i];
            e.live = live;
            if (live) e.scene = i;
            else e.res = res[i];
            out.add(e);
        }
        return out;
    }

    private static List<Entry> fromFolder(Context c, boolean live) {
        List<Entry> out = new ArrayList<>();
        File dir = new File(Config.dir(c), "wallpapers");
        File manifest = new File(dir, "wallpapers.json");
        if (!manifest.isFile()) return out;
        try (FileInputStream in = new FileInputStream(manifest)) {
            byte[] b = new byte[(int) Math.min(manifest.length(), 64 * 1024)];
            int n = in.read(b);
            JSONArray arr = new JSONObject(new String(b, 0, Math.max(0, n), "UTF-8")).optJSONArray(live ? "live" : "static");
            if (arr == null) return out;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Entry e = new Entry();
                e.id = o.optString("id", "w" + i);
                e.mood = o.optString("mood", "");
                e.name = o.optString("name", "");
                e.live = live;
                e.file = new File(dir, o.optString("file", ""));
                String t = o.optString("thumb", "");
                e.thumb = t.isEmpty() ? null : new File(dir, t);
                if (e.file.isFile()) out.add(e);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    static Entry find(Context c, String choice) {
        boolean live = choice.startsWith("live:");
        if (!live && !choice.startsWith("static:")) return null;
        String id = choice.substring(live ? 5 : 7);
        for (Entry e : list(c, live)) if (e.id.equals(id)) return e;
        return null;
    }

    /**
     * Puts the chosen mood wallpaper behind everything in root (index 0). Returns false for "theme" (or a choice that
     * no longer exists), so the caller shows the theme's wallpaper as before. Moving wallpapers leave the glass without
     * a frozen backdrop, so the motion shows through the glass itself.
     */
    static boolean applyMood(Activity a, ViewGroup root) {
        return applyMood(a, root, true);
    }

    /** play=false shows a live video's still frame instead of playing it (secondary screens: one decoder at a time). */
    static boolean applyMood(Activity a, ViewGroup root, boolean play) {
        View v = create(a, choice(a), true, play);
        if (v == null) return false;
        root.addView(v, 0, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        return true;
    }

    /** A full-screen view for the choice, or null for the theme wallpaper. setGlass feeds a still image to the glass. */
    static View create(Activity a, String choice, boolean setGlass) {
        return create(a, choice, setGlass, true);
    }

    static View create(Activity a, String choice, boolean setGlass, boolean play) {
        Entry e = find(a, choice);
        if (e == null) return null;
        if (e.live) {
            if (setGlass) LiquidGlass.clear();
            if (e.scene >= 0) return new LiveWallpaperView(a, e.scene);
            return play ? new LoopingVideoView(a, e.file) : LoopingVideoView.still(a, e.file);
        }
        Bitmap bmp = staticBitmap(a, choice, e);
        if (bmp == null) return null;
        if (setGlass) {
            android.util.DisplayMetrics dm = a.getResources().getDisplayMetrics();
            LiquidGlass.setWallpaper(bmp, dm.widthPixels, dm.heightPixels);
        }
        ImageView iv = new ImageView(a);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setImageBitmap(bmp);
        return iv;
    }

    // ---- caches: decode each wallpaper once, never again on every screen open ----

    private static String bmpKey;
    private static Bitmap bmpCache;

    private static Bitmap staticBitmap(Context c, String key, Entry e) {
        if (key.equals(bmpKey) && bmpCache != null) return bmpCache;
        Bitmap b = e.res != 0 ? decode(c, e.res, 1) : decodeFile(e.file, 1);
        if (b != null) { bmpKey = key; bmpCache = b; }
        return b;
    }

    private static String themeKey;
    private static Bitmap themeCache;

    /** The theme wallpaper decoded last time, if the file has not changed since. */
    static synchronized Bitmap cachedTheme(File f) {
        return (f.getPath() + f.lastModified()).equals(themeKey) ? themeCache : null;
    }

    static synchronized void putTheme(File f, Bitmap b) {
        themeKey = f.getPath() + f.lastModified();
        themeCache = b;
    }

    private static final java.util.Map<String, Bitmap> frames = new java.util.HashMap<>();
    private static final java.util.concurrent.ExecutorService io = java.util.concurrent.Executors.newSingleThreadExecutor();
    private static final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());

    interface FrameCallback { void done(Bitmap b); }

    static synchronized Bitmap cachedFrame(File video) {
        return frames.get(video.getPath());
    }

    private static synchronized void putFrame(File video, Bitmap b) {
        if (frames.size() > 2) frames.clear();   // keep memory small on a 2 GB TV
        frames.put(video.getPath(), b);
    }

    /** A still of the video (its thumbnail-sized poster on disk if present, else the first frame), off the main thread. */
    static void loadFrame(Context c, File video, FrameCallback cb) {
        io.execute(() -> {
            Bitmap b = null;
            File poster = new File(video.getParentFile(), video.getName().replaceAll("\\.[^.]+$", "") + ".still.jpg");
            if (poster.isFile()) b = decodeFile(poster, 1);
            if (b == null) {
                android.media.MediaMetadataRetriever r = new android.media.MediaMetadataRetriever();
                try {
                    r.setDataSource(video.getPath());
                    Bitmap f = r.getFrameAtTime(0);
                    if (f != null) {
                        b = f.copy(Bitmap.Config.RGB_565, false);
                        f.recycle();
                        try (java.io.FileOutputStream out = new java.io.FileOutputStream(poster)) {
                            b.compress(Bitmap.CompressFormat.JPEG, 88, out);
                        } catch (Exception ignored) {
                        }
                    }
                } catch (Exception | OutOfMemoryError ignored) {
                } finally {
                    try { r.release(); } catch (Exception ignored) { }
                }
            }
            if (b != null) putFrame(video, b);
            Bitmap result = b;
            main.post(() -> cb.done(result));
        });
    }

    /** Small preview for the Wallpapers screen. */
    static Bitmap thumbnail(Context c, Entry e) {
        if (e.thumb != null && e.thumb.isFile()) return decodeFile(e.thumb, 1);
        if (e.res != 0) return decode(c, e.res, 8);
        if (e.file != null && !e.live) return decodeFile(e.file, 4);
        return null;
    }

    static Bitmap decode(Context c, int res, int sample) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeResource(c.getResources(), res, o);
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    static Bitmap decodeFile(File f, int sample) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(f.getPath(), o);
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }
}
