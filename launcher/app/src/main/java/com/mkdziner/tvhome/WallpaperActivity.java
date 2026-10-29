package com.mkdziner.tvhome;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Launcher settings → Wallpapers: pick one by mood. Two rows of five, Static and Live. The current choice fills
 * the screen behind the cards (dimmed), so a pick is previewed the moment it is made; Home shows it for real.
 */
public class WallpaperActivity extends Activity {

    private float density;
    private FrameLayout root, bgHolder;
    private View currentBg;
    private TextView themeRow, status;
    private final List<TextView> marks = new ArrayList<>();
    private final List<String> markIds = new ArrayList<>();
    private Typeface semibold, medium, regular;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Motion.init(this);
        density = getResources().getDisplayMetrics().density;
        semibold = getResources().getFont(R.font.outfit_semibold);
        medium = getResources().getFont(R.font.outfit_medium);
        regular = getResources().getFont(R.font.outfit_regular);

        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0B0E12);
        bgHolder = new FrameLayout(this);
        root.addView(bgHolder, match());
        View dim = new View(this);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xCC05070A, 0x9905070A, 0xE605070A});
        dim.setBackground(g);
        root.addView(dim, match());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int side = dp(72);
        col.setPadding(side, dp(26), side, dp(20));
        scroll.addView(col);
        root.addView(scroll, match());
        setContentView(root);

        col.addView(text("Wallpapers", 28, semibold, 0xFFFFFFFF));
        status = text("Pick one for your mood. Live wallpapers pause while an app is open.", 15, regular, 0xB3FFFFFF);
        status.setPadding(0, dp(2), 0, dp(10));
        col.addView(status);

        themeRow = text("", 17, medium, 0xFFFFFFFF);
        themeRow.setBackgroundResource(R.drawable.settings_choice_bg);
        themeRow.setGravity(Gravity.CENTER_VERTICAL);
        themeRow.setPadding(dp(18), 0, dp(18), 0);
        themeRow.setFocusable(true);
        themeRow.setClickable(true);
        themeRow.setOnClickListener(v -> choose(Wallpapers.THEME));
        col.addView(themeRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));

        col.addView(section("STATIC"));
        LinearLayout staticRow = row();
        List<Wallpapers.Entry> statics = Wallpapers.list(this, false);
        for (int i = 0; i < statics.size(); i++) {
            Wallpapers.Entry e = statics.get(i);
            ImageView img = new ImageView(this);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            img.setImageBitmap(Wallpapers.thumbnail(this, e));
            staticRow.addView(card(img, e.mood, e.name, Wallpapers.key(e), null), cardParams(i));
        }
        col.addView(staticRow);

        col.addView(section("LIVE  ·  ANIMATED"));
        LinearLayout liveRow = row();
        List<Wallpapers.Entry> lives = Wallpapers.list(this, true);
        for (int i = 0; i < lives.size(); i++) {
            Wallpapers.Entry e = lives.get(i);
            View preview;
            LiveWallpaperView scene = null;
            if (e.scene >= 0) {
                scene = new LiveWallpaperView(this, e.scene);
                scene.setRunning(false);
                preview = scene;
            } else {
                ImageView img = new ImageView(this);
                img.setScaleType(ImageView.ScaleType.CENTER_CROP);
                img.setImageBitmap(Wallpapers.thumbnail(this, e));
                preview = img;
            }
            liveRow.addView(card(preview, e.mood, e.name, Wallpapers.key(e), scene), cardParams(i));
        }
        col.addView(liveRow);


        showBackground(Wallpapers.choice(this), false);
        refresh();
        View first = findFocusTarget(Wallpapers.choice(this));
        (first != null ? first : themeRow).requestFocus();
        // the screen eases in
        scroll.setAlpha(0f);
        scroll.setTranslationY(dp(16));
        scroll.animate().alpha(1f).translationY(0f).setDuration(Motion.ms(320)).setInterpolator(Motion.EMPHASIZED).start();
    }

    private View findFocusTarget(String choice) {
        int i = markIds.indexOf(choice);
        return i < 0 ? null : (View) marks.get(i).getTag();
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setClipChildren(false);
        r.setClipToPadding(false);
        r.setPadding(0, dp(8), 0, 0);
        return r;
    }

    private LinearLayout.LayoutParams cardParams(int i) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(150), ViewGroup.LayoutParams.WRAP_CONTENT);
        if (i > 0) lp.leftMargin = dp(16);
        return lp;
    }

    private TextView section(String label) {
        TextView t = text(label, 13, medium, 0xCCFFFFFF);
        t.setLetterSpacing(0.16f);
        t.setPadding(0, dp(16), 0, 0);
        return t;
    }

    private View card(View preview, String mood, String hint, String id, LiveWallpaperView live) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setClipChildren(false);
        FrameLayout thumb = new FrameLayout(this);
        thumb.setFocusable(true);
        thumb.setClickable(true);
        thumb.addView(preview, match());
        thumb.setForeground(getDrawable(R.drawable.focus_ring));
        thumb.setClipToOutline(true);
        thumb.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(12));
            }
        });
        c.addView(thumb, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(84)));
        TextView name = text(mood, 16, semibold, 0xFFFFFFFF);
        name.setPadding(dp(2), dp(8), 0, 0);
        c.addView(name);
        TextView sub = text(hint, 12, regular, 0xB3FFFFFF);
        sub.setPadding(dp(2), 0, 0, 0);
        c.addView(sub);
        // "In use" badge on the thumbnail itself
        TextView mark = text("\u2713 In use", 11, semibold, 0xFF0B0E12);
        android.graphics.drawable.GradientDrawable pillBg = new android.graphics.drawable.GradientDrawable();
        pillBg.setColor(0xFF7BE3A0);
        pillBg.setCornerRadius(dp(10));
        mark.setBackground(pillBg);
        mark.setPadding(dp(8), dp(2), dp(8), dp(2));
        FrameLayout.LayoutParams mlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        mlp.setMargins(0, dp(6), dp(6), 0);
        thumb.addView(mark, mlp);
        mark.setTag(thumb);
        marks.add(mark);
        markIds.add(id);

        thumb.setOnFocusChangeListener((v, f) -> {
            Motion.focus(v, f, 1.08f, dp(3));
            if (live != null) live.setRunning(f);
        });
        thumb.setOnClickListener(v -> {
            v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(Motion.ms(90)).setInterpolator(Motion.STANDARD)
                    .withEndAction(() -> Motion.focus(v, v.hasFocus(), 1.08f, dp(3))).start();
            choose(id);
        });
        return c;
    }

    private void choose(String id) {
        if (id.equals(Wallpapers.choice(this))) return;
        Wallpapers.set(this, id);
        setResult(RESULT_OK);
        showBackground(id, true);
        refresh();
        status.setText("Applied. Press Home to see it.");
    }

    /** Crossfades the full-screen preview behind the cards to the given choice. */
    private void showBackground(String id, boolean animate) {
        // live videos preview as a still here: Home plays them, and two players crossfading stalled the TV
        View v = Wallpapers.create(this, id, false, false);
        if (v == null) v = themePreview();
        if (v == null) return;
        bgHolder.addView(v, match());
        View old = currentBg;
        currentBg = v;
        if (!animate) {
            if (old != null) bgHolder.removeView(old);
            return;
        }
        v.setAlpha(0f);
        v.animate().alpha(1f).setDuration(Motion.ms(450)).setInterpolator(Motion.STANDARD)
                .withEndAction(() -> { if (old != null) bgHolder.removeView(old); }).start();
    }

    private View themePreview() {
        File f = Config.load(this).wallpaper;
        if (f == null || !f.isFile()) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = 2;
        o.inPreferredConfig = Bitmap.Config.RGB_565;
        Bitmap b = BitmapFactory.decodeFile(f.getPath(), o);
        if (b == null) return null;
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setImageBitmap(b);
        return iv;
    }

    private void refresh() {
        String c = Wallpapers.choice(this);
        boolean theme = Wallpapers.THEME.equals(c);
        themeRow.setText(theme ? "●  My theme wallpaper (in use)" : "My theme wallpaper");
        themeRow.setSelected(theme);
        for (int i = 0; i < marks.size(); i++) {
            boolean on = markIds.get(i).equals(c);
            TextView m = marks.get(i);
            m.animate().alpha(on ? 1f : 0f).setDuration(Motion.ms(200)).start();
        }
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, R.anim.popup_exit);
    }

    private TextView text(String s, float sp, Typeface tf, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(tf);
        t.setTextColor(color);
        return t;
    }

    private FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private int dp(float v) {
        return Math.round(v * density);
    }
}
