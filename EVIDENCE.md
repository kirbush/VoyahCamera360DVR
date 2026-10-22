# Evidence — 2026-10-22

## Confirmed on head unit before disconnection

- Android11/API30, ARM64 application support, ARM32 V4L2 provider. Four Camera2 IDs; ID1 produces compositeAVM. No concurrentCameraSets announced. IDs0/2/3 opened but gave no frames during8-second tests.
- Source1 transport1920×1200; usable height720. A previous independent firmware camera fix was installed and survived full reboot. This APK does not install or replace that fix.
- Front and user-selected rear MP4:1200×720,H264,247 decodedframes each,9.95/9.88seconds,monotonic decodedtimestamps,no decodeerrors.
- Version0.2 device instrumentation: startup preview without recording; manual12-second recording and stop preserving preview; settings preserving frames; opt-in autorecord and crop preferences surviving restart;600×320controlsfit; invisibleActivityreleasescamera. ManualMP4:12.13seconds,24.24fps,fullydecodedwithout errors.
- Touch replay while native360 hidden: transport accepted, perspective did not change. Binder touchEvent is empty in this firmware; sendMsg only handles13, so old front/rear calls using14 are ineffective.
- Direct native EventManager::SendViewSelect(1) **changed rear→front while dumpsys native360 remained STATE_HIDE(0)** and the application continued streaming. This was the last positive switching probe before disconnection. Rear value2 is derived from the same native mapping; application/root-helper delivery still requires live verification.

## Native command provenance

`dls_reverse_svr` SHA256 `88e65cdae3f3793156d000be26620d36a696a54b3073deb6c72d4595e548ee19`.
`libqg_hal.so` SHA256 `87306b1bd0b1e189c702170186b4507e5128d8c431a902d5dc75ebe7f79c57a6`.

The original ARM64 function atVA0x4aa30 builds a10-byte CIS control structure with type0x1c,length8 and view<<1 atbyte6; all remaining byteszero. Front=1,rear=2. This is the helper's only payload. These bytes are a vendor control structure, **not a raw physical CAN frame ID**.

`qg_canbus_open(void)` opens its own fd; `qg_canbus_control(int,char*)` performs ioctl; `qg_canbus_close(void)` closes that fd. ABI verified from local disassembly; no proprietary binaries included here.

## Offline 0.3 status

Minimal UI plus quick view toggle, saved startup view, restricted root helper and frame-stall indicator prepared after the laptop disconnected. ARM64 request encoding is emulated in tests. APK compilation/signature checks do not prove application-UID su access, rear switching, cold-start activation or speed compatibility. See evening checklist before marking any of those complete.

Gradle8.9/AGP8.7.3/JDK17 build passed locally: `:app:corePolicyTest :app:assembleDebug :app:assembleDebugAndroidTest` (59tasks,2m54s). Separate Python/Unicorn test passed against compiled ARM64 request code. PowerShell install/capture scripts parsed without syntax errors. The application and instrumentation APK build successfully; instrumentation was not run again after device disconnection.
