# Project context

Read PLAN.md, TODO.md, DECISIONS.md and EVIDENCE.md before changing behavior. Keep confirmed device observations separate from offline compilation and hypotheses. User prefers concise operational updates and concrete results.

Source of truth is this Gradle project. Never commit vehicle dumps, videos, vendor APKs/shared libraries, SDK/NDK downloads or signing keys. Device logs belong in ignored diagnostics/.

Camera2 ID1 is the observed composite AVM feed. IDs0–3 are not front/rear labels. Root AVM helper supports only the two verified view-request encodings. Do not add arbitrary CAN/raw-packet interfaces or alter speed signals/interlocks. No new rooting, firmware replacement or persistent native injection is part of this application.

No car is currently connected. Do not claim hardware verification from unit tests. Evening checks are in docs/EVENING-TESTS.md. Keep the same package ID and use the existing local signing key when preparing an in-place update; the key is intentionally outside Git.
