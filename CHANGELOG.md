# Changelog

## Android 0.1.0 preview 2 — 2026-10-12

- Rebuilt the administrator experience around a Jellyfin-inspired dark media
  dashboard with cyan-purple branding, rounded cards and clearer hierarchy.
- Added prominent plugin/guard health, themed sign-in fields, dark spinner
  menus, compact household and library controls, and a fixed draft/apply area.
- Reworked title rows with media-type badges and segmented Inherit/Allow/Deny
  controls that show the active state and pending changes more clearly.
- Added explicit Android system-bar inset handling and validated the layout at
  normal and compact phone sizes on an API 36 emulator.
- Preserved all authentication, encrypted-session, atomic update, conflict,
  restore, updater and server-side enforcement behavior.

## 0.1.0 preview — 2026-10-12

- Deterministic content and time policies, three-valued conditions, explicit conflict
  priority, hard blocks, expiry, overnight/DST handling and decision explanations.
- PG preset plus parent rule editor and per-title/episode decisions.
- Responsive paginated library-by-user matrix with atomic Inherit/Allow/Deny
  updates and per-account draft preservation.
- Parent-owned subject/category/franchise annotations; imported labels cannot grant.
- Administrator-only API, revision conflict checks, atomic state writes and bounded audit.
- Native browsing tags, current-policy playback filter, ongoing-response cutoff,
  attempted client Stop, independent health guard and captured-policy restoration.
- Generated-media/native-API/HLS/browser/recovery acceptance harness and pinned CI.
- Native Android administrator app for user selection, rating presets, paginated
  title Inherit/Allow/Deny controls, atomic saves, conflict handling and restoration.
- Android Keystore AES-GCM session storage with passwords never retained, backup
  exclusion, normal platform TLS validation and an explicit cleartext warning.
- Daily/manual GitHub APK updates with version, filename, HTTPS, size and SHA-256
  verification plus private-provider handoff to Android's package installer.

The net9 backport passed an installed Jellyfin 10.11.11 web-shell pilot at a
390x844 viewport. The Android app passed API 36 emulator workflows and verified
installer handoff. Experimental: physical household devices, broader route matrix,
large-library behavior, Seerr integration and stable provider rematching are pending.
