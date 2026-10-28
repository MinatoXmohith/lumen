package com.mkdziner.tvhome;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;

/**
 * Liquid glass for the top pills and the dock band: the wallpaper behind, blurred, then a light tint, a top
 * sheen and a rim that is bright at the top and fades down. Android 9 has no live blur, so the blur is made
 * once from the wallpaper (a small copy, about 130 KB) and each glass shape samples the part of it that sits
 * behind it. Without a wallpaper the same tint, sheen and rim are drawn over the plain background.
 */
final class LiquidGlass {

    private static final int SCALE = 3;           // backdrop is 1/3 of the screen with a light blur: clear glass
    private static Bitmap backdrop;
    private static float backdropScale;

    private LiquidGlass() {}

    /** No wallpaper: the glass is drawn over the plain background colour. */
    static void clear() {
        backdrop = null;
    }

    /** Builds the blurred backdrop from the wallpaper as CENTER_CROP shows it on a screenW x screenH screen. */
    static void setWallpaper(Bitmap wall, int screenW, int screenH) {
        try {
            int bw = Math.max(1, screenW / SCALE), bh = Math.max(1, screenH / SCALE);
            Bitmap small = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(small);
            float s = Math.max((float) bw / wall.getWidth(), (float) bh / wall.getHeight());
            Matrix m = new Matrix();
            m.setScale(s, s);
            m.postTranslate((bw - wall.getWidth() * s) / 2f, (bh - wall.getHeight() * s) / 2f);
            c.drawBitmap(wall, m, new Paint(Paint.FILTER_BITMAP_FLAG));
            int[] px = new int[bw * bh];
            small.getPixels(px, 0, bw, 0, 0, bw, bh);
            for (int pass = 0; pass < 2; pass++) boxBlur(px, bw, bh, 2);
            small.setPixels(px, 0, bw, 0, 0, bw, bh);
            backdrop = small;
            backdropScale = (float) screenW / bw;
        } catch (Exception | OutOfMemoryError e) {
            backdrop = null;   // glass without the blur is still glass
        }
    }

    /** Horizontal then vertical box blur of the given radius, in place. */
    private static void boxBlur(int[] px, int w, int h, int r) {
        int[] tmp = new int[px.length];
        int div = r * 2 + 1;
        for (int y = 0; y < h; y++) {
            int ra = 0, ga = 0, ba = 0;
            for (int i = -r; i <= r; i++) {
                int p = px[y * w + Math.min(w - 1, Math.max(0, i))];
                ra += (p >> 16) & 0xFF; ga += (p >> 8) & 0xFF; ba += p & 0xFF;
            }
            for (int x = 0; x < w; x++) {
                tmp[y * w + x] = 0xFF000000 | ((ra / div) << 16) | ((ga / div) << 8) | (ba / div);
                int out = px[y * w + Math.max(0, x - r)], in = px[y * w + Math.min(w - 1, x + r + 1)];
                ra += ((in >> 16) & 0xFF) - ((out >> 16) & 0xFF);
                ga += ((in >> 8) & 0xFF) - ((out >> 8) & 0xFF);
                ba += (in & 0xFF) - (out & 0xFF);
            }
        }
        for (int x = 0; x < w; x++) {
            int ra = 0, ga = 0, ba = 0;
            for (int i = -r; i <= r; i++) {
                int p = tmp[Math.min(h - 1, Math.max(0, i)) * w + x];
                ra += (p >> 16) & 0xFF; ga += (p >> 8) & 0xFF; ba += p & 0xFF;
            }
            for (int y = 0; y < h; y++) {
                px[y * w + x] = 0xFF000000 | ((ra / div) << 16) | ((ga / div) << 8) | (ba / div);
                int out = tmp[Math.max(0, y - r) * w + x], in = tmp[Math.min(h - 1, y + r + 1) * w + x];
                ra += ((in >> 16) & 0xFF) - ((out >> 16) & 0xFF);
                ga += ((in >> 8) & 0xFF) - ((out >> 8) & 0xFF);
                ba += (in & 0xFF) - (out & 0xFF);
            }
        }
    }

