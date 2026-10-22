# Decisions

- Native Java/API30, no third-party Android UI or camera dependencies.
- One Camera2 SurfaceTexture → GLES → preview and MediaCodec Surface → H264/MP4. The HAL only needs one Camera2 output.
- Preserve source geometry and letterbox. Ordinary360 has useful1920×720 and perspective x720..1920; APA has useful1440×720 and perspective x560..1440. Camera2 may scale source720→1080; GPU output restores720-line proportions.
- Default is preview only. Auto-record is opt-in and persisted. Record/stop is manual with no old10-second limit. No audio/background service/boot receiver.
- A visible but unfocused widget keeps preview; onStop releases capture. No retry loop competing with factory360.
- Direct view selection instead of touch replay. Standalone fixed-command helper, existing su, firmware-hash gate, no Frida and no vendor-file changes.
- Keep camera ID selection distinct from front/rear view selection. Current view remains usable if privileged control is unavailable.
- Speed-dependent shutdown is unconfirmed; no synthetic speed or CAN-interlock bypass.
