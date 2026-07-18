# Performance Baseline

Last Updated: 2026-07-13

---

## Audio Pipeline

### Theoretical Metrics

| Metric | Value | Source |
|--------|-------|--------|
| Sample Rate | 48,000 Hz | AudioConstants.SAMPLE_RATE |
| Frame Duration | 20 ms | AudioConstants.FRAME_DURATION_MS |
| Samples per Frame | 960 | AudioConstants.SAMPLES_PER_FRAME |
| Frames per Second | 50 fps | 48000 / 960 |
| Target Bitrate | 24 kbps | AudioConstants.INITIAL_BITRATE |
| Channels | 1 (mono) | AudioConstants.CHANNELS |
| Codec | Opus (VOIP) | NativeOpusEncoder |

### Packet Metrics

| Metric | Value |
|--------|-------|
| Header Size | 13 bytes |
| Header Layout | [1B Type][4B Seq][8B Ts] |
| Average Opus Frame | ~60-120 bytes (VBR at 24kbps) |
| Max Opus Frame | 4,000 bytes (buffer limit) |
| Header Overhead | 13 / (13 + ~90) ≈ 12.6% |
| Packets per Second | 50 |
| Wire Bitrate | ~24.5 kbps (24k audio + headers) |

### Memory Budget (per pipeline instance)

| Buffer | Size | Lifecycle |
|--------|------|-----------|
| PCM Buffer | 1,920 bytes (960 shorts) | Allocated once at start |
| Opus Buffer | 4,000 bytes | Allocated once at start |
| AudioFrameBuilder ByteBuffer | 4,013 bytes | Allocated once per repo |
| AudioFrame (per emit) | ~100-150 bytes | GC'd per frame |
| Total Fixed | ~10 KB | |
| Per-frame Allocation | ~100-150 bytes | Unavoidable (Flow emission) |

### Latency Budget (theoretical)

| Stage | Duration |
|-------|----------|
| PCM Capture (blocking read) | 20 ms (frame duration) |
| Opus Encode | < 1 ms (expected) |
| Frame Serialize | < 0.1 ms |
| WebSocket Send | < 1 ms (local network) |
| **End-to-End** | **~22 ms** |

### Observable Statistics (AudioSession)

| Metric | Field | Unit |
|--------|-------|------|
| Frames Encoded | framesEncoded | count |
| Dropped Frames | droppedFrames | count |
| Avg Encode Time | averageEncodeTimeUs | µs |
| Max Encode Time | maxEncodeTimeNs / 1000 | µs |
| p99 Encode Time | encodeTimeP99Us | µs |
| Session Duration | durationMs | ms |

---

## Reconnect Performance

### Backoff Configuration

| Parameter | Value |
|-----------|-------|
| Initial Delay | 1,000 ms |
| Max Delay | 30,000 ms |
| Max Attempts | 10 |
| Multiplier | 2.0x |
| Jitter Factor | 0.25 |

### Reconnect Latency

| Scenario | Expected |
|----------|----------|
| Transport failure (server reachable) | 1-3 seconds |
| Server restart (after boot) | 3-10 seconds |
| Network switch (Wi-Fi → LTE) | 1-5 seconds |
| Extended outage (all retries) | Up to ~60 seconds |

---

## Heartbeat Performance

| Parameter | Value |
|-----------|-------|
| Interval | 20 seconds |
| Timeout | 60 seconds |
| Missed Beats Before Disconnect | 3 |
| Heartbeat Message Size | ~80 bytes (JSON) |

---

## Location Performance

| Parameter | Value |
|-----------|-------|
| Update Interval | 15 seconds (configurable) |
| Min Displacement | 10 meters |
| Accuracy | Balanced Power |
| Message Size | ~150 bytes (JSON) |

---

## Go Backend

### Profiling Commands

```bash
# CPU profile
go test -cpuprofile=cpu.prof ./internal/gateway/...
go tool pprof cpu.prof

# Memory profile
go test -memprofile=mem.prof ./internal/gateway/...
go tool pprof mem.prof

# Benchmark
go test -bench=. -benchmem ./internal/protocol/...

# Live profiling (add to server)
import _ "net/http/pprof"
# Then: http://localhost:6060/debug/pprof/
```

---

## Android

### Profiling Tools

| Tool | Purpose |
|------|---------|
| Android Studio Memory Profiler | Heap allocations, GC frequency |
| Android Studio CPU Profiler | Method tracing, frame render time |
| StrictMode (debug builds) | Disk/network on main thread |
| LeakCanary (debug builds) | Memory leak detection |

### StrictMode Configuration (Debug)

```kotlin
if (BuildConfig.DEBUG) {
    StrictMode.setThreadPolicy(
        StrictMode.ThreadPolicy.Builder()
            .detectDiskReads()
            .detectDiskWrites()
            .detectNetwork()
            .penaltyLog()
            .build()
    )
    StrictMode.setVmPolicy(
        StrictMode.VmPolicy.Builder()
            .detectLeakedClosableObjects()
            .detectActivityLeaks()
            .penaltyLog()
            .build()
    )
}
```

---

## Battery Impact (Estimated)

| Component | Impact | Notes |
|-----------|--------|-------|
| Audio Capture | Medium | VOICE_COMMUNICATION source with OS echo cancel |
| Opus Encoding | Low | ~1ms per frame, 50fps |
| WebSocket (control) | Low | Heartbeat every 20s, location every 15s |
| WebSocket (audio) | Medium | 50 packets/s × ~100 bytes |
| GPS (Balanced) | Medium | OS-managed, 15s interval |
| Total | Medium-High | Typical for always-on monitoring |
