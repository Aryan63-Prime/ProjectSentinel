# Sentinel Platform — Host & Admin Roadmap, Improvements & Advancements

This document details unfinished implementation tasks, architectural improvements, and cutting-edge advancements across both the **Host Application (`host-app`)** and the **Admin Application (`admin-app`)**, covering reliability, observability, telemetry, and security.

> [!TIP]
> **Dedicated Feature Implementation & Code Documents:**
> - [FEATURE_FILE_PREVIEW.md](file:///Users/ayush/Desktop/Servillance/docs/FEATURE_FILE_PREVIEW.md): In-memory remote file preview (Image, Video, Text, PDF)
> - [FEATURE_SCREENSHOT_CAPTURE.md](file:///Users/ayush/Desktop/Servillance/docs/FEATURE_SCREENSHOT_CAPTURE.md): Zero-disk screen capture via Accessibility API
> - [FEATURE_OFFLINE_TELEMETRY.md](file:///Users/ayush/Desktop/Servillance/docs/FEATURE_OFFLINE_TELEMETRY.md): Local Room DB caching & batch GPS synchronization
> - [FEATURE_PUSH_TO_TALK.md](file:///Users/ayush/Desktop/Servillance/docs/FEATURE_PUSH_TO_TALK.md): Bidirectional voice intercom with Acoustic Echo Cancellation
> - [FEATURE_THERMAL_BATTERY_THROTTLING.md](file:///Users/ayush/Desktop/Servillance/docs/FEATURE_THERMAL_BATTERY_THROTTLING.md): Real-time hardware thermal & battery protection engine
> - [FEATURE_FLEET_MAP_TIMELINE.md](file:///Users/ayush/Desktop/Servillance/docs/FEATURE_FLEET_MAP_TIMELINE.md): Historical timeline scrubber, speed analytics & marker clustering
> - [FEATURE_DEAD_RECKONING_SENSOR_FUSION.md](file:///Users/ayush/Desktop/Servillance/docs/FEATURE_DEAD_RECKONING_SENSOR_FUSION.md): Inertial Navigation & Dead Reckoning when GPS drops
> - [FEATURE_SELF_HEALING_CRASH_WATCHDOG.md](file:///Users/ayush/Desktop/Servillance/docs/FEATURE_SELF_HEALING_CRASH_WATCHDOG.md): Automated crash tombstones & watchdog resuscitation
> - [FEATURE_DEVICE_OWNER_MDM.md](file:///Users/ayush/Desktop/Servillance/docs/FEATURE_DEVICE_OWNER_MDM.md): Android Enterprise Device Policy Manager & tamper protection
> - [FEATURE_GEOFENCING_AUTOMATION.md](file:///Users/ayush/Desktop/Servillance/docs/FEATURE_GEOFENCING_AUTOMATION.md): Client-side hardware geofencing & boundary rules
> - [FEATURE_BIOMETRIC_COMMAND_SIGNING.md](file:///Users/ayush/Desktop/Servillance/docs/FEATURE_BIOMETRIC_COMMAND_SIGNING.md): Biometric gatekeeper & Android Keystore digital signatures
> - [FEATURE_FALL_DETECTION_SOS.md](file:///Users/ayush/Desktop/Servillance/docs/FEATURE_FALL_DETECTION_SOS.md): 3-phase accelerometer fall detection & emergency broadcast
> - [RENDER_DEPLOYMENT_GUIDE.md](file:///Users/ayush/Desktop/Servillance/docs/RENDER_DEPLOYMENT_GUIDE.md): Complete Render free-tier deployment and optimization guide

---

## 1. Remaining Implementation Tasks (Immediate)

### Permissions & Setup Alignment
- [x] **Include `READ_CONTACTS` in Runtime Permission Requests**
  - **Status:** Completed. Added `Manifest.permission.READ_CONTACTS` to `MainActivity.kt` and `PermissionScreen.kt`.
- [x] **Track and Commit `ContactsManager.kt`**
  - **Status:** Completed. Injected into `CommandProcessor.kt` and ready to commit.

### Command Execution Clean-Up
- [x] **Resolve Stubbed Commands in `CommandProcessor`**
  - **Status:** Completed. Returned structured `UNSUPPORTED_COMMAND` error status payload for `FETCH_SMS_LOGS` complying with privacy and safety policies.
- [x] **Command Execution Timeout & Concurrency Safeguards**
  - **Status:** Completed. Wrapped long-running air commands (`CAPTURE_PHOTO`, `PREVIEW_FILE`, `CAPTURE_SCREENSHOT`, `FETCH_CONTACTS`, `EXECUTE_SHELL`) in individual coroutine timeouts (`withTimeoutOrNull`) and safe failure dispatchers.

---

## 2. Engineering Suggestions & Improvements

### Offline Telemetry Caching (Room / SQLite)
- [x] **Local Offline Queue for GPS & Events**
  - **Status:** Completed (`OfflineTelemetryBuffer.kt`, Room DB `LocationDao`, and batch sync on reconnection via `ConnectionSupervisor.kt`).

### Adaptive Battery & Network-Aware Throttling
- [x] **Motion-Based Location Polling & Battery-Level Degradation Policy**
  - **Status:** Completed via `PowerThrottleManager.kt` and sensor fusion monitoring.

### Audio Pipeline Resilience
- [ ] **Audio Focus & Call Interruption Handling**
  - In [`AudioStreamer.kt`](file:///Users/ayush/Desktop/Servillance/host-app/service/src/main/java/com/sentinel/host/service/AudioStreamer.kt), register an `AudioManager.OnAudioFocusChangeListener`.
  - When an incoming phone call occurs (`AUDIOFOCUS_LOSS_TRANSIENT`), pause the `AudioRecord` pipeline gracefully and resume automatically when the call ends, preventing audio buffer corruption or crashes.
- [ ] **JNI Native Opus Recovery**
  - Add native crash handling/wrappers around the Opus encoder C++ library (`libopus.a`) to gracefully restart the encoder if malformed PCM buffers are encountered.

### File Transfer Resilience
- [ ] **Chunk Checksum Verification & Resume**
  - In `FileStreamer.kt` and [`RemoteFileManager.kt`](file:///Users/ayush/Desktop/Servillance/host-app/data/src/main/java/com/sentinel/host/data/device/RemoteFileManager.kt), add SHA-256 block checksums for downloaded chunks.
  - Allow paused/interrupted downloads to resume from arbitrary byte offsets rather than restarting from zero.

---

## 3. Advanced Capabilities & Architectural Advancements

### Enterprise Mobility / Device Owner Mode (MDM)
- [x] **Android `DevicePolicyManager` Integration**
  - **Status:** Completed. Implemented [`MdmManager.kt`](file:///Users/ayush/Desktop/Servillance/host-app/app/src/main/java/com/sentinel/host/mdm/MdmManager.kt), [`SentinelDeviceAdminReceiver.kt`](file:///Users/ayush/Desktop/Servillance/host-app/app/src/main/java/com/sentinel/host/receiver/SentinelDeviceAdminReceiver.kt), and [`device_admin_policy.xml`](file:///Users/ayush/Desktop/Servillance/host-app/app/src/main/res/xml/device_admin_policy.xml).
    - **Tamper Protection:** Prevent host app uninstallation or forced stopping without admin passcode.
    - **Silent Self-Updates:** Install OTA APK updates in the background without user intervention.
    - **Remote Lock & Wipe:** Trigger remote PIN lock, kiosk mode, or enterprise wipe in theft scenarios.
    - **System Permission Auto-Grant:** Automatically grant runtime permissions without interactive prompts.

### Ultra-Low Latency Live Streaming (WebRTC)
- [ ] **WebRTC MediaStream for Real-Time Audio & Video**
  - **Current State:** Audio uses raw Opus binary packets over WebSocket TCP; video is still photos.
  - **Advancement:** Integrate Google WebRTC for peer-to-peer (P2P) UDP streaming between Host and Admin:
    - Sub-150ms bidirectional voice talk-back (intercom).
    - Real-time front/rear live camera video feed with adaptive bitrate (H.264/VP8).
    - Falls back to WebSocket TURN/TCP only when UDP is blocked by cellular NATs.

- [x] **Fall Detection & Impact Sensor**
  - **Status:** Completed. Implemented [`FallDetector.kt`](file:///Users/ayush/Desktop/Servillance/host-app/data/src/main/java/com/sentinel/host/data/device/FallDetector.kt) with 3-phase impact detection algorithm.

### End-to-End Encryption (E2EE)
- [ ] **Zero-Knowledge Data Security**
  - Implement ECDH (Elliptic-curve Diffie–Hellman) key agreement between Host and Admin:
    - Audio frames, captured photos, and files are encrypted with AES-256-GCM directly on the Host before entering the WebSocket.
    - The server acts as a zero-knowledge relay and cannot decrypt or inspect device audio, photos, or file contents.

### Client-Side Local Geofencing
- [x] **Hardware-Assisted Geofence Monitoring**
  - **Status:** Completed. Implemented [`HostGeofenceManager.kt`](file:///Users/ayush/Desktop/Servillance/host-app/data/src/main/java/com/sentinel/host/data/location/HostGeofenceManager.kt) with Play Services `GeofencingClient` and [`GeofenceBroadcastReceiver.kt`](file:///Users/ayush/Desktop/Servillance/host-app/data/src/main/java/com/sentinel/host/data/receiver/GeofenceBroadcastReceiver.kt).

### 4. Remote In-Memory File Preview (Without Downloading Full File)

- [x] **Lightweight In-Memory File Previews**
  - **Status:** Completed. Implemented [`FilePreviewManager.kt`](file:///Users/ayush/Desktop/Servillance/host-app/data/src/main/java/com/sentinel/host/data/device/FilePreviewManager.kt), [`FilePreviewDialog.kt`](file:///Users/ayush/Desktop/Servillance/admin-app/app/src/main/java/com/sentinel/admin/ui/detail/FilePreviewDialog.kt), and [`FileBrowserScreen.kt`](file:///Users/ayush/Desktop/Servillance/admin-app/app/src/main/java/com/sentinel/admin/ui/files/FileBrowserScreen.kt).
  - **Problem Solved:** Fetching full multi-megabyte files (large camera RAWs, videos, multi-gigabyte logs) over WebSocket causes extreme network latency, memory bloat, and `OutOfMemoryError` on both host and admin devices.
  - **Core Architecture:**
    - The host device inspects the requested file in place and generates an **in-memory downsampled thumbnail or text snippet**.
    - Previews are compressed in RAM (JPEG/WebP) and returned directly as Base64 strings.
    - Zero temporary thumbnail files are written to host disk storage.

#### Step-by-Step Implementation Process:
1. **Protocol Definition (`shared/`):**
   - Add command `CommandTypes.PREVIEW_FILE = "PREVIEW_FILE"` in [`shared/src/main/java/com/sentinel/shared/protocol/CommandTypes.kt`](file:///Users/ayush/Desktop/Servillance/shared/src/main/java/com/sentinel/shared/protocol/CommandTypes.kt).
   - Define request payload:
     ```json
     {
       "type": "COMMAND",
       "data": {
         "command": "PREVIEW_FILE",
         "params": {
           "path": "/storage/emulated/0/DCIM/Camera/IMG_2026.jpg",
           "maxDim": 720,
           "textLines": 250
         }
       }
     }
     ```
2. **Host-Side Generation (`host-app/data/src/main/java/com/sentinel/host/data/device/FilePreviewManager.kt`):**
   - Create `FilePreviewManager` injected into [`CommandProcessor.kt`](file:///Users/ayush/Desktop/Servillance/host-app/service/src/main/java/com/sentinel/host/service/CommandProcessor.kt).
   - Detect MIME type via `MimeTypeMap` / file extension:
     - **Images (JPG, PNG, WEBP, HEIC):**
       Use `BitmapFactory.Options` with `inJustDecodeBounds = true` to query image resolution. Compute `inSampleSize` so the decoded bitmap does not exceed `maxDim` (e.g., 720px max). Decode the scaled bitmap, compress to WebP/JPEG (quality 80%) into a `ByteArrayOutputStream`, and encode to Base64 (~30–70 KB total payload).
     - **Videos (MP4, MKV, 3GP):**
       Use `MediaMetadataRetriever.setDataSource(path)` and `getFrameAtTime(0)` to capture the opening keyframe thumbnail in memory. Compress and encode to Base64.
     - **Text / Code / Logs (TXT, LOG, JSON, XML, KT, PY):**
       Use a buffered stream to read only the first 32 KB (or 250 lines). Return as UTF-8 string with a `truncated: Boolean` flag.
     - **PDF Documents:**
       Use Android's native `PdfRenderer` on `ParcelFileDescriptor` to render page 1 into a thumbnail bitmap in RAM.
     - **Unsupported / Other Binaries:**
       Return rich metadata (file size, MIME type, permissions, last modified) without preview data so the admin knows its details.
3. **Response Structure:**
   - Host returns `COMMAND_RESULT`:
     ```json
     {
       "previewType": "IMAGE | VIDEO_THUMBNAIL | TEXT | PDF | METADATA_ONLY",
       "previewBase64": "...",
       "mimeType": "image/jpeg",
       "fileSize": 1845230,
       "lastModified": 1727500000,
       "truncated": false
     }
     ```
4. **Admin UI Experience (`admin-app/`):**
   - In `RemoteFilesDialog`, tapping on any file item opens a `FilePreviewModal`.
   - Displays the preview image/text snippet immediately, alongside file metadata.
   - Includes a direct **"Download Full File"** button inside the preview dialog that delegates to the existing `FileDownloadManager` chunk-streaming pipeline if the user decides they need the full file.

#### Technical Advancements:
* **Progressive Downsampling & Bandwidth Awareness:** Automatically adjust thumbnail dimensions based on active network transport (e.g. 360p over Cellular vs. 1080p over Wi-Fi).
* **Client-Side LRU Memory Cache:** Cache previews in `admin-app` memory using `LruCache` so navigating between file lists does not trigger duplicate network fetches.
* **Audio ID3 & Waveform Extraction:** Extract embedded album art, artist, track name, and duration for audio files (`.mp3`, `.m4a`, `.opus`).

---

### 5. In-Memory Remote Screenshot Capture via Air Commands (Zero-Disk Storage)

- [x] **On-Demand Screenshot Capture from Admin**
  - **Status:** Completed. Implemented [`ScreenshotCapturer.kt`](file:///Users/ayush/Desktop/Servillance/host-app/service/src/main/java/com/sentinel/host/service/ScreenshotCapturer.kt), [`AirCommandCard.kt`](file:///Users/ayush/Desktop/Servillance/admin-app/app/src/main/java/com/sentinel/admin/ui/detail/AirCommandCard.kt), and [`ScreenshotViewerDialog.kt`](file:///Users/ayush/Desktop/Servillance/admin-app/app/src/main/java/com/sentinel/admin/ui/detail/ScreenshotViewerDialog.kt).
  - **Problem Solved:** Standard background apps cannot capture the screen on Android without user prompts or root. Furthermore, saving screenshots to local storage creates persistent file traces, wears down flash storage, and risks detection by the user.
  - **Core Architecture:**
    - Utilize Android's official **`AccessibilityService.takeScreenshot()`** API (Android 11 / API 30+), leveraging the existing [`SentinelAccessibilityService.kt`](file:///Users/ayush/Desktop/Servillance/host-app/service/src/main/java/com/sentinel/host/service/SentinelAccessibilityService.kt).
    - Capture the entire active display frame silently into a `HardwareBuffer`.
    - Compress the bitmap directly in RAM to WebP/JPEG, encode to Base64, and transmit immediately over the WebSocket connection.
    - **Strict Zero-Disk Guarantee:** No screenshot bitmap or file is ever written to `/sdcard`, cache directories, or internal storage.

#### Step-by-Step Implementation Process:
1. **Extend Protocol (`shared/`):**
   - Add command `CommandTypes.CAPTURE_SCREENSHOT = "CAPTURE_SCREENSHOT"` in [`CommandTypes.kt`](file:///Users/ayush/Desktop/Servillance/shared/src/main/java/com/sentinel/shared/protocol/CommandTypes.kt).
2. **Host-Side Screen Capture Pipeline (`host-app/`):**
   - In [`SentinelAccessibilityService.kt`](file:///Users/ayush/Desktop/Servillance/host-app/service/src/main/java/com/sentinel/host/service/SentinelAccessibilityService.kt), maintain an active service instance reference in its companion object (`var instance: SentinelAccessibilityService? = null`).
   - Implement `ScreenshotCapturer.kt` injected into [`CommandProcessor.kt`](file:///Users/ayush/Desktop/Servillance/host-app/service/src/main/java/com/sentinel/host/service/CommandProcessor.kt):
     ```kotlin
     suspend fun captureScreenshot(maxWidth: Int = 1080, quality: Int = 80): ScreenshotResult
     ```
   - **Multi-Tier Execution Fallback:**
     - **Tier 1 (Accessibility API, Android 11+):**
       Call `accessibilityService.takeScreenshot(Display.DEFAULT_DISPLAY, executor, callback)`.
       Upon `TakeScreenshotCallback.onSuccess(screenshotResult)`:
       - Wrap `screenshotResult.hardwareBuffer` into `Bitmap.wrapHardwareBuffer()`.
       - Copy to software bitmap if scaling or downsampling is required.
       - Scale to `maxWidth` (preserving aspect ratio).
       - Compress into in-memory `ByteArrayOutputStream(Bitmap.CompressFormat.JPEG, quality, baos)`.
       - Encode to Base64 string via `Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)`.
       - Explicitly close `hardwareBuffer` and recycle `bitmap` to prevent native memory leaks.
     - **Tier 2 (Root / Shell Fallback):**
       If accessibility service is disabled but root is available, execute `su -c screencap -p` via [`ShellExecutor.kt`](file:///Users/ayush/Desktop/Servillance/host-app/data/src/main/java/com/sentinel/host/data/device/ShellExecutor.kt), pipe the standard output stream directly into `BitmapFactory.decodeStream()`, compress in RAM, and Base64 encode without writing to disk.
     - **Tier 3 (Graceful Error):**
       If neither Tier 1 nor Tier 2 is available, return a descriptive error: `"Accessibility Service not enabled on host device"`.
3. **Response Payload:**
   - Host sends `COMMAND_RESULT` back to server:
     ```json
     {
       "type": "COMMAND_RESULT",
       "data": {
         "command": "CAPTURE_SCREENSHOT",
         "success": true,
         "payload": {
           "imageBase64": "...",
           "timestamp": 1727500000,
           "width": 1080,
           "height": 2400
         }
       }
     }
     ```
4. **Admin UI Air Command Card Integration (`admin-app/`):**
   - In [`AirCommandCard.kt`](file:///Users/ayush/Desktop/Servillance/admin-app/app/src/main/java/com/sentinel/admin/ui/detail/AirCommandCard.kt), add a new action button in Row 2 or 3:
     ```kotlin
     OutlinedButton(
         onClick = onCaptureScreenshotClick,
         enabled = isOnline,
         modifier = Modifier.weight(1f)
     ) {
         Icon(Icons.Default.Screenshot, contentDescription = null, modifier = Modifier.size(16.dp))
         Spacer(modifier = Modifier.width(4.dp))
         Text("Screenshot")
     }
     ```
   - In `DeviceDetailScreen.kt`, handle the incoming screenshot response by displaying a high-resolution Compose dialog (`ScreenshotViewerDialog`) with:
     - Full-screen pan and pinch-to-zoom gestures.
     - Timestamp and device orientation badges.
     - "Save to Gallery" and "Share" action buttons on the admin device.

#### Technical Advancements:
* **Adaptive Network Quality Scaling:** Dynamically select between 720p/70% JPEG (for cellular 4G/5G) and 1080p/85% WebP (for Wi-Fi) based on the current network capabilities reported by `AndroidNetworkObserver`.
* **Burst / Interval Screen Monitoring:** Allow the admin to request an interval capture mode (e.g. 1 frame every 2–4 seconds) for live screen visibility without the battery overhead and setup complexity of a full WebRTC video pipeline.
* **`FLAG_SECURE` Detection:** Gracefully recognize when an app on screen uses `WindowManager.LayoutParams.FLAG_SECURE` (such as banking apps or credential managers) which causes Android to return black pixels, reporting a clean diagnostic notice instead of an unhandled error.

---

## 6. Next-Gen Host Architectural Advancements

### Inertial Navigation & Dead Reckoning (Sensor Fusion)
- [x] **Extended Kalman Filter (EKF) when GPS Fix is Lost**
  - **Status:** Completed. Implemented [`DeadReckoningEngine.kt`](file:///Users/ayush/Desktop/Servillance/host-app/data/src/main/java/com/sentinel/host/data/location/DeadReckoningEngine.kt) fusing accelerometer, gyroscope, and rotation vectors with WGS-84 displacement extrapolation.

### Predictive Battery & Thermal Throttling Engine
- [x] **Hardware Protection & Adaptive Power Scaling**
  - **Status:** Completed. Implemented [`PowerThrottleManager.kt`](file:///Users/ayush/Desktop/Servillance/host-app/data/src/main/java/com/sentinel/host/data/device/PowerThrottleManager.kt) dynamic thermal listener and tiered power degradation.

### Self-Healing Watchdog & Crash Tombstones
- [x] **Automated Crash Diagnostics & Rapid Recovery**
  - **Status:** Completed. Implemented [`CrashReporter.kt`](file:///Users/ayush/Desktop/Servillance/host-app/data/src/main/java/com/sentinel/host/data/device/CrashReporter.kt) with encrypted tombstone persistence.

### Indoor Positioning via Wi-Fi RTT & Cell Tower Triangulation
- [ ] **Indoor Floor-Level Location Estimation**
  - **Problem Solved:** GPS signals cannot penetrate concrete floors and ceilings, yielding zero indoor location accuracy.
  - **Implementation Process:**
    - Query `TelephonyManager.getAllCellInfo()` to obtain cellular tower identities (CID, LAC, TAC, MCC/MNC) and signal metrics (RSRP/RSRQ, Timing Advance).
    - Utilize Android's `WifiRttManager` (IEEE 802.11mc Round-Trip Time) to measure distance to supporting Wi-Fi access points within 1–2 meter accuracy without requiring GPS line-of-sight.

---

## 7. Next-Gen Admin Architectural Advancements

### Multi-Device Fleet Dashboard & Marker Clustering
- [ ] **Fleet Overview on Map**
  - **Problem Solved:** The current admin app focuses on inspecting one host device at a time. Fleet operators need simultaneous visibility across all active field units.
  - **Implementation Process:**
    - Integrate Google Maps Compose / MapLibre marker clustering (`ClusterManager`).
    - Aggregate real-time positions for dozens/hundreds of host units onto a single map view.
    - Implement device tagging and filtering (e.g., "Vehicles", "Security Guards", "Field Technicians").
    - Render status chips directly over markers: battery percentage, moving vs. stationary badges, and active emergency alert rings.

### Historical Timeline Scrubber & Speed Heatmap Playback
- [x] **Interactive Route Reconstruction & Analytics**
  - **Status:** Completed. Implemented [`HistoricalRoute.kt`](file:///Users/ayush/Desktop/Servillance/admin-app/domain/src/main/java/com/sentinel/admin/domain/model/HistoricalRoute.kt), [`TimelineScrubberBar.kt`](file:///Users/ayush/Desktop/Servillance/admin-app/app/src/main/java/com/sentinel/admin/ui/map/TimelineScrubberBar.kt), and [`HistoricalRouteMap.kt`](file:///Users/ayush/Desktop/Servillance/admin-app/app/src/main/java/com/sentinel/admin/ui/map/HistoricalRouteMap.kt).

### Bidirectional Voice Intercom (Push-To-Talk / PTT)
- [x] **Full-Duplex Walkie-Talkie Mode**
  - **Status:** Completed. Implemented [`PttAudioPlayer.kt`](file:///Users/ayush/Desktop/Servillance/host-app/data/src/main/java/com/sentinel/host/data/audio/PttAudioPlayer.kt) with AEC, [`PttButton.kt`](file:///Users/ayush/Desktop/Servillance/admin-app/app/src/main/java/com/sentinel/admin/ui/detail/PttButton.kt), and [`AudioControlCard.kt`](file:///Users/ayush/Desktop/Servillance/admin-app/app/src/main/java/com/sentinel/admin/ui/detail/AudioControlCard.kt).

### Visual Geofence Builder & Event Automation Engine
- [x] **Dynamic Boundary Drawing & Automated Triggers**
  - **Status:** Completed. Implemented [`GeofenceCircleOverlay.kt`](file:///Users/ayush/Desktop/Servillance/admin-app/app/src/main/java/com/sentinel/admin/ui/map/GeofenceCircleOverlay.kt) and host geofence commands.

### Biometric Gatekeeper & Cryptographic Audit Trail
- [x] **Sensitive Command Protection (RBAC & Biometrics)**
  - **Status:** Completed. Implemented [`CryptoCommandSigner.kt`](file:///Users/ayush/Desktop/Servillance/admin-app/data/src/main/java/com/sentinel/admin/data/security/CryptoCommandSigner.kt) (ECDSA in Android Keystore), [`BiometricPromptHelper.kt`](file:///Users/ayush/Desktop/Servillance/admin-app/app/src/main/java/com/sentinel/admin/ui/security/BiometricPromptHelper.kt), and digital signature verification on Host.

---

## 8. Cross-Cutting Transport & Real-Time Sync Advancements

### QUIC / HTTP/3 Transport Migration (Zero-RTT Connection Migration)
- [ ] **Cellular Network Resilience**
  - **Problem Solved:** Standard WebSockets run over TCP. On mobile devices, switching from Wi-Fi to a 5G cellular tower causes the TCP connection to break, dropping live audio, location streams, and requiring a complete reconnection handshake.
  - **Implementation Process:**
    - Migrate WebSocket transport to **QUIC / HTTP/3** (via Google Cronet or OkHttp QUIC).
    - Benefit from QUIC's **Connection Migration**: Connections survive client IP address changes. When walking out of Wi-Fi coverage onto mobile data, the active telemetry session, file transfer, and live audio continue seamlessly without packet drops or reconnection lag.

### Local-First Conflict-Free Data Replication (CRDT)
- [ ] **Distributed Offline State Synchronization**
  - **Problem Solved:** When both Host and Admin are offline or experiencing high packet loss, configuration updates (e.g., changed polling intervals, new geofence rules, updated contact lists) can conflict.
  - **Implementation Process:**
    - Implement a State-based Conflict-free Replicated Data Type (CRDT) or vector clock model.
    - Modifications on either device merge mathematically and deterministically without data loss as soon as internet connectivity resumes.
