# Project Sentinel — Render Free Tier Deployment & Optimization Guide

This document provides a comprehensive technical guide for running **Project Sentinel** on **Render's Free Tier**. It outlines platform constraints, resource limits, performance impacts on each feature, and the exact architectural adjustments needed on the Go backend, Android Host app, and Android Admin app.

---

## 1. Render Free Tier Specifications & Constraints

| Resource | Free Tier Limit | Impact on Project Sentinel |
| :--- | :--- | :--- |
| **RAM** | 512 MB | Strict limit. Exceeding triggers Linux kernel OOM killer (`kill -9`). |
| **CPU** | 0.1 vCPU (Shared) | CPU bursts on startup; requires efficient non-blocking I/O. |
| **Inactivity Sleep** | Spins down after 15 min idle | Container cold starts take **50–60 seconds** to boot up. |
| **Bandwidth** | 100 GB / month | Outbound traffic allowance; resets monthly. |
| **Storage** | Ephemeral (No persistent disk) | Files written to local disk vanish upon restart or redeployment. |
| **Networking** | TCP only (HTTP/1.1, HTTP/2, WSS) | **No raw UDP ports**. Only ports 80/443 exposed via Render reverse proxy. |
| **Managed Postgres** | 90-day expiration | Render's free PostgreSQL is **permanently deleted after 90 days**. |
| **Managed Redis** | 25 MB RAM limit | Sufficient for Sentinel active connection pub/sub and session registry. |

---

## 2. Inactivity Sleep & Cold Start Mitigation

### The 15-Minute Sleep Behavior
Render suspends free web service instances after **15 consecutive minutes** without incoming HTTP requests or active WebSocket traffic. Once suspended:
- The next incoming connection triggers a cold boot.
- Container initialization, Go binary launch, and TLS termination take **45 to 60 seconds**.
- Connections attempting to establish during this period will stall.

