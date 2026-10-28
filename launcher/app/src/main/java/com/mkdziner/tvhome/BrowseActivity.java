package com.mkdziner.tvhome;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

/**
 * Browse (mkdziner design, Frame 2): a glass panel with three genre stacks of trending posters from TMDB, and
 * a glass music bar. OK on a stack searches for its front title on the TV; long-press OK swaps the posters.
 * On the music bar, OK plays or pauses and Left / Right skip tracks.
 */
public class BrowseActivity extends GlassScreen implements NowPlaying.Listener {

    private final Handler main = new Handler(Looper.getMainLooper());
    private NowPlaying nowPlaying;
    private final View[] stacks = new View[3];
    private final ImageView[] fronts = new ImageView[3], backs = new ImageView[3];
    private final TextView[] genres = new TextView[3], titles = new TextView[3];
    private final Tmdb.Title[][] shown = new Tmdb.Title[3][2];
    private TextView barText, message;
    private ImageView barPlay, barArt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_browse);
        FrameLayout root = findViewById(R.id.browseRoot);
        setUpScreen(root);
        String kind = getIntent().getStringExtra("kind");
        if (kind == null) kind = "featured";

        glass(findViewById(R.id.browsePanel), 28, 0x14000000);
        ((TextView) findViewById(R.id.browseHeading)).setText(
                (kind.equals("movies") ? "Movies" : kind.equals("shows") ? "Shows" : "Featured") + "  ·  trending this week");
        message = findViewById(R.id.browseMessage);

        int[] ids = {R.id.stack0, R.id.stack1, R.id.stack2};
        int[] fr = {R.id.front0, R.id.front1, R.id.front2};
        int[] bk = {R.id.back0, R.id.back1, R.id.back2};
        int[] gn = {R.id.genre0, R.id.genre1, R.id.genre2};
        int[] tt = {R.id.title0, R.id.title1, R.id.title2};
        for (int i = 0; i < 3; i++) {
            final int k = i;
            stacks[i] = findViewById(ids[i]);
            fronts[i] = findViewById(fr[i]);
            backs[i] = findViewById(bk[i]);
            genres[i] = findViewById(gn[i]);
            titles[i] = findViewById(tt[i]);
            round(fronts[i], 10);
            round(backs[i], 10);
            fronts[i].setBackgroundColor(0x33FFFFFF);
            backs[i].setBackgroundColor(0x22FFFFFF);
            stacks[i].setOnFocusChangeListener((v, f) -> Motion.focus(v, f, 1.05f, 2 * density));
            stacks[i].setOnClickListener(v -> {
                if (shown[k][0] != null) search(shown[k][0].name);
            });
            stacks[i].setOnKeyListener((v, code, e) -> {
                if (e.getAction() == KeyEvent.ACTION_DOWN && code == KeyEvent.KEYCODE_DPAD_UP) {
                    finish();
                    return true;
                }
                return false;
            });
            stacks[i].setOnLongClickListener(v -> {
                swap(k);
                return true;
            });
            stacks[i].setVisibility(View.INVISIBLE);
        }

        View bar = findViewById(R.id.musicBar);
        glass(bar, 29, 0x2E000000);
        barText = findViewById(R.id.barText);
        barPlay = findViewById(R.id.barPlay);
        barArt = findViewById(R.id.barArt);
        round(barArt, 8);
        bar.setOnClickListener(v -> nowPlaying.playPause());
        bar.setOnKeyListener((v, code, e) -> {
            if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (code == KeyEvent.KEYCODE_DPAD_LEFT) { nowPlaying.previous(); return true; }
            if (code == KeyEvent.KEYCODE_DPAD_RIGHT) { nowPlaying.next(); return true; }
            if (code == KeyEvent.KEYCODE_DPAD_DOWN) return true;
            return false;
        });
        nowPlaying = new NowPlaying(this, this);
        load(kind);
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.hold, R.anim.slide_down_out);
    }

    @Override
    protected void onResume() {
        super.onResume();
        nowPlaying.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        nowPlaying.stop();
    }

    private void load(String kind) {
        String key = Tmdb.key(Config.dir(this));
        if (key == null) {
            showMessage("Add your TMDB key to show trending titles.\n"
                    + "Save it as tmdb_key.txt in your theme folder and push the theme.");
            return;
        }
        List<Tmdb.Genre> ready = Tmdb.cached(kind);
        if (ready != null) {
            show(ready);
            return;
        }
        showMessage("Loading…");
        Net.IO.execute(() -> {
            try {
                List<Tmdb.Genre> list = Tmdb.load(key, kind);
                main.post(() -> show(list));
            } catch (Exception e) {
                main.post(() -> showMessage("Couldn't reach TMDB. Check the key and the network."));
            }
        });
    }

    private void showMessage(String text) {
        message.setText(text);
        message.setVisibility(View.VISIBLE);
        findViewById(R.id.musicBar).requestFocus();
    }

    private void show(List<Tmdb.Genre> list) {
        if (list.isEmpty()) {
            showMessage("Nothing trending right now.");
            return;
        }
        message.setVisibility(View.GONE);
        for (int i = 0; i < 3; i++) {
            if (i >= list.size()) {
                stacks[i].setVisibility(View.INVISIBLE);
                stacks[i].setFocusable(false);
                continue;
            }
            Tmdb.Genre g = list.get(i);
            genres[i].setText(g.name);
            shown[i][0] = g.titles.get(0);
            shown[i][1] = g.titles.size() > 1 ? g.titles.get(1) : null;
            titles[i].setText(shown[i][0].name);
            poster(fronts[i], shown[i][0]);
            poster(backs[i], shown[i][1]);
            stacks[i].setVisibility(View.VISIBLE);
            stacks[i].setAlpha(0f);
            stacks[i].setTranslationY(14 * density);
            stacks[i].animate().alpha(1f).translationY(0f).setStartDelay(Motion.ms(60L * i)).setDuration(Motion.ms(260))
                    .setInterpolator(Motion.EMPHASIZED).start();
        }
        stacks[0].requestFocus();
    }

    private void swap(int k) {
        if (shown[k][1] == null) return;
        Tmdb.Title t = shown[k][0];
        shown[k][0] = shown[k][1];
        shown[k][1] = t;
        titles[k].setText(shown[k][0].name);
        poster(fronts[k], shown[k][0]);
        poster(backs[k], shown[k][1]);
    }

    private void poster(ImageView into, Tmdb.Title t) {
        if (t == null) {
            into.setImageDrawable(null);
            return;
        }
        into.setTag(t.poster);
        Bitmap hit = Tmdb.POSTERS.get(t.poster);
        if (hit != null) {
            into.setImageBitmap(hit);
            into.setAlpha(1f);
            return;
        }
        into.setAlpha(0f);
        Net.IO.execute(() -> {
            Bitmap b = Tmdb.poster(t.poster);
            main.post(() -> {
                if (b != null && t.poster.equals(into.getTag())) {
                    into.setImageBitmap(b);
                    into.animate().alpha(1f).setDuration(Motion.ms(220)).start();
                }
            });
        });
    }

    @Override
    public void onNowPlaying(NowPlaying np) {
        if (!np.allowed()) barText.setText("Music access is off");
        else if (!np.hasSession() || np.title.isEmpty()) barText.setText("Nothing playing");
        else barText.setText(np.artist.isEmpty() ? np.title : np.title + "  ·  " + np.artist);
        barPlay.setImageResource(np.playing ? R.drawable.ic_pause : R.drawable.ic_play);
        if (np.art != null) {
            barArt.setImageBitmap(np.art);
            barArt.setPadding(0, 0, 0, 0);
        } else {
            barArt.setImageResource(R.drawable.ic_music);
            int p = (int) (8 * density);
            barArt.setPadding(p, p, p, p);
        }
    }
}
