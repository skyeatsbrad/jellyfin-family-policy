# Native Android administrator app

Family Policy includes an optional native Android app for administrators. It uses
the same administrator-only Family Policy API as the Jellyfin settings page; it
is not a Jellyfin player and does not replace the plugin or independent guard.

The preview supports Android 8.0 (API 26) and later. The current build targets
Android 16 (API 36). It has been exercised on an API 36 emulator; physical-phone
validation is still pending.

## Install

1. Install and validate the Family Policy plugin and guard first. Confirm the web
   editor works before adding another administrator client.
2. Download `family-policy-android-v2-0.1.0-preview.2.apk` from
   [GitHub Releases](https://github.com/skyeatsbrad/jellyfin-family-policy/releases).
3. Allow the browser or file manager used for the initial download to install
   unknown apps, then open the APK. Android always requires user confirmation.
4. Open **Family Policy**, enter the Jellyfin base URL, and sign in with a Jellyfin
   administrator account.

The release certificate SHA-256 is:

```text
8B:6B:E8:FB:4D:D3:FF:40:69:5F:64:CC:0E:6D:1B:EF:02:DD:BD:A7:90:45:9E:4B:6B:32:C1:6E:3B:57:CD:40
```

Keep the same certificate for every future version. Android will reject an update
signed by a different key.

## Use

- Select a non-administrator Jellyfin user.
- Use the Jellyfin-inspired dark dashboard to review server and guard health,
  household profiles, default access, and title-specific exceptions.
- Choose a rating preset, including PG / TV-PG or lower, or remove the default
  rating ceiling while retaining explicit denies and advanced rules.
- Search or page through the library and set each title to **Inherit**, **Allow**,
  or **Deny**. Drafts remain available when switching between users.
- Apply all drafted account changes atomically. A stale server revision or guard
  failure leaves the drafts intact for review and retry.
- Use **Restore original account policy** to restore the native Jellyfin policy
  captured before Family Policy enrollment.

The title controls follow the web matrix semantics: direct exceptions use priority
100, deny wins a tied priority, IDs are normalized, and large title lists are
packed into bounded rules.

## Credentials and transport

Passwords are used only for Jellyfin authentication and are never stored. The
returned access token is encrypted with an Android Keystore AES-GCM key. Android
cloud backup and device-to-device extraction are disabled for app data.

Use HTTPS for remote servers. The app uses Android's normal system certificate
trust and does not bypass TLS validation. HTTP remains available for trusted local
installations, but the app displays an explicit warning before sending credentials.
Anyone who controls an unlocked device with a saved administrator session can
change household policy, so protect the device accordingly.

## Updates

The app checks the public GitHub releases once per day and also provides a manual
update check. An update is accepted only when all of these checks pass:

- the asset name is `family-policy-android-v<versionCode>-<versionName>.apk`;
- the version code is newer than the installed version;
- the GitHub asset URL is HTTPS;
- the asset is at most 100 MB and matches GitHub's reported size;
- GitHub supplies a `sha256:` digest and the downloaded APK matches it.

The verified APK is kept in app-private storage and exposed through a read-only,
non-exported provider only for the Android package installer. The first app-driven
update requires enabling **Install unknown apps** for Family Policy. Every update
still requires Android's installer confirmation; silent installation is not used.

## Build and test

Java 17 and Android SDK 36 are required.

```sh
./android/gradlew -p android --no-daemon \
  testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

The device tests use a self-contained local APK server and verify successful
download/hash validation, digest rejection, provider metadata/read access, and
handoff to Android's package installer.

Release signing is configured only through environment variables:

```text
FAMILY_POLICY_KEYSTORE
FAMILY_POLICY_STORE_PASSWORD
FAMILY_POLICY_KEY_ALIAS
FAMILY_POLICY_KEY_PASSWORD
```

Never commit the keystore or passwords. Preserve an encrypted backup of both; loss
of either prevents updates to already-installed copies.
