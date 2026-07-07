# Mock Device Kit Validation Guide

This document describes how to perform manual validation of the Vaultier Retail wearable experience using the built-in Mock Device Kit (MDK).

## Manual Validation Path

1. **Enable Mock Mode**: Toggle the "Mock Mode" switch in the Vaultier Retail mobile app.
2. **Pair Device**: Tap "Pair Mock Ray-Ban Meta". This creates a virtual device in the SDK stack.
3. **Simulate State**:
   - Tap **Power On**: Simulates battery connection.
   - Tap **Unfold**: Simulates the temple arms being opened.
   - Tap **Don**: Simulates the user wearing the glasses (required for streaming).
4. **Configure Camera**:
   - By default, the mock device uses the phone's camera to simulate the glasses' perspective.
   - You can also configure a static video feed (requires HEVC format).
5. **Verify UI**:
   - Once "READY FOR STREAMING" appears in green, tap the mock device in the "Available Devices" list.
   - Tap "Push UI to Glasses" to verify that the Glimmer-based Retail UI is correctly formatted for the additive display.

## Hardware-Only Verification (What MDK Cannot Simulate)

While the Mock Device Kit is excellent for logic and UI validation, the following behaviors still require physical hardware for 100% certainty:

- **Bluetooth Latency & Range**: MDK handles communication in-process; it cannot simulate packet loss or signal degradation when walking away from the host device.
- **Physical Thermal Constraints**: Real glasses may throttle frame rates or shut down features (like camera) if they overheat during prolonged usage.
- **Sensor Calibration**: Actual IMU noise and magnetometer interference from environment-specific magnetic fields.
- **Microphone Echo Cancellation**: Verification of how the host device handles beamforming and noise suppression from the glasses' dual-microphone array.
- **Battery Drain Profiles**: MDK does not simulate the non-linear battery drain associated with specific combinations of display brightness, camera resolution, and frame rate.
- **Firmware Update Flow**: While MDK can mock a successful update, verifying the actual bit-level transmission and device reboot cycle requires physical glasses.
