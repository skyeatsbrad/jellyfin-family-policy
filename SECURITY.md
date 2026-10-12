# Security

Family Policy is an experimental community project. Use a disposable/staging
server before a household pilot. Real Apple TV/iPhone client validation is pending.
The Android administrator app has API 36 emulator coverage; physical-phone
validation is pending.

Please report suspected authorization bypasses privately using GitHub's private
vulnerability reporting when enabled; do not include tokens, household data or
working exploit details in a public issue. If private reporting is unavailable,
open an issue asking for a private contact without exploit details.

## Android administrator app

- The app requires a Jellyfin administrator account. It is an administration
  surface, not a child player; anyone with an unlocked saved session can change
  managed-account policy.
- Passwords are never stored. The Jellyfin access token is encrypted with an
  Android Keystore AES-GCM key, and app data is excluded from cloud backup and
  device transfer.
- HTTPS uses Android's system trust store without a trust-all bypass. HTTP is
  supported for trusted local deployments only and triggers a warning before
  credentials are sent.
- Updates are selected only from matching GitHub APK assets with a newer version
  code, HTTPS URL, bounded size and GitHub `sha256:` digest. The downloaded bytes
  must match both size and digest before installer access is granted.
- The verified APK remains in app-private storage. A non-exported, read-only
  provider grants access only to Android's package installer, and installation
  always requires user confirmation.
- All public APKs must retain this release certificate SHA-256:
  `8B:6B:E8:FB:4D:D3:FF:40:69:5F:64:CC:0E:6D:1B:EF:02:DD:BD:A7:90:45:9E:4B:6B:32:C1:6E:3B:57:CD:40`.
  Losing or replacing the private signing key prevents trusted updates.

## Boundaries

- Jellyfin administrators/API keys can change policies and remain trusted.
- Children must use their own accounts. A saved administrator login bypasses child rules.
- A separate guard, supervised with automatic restart, must cover every managed ID.
- Native content tags are a browsing cache; request-time decisions remain authoritative.
- A server cutoff stops future delivery and attempts Stop; already buffered/offline bytes cannot be recalled.
- Unbound anonymous legacy media routes are denied; no claim of universal client compatibility.
- Simultaneous loss of plugin and guard is outside the tested single-failure guarantee.
- Raw household config/state contains private IDs/labels; keep backups private.

See docs/validation/ for executed tests. Security and supported-version claims
must follow actual results. Every release gate should test both allowed and denied
paths, metadata changes, cross-user requests, live streams and failure recovery.
