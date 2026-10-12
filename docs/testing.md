# Compatibility and validation

## Executed local evidence

Local native/browser acceptance passed on unmodified Jellyfin 10.11.11, 12.1 and
12.2. See validation/2026-10-12-server-10.11.11-windows.json,
validation/2026-10-12-server-10.11.11-windows-matrix.json,
validation/2026-10-12-staging-webshell.json,
validation/2026-10-07.json and validation/2026-10-07-server-12.2.json for
sanitized reports. The 10.11.11 run used the net9.0 plugin and guard targets on
Windows x64. The 28 core behavioral tests passed on both Windows x64 and macOS arm64.
Core tests express policy behavior; the integration harness builds a fresh,
loopback-only server, generates permitted media, creates synthetic identities,
tests privileged/child paths and removes successful disposable data.

The repeatable browser harness uses real Chromium and native Jellyfin APIs with a
small ApiClient compatibility shim. An additional isolated staging pilot loaded the
embedded page through the installed Jellyfin 10.11.11 web shell at 390x844,
verified contained horizontal scrolling with four account columns, preserved
unsaved drafts across matrix reloads and reported no console errors. Swiftfin,
Apple TV, iPhone and other real child-device acceptance is still pending.

Native acceptance covers administrator-only endpoints, imported-label rejection,
atomic multi-account policy updates, duplicate-account rejection, individual R
exception, denied other child, series grant/episode block, catalog visibility,
actual file and HLS segment delivery, timed ongoing physical-file cutoff,
post-cutoff denial, cross-user query rejection, legacy segment rejection,
original-policy restoration and independent fallback after plugin removal.
Chromium additionally covers paginated matrix loading, per-user title updates,
stale-revision conflicts and preservation of unrelated policy drafts.

## Run locally

Build core/plugin/guard with the pinned SDK. Build or unpack an **unmodified**
Jellyfin 10.11.11, 12.1 or 12.2 server with the corresponding official Jellyfin
FFmpeg build. Then:

```sh
python -m pip install -r tests/integration/requirements.txt
python -m playwright install chromium
export DOTNET_EXE="$(command -v dotnet)"
export JELLYFIN_SERVER_DLL="/path/to/unmodified/server/jellyfin.dll"
export JELLYFIN_FFMPEG="/path/to/jellyfin-ffmpeg/ffmpeg"
FAMILY_POLICY_BROWSER_TESTS=1 python tests/integration/run.py
```

Port 18097 must be free. The harness uses fresh private temporary directories,
no household media/credentials. Failed diagnostics are retained locally for
inspection; successful directories are removed. Never upload raw diagnostics.
On Windows, set `FAMILY_POLICY_TEST_ROOT` to a long-form path if the system temp
directory resolves through an 8.3 alias. Set `FAMILY_POLICY_PLUGIN_TFM`,
`FAMILY_POLICY_GUARD_TFM` and `FAMILY_POLICY_GUARD_DOTNET` when validating the
net9.0 backport against Jellyfin 10.11.11.

Transcoder cleanup follows Jellyfin's native idle cleanup. The worker does not
terminate all jobs by a caller-controlled device ID.

## Remaining production release gates

- Apple TV/iPhone and other real child sessions, including buffered playback at
  cutoffs and reconnection.
- Wider routes: universal audio, remote sources, extras/trailers, attachments,
  subtitles and alternate-source/edition attempts; unsupported paths fail closed.
- Labels and policy persistence across replacement IDs; this preview does not
  auto-rematch provider IDs.
- Large-library metadata/reconciliation latency and native pagination count accuracy
  during cache lag; streamed access is evaluated separately.
- Guard supervision and failure latency on each intended production deployment.
- Independent community review before describing the preview as production-ready.

Automated build matrices do not imply device compatibility. Update this page only
from dated execution results, not assumptions.
