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

### Release

- Bump `versionCode` and `versionName` in `app/build.gradle.kts`, add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`, and tag `v<versionName>`. F-Droid picks up new tags.
- For a signed APK on GitHub releases, create `keystore.properties` (gitignored) with `storeFile`, `storePassword`, `keyAlias` and `keyPassword`, then run `./gradlew assembleRelease`.

## F-Droid

The store listing is in `fastlane/metadata/android/`. A draft of the F-Droid build recipe is in [fdroid/site.yaft.app.yml](fdroid/site.yaft.app.yml); submit it as a merge request to [fdroiddata](https://gitlab.com/fdroid/fdroiddata). Expect the `NonFreeNet` anti-feature, since the yaft.site server isn't open source.

## Licence

GPL-3.0-only. See [LICENSE](LICENSE).
