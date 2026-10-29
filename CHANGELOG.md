# Changelog

## 3.1 (29 October 2026)
- **Trending is off for now.** Down on Home no longer opens it and nothing is fetched from TMDB; it froze the TV on
  some opens. It will come back once it is rebuilt to be lighter.
- **Live wallpapers rebuilt:** the video now draws like any other view (TextureView), so it fades and moves with the
  screen instead of flashing black or tearing during transitions. A still frame shows instantly and the video fades in
  over it.
- **One video decoder at a time:** Home and All apps both play the live wallpaper, handing one decoder over between
  them and carrying on from the same moment, so two players never overlap on the 2 GB TV. The Wallpapers screen
  previews it as a still.
- **Faster screen opens:** the wallpaper and its glass blur are decoded once and reused, not rebuilt on every open.
- **Calmer return to Home:** the cards fade and rise a little instead of scaling, which was dropping frames over video.

## 3.0 · Lumen (28 October 2026)
First release under its own name and app ID (`com.mkdziner.tvhome`), with its own icon and TV banner.
Everything from the CleanYourTV-based builds below is included.

## Before Lumen (20–28 October 2026, as a CleanYourTV fork)
- **Wallpapers by mood:** Static and Live, five each; your own images and videos via `wallpapers.json`; videos stop while an app is open.
- **Motion:** shared curves, spring focus, menus that fade out, durations independent of the system animation scale.
- **Home:** greeting and date, HOME · APPS tabs, time + photo pill with a glass control panel, now-playing card, weather with details, five favourite apps.
- **Trending:** this week's titles from TMDB by genre, a page below Home (press Down), prefetched so it opens filled.
- **Liquid glass:** clear glass with edge refraction and a specular rim, computed once from the wallpaper.
