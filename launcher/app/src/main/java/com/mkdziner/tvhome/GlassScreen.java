package com.mkdziner.tvhome;

import android.app.Activity;
import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Outline;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;

/** Shared by the dashboard and the browse screen: wallpaper, liquid glass, theme values, opening things. */
abstract class GlassScreen extends Activity {

    protected float density;
    /** The wallpaper choice this screen was built with; a different one means rebuild (see DashboardActivity). */
    protected String appliedWallpaper = "";
    protected Config cfg;
    /** The theme's config.json, for keys Config does not know (weather_city). */
    protected JSONObject themeJson = new JSONObject();

    protected void setUpScreen(ViewGroup root) {
        density = getResources().getDisplayMetrics().density;
        cfg = Config.load(this);
        root.setBackgroundColor(cfg.backgroundColor);
        Motion.init(this);
        LiquidGlass.clear();
        appliedWallpaper = Wallpapers.choice(this);
        if (!Wallpapers.applyMood(this, root, playLiveWallpaper()) && cfg.wallpaper != null) showWallpaper(root, cfg.wallpaper);
        File json = new File(Config.dir(this), "config.json");
        if (json.isFile()) {
            try (FileInputStream in = new FileInputStream(json)) {
                byte[] b = new byte[(int) Math.min(json.length(), 256 * 1024)];
                int n = in.read(b);
                themeJson = new JSONObject(new String(b, 0, Math.max(0, n), "UTF-8"));
            } catch (Exception ignored) {
            }
        }
    }

    /** Only Home plays a live video; other screens show its still frame, so two decoders never overlap. */
    protected boolean playLiveWallpaper() {
        return false;
    }

    private void showWallpaper(ViewGroup root, File file) {
        try {
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            Bitmap cached = Wallpapers.cachedTheme(file);
            if (cached != null) {
                LiquidGlass.setWallpaper(cached, dm.widthPixels, dm.heightPixels);
                ImageView wall = new ImageView(this);
                wall.setScaleType(ImageView.ScaleType.CENTER_CROP);
                wall.setImageBitmap(cached);
                root.addView(wall, 0, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
                return;
            }
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getPath(), o);
            int w = o.outWidth, h = o.outHeight;
            if (w <= 0 || h <= 0) return;
            int sample = WallpaperSize.sampleSize(w, h, dm.widthPixels, dm.heightPixels);
            if (!WallpaperSize.accepted(w / sample, h / sample)) return;
            o.inJustDecodeBounds = false;
            o.inSampleSize = sample;
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap bmp = BitmapFactory.decodeFile(file.getPath(), o);
            if (bmp == null) return;
            Wallpapers.putTheme(file, bmp);
            LiquidGlass.setWallpaper(bmp, dm.widthPixels, dm.heightPixels);
            ImageView wall = new ImageView(this);
            wall.setScaleType(ImageView.ScaleType.CENTER_CROP);
            wall.setImageBitmap(bmp);
            root.addView(wall, 0, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        } catch (Exception | OutOfMemoryError ignored) {
            // the plain background colour stays
        }
    }

    /** Glass card behind a view; tint is a dark veil for legibility (0x10000000 light ... 0x47000000 dark). */
    protected void glass(View v, float radiusDp, int tint) {
        v.setBackground(new LiquidGlass.CardDrawable(v, density, radiusDp * density, tint));
        // Cached on the GPU: a live wallpaper redraws the screen 30 times a second, and repainting every gradient,
        // rim and rounded corner of the glass each time is what made focus moves stutter on this TV.
        v.setLayerType(View.LAYER_TYPE_HARDWARE, null);
    }

    /** Glass pill that turns solid white when focused (the existing pill style). */
    protected void pill(View v) {
        v.setBackground(new LiquidGlass.PillDrawable(v, v.getBackground(), density));
    }

    protected void round(View v, float radiusDp) {
        final float r = radiusDp * density;
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), r);
            }
        });
        v.setClipToOutline(true);
    }

    protected void openPackage(String pkg) {
        Intent i = getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = getPackageManager().getLaunchIntentForPackage(pkg);
        open(i);
    }

    protected void open(Intent i) {
        if (i == null) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
    }

    /** Google search on the TV (Assistant), optionally for a title. */
    protected void search(String query) {
        Intent i = new Intent("android.search.action.GLOBAL_SEARCH");
        if (query != null) i.putExtra(SearchManager.QUERY, query);
        if (i.resolveActivity(getPackageManager()) == null) i = new Intent(Intent.ACTION_ASSIST);
        open(i);
    }

    protected Intent settingsIntent(String action) {
        return new Intent(action);
    }
}
