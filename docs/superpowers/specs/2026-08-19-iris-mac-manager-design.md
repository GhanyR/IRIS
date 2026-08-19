# IRIS Mac Manager Design

## Goal

Give Ghany one Mac dashboard for IRIS setup and daily operation without requiring Terminal commands, while keeping all vision inference offline on the Motorola.

## Architecture

- A Python standard-library HTTP server binds only to `127.0.0.1:8765`.
- The dashboard reads ADB metadata: connection, lock, package version, calibration/armed flags, service health, latest non-pixel IRIS log, battery, power, and temperature.
- Whitelisted controls connect Wi-Fi ADB, open IRIS, start/stop monitoring, test the alarm, launch scrcpy Live View, install the local debug APK, and start a bounded soak monitor.
- `MainActivity` accepts three explicit manager actions and remains the foreground UI entry point, preserving Android camera foreground-service rules.
- The manager never requests Android frames. Live View is handled by scrcpy locally and separately.
- Calibration is completed inside that Live View: four numbered cyan points can be clicked and then dragged with the Mac mouse before saving.

## Acceptance

- Parser/unit tests cover duplicate USB/Wi-Fi devices, offline devices, battery, lock, and runtime logs.
- The dashboard renders without browser console errors and shows the live Motorola state.
- All JVM and physical Android tests pass, the final APK installs, and manager start/stop/test controls are exercised on the Motorola.
- The final physical acceptance sequence remains phone present `ALARM`, phone removed `CLEAR` within about four seconds, and phone replaced from a different angle `ALARM`.