    /**
     * Paints for one glass shape, reused across frames. Apple-style clear glass: the backdrop lightly blurred and
     * barely tinted, a lens ring along the inner edge that shows the backdrop slightly magnified, a specular rim
     * brightest at the top-left and bottom-right, and a soft inner highlight along the top.
     */
    static final class Painter {
        private final float density;
        private final Paint body = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint lens = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint tint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint lensLight = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint topGlow = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint shade = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint liveVeil = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Matrix bodyM = new Matrix(), lensM = new Matrix();
        private final RectF inner = new RectF();
        private Bitmap shaderFor;
        private BitmapShader bodyShader, lensShader;
        private RectF last = new RectF();

        Painter(float density) {
            this(density, 0x10000000);
        }

        /** tintArgb: a light veil over the glass; darker for panels that carry white text. */
        Painter(float density, int tintArgb) {
            this.density = density;
            ColorMatrix cm = new ColorMatrix();
            cm.setSaturation(1.15f);
            ColorMatrix lift = new ColorMatrix();
            lift.setScale(1.04f, 1.04f, 1.04f, 1f);
            cm.postConcat(lift);
            body.setColorFilter(new ColorMatrixColorFilter(cm));
            lens.setColorFilter(new ColorMatrixColorFilter(cm));
            lens.setStyle(Paint.Style.STROKE);
            tint.setColor(tintArgb);
            lensLight.setStyle(Paint.Style.STROKE);
            lensLight.setColor(0x1CFFFFFF);
            rim.setStyle(Paint.Style.STROKE);
            rim.setStrokeWidth(1.3f * density);
            topGlow.setStyle(Paint.Style.STROKE);
            topGlow.setStrokeWidth(2.5f * density);
            shade.setStyle(Paint.Style.STROKE);
            shade.setStrokeWidth(1f * density);
            shade.setColor(0x40000000);
            liveVeil.setColor(0x2E0A0E14);
        }

        /**
         * Draws glass in rect (canvas coordinates). originX/originY is where the canvas's (0,0) sits on the
         * screen, so the backdrop lines up with the real wallpaper behind.
         */
        void draw(Canvas c, RectF rect, float radius, float originX, float originY) {
            float ring = Math.min(8f * density, rect.height() * 0.22f);
            Bitmap b = backdrop;
            if (b != null) {
                if (shaderFor != b) {
                    bodyShader = new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                    lensShader = new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                    body.setShader(bodyShader);
                    lens.setShader(lensShader);
                    shaderFor = b;
                }
                bodyM.setScale(backdropScale, backdropScale);
                bodyM.postTranslate(-originX, -originY);
                bodyShader.setLocalMatrix(bodyM);
                c.drawRoundRect(rect, radius, radius, body);
                // Lens: the same backdrop magnified about the glass centre, seen only in a ring at the edge.
                float k = 1.0f + Math.min(0.12f, 18f * density / Math.max(rect.width(), 1f));
                lensM.set(bodyM);
                lensM.postScale(k, k, rect.centerX(), rect.centerY());
                lensShader.setLocalMatrix(lensM);
                lens.setStrokeWidth(ring);
                inner.set(rect);
                inner.inset(ring / 2f, ring / 2f);
                c.drawRoundRect(inner, Math.max(0, radius - ring / 2f), Math.max(0, radius - ring / 2f), lens);
            }
            else {
                // Live wallpaper: no frozen backdrop, the motion shows through; a soft veil keeps text readable.
                c.drawRoundRect(rect, radius, radius, liveVeil);
            }
            c.drawRoundRect(rect, radius, radius, tint);
            lensLight.setStrokeWidth(ring);
            inner.set(rect);
            inner.inset(ring / 2f, ring / 2f);
            c.drawRoundRect(inner, Math.max(0, radius - ring / 2f), Math.max(0, radius - ring / 2f), lensLight);
            if (!rect.equals(last)) {
                rim.setShader(new LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
                        new int[]{0xF2FFFFFF, 0x40FFFFFF, 0x26FFFFFF, 0xBFFFFFFF}, new float[]{0f, 0.4f, 0.62f, 1f},
                        Shader.TileMode.CLAMP));
                topGlow.setShader(new LinearGradient(0, rect.top, 0, rect.top + rect.height() * 0.35f,
                        0x66FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
                last.set(rect);
            }
            inner.set(rect);
            inner.inset(-0.5f * density, -0.5f * density);
            c.drawRoundRect(inner, radius + 0.5f * density, radius + 0.5f * density, shade);
            inner.set(rect);
            inner.inset(2.2f * density, 2.2f * density);
            c.drawRoundRect(inner, Math.max(0, radius - 2.2f * density), Math.max(0, radius - 2.2f * density), topGlow);
            inner.set(rect);
            inner.inset(0.65f * density, 0.65f * density);
            c.drawRoundRect(inner, radius, radius, rim);
        }
    }

