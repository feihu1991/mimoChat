# Android Releases

Installable APK files are published on the repository Releases page instead of being committed into Git history.

## Required GitHub Actions secrets

Configure one permanent Android release signing key in repository Actions secrets:

- `RELEASE_KEYSTORE_BASE64`
- `RELEASE_STORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`

The same key must be used for every release. Keep an offline backup. Do not commit the keystore or passwords.

## Publishing

Android-related pushes to `master` run `.github/workflows/release.yml`. The workflow builds a signed APK, runs tests and lint, and publishes these files to GitHub Releases:

- `mimoChat-<version>.apk`
- `mimoChat-<version>.apk.sha256`

A release can also be started manually from Actions with a version such as `1.2.0`.

## In-app update

Open Settings, then Software update. The app checks the latest GitHub Release, downloads the APK, and opens the Android system installer.

Old CI Debug APKs used temporary debug signing. The first migration to the permanent release key may require uninstalling the old Debug build and installing the first signed Release manually. Later signed Releases can update in place.
