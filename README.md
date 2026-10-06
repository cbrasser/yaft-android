# yaft for Android

Record hydrofoil sessions with your phone's GPS and send them to [yaft.site](https://yaft.site) ("yet another foil tracker") for the full analysis.

- **Record:** pick the category, start, and pocket the phone. A foreground service logs GPS at 1 Hz with the screen off. Every fix is appended to disk right away, so a killed app resumes the same session. Stopping takes a long press.
- **View:** the track (on foil in ink, the rest grey) and the basic numbers, offline.
- **Save:** sessions stay on the phone. Signed-in riders upload them to yaft.site, or anyone can share the GPX.

No Google Play Services, no trackers, no networking library: GPS comes from `LocationManager`, HTTP from `HttpURLConnection`.

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
