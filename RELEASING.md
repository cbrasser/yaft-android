# Releasing

Each release is one signed APK on GitHub Releases. Riders install it directly or through [Obtainium](https://github.com/ImranR98/Obtainium). F-Droid rebuilds the same tag from source and, when its build matches byte for byte, publishes this same signed APK. Users can switch between the two without reinstalling.

## One time: the signing key

The key is the app's identity forever: lose it and no installed copy can be updated. Make it once, outside the repo, and back up the file and its password (for example in a password manager).

```bash
keytool -genkeypair -v -keystore ~/yaft-release.jks -alias yaft -keyalg RSA -keysize 4096 -validity 36500 -dname "CN=yaft"
```

Give the key to the release workflow as repository secrets:

```bash
base64 -i ~/yaft-release.jks | gh secret set KEYSTORE_BASE64 -R cbrasser/yaft-android
gh secret set KEYSTORE_PASSWORD -R cbrasser/yaft-android
gh secret set KEY_ALIAS -R cbrasser/yaft-android --body yaft
gh secret set KEY_PASSWORD -R cbrasser/yaft-android
```

The certificate fingerprint goes into the F-Droid recipe (`AllowedAPKSigningKeys`, lowercase, no colons):

```bash
keytool -list -v -keystore ~/yaft-release.jks -alias yaft | grep SHA256
```

## Each release

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`.
3. Commit, then tag and push: `git tag v<versionName> && git push origin main --tags`.
4. The Release workflow builds, signs and attaches `yaft-<versionName>.apk`. F-Droid notices the tag within a few days.

## Reproducible builds

F-Droid only publishes our APK if its rebuild is identical apart from the signature. `app/build.gradle.kts` turns off the two known sources of differences (version-control info and baseline profiles). Keep JDK 21 in CI, since F-Droid's recipe builds with it too. To check locally that two clean builds match:

```bash
./gradlew clean assembleRelease && shasum -a 256 app/build/outputs/apk/release/*.apk
```

## F-Droid submission (first release only)

1. Fork [fdroiddata](https://gitlab.com/fdroid/fdroiddata) on GitLab and add `metadata/site.yaft.app.yml` from [fdroid/site.yaft.app.yml](fdroid/site.yaft.app.yml), with the real `AllowedAPKSigningKeys`.
2. Check it with `fdroid lint site.yaft.app` and `fdroid build -v -l site.yaft.app` (fdroidserver, or its Docker image).
3. Open the merge request. Say that the app was written with an AI coding assistant and reviewed by the maintainer (F-Droid's interim policy on generative AI asks for human review and encourages disclosure).
