package com.mkdziner.tvhome;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Small HTTPS helpers for the dashboard (weather and TMDB). Always called off the main thread. */
final class Net {

    static final ExecutorService IO = Executors.newFixedThreadPool(2);

    private Net() {}

    static byte[] get(String url, String bearer) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(10000);
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("User-Agent", "Lumen-launcher");
        if (bearer != null) c.setRequestProperty("Authorization", "Bearer " + bearer);
        try {
            int code = c.getResponseCode();
            if (code / 100 != 2) throw new Exception("HTTP " + code);
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                return out.toByteArray();
            }
        } finally {
            c.disconnect();
        }
    }

    static JSONObject json(String url, String bearer) throws Exception {
        return new JSONObject(new String(get(url, bearer), "UTF-8"));
    }

    static Bitmap bitmap(String url) {
        try {
            byte[] b = get(url, null);
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeByteArray(b, 0, b.length, o);
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }
}
