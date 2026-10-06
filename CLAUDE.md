# yaft for Android

The phone recorder for [yaft.site](https://yaft.site); the website lives in the private `cbrasser/yaft` repo. Read README.md first.

## Commands

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug   # needs JAVA_HOME (JDK 21) and ANDROID_HOME
```

## Layout (`app/src/main/java/site/yaft/app/`)

- `track/`: `Category` (keep it in step with the website's `lib/categories.ts`), `TrackPoint` and distance, `LiveStats` (the live numbers; the website does the real analysis), and `Gpx` (writes the format the website's `parseGpx` reads).
- `record/`: `RecordingService` (foreground service, GPS at 1 Hz, appends every fix to disk, resumes after being killed) and `Recorder` (shared state).
- `data/SessionStore`: sessions as `files/sessions/<id>.gpx` plus `.json`; recordings in progress in `files/recording/`.
- `net/`: `Account` (sign-in via the site's `/api/app/config`, then Supabase Auth), `Uploader` (storage upload, then `POST /api/app/sessions`) and `Http`.
- `ui/`: Compose screens. The colours are the website's tokens (`Yaft` in `Theme.kt`): ink outlines, hard shadows, the category hue as the ground, text on white leaves only, tabular figures for numbers.

## Rules

- F-Droid: free dependencies only. No Google Play Services, Firebase, analytics or crash reporters; add a dependency only when it's clearly worth it.
- The session id is a UUID made on the phone and kept on yaft.site, so uploads can be retried safely.
- Never commit keystores or `keystore.properties`.
