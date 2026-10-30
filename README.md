# yaft for Android

Record hydrofoil sessions with your phone's GPS and send them to [yaft.site](https://yaft.site) ("yet another foil tracker") for the full analysis.

- **Record:** pick the category, start, and pocket the phone. A foreground service logs GPS at 1 Hz with the screen off. Every fix is appended to disk right away, so a killed app resumes the same session. Stopping takes a long press.
- **View:** the track (on foil in ink, the rest grey) and the basic numbers, offline.
- **Save:** sessions stay on the phone. Signed-in riders upload them to yaft.site, or anyone can share the GPX.
- **From a watch:** share a GPX from the watch's app (Gadgetbridge, Zepp, a file manager) to yaft and it opens as a session: pick what you rode, then upload. The file goes up as it came, so the watch's heart rate and name reach yaft. Sharing the same track again opens the session it already is.
- **History:** signed-in riders see all their yaft sessions next to the phone's (from `GET /api/app/sessions`, cached for offline use). A phone session deleted on the website goes back to "Not uploaded"; the phone keeps its copy.
- **The session on yaft:** opening a session that's on yaft, including a phone recording once it's uploaded, shows the website's analysis: the track on a map (OpenFreeMap's tiles, the website's basemap; offline, the track on its own), the numbers sheet (speeds, distance, time on foil, wind, turns, heart rate when there is some) and the runs or flights; tapping one highlights it on the track. Below them, forms change what you rode, the wind (direction, base and gusts), and your notes and rating (`PATCH /api/app/sessions/<id>`); a new category or wind direction redoes the analysis on yaft and the screen redraws from the answer. Editing needs a connection: nothing is queued offline. Gear is set on the website. It comes from `GET /api/app/sessions/<id>`, which servers report as API version 2 in their config; with an older server the app shows the summary from the list. The analysis isn't ported: the website stays the one place it's worked out.

No Google Play Services, no trackers: GPS comes from `LocationManager`, HTTP from `HttpURLConnection`. The one library with native code is [MapLibre Native](https://github.com/maplibre/maplibre-native) (BSD-2-Clause) for the session view's map; its tiles come from [OpenFreeMap](https://openfreemap.org), which so learns roughly where a session was, as the website's map does.

## How upload works

1. `GET <site>/api/app/config` returns the Supabase URL and publishable key that the site uses.
2. The rider signs in with email and password (Supabase Auth `grant_type=password`), the same account as the website.
3. The GPX goes to the rider's own storage folder, `tracks/<user id>/<session id>.gpx`.
4. `POST <site>/api/app/sessions` with the bearer token and `{ id, category, place, timeZone, fileName, allowDuplicate }`. The server parses and analyses the file, takes the gear of the rider's last session of that category, and stores it. It returns 201, or 409 with `duplicateOf` when the rider already has a session that looks the same.

The phone's Doppler speed is written to the GPX's Garmin `TrackPointExtension` speed field, so yaft treats it like a watch's speed.

## Build

Needs JDK 21 and the Android SDK (platform 37).

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Debug builds allow plain HTTP to `127.0.0.1`, `localhost` and `10.0.2.2`. To try against a local yaft, run its dev server and local Supabase, then `adb reverse tcp:3000 tcp:3000` and `adb reverse tcp:55421 tcp:55421`, and in the app choose Account → Use another server → `http://127.0.0.1:3000`.

## Install

- **GitHub Releases:** download `yaft-<version>.apk` from [Releases](https://github.com/cbrasser/yaft-android/releases), or add this repo to [Obtainium](https://github.com/ImranR98/Obtainium) for updates.
- **F-Droid:** submission planned. F-Droid will carry the same signed APK (reproducible builds), with the `NonFreeNet` anti-feature, since the yaft.site server isn't open source.

Releasing, signing and the F-Droid submission are in [RELEASING.md](RELEASING.md).

## How it was made

yaft for Android was written with Claude Code, an AI coding assistant, working with the maintainer.

## Licence

GPL-3.0-only. See [LICENSE](LICENSE).
