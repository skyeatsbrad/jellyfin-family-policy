---
project: family-policy
status: active
updated: 2026-10-12
---

# Family Policy re-entry

Implemented: deterministic content/time core, administrator-only adapter, parent
annotations, native content tags, ongoing-stream authorization, independent guard,
revisioned persistence/restoration and PG/advanced editor. Source under src/;
upstream source is retained in Git history, never the release DLL.

The optional native Android administrator app under android/ supports API 26+ and
targets API 36. It uses administrator authentication, Android Keystore AES-GCM
token storage, rating/title drafts, atomic multi-account saves, restoration and
GitHub APK updates with filename/version/HTTPS/size/SHA-256 verification. API 36
emulator validation covered the complete policy workflow and Android installer
handoff; a physical-phone pilot remains pending.

The persistent Android release certificate SHA-256 is
8B:6B:E8:FB:4D:D3:FF:40:69:5F:64:CC:0E:6D:1B:EF:02:DD:BD:A7:90:45:9E:4B:6B:32:C1:6E:3B:57:CD:40.
Never rotate it for an update to the existing package.

Standing decisions: explicit priorities and deny-on-tie; unknown exceptions do not
grant and unknown denies block; independent content/time gates; no child self-grants;
parent annotations are separate from imported/AI tags. Runtime clock is server-owned.
Do not promise exact buffered-client cutoff or untested device compatibility.

Run the pinned SDK, locked restore, core tests and native/browser integration.
DEVELOPMENT-PLAN.md and docs/testing.md track delivery/remaining acceptance.
Household state remains private and outside source/fixtures. Public publication
is authorized by the user. Native Apple TV/iPhone pilot remains outstanding.

## Handoff Note
- Date: 2026-10-12
- Next: complete physical Android and child-client pilots plus remaining
  route/replacement/performance gates.
- Do not: install the upstream reference/PolicySpike DLL, publish credentials or
  claim production readiness from synthetic/browser acceptance alone.