### How Project Sentinel Prevents Sleep
* **The Heartbeat Advantage:**
  In [`HeartbeatScheduler.kt`](file:///Users/ayush/Desktop/Servillance/host-app/service/src/main/java/com/sentinel/host/service/HeartbeatScheduler.kt), the host app transmits a `PING` packet every 15–30 seconds, and [`LocationStreamer.kt`](file:///Users/ayush/Desktop/Servillance/host-app/service/src/main/java/com/sentinel/host/service/LocationStreamer.kt) pushes GPS telemetry.
* **Result:** **As long as a single host phone is connected and active, the Render container will NEVER sleep.** It stays awake 24/7.

### What Happens When Host Loses Connectivity (> 15 min)
If the host device loses cellular connection in a dead zone or shuts down for over 15 minutes, Render will put the backend to sleep. When the host reconnects, it encounters a cold start.

### Required Host Android Adjustments
In `host-app`'s OkHttp WebSocket client configuration, the default 10-second connect timeout will abort before Render finishes spinning up. Update the client settings:

```kotlin
// In WebSocketConnectionManager.kt / NetworkModule.kt
val okHttpClient = OkHttpClient.Builder()
    // Allow up to 90 seconds for Render container cold starts
    .connectTimeout(90, TimeUnit.SECONDS)
    .readTimeout(0, TimeUnit.MILLISECONDS) // 0 for infinite WebSocket stream
    .writeTimeout(30, TimeUnit.SECONDS)
    .pingInterval(20, TimeUnit.SECONDS)
    .retryOnConnectionFailure(true)
    .build()
```

#### Exponential Backoff Strategy
Implement progressive reconnection intervals when the socket disconnects:
1. Attempt 1: Immediate (retry in 2s).
2. Attempt 2: Retry in 5s.
3. Attempt 3: Retry in 15s.
4. Attempt 4+: Retry every 30s–60s until Render responds with HTTP `101 Switching Protocols`.

---

## 3. Memory Optimization (Staying Safely Under 512 MB)

### Go Server Memory Budget
Under Render's 512 MB ceiling, the Go backend should target **< 150 MB baseline RAM usage**, leaving 350+ MB buffer for concurrent WebSocket buffers and JSON payloads.

```
Total Limit: 512 MB
├── Go Runtime + Goroutines: ~25 MB
├── Redis Client + Sessions Cache: ~15 MB
├── Active WebSocket Buffers (10 devices): ~20 MB
├── In-Transit Message Payloads Buffer: ~50 MB
└── Safety Headroom (OS + Dynamic Allocation): ~402 MB
```

### Preventing OOM on Heavy Features

#### 1. In-Memory Remote File Previews
* **The Danger:** Loading an uncompressed 20 MB camera image or 500 MB video on the host and piping raw Base64 over the WebSocket would consume 70+ MB of RAM in a single JSON frame on the server, risking an instant OOM crash.
* **The Solution:** The host device downsamples images **before transmission**:
  - Image thumbnails are scaled to `maxDim = 720px` using `BitmapFactory.Options(inSampleSize = ...)`.
  - Compressed to WebP/JPEG (quality 80%) in RAM.
  - Final Base64 payload size is strictly **30 KB – 80 KB**.
  - Server routes a 50 KB frame in less than 2 milliseconds using minimal RAM.

#### 2. In-Memory Screenshot Captures
* Standard 1080x2400 screen frame is downscaled and compressed to JPEG (quality 80%) on the host.
* Resulting Base64 payload is **~150 KB – 250 KB**.
* Server dispatches this frame without storing it, releasing memory immediately to the Go garbage collector.

#### 3. Go Runtime Memory Enforcement (`GOMEMLIMIT`)
In [`render.yaml`](file:///Users/ayush/Desktop/Servillance/server/render.yaml), add Go runtime memory limit flags so the Go GC runs aggressively before Render's hard cgroup limit is breached:

```yaml
envVars:
  - key: GOMEMLIMIT
    value: 450MiB  # Triggers GC at 450MB, preventing 512MB hard crash
  - key: GOGC
    value: 80      # More aggressive garbage collection
```

---

## 4. Bandwidth Budgeting (100 GB Monthly Allowance)

Render permits **100 GB of free outbound bandwidth per month**. Sentinel's protocols are engineered for extreme network efficiency:

### Monthly Bandwidth Calculation (1 Host Device, 24/7 Operation)

| Data Stream | Frequency / Duration | Packet Size | Monthly Usage |
| :--- | :--- | :--- | :--- |
| **Heartbeats (`PING/PONG`)** | Every 20 seconds | ~120 bytes | ~15.5 MB |
| **GPS Telemetry** | Every 10 seconds | ~250 bytes | ~64.8 MB |
| **System Info Updates** | Every 5 minutes | ~800 bytes | ~6.9 MB |
| **Live Opus Audio** | 1 hour / day | ~2.5 KB/s | ~270.0 MB |
| **Screenshots** | 20 captures / day | ~180 KB | ~108.0 MB |
| **File Previews** | 30 previews / day | ~50 KB | ~45.0 MB |
| **Total Monthly Usage** | — | — | **~510.2 MB** |

**Conclusion:** A single 24/7 host consumes approximately **0.5 GB per month** out of your 100 GB allowance (**0.5% of total limit**). You can comfortably support **20+ active host devices** simultaneously on the free tier without incurring bandwidth overage.

---

## 5. Storage & Database Persistence Strategy

### The Ephemeral Disk Limitation
Render free web services operate on an ephemeral filesystem:
- Any file saved to `./` or `/tmp` on the server is **deleted upon container restart or redeploy**.
- **Architectural Rule:** Never rely on local server disk for file downloads, screenshots, or logs. Sentinel's chunk-streaming file protocol streams directly from Host → Server Memory → Admin Memory.

### Avoiding the 90-Day Render PostgreSQL Deletion
* **The Caveat:** Render offers a free PostgreSQL database, but **automatically terminates and permanently deletes the database after 90 days**.
* **The Permanent Free Solution:** Use an external, zero-cost, non-expiring serverless PostgreSQL provider. Connect your Go server by configuring the `DATABASE_URL` environment variable:

1. **[Neon.tech](https://neon.tech) (Recommended):**
   - 0.5 GB permanent free PostgreSQL storage.
   - Built-in connection pooling (`PgBouncer`).
   - Serverless compute that scales to zero and never expires.
2. **[Supabase](https://supabase.com):**
   - 500 MB permanent free PostgreSQL database.
   - Provides REST APIs and database management dashboard.

#### Connecting External DB in `render.yaml`
```yaml
services:
  - type: web
    name: project-sentinel
    runtime: docker
    plan: free
    rootDir: server
    dockerfilePath: ./Dockerfile
    envVars:
      - key: DATABASE_URL
        value: "postgres://user:password@ep-cool-project.neon.tech/sentinel?sslmode=require"
```

---

## 6. Network Protocol Compatibility (TCP vs. UDP on Render)

### What Works Natively on Render
* **WebSocket Secure (`wss://` on port 443):**
  Render terminates TLS at its edge proxy and forwards decrypted WebSocket traffic directly to your Go container over TCP. Live Opus audio frames, commands, screenshots, and telemetry work smoothly.
* **REST APIs (`https://` on port 443):**
  All admin management and device registration endpoints work out of the box.

### What Does NOT Work on Render Free Tier
* **Raw UDP Ports (Direct WebRTC Media & Raw QUIC):**
  Render's edge router **blocks arbitrary UDP incoming ports**. It only listens on TCP ports 80 and 443.

### How to Implement WebRTC or PTT on Render
If you implement WebRTC for real-time video or two-way Push-to-Talk (PTT), you cannot route peer-to-peer UDP media packets through Render:
1. **Signaling Layer:** The Go server on Render handles the lightweight SDP offer/answer and ICE candidate exchange over the existing WebSocket connection.
2. **Media Relaying (STUN/TURN):** Media audio/video streams route directly between Host and Admin, or via a free external TURN relay when symmetric NATs block direct connections:
   - **[Cloudflare Calls](https://developers.cloudflare.com/calls/):** Free tier WebRTC SFU relay.
   - **[Metered.ca](https://www.metered.ca/tools/open-relay/):** 50 GB/month free global TURN relay.
   - **Google Public STUN:** `stun:stun.l.google.com:19302` (Free for NAT discovery).

---

## 7. Optimized `render.yaml` Configuration

Here is the production-ready configuration incorporating all free-tier safeguards:

```yaml
services:
  - type: web
    name: project-sentinel
    runtime: docker
    plan: free
    rootDir: server
    dockerfilePath: ./Dockerfile
    envVars:
      - key: APP_NAME
        value: ProjectSentinel
      - key: APP_ENV
        value: production
      - key: SERVER_HOST
        value: 0.0.0.0
      - key: SERVER_PORT
        value: 10000
      - key: GOMEMLIMIT
        value: 450MiB
      - key: GOGC
        value: 80
      - key: REDIS_HOST
        fromService:
          name: sentinel-redis
          type: redis
          property: host
      - key: REDIS_PORT
        fromService:
          name: sentinel-redis
          type: redis
          property: port
      - key: JWT_SECRET
        generateValue: true
      # Connect to external non-expiring Neon/Supabase Postgres
      # - key: DATABASE_URL
      #   sync: false
    healthCheckPath: /health

  - type: redis
    name: sentinel-redis
    plan: free
    maxmemoryPolicy: allkeys-lru
    ipAllowList: []
```

---

## 8. Summary Checklist for Production on Render

- [ ] **Cold Start Timeout:** Set Android Host connection timeout to `90s` with exponential backoff.
- [ ] **Heartbeat Interval:** Keep Host `PING` interval between `15s–25s` to keep the Render container awake 24/7.
- [ ] **Memory Protection:** Set `GOMEMLIMIT=450MiB` in `render.yaml` to prevent container OOM termination.
- [ ] **Thumbnail Downscaling:** Ensure all file previews and screenshots are scaled down on the host (`< 200 KB`) before dispatching over the WebSocket.
- [ ] **Zero Server Disk Usage:** Do not write files to container local disk; use in-memory streaming.
- [ ] **Database Persistence:** Use Neon.tech or Supabase for PostgreSQL history to prevent Render's 90-day deletion.
- [ ] **WebRTC Relay:** Use free external STUN/TURN (Metered.ca / Google STUN) for UDP audio/video, using Render purely for WebSocket signaling.
