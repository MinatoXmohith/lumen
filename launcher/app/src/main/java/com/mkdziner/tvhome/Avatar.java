package com.mkdziner.tvhome;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import java.io.File;

/**
 * The round profile picture in the status pill, tvOS style: avatar.jpg from the theme folder (next to
 * wallpaper.jpg) when there is one, otherwise the initials of greeting_name on a soft gradient.
 */
final class Avatar {

    static final String FILE_NAME = "avatar.jpg";

    private Avatar() {}

    static Drawable make(Resources res, File themeDir, String name, Typeface face, float density) {
        int size = Math.round(36 * density);
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        float r = size / 2f;
        Bitmap photo = themeDir == null ? null : load(new File(themeDir, FILE_NAME), size);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        if (photo != null) {
            BitmapShader shader = new BitmapShader(photo, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            float s = Math.max((float) size / photo.getWidth(), (float) size / photo.getHeight());
            Matrix m = new Matrix();
            m.setScale(s, s);
            m.postTranslate((size - photo.getWidth() * s) / 2f, (size - photo.getHeight() * s) / 2f);
            shader.setLocalMatrix(m);
            p.setShader(shader);
            c.drawCircle(r, r, r, p);
        } else {
            p.setShader(new LinearGradient(0, 0, size, size, 0xFFB0C4D8, 0xFF5E7A94, Shader.TileMode.CLAMP));
            c.drawCircle(r, r, r, p);
            String initials = initials(name);
            if (!initials.isEmpty()) {
                Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
                t.setColor(0xFFFFFFFF);
                t.setTextAlign(Paint.Align.CENTER);
                t.setTextSize(14 * density);
                if (face != null) t.setTypeface(face);
                Paint.FontMetrics fm = t.getFontMetrics();
                c.drawText(initials, r, r - (fm.ascent + fm.descent) / 2f, t);
            }
        }
        // A thin bright rim, like the glass around it.
        Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(density);
        rim.setColor(0x66FFFFFF);
        c.drawCircle(r, r, r - density / 2f, rim);
        return new BitmapDrawable(res, out);
    }

    /** "Mohith Reddy K" -> "MK": first letter of the first and last words. */
    static String initials(String name) {
        if (name == null) return "";
        String[] words = name.trim().split("\\s+");
        if (words.length == 0 || words[0].isEmpty()) return "";
        String first = words[0].substring(0, 1);
        String last = words.length > 1 ? words[words.length - 1].substring(0, 1) : "";
        return (first + last).toUpperCase(java.util.Locale.ROOT);
    }

    private static Bitmap load(File f, int size) {
        if (!f.isFile()) return null;
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), o);
            int sample = 1;
            while (o.outWidth / (sample * 2) >= size * 2 && o.outHeight / (sample * 2) >= size * 2) sample *= 2;
            o.inJustDecodeBounds = false;
            o.inSampleSize = sample;
            return BitmapFactory.decodeFile(f.getPath(), o);
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }
}
