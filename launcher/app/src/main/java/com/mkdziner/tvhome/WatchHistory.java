package com.mkdziner.tvhome;

import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.util.AtomicFile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What was being watched, for the Continue watching row in All apps.
 *
 * Android keeps each app's own "Watch Next" list behind a system-only permission, so a launcher that isn't part of
 * the TV's firmware can't read it. This list is built from what apps announce through their media sessions instead
 * (see {@link WatchTracker}), and it stays on the TV: nothing here ever goes online.
 */
final class WatchHistory {

    static final class Item {
        String pkg = "", title = "", subtitle = "", art = "";
        long position, duration, updated;

        /** Fraction watched, 0 to 1, or -1 when the app doesn't report a length. */
        float progress() {
            return duration > 0 ? Math.max(0f, Math.min(1f, (float) position / duration)) : -1f;
        }

        String key() {
            return pkg + '\n' + title;
        }
    }

    static final int MAX_ITEMS = 10;
    /** Nearly at the end counts as finished: it leaves the row. */
    static final float FINISHED = 0.95f;
    /** Less than this is a trailer or a preview on an app's home page, not something being watched. */
    static final long MIN_WATCHED_MS = 60_000;

    private static final String FILE = "continue_watching.json";
    private static final String ART_DIR = "continue_art";
    /** Sessions alive right now: their own "open the player" intent, which resumes better than opening the app. */
    private static final Map<String, PendingIntent> live = new HashMap<>();

    private WatchHistory() {}

    /** Pure rule: true when this playback belongs in the list at all. */
    static boolean finished(long position, long duration) {
        return duration > 0 && position >= duration * FINISHED;
    }

    static boolean isMusicApp(Context c, String pkg) {
        String p = pkg.toLowerCase(java.util.Locale.ROOT);
        if (p.contains("spotify") || p.contains("music") || p.contains("saavn") || p.contains("gaana")
                || p.contains("wynk") || p.contains("radio") || p.contains("podcast")) return true;
        try {
            return c.getPackageManager().getApplicationInfo(pkg, 0).category == ApplicationInfo.CATEGORY_AUDIO;
        } catch (PackageManager.NameNotFoundException e) {
            return true;   // gone: nothing to continue in
        }
    }

    static synchronized void setLive(String pkg, PendingIntent intent) {
        if (intent == null) live.remove(pkg);
        else live.put(pkg, intent);
    }

    static synchronized List<Item> load(Context c) {
        List<Item> out = new ArrayList<>();
        try {
            AtomicFile f = new AtomicFile(new File(c.getFilesDir(), FILE));
            if (!f.getBaseFile().exists()) return out;
            JSONArray a = new JSONArray(new String(f.readFully(), StandardCharsets.UTF_8));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Item it = new Item();
                it.pkg = o.getString("pkg");
                it.title = o.optString("title");
                it.subtitle = o.optString("subtitle");
                it.art = o.optString("art");
                it.position = o.optLong("position");
                it.duration = o.optLong("duration");
                it.updated = o.optLong("updated");
                out.add(it);
            }
        } catch (Exception ignored) {
            // a damaged list is treated as empty, and the next save replaces it
        }
        return out;
    }

    private static void save(Context c, List<Item> items) {
        AtomicFile f = new AtomicFile(new File(c.getFilesDir(), FILE));
        FileOutputStream out = null;
        try {
            JSONArray a = new JSONArray();
            for (Item it : items) {
                a.put(new JSONObject().put("pkg", it.pkg).put("title", it.title).put("subtitle", it.subtitle)
                        .put("art", it.art).put("position", it.position).put("duration", it.duration)
                        .put("updated", it.updated));
            }
            out = f.startWrite();
            out.write(a.toString().getBytes(StandardCharsets.UTF_8));
            f.finishWrite(out);
        } catch (Exception e) {
            if (out != null) f.failWrite(out);
        }
    }

    /** Adds or updates this title and moves it to the front; a finished title leaves the list. */
    static synchronized void record(Context c, Item item, Bitmap art) {
        List<Item> items = load(c);
        Item old = null;
        for (Item it : items) {
            if (it.key().equals(item.key())) old = it;
        }
        if (old != null) items.remove(old);
        if (finished(item.position, item.duration)) {
            if (old != null) deleteArt(c, old);
            save(c, items);
            return;
        }
        item.art = old != null ? old.art : "";
        if (art != null && item.art.isEmpty()) item.art = saveArt(c, item, art);
        items.add(0, item);
        while (items.size() > MAX_ITEMS) deleteArt(c, items.remove(items.size() - 1));
        save(c, items);
    }

    static synchronized void remove(Context c, Item item) {
        List<Item> items = load(c);
        for (int i = items.size() - 1; i >= 0; i--) {
            if (items.get(i).key().equals(item.key())) deleteArt(c, items.remove(i));
        }
        save(c, items);
    }

    static File artFile(Context c, Item item) {
        return item.art.isEmpty() ? null : new File(new File(c.getFilesDir(), ART_DIR), item.art);
    }

    private static String saveArt(Context c, Item item, Bitmap art) {
        try {
            File dir = new File(c.getFilesDir(), ART_DIR);
            if (!dir.isDirectory() && !dir.mkdirs()) return "";
            String name = Integer.toHexString(Objects.hash(item.pkg, item.title)) + ".jpg";
            int w = Math.min(480, art.getWidth());
            int h = Math.max(1, art.getHeight() * w / Math.max(1, art.getWidth()));
            Bitmap small = Bitmap.createScaledBitmap(art, w, h, true);
            try (FileOutputStream out = new FileOutputStream(new File(dir, name))) {
                small.compress(Bitmap.CompressFormat.JPEG, 86, out);
            }
            return name;
        } catch (Exception | OutOfMemoryError e) {
            return "";
        }
    }

    private static void deleteArt(Context c, Item item) {
        File f = artFile(c, item);
        if (f != null) f.delete();
    }

    /**
     * Opens the app. A session that is still alive goes straight back to its player; otherwise the app opens as
     * usual, on its own home page with its own Continue watching row. (No app gives a launcher a way to reopen a
     * specific title later, so that second case can't land on the title itself.)
     */
    static void open(Context c, Item item) {
        PendingIntent session;
        synchronized (WatchHistory.class) {
            session = live.get(item.pkg);
        }
        if (session != null) {
            try {
                session.send();
                return;
            } catch (PendingIntent.CanceledException ignored) {
            }
        }
        PackageManager pm = c.getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(item.pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(item.pkg);
        if (i == null) return;
        try {
            c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException ignored) {
        }
    }

    static List<Item> sortedForRow(Context c) {
        List<Item> items = load(c);
        Collections.sort(items, (a, b) -> Long.compare(b.updated, a.updated));
        return items;
    }
}