    /**
     * Background for a pill: liquid glass normally, the pill's own drawable (solid white) when focused or
     * activated, so the focused button reads as clearly as before.
     */
    static final class PillDrawable extends Drawable {
        private final View host;
        private final Drawable original;
        private final Painter painter;
        private final RectF rect = new RectF();
        private final int[] loc = new int[2];
        private boolean solid;
        /** Corner radius in px; 0 = fully rounded ends (half the height). */
        private final float radius;

        PillDrawable(View host, Drawable original, float density) {
            this(host, original, density, 0f);
        }

        /** Glass panel with fixed corners; original may be null when the panel never takes focus. */
        PillDrawable(View host, Drawable original, float density, float radius) {
            this.host = host;
            this.original = original != null ? original : new android.graphics.drawable.ColorDrawable(0);
            // Panels (fixed radius) carry rows of white text, so they get a darker veil than the pills.
            this.painter = radius > 0 ? new Painter(density, 0x47000000) : new Painter(density, 0x1A000000);
            this.radius = radius;
        }

        @Override
        public void draw(Canvas c) {
            if (solid) {
                original.draw(c);
                return;
            }
            Rect r = getBounds();
            rect.set(r);
            host.getLocationOnScreen(loc);
            painter.draw(c, rect, radius > 0 ? radius : rect.height() / 2f, loc[0], loc[1]);
        }

        @Override
        public boolean isStateful() {
            return true;
        }

        @Override
        protected boolean onStateChange(int[] state) {
            original.setState(state);
            boolean s = false;
            for (int st : state) {
                if (st == android.R.attr.state_focused || st == android.R.attr.state_activated) s = true;
            }
            boolean changed = s != solid;
            solid = s;
            return changed;
        }

        @Override
        protected void onBoundsChange(Rect bounds) {
            original.setBounds(bounds);
        }

        @Override
        public void setAlpha(int alpha) {
            original.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
            original.setColorFilter(cf);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    /**
     * Background for a glass card or panel that stays glass when focused: focus adds a bright white outline
     * and a little more light instead of turning solid white like the pills.
     */
    static final class CardDrawable extends Drawable {
        private final View host;
        private final Painter painter;
        private final float radius, density;
        private final RectF rect = new RectF();
        private final int[] loc = new int[2];
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint lift = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean focused;

        CardDrawable(View host, float density, float radius, int tintArgb) {
            this.host = host;
            this.density = density;
            this.radius = radius;
            this.painter = new Painter(density, tintArgb);
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(3f * density);
            ring.setColor(0xF2FFFFFF);
            lift.setColor(0x1FFFFFFF);
        }

        @Override
        public void draw(Canvas c) {
            rect.set(getBounds());
            host.getLocationOnScreen(loc);
            float r = radius > 0 ? radius : rect.height() / 2f;
            painter.draw(c, rect, r, loc[0], loc[1]);
            if (focused) {
                c.drawRoundRect(rect, r, r, lift);
                rect.inset(1.5f * density, 1.5f * density);
                c.drawRoundRect(rect, r, r, ring);
            }
        }

        @Override
        public boolean isStateful() {
            return true;
        }

        @Override
        protected boolean onStateChange(int[] state) {
            boolean f = false;
            for (int st : state) if (st == android.R.attr.state_focused) f = true;
            boolean changed = f != focused;
            focused = f;
            return changed;
        }

        @Override
        public void setAlpha(int alpha) {}

        @Override
        public void setColorFilter(ColorFilter cf) {}

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
