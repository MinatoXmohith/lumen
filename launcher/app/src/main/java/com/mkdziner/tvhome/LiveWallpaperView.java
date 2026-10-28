package com.mkdziner.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

import java.util.Random;

/**
 * Animated ("live") wallpapers, drawn every frame on the GPU instead of decoding a GIF or video, which a 2 GB TV
 * could not do smoothly behind the home screen. Every scene is built from a gradient, one pre-rendered soft glow
 * sprite (tinted and scaled) and a few shapes, so a frame costs a handful of draw calls. Motion is slow and eased,
 * and the view stops redrawing whenever its window is hidden (an app is open) or {@link #setRunning} is false.
 */
public class LiveWallpaperView extends View {

    static final int AURORA = 0, STARS = 1, OCEAN = 2, FIREFLIES = 3, NEON = 4;

    private static Bitmap glow;

    private final int kind;
    private final float density;
    private final long start = SystemClock.uptimeMillis();
    private boolean running = true, windowVisible = true;
    private float frozenT = 12f;

    private final Paint bg = new Paint();
    private final Paint sprite = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF dst = new RectF();
    private PorterDuffColorFilter[] tints;

    // per-scene random layout, normalised 0..1
    private float[] px, py, ps, pp;

    public LiveWallpaperView(Context context, int kind) {
        super(context);
        this.kind = kind;
        density = getResources().getDisplayMetrics().density;
        if (glow == null) glow = makeGlow();
        Random r = new Random(7 + kind);
        int n = kind == STARS ? 150 : kind == FIREFLIES ? 46 : kind == AURORA ? 50 : 0;
        px = new float[n]; py = new float[n]; ps = new float[n]; pp = new float[n];
        for (int i = 0; i < n; i++) {
            px[i] = r.nextFloat();
            py[i] = kind == FIREFLIES ? 0.35f + r.nextFloat() * 0.6f : r.nextFloat() * (kind == AURORA ? 0.55f : 1f);
            ps[i] = 0.5f + r.nextFloat();
            pp[i] = r.nextFloat() * 6.283f;
        }
        int[] colors;
        switch (kind) {
            case AURORA: colors = new int[]{0xFF35FFB4, 0xFF2BD4FF, 0xFF8A5CFF, 0xFF4CFF8F}; break;
            case STARS: colors = new int[]{0xFFFFF1D6}; break;
            case OCEAN: colors = new int[]{0xFF9FDDEB}; break;
            case FIREFLIES: colors = new int[]{0xFFFFD27A, 0xFFFFB347}; break;
            default: colors = new int[]{0xFFFF3EA5, 0xFF2EE6FF, 0xFF7A4DFF}; break;
        }
        tints = new PorterDuffColorFilter[colors.length];
        for (int i = 0; i < colors.length; i++) tints[i] = new PorterDuffColorFilter(colors[i], PorterDuff.Mode.SRC_IN);
        if (kind == NEON) sprite.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD));
        setWillNotDraw(false);
    }

    /** A 128 px white disc that fades out smoothly: scaled up and tinted, it is every glow in every scene. */
    private static Bitmap makeGlow() {
        Bitmap b = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new RadialGradient(64, 64, 64, new int[]{0xFFFFFFFF, 0x99FFFFFF, 0x33FFFFFF, 0x00FFFFFF},
                new float[]{0f, 0.35f, 0.7f, 1f}, Shader.TileMode.CLAMP));
        new Canvas(b).drawCircle(64, 64, 64, p);
        return b;
    }

    /** Animate (true) or hold a still frame (false), e.g. for thumbnails that are not focused. */
    void setRunning(boolean run) {
        if (running == run) return;
        if (!run) frozenT = now();
        running = run;
        invalidate();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        windowVisible = visibility == VISIBLE;
        if (windowVisible) invalidate();
    }

    private float now() {
        return (SystemClock.uptimeMillis() - start) / 1000f + 12f;
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        int top, bottom;
        switch (kind) {
            case AURORA: top = 0xFF041826; bottom = 0xFF03101A; break;
            case STARS: top = 0xFF020617; bottom = 0xFF0E1A36; break;
            case OCEAN: top = 0xFF1B4E6B; bottom = 0xFF03101C; break;
            case FIREFLIES: top = 0xFF0E1410; bottom = 0xFF231509; break;
            default: top = 0xFF0A0616; bottom = 0xFF05030B; break;
        }
        bg.setShader(new LinearGradient(0, 0, 0, h, top, bottom, Shader.TileMode.CLAMP));
    }

    private void glowAt(Canvas c, float x, float y, float rx, float ry, int tint, int alpha) {
        sprite.setColorFilter(tints[tint % tints.length]);
        sprite.setAlpha(Math.max(0, Math.min(255, alpha)));
        dst.set(x - rx, y - ry, x + rx, y + ry);
        c.drawBitmap(glow, null, dst, sprite);
    }

    private static float s(double v) {
        return (float) Math.sin(v);
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        float t = running ? now() : frozenT;
        c.drawRect(0, 0, w, h, bg);
        switch (kind) {
            case AURORA: aurora(c, w, h, t); break;
            case STARS: stars(c, w, h, t); break;
            case OCEAN: ocean(c, w, h, t); break;
            case FIREFLIES: fireflies(c, w, h, t); break;
            default: neon(c, w, h, t); break;
        }
        if (running && windowVisible) postInvalidateOnAnimation();
    }

    private void aurora(Canvas c, int w, int h, float t) {
        dot.setColor(Color.WHITE);
        for (int i = 0; i < px.length; i++) {
            dot.setAlpha((int) (60 + 50 * s(t * 0.6 * ps[i] + pp[i])));
            c.drawCircle(px[i] * w, py[i] * h, density * (0.6f + ps[i] * 0.5f), dot);
        }
        for (int i = 0; i < 4; i++) {
            float x = w * (0.5f + 0.38f * s(t * 0.045 * (i + 1) + i * 1.7));
            float y = h * (0.34f + 0.1f * s(t * 0.06 * (i + 2) + i));
            float rx = w * (0.42f + 0.08f * s(t * 0.05 + i)), ry = h * (0.2f + 0.05f * s(t * 0.07 + i * 2));
            glowAt(c, x, y, rx, ry, i, (int) (120 + 40 * s(t * 0.2 + i)));
        }
    }

    private void stars(Canvas c, int w, int h, float t) {
        glowAt(c, w * 0.8f, h * 0.22f, w * 0.16f, w * 0.16f, 0, 70);
        dot.setColor(0xFFFFF6E8);
        c.drawCircle(w * 0.8f, h * 0.22f, w * 0.022f, dot);
        dot.setColor(Color.WHITE);
        for (int i = 0; i < px.length; i++) {
            float tw = 0.5f + 0.5f * s(t * 1.3 * ps[i] + pp[i]);
            dot.setAlpha((int) (40 + 200 * tw * tw));
            c.drawCircle(px[i] * w, py[i] * h, density * (0.5f + ps[i] * 0.7f), dot);
        }
        // a shooting star every 9 s, 1.1 s long, eased
        float cycle = t % 9f;
        if (cycle < 1.1f) {
            float k = cycle / 1.1f, e = 1 - (1 - k) * (1 - k);
            float x0 = w * 0.15f, y0 = h * 0.12f, x1 = w * 0.55f, y1 = h * 0.42f;
            float hx = x0 + (x1 - x0) * e, hy = y0 + (y1 - y0) * e;
            fill.setStyle(Paint.Style.STROKE);
            fill.setStrokeCap(Paint.Cap.ROUND);
            fill.setStrokeWidth(density * 1.6f);
            fill.setShader(new LinearGradient(hx - (x1 - x0) * 0.25f, hy - (y1 - y0) * 0.25f, hx, hy,
                    0x00FFFFFF, (int) (255 * (1 - k)) << 24 | 0xFFFFFF, Shader.TileMode.CLAMP));
            c.drawLine(hx - (x1 - x0) * 0.25f, hy - (y1 - y0) * 0.25f, hx, hy, fill);
            fill.setShader(null);
            fill.setStyle(Paint.Style.FILL);
        }
    }

    private void ocean(Canvas c, int w, int h, float t) {
        glowAt(c, w * 0.5f, h * 0.38f, w * 0.45f, h * 0.18f, 0, 70);
        int[] col = {0xFF2C6E96, 0xFF1F5A80, 0xFF14476A, 0xFF0B3352, 0xFF06223A};
        for (int k = 0; k < 5; k++) {
            float base = h * (0.52f + k * 0.1f), amp = h * (0.012f + k * 0.006f);
            float len = w * (0.55f - k * 0.05f), speed = 0.25f + k * 0.12f;
            path.reset();
            path.moveTo(0, h);
            for (int i = 0; i <= 48; i++) {
                float x = w * i / 48f;
                path.lineTo(x, base + amp * s(x / len * 6.283 + t * speed + k) + amp * 0.4f * s(x / len * 14 - t * speed * 1.3));
            }
            path.lineTo(w, h);
            path.close();
            fill.setColor(col[k]);
            fill.setAlpha(110 + k * 30);
            c.drawPath(path, fill);
        }
    }

    private void fireflies(Canvas c, int w, int h, float t) {
        // soft treeline silhouette
        path.reset();
        path.moveTo(0, h);
        for (int i = 0; i <= 40; i++) {
            float x = w * i / 40f;
            path.lineTo(x, h * (0.72f + 0.05f * s(i * 0.7) + 0.03f * s(i * 2.3)));
        }
        path.lineTo(w, h);
        path.close();
        fill.setColor(0xFF080A07);
        c.drawPath(path, fill);
        glowAt(c, w * 0.5f, h * 0.95f, w * 0.7f, h * 0.25f, 1, 40);
        for (int i = 0; i < px.length; i++) {
            float x = w * (px[i] + 0.035f * s(t * 0.3 * ps[i] + pp[i]));
            float y = h * (py[i] + 0.045f * s(t * 0.22 * ps[i] + pp[i] * 1.3));
            float pulse = Math.max(0f, s(t * 0.9 * ps[i] + pp[i]));
            pulse = pulse * pulse;
            float r = h * 0.03f * (0.7f + ps[i] * 0.4f);
            glowAt(c, x, y, r, r, i, (int) (40 + 215 * pulse));
            dot.setColor(0xFFFFF4C8);
            dot.setAlpha((int) (60 + 195 * pulse));
            c.drawCircle(x, y, density * 1.4f, dot);
        }
    }

    private void neon(Canvas c, int w, int h, float t) {
        for (int i = 0; i < 3; i++) {
            float x = w * (0.5f + 0.32f * s(t * 0.21 * (i + 1) + i * 2.1));
            float y = h * (0.5f + 0.3f * s(t * 0.17 * (i + 2) + i * 1.3));
            float r = Math.min(w, h) * (0.55f + 0.12f * s(t * 0.4 + i));
            glowAt(c, x, y, r * 1.25f, r, i, 150);
        }
        // a slow thin ring for depth
        fill.setStyle(Paint.Style.STROKE);
        fill.setStrokeWidth(density * 1.2f);
        fill.setColor(0x33FFFFFF);
        float rr = Math.min(w, h) * (0.32f + 0.03f * s(t * 0.5));
        c.drawCircle(w * 0.5f, h * 0.5f, rr, fill);
        fill.setStyle(Paint.Style.FILL);
    }
}
