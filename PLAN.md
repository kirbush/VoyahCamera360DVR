# Current plan

1. Publish portable Android/Gradle source, fixed native helper, tests and CI.
2. Prepare signed 0.3 APK and reproduce offline build checks.
3. Evening: validate helper invocation from the application UID, forward/reverse toggles while native360 is hidden, saved startup view, stock-camera handoff and embedded widget lifecycle.
4. Establish whether movement/speed actually interrupts the stream, using passive logs and recorded files. Locate the failing layer before proposing a change.
5. Later: sleep/wake behavior, storage limits/segmentation and independent front/rear capture feasibility.
