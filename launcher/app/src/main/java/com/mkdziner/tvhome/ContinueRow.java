package com.mkdziner.tvhome;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.util.List;

/** The Continue watching row above the favourites in All apps. Hidden while there is nothing to continue. */
final class ContinueRow {

    interface Host {
        void onRowDown();
        void onRowUp();
        void onRowFocused();
        void onRowMenu(WatchHistory.Item item, View card);
    }

    private static final int TOP_DP = 86, CARD_W_DP = 148, CARD_H_DP = 83, GAP_DP = 14;
    /** Everything the row needs above the favourites, in dp. */
    static final int NEEDED_DP = TOP_DP + 20 + CARD_H_DP + 46;

    private final Activity a;
    private final Host host;
    private final float density;
    private final Typeface semibold, medium;
    private final LinearLayout view, cards;
    private View lastFocused;
    private boolean fits = true, entering;

    ContinueRow(Activity a, FrameLayout root, Host host) {
        this.a = a;
        this.host = host;
        density = a.getResources().getDisplayMetrics().density;
        semibold = a.getResources().getFont(R.font.outfit_semibold);
        medium = a.getResources().getFont(R.font.outfit_medium);
        view = new LinearLayout(a);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setClipChildren(false);
        view.setClipToPadding(false);
        view.setVisibility(View.GONE);
        TextView title = text(a.getString(R.string.continue_watching), 13, semibold, 0xE6FFFFFF);
        title.setLetterSpacing(0.14f);
        title.setShadowLayer(8, 0, 0, 0x80000000);
        title.setPadding(0, 0, 0, dp(2));
        view.addView(title);
        HorizontalScrollView scroll = new HorizontalScrollView(a);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        scroll.setFocusable(false);
        scroll.setPadding(0, dp(4), dp(40), dp(2));
        cards = new LinearLayout(a);
        cards.setOrientation(LinearLayout.HORIZONTAL);
        cards.setClipChildren(false);
        cards.setClipToPadding(false);
        scroll.addView(cards);
        view.addView(scroll);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP);
        lp.topMargin = dp(TOP_DP);
        lp.leftMargin = dp(58);
        root.addView(view, lp);
    }

    /** Rebuilds the row from the saved list. */
    View view() {
        return view;
    }

    /** While the screen fades in, the fade owns the row's alpha and position. */
    void setEntering(boolean on) {
        entering = on;
    }

    void refresh() {
        List<WatchHistory.Item> items = WatchHistory.sortedForRow(a);
        cards.removeAllViews();
        lastFocused = null;
        for (int i = 0; i < items.size(); i++) {
            if (!installed(items.get(i).pkg)) continue;
            cards.addView(card(items.get(i)));
        }
        update();
    }

    /** The grid moved or was laid out: the row follows it up and fades, and steps aside when there is no room. */
    void setGrid(int dockTopPx, int scrolledPx) {
        fits = dockTopPx >= dp(NEEDED_DP - 12);
        update();
        if (entering) return;
        view.setTranslationY(-scrolledPx);
        float alpha = 1f - Math.min(1f, scrolledPx / (90f * density));
        view.setAlpha(alpha);
        if (visible() != (alpha > 0.02f) && view.getVisibility() != View.GONE) {
            view.setVisibility(alpha > 0.02f ? View.VISIBLE : View.INVISIBLE);
        }
    }

    private void update() {
        view.setVisibility(fits && cards.getChildCount() > 0 ? View.VISIBLE : View.GONE);
    }

    boolean visible() {
        return view.getVisibility() == View.VISIBLE;
    }

    void focus() {
        if (lastFocused != null && lastFocused.isAttachedToWindow()) {
            lastFocused.requestFocus();
        } else if (cards.getChildCount() > 0) {
            ((ViewGroup) cards.getChildAt(0)).getChildAt(0).requestFocus();
        }
    }

    private boolean installed(String pkg) {
        try {
            a.getPackageManager().getApplicationInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private View card(WatchHistory.Item item) {
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        FrameLayout tile = new FrameLayout(a);
        tile.setFocusable(true);
        tile.setClickable(true);
        tile.setBackgroundColor(0x66101216);
        tile.setClipToOutline(true);
        tile.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(11));
            }
        });
        tile.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        ImageView img = new ImageView(a);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        img.setImageDrawable(banner(item.pkg));
        tile.addView(img, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        loadArt(item, img);
        float p = item.progress();
        if (p >= 0) {
            View track = new View(a);
            track.setBackgroundColor(0x66FFFFFF);
            FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4), Gravity.BOTTOM);
            tile.addView(track, tl);
            View bar = new View(a);
            bar.setBackgroundColor(0xFFFFBE5C);
            FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(Math.max(dp(12), (int) (dp(CARD_W_DP) * p)), dp(4), Gravity.BOTTOM);
            tile.addView(bar, bl);
        }
        tile.setForeground(a.getDrawable(R.drawable.focus_ring));
        tile.setContentDescription(item.title.isEmpty() ? appName(item.pkg) : item.title);
        tile.setOnClickListener(v -> WatchHistory.open(a, item));
        tile.setOnLongClickListener(v -> {
            host.onRowMenu(item, v);
            return true;
        });
        tile.setOnFocusChangeListener((v, f) -> {
            Motion.focus(v, f, 1.07f, 3 * density);
            if (f) {
                lastFocused = v;
                host.onRowFocused();
            }
        });
        tile.setOnKeyListener((v, code, e) -> {
            if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
                host.onRowDown();
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_UP) {
                host.onRowUp();
                return true;
            }
            return false;
        });
        col.addView(tile, new LinearLayout.LayoutParams(dp(CARD_W_DP), dp(CARD_H_DP)));
        String head = item.title.isEmpty() ? appName(item.pkg) : item.title;
        TextView title = text(head, 13, semibold, 0xFFFFFFFF);
        title.setPadding(dp(1), dp(6), 0, 0);
        col.addView(title, new LinearLayout.LayoutParams(dp(CARD_W_DP), ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView sub = text(detail(item), 11, medium, 0xCCFFFFFF);
        sub.setPadding(dp(1), dp(1), 0, 0);
        col.addView(sub, new LinearLayout.LayoutParams(dp(CARD_W_DP), ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(GAP_DP);
        col.setLayoutParams(lp);
        return col;
    }

    /** Line under the title: the app, and time left when the app reports it, otherwise when it was watched. */
    private String detail(WatchHistory.Item item) {
        String app = appName(item.pkg);
        if (item.title.isEmpty()) return relative(item.updated);
        if (item.duration > 0) {
            long left = Math.max(1, (item.duration - item.position) / 60_000);
            return a.getString(R.string.continue_left, app, left);
        }
        return app + "  ·  " + relative(item.updated);
    }

    private String relative(long when) {
        return DateUtils.getRelativeTimeSpanString(when, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE).toString();
    }

    private String appName(String pkg) {
        try {
            PackageManager pm = a.getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    private Drawable banner(String pkg) {
        try {
            PackageManager pm = a.getPackageManager();
            Drawable d = pm.getApplicationBanner(pkg);
            return d != null ? d : pm.getApplicationIcon(pkg);
        } catch (PackageManager.NameNotFoundException e) {
            return new GradientDrawable();
        }
    }

    private void loadArt(WatchHistory.Item item, ImageView img) {
        File f = WatchHistory.artFile(a, item);
        if (f == null) return;
        Net.IO.execute(() -> {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap b;
            try {
                b = BitmapFactory.decodeFile(f.getPath(), o);
            } catch (OutOfMemoryError e) {
                return;
            }
            if (b != null) img.post(() -> img.setImageBitmap(b));
        });
    }

    private TextView text(String s, float sp, Typeface tf, int color) {
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(tf);
        t.setTextColor(color);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setShadowLayer(6, 0, 0, 0x80000000);
        return t;
    }

    private int dp(float v) {
        return Math.round(v * density);
    }
}
