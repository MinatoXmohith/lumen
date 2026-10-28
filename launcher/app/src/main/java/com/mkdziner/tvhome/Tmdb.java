package com.mkdziner.tvhome;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Trending movies and shows from TMDB, grouped into genres for the browse screen. The key is read from
 * tmdb_key.txt in the theme folder (never from config.json, which may be public): a v3 API key (32 hex
 * characters) or a v4 read access token.
 */
final class Tmdb {

    static final String KEY_FILE = "tmdb_key.txt";
    static final String IMG = "https://image.tmdb.org/t/p/w342";

    static final class Title {
        String name, poster, mediaType;
        int genre;
    }

    static final class Genre {
        String name;
        List<Title> titles = new ArrayList<>();
    }

    private Tmdb() {}

    /** In-memory cache: the browse page opens already filled. Trending changes weekly; refreshed after 3 hours. */
    private static final java.util.Map<String, List<Genre>> CACHE = new java.util.HashMap<>();
    private static final java.util.Map<String, Long> CACHE_AT = new java.util.HashMap<>();
    static final android.util.LruCache<String, android.graphics.Bitmap> POSTERS =
            new android.util.LruCache<String, android.graphics.Bitmap>(8 * 1024 * 1024) {
                @Override
                protected int sizeOf(String k, android.graphics.Bitmap b) {
                    return b.getByteCount();
                }
            };

    static synchronized List<Genre> cached(String kind) {
        Long at = CACHE_AT.get(kind);
        if (at == null || System.currentTimeMillis() - at > 3 * 3600 * 1000L) return null;
        return CACHE.get(kind);
    }

    /** Blocking: fetches trending and the two front posters of each stack into the caches. */
    static List<Genre> load(String key, String kind) throws Exception {
        List<Genre> c = cached(kind);
        if (c != null) return c;
        List<Genre> list = trending(key, kind);
        synchronized (Tmdb.class) {
            CACHE.put(kind, list);
            CACHE_AT.put(kind, System.currentTimeMillis());
        }
        for (Genre g : list) {
            for (int i = 0; i < Math.min(2, g.titles.size()); i++) poster(g.titles.get(i).poster);
        }
        return list;
    }

    /** Blocking poster fetch through the cache. */
    static android.graphics.Bitmap poster(String url) {
        android.graphics.Bitmap b = POSTERS.get(url);
        if (b != null) return b;
        b = Net.bitmap(url);
        if (b != null) POSTERS.put(url, b);
        return b;
    }

    static String key(File themeDir) {
        if (themeDir == null) return null;
        File f = new File(themeDir, KEY_FILE);
        if (!f.isFile()) return null;
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] b = new byte[(int) Math.min(f.length(), 4096)];
            int n = in.read(b);
            String k = new String(b, 0, Math.max(0, n), "UTF-8").trim();
            return k.isEmpty() ? null : k;
        } catch (Exception e) {
            return null;
        }
    }

    private static JSONObject call(String key, String path) throws Exception {
        boolean bearer = key.length() > 40;
        String sep = path.contains("?") ? "&" : "?";
        String url = "https://api.themoviedb.org/3" + path + sep + "language=en-US" + (bearer ? "" : "&api_key=" + key);
        return Net.json(url, bearer ? key : null);
    }

    /** kind: "featured", "movies" or "shows". Blocking: call on Net.IO. Returns up to 3 genres with 2+ titles. */
    static List<Genre> trending(String key, String kind) throws Exception {
        Map<Integer, String> names = new HashMap<>();
        for (String g : new String[]{"/genre/movie/list", "/genre/tv/list"}) {
            JSONArray arr = call(key, g).optJSONArray("genres");
            if (arr == null) continue;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                names.put(o.getInt("id"), o.getString("name"));
            }
        }
        String path = kind.equals("movies") ? "/trending/movie/week" : kind.equals("shows") ? "/trending/tv/week"
                : "/trending/all/week";
        Map<Integer, Genre> groups = new LinkedHashMap<>();
        for (int page = 1; page <= 2; page++) {
            JSONArray res = call(key, path + "?page=" + page).optJSONArray("results");
            if (res == null) break;
            for (int i = 0; i < res.length(); i++) {
                JSONObject o = res.getJSONObject(i);
                String poster = o.optString("poster_path", "");
                JSONArray gids = o.optJSONArray("genre_ids");
                if (poster.isEmpty() || poster.equals("null") || gids == null || gids.length() == 0) continue;
                Title t = new Title();
                t.name = o.optString("title", o.optString("name", ""));
                t.poster = IMG + poster;
                t.mediaType = o.optString("media_type", kind.equals("shows") ? "tv" : "movie");
                t.genre = gids.getInt(0);
                Genre g = groups.get(t.genre);
                if (g == null) {
                    g = new Genre();
                    g.name = names.containsKey(t.genre) ? names.get(t.genre) : "Trending";
                    groups.put(t.genre, g);
                }
                if (g.titles.size() < 6) g.titles.add(t);
            }
        }
        List<Genre> out = new ArrayList<>();
        for (Genre g : groups.values()) if (g.titles.size() >= 2 && out.size() < 3) out.add(g);
        if (out.size() < 3) {
            for (Genre g : groups.values()) if (!out.contains(g) && !g.titles.isEmpty() && out.size() < 3) out.add(g);
        }
        return out;
    }
}
