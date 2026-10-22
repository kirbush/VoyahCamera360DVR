# Contributor guidance

- Read README.md and docs/ARCHITECTURE.md before changing capture or view selection. Keep device observations separate from automated test results and hypotheses.
- Preserve the application ID and signing compatibility for updates. Never commit signing keys, vendor binaries, vehicle dumps, recordings or device logs. Use ignored diagnostics/ for local captures.
- Camera2 IDs are input identifiers, not front/rear labels. The privileged helper accepts only fixed front/rear requests for supported firmware. Do not add arbitrary packet interfaces or alter vehicle speed signals or interlocks.
- Keep preview-only startup as the default. Auto-record is opt-in. Preserve source aspect ratio, release capture when hidden and avoid retry loops competing with factory cameras.
- Run corePolicyTest and assembleDebug for application changes; run the native protocol test for helper changes. Compilation alone cannot establish hardware compatibility.
