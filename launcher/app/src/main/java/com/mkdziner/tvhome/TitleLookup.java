package com.mkdziner.tvhome;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the ID a streaming app reports into a name and poster. Prime Video announces no title, but its public page
 * for the ID has both. Only that ID goes out, and only to Prime Video's own site.
 */
final class TitleLookup {

    interface Result {
        void done(String name, Bitmap poster);
    }

    private static final String PRIME = "com.amazon.amazonvideo.livingroom", PRIME_ID = "amzn1.dv.gti.";
    private static final Pattern PAGE_TITLE = Pattern.compile("<title>\\s*Prime Video:\\s*([^<]+)</title>");
    private static final Pattern WATCH_TITLE = Pattern.compile("name=\"title\" content=\"Watch ([^\"]+?) online");
    private static final Pattern POSTER = Pattern.compile("property=\"og:image\" content=\"(https://[^\"]+)\"");
    private static final int MAX_PAGE = 1024 * 1024, MAX_IMAGE = 2 * 1024 * 1024;

    private TitleLookup() {}

    static boolean supports(String pkg, String id) {
        return PRIME.equals(pkg) && id.startsWith(PRIME_ID);
    }

    /** Opens this title in its app, when the app has a way to. */
    static Intent openIntent(String pkg, String id) {
        if (!supports(pkg, id)) return null;
        return new Intent(Intent.ACTION_VIEW, Uri.parse("https://app.primevideo.com/detail?gti=" + Uri.encode(id)))
                .setPackage(pkg);
    }

    static void find(String id, Result result) {
        Net.IO.execute(() -> {
            String name = "";
            Bitmap poster = null;
            try {
                String page = new String(fetch("https://www.primevideo.com/detail/" + id + "/", MAX_PAGE), StandardCharsets.UTF_8);
                name = first(PAGE_TITLE, page);
                if (name.isEmpty()) name = first(WATCH_TITLE, page);
                String image = first(POSTER, page);
                if (!image.isEmpty()) {
                    byte[] b = fetch(image, MAX_IMAGE);
                    BitmapFactory.Options o = new BitmapFactory.Options();
                    o.inSampleSize = 2;
                    poster = BitmapFactory.decodeByteArray(b, 0, b.length, o);
                }
            } catch (Exception | OutOfMemoryError ignored) {
                // offline, or the page changed: the card stays "last watched in Prime Video"
            }
            String n = name;
            Bitmap p = poster;
            new Handler(Looper.getMainLooper()).post(() -> result.done(n, p));
        });
    }

    private static String first(Pattern p, String text) {
        Matcher m = p.matcher(text);
        return m.find() ? Html.fromHtml(m.group(1).trim(), Html.FROM_HTML_MODE_LEGACY).toString().trim() : "";
    }

    private static byte[] fetch(String url, int max) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(10000);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 Version/18.0 Safari/605.1.15");
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = in.read(buf)) != -1 && out.size() < max) out.write(buf, 0, n);
            return out.toByteArray();
        } finally {
            c.disconnect();
        }
    }
}
