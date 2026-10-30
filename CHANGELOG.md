# Changelog

## 3.2 (30 October 2026)
- **Continue watching:** a row above the favourites in All apps, with title, poster and progress. Android keeps each
  app's own Watch Next list behind a system-only permission, so Lumen builds this list itself from what apps announce
  through media sessions. It stays on the TV. Music apps and anything under a minute are left out; hold OK to remove one.
- **Favourites:** hold OK on any app to move it, or add it to or remove it from favourites. Home and All apps now share
  one saved order. The hold menu also keeps App info.
- **Remote buttons:** Launcher settings > Remote buttons: press a button and choose what it does, from any app.
  Arrows, OK, Back, Home and Power can't be changed. Needs the "Lumen remote buttons" accessibility service.
- **Smoother Home:** glass and tiles are cached on the GPU, so moving between apps no longer drops frames over a live
  wallpaper. Video starts and stops off the main thread.
- **APPS transition rebuilt:** the wallpaper stays put and only the content changes. Zooming two full-screen windows
  over a video was too much for this TV's Mali-470 GPU. Heavy setup now finishes before the fade begins, and the video
  carries on from the same frame between screens, with no fade and no jump.

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
