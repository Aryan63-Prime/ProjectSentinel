# Testing Guide

Last Updated: 2026-07-13

---

## Test Inventory

### Go Backend (19 test files)

| Package | File | Tests |
|---------|------|-------|
| admin | handler_test.go | Admin API endpoint tests |
| admin | service_test.go | Admin service logic tests |
| audio | service_test.go | Audio routing/service tests |
| auth | service_test.go | JWT validation, auth flow |
| bootstrap | bootstrap_test.go | Server bootstrap lifecycle |
| config | config_test.go | Configuration parsing |
| database | redis_test.go | Redis connection, operations |
| device | service_test.go | Device registration logic |
| dispatcher | dispatcher_test.go | Message routing |
| gateway | websocket_integration_test.go | Full WebSocket integration (18 tests) |
| health | service_test.go | Health check endpoints |
| heartbeat | service_test.go | Heartbeat processing |
| location | service_test.go | Location update handling |
| logger | logger_test.go | Structured logging |
| metrics | collector_test.go | Prometheus metrics |
| protocol | message_test.go | Protocol message parsing |
| repository | listener_repository_test.go | Listener state management |
| repository | location_repository_test.go | Location storage |
| server | server_test.go | HTTP server lifecycle |

### Android Host (17 test files)

| Module | File | Tests |
|--------|------|-------|
| :data | AudioFrameBuilderTest | Binary frame serialization (14 tests) |
| :data | AudioPipelineTest | Capture loop lifecycle |
| :data | AudioSessionTest | Statistics + p99 latency |
| :data | AudioRepositoryImplTest | Binary send integration |
| :data | EncoderStressTest | Create/destroy stress (7 tests) |
| :data | OpusEncoderTest | Encoder interface tests |
| :data | ReconnectPolicyTest | Exponential backoff |
| :data | SequenceGeneratorTest | Atomic sequence |
| :data | MessageSerializerTest | JSON message construction |
| :data | WebSocketStateTest | WebSocket state machine |
| :data | AuthAndDeviceRepositoryTest | Auth/register flows |
| :data | ConnectionRepositoryImplTest | Connection lifecycle |
| :service | ConnectionSupervisorTest | State machine (18+ tests) |
| :service | HeartbeatSchedulerTest | Heartbeat timing |
| :service | LocationStreamerTest | Location lifecycle |
| :service | AudioStreamerTest | Audio lifecycle (12 tests) |

---

## Running Tests

### Backend

```bash
# All tests
cd server && go test ./...

# With race detection (requires CGO/GCC)
CGO_ENABLED=1 go test -race ./...

# Specific package
go test ./internal/gateway/...

# Verbose
go test -v ./internal/gateway/...

# Static analysis
go vet ./...
go fmt ./...
```

### Android Host

```bash
cd host-app

# Full build + test + lint
./gradlew build

# Unit tests only
./gradlew test

# Specific module
./gradlew :data:testDebugUnitTest
./gradlew :service:testDebugUnitTest

# Lint only
./gradlew lint
```

---

## Test Categories

### Unit Tests
- Pure logic tests with no external dependencies
- Run on JVM (no Android device required)
- Fast execution (< 1 second each)

### Integration Tests
- WebSocket integration tests (gateway package)
- Use in-memory mocks for Redis
- Test full protocol flows

### Stress Tests
- Encoder create/destroy cycles (50-100 iterations)
- Concurrent thread safety
- Pipeline start/stop cycles

### Infrastructure Tests (Requires Running Services)
- Redis integration (redis_test.go)
- Full E2E with server + Redis + Android emulator

---

## Race Detection

### Status

| Platform | `-race` Support | Status |
|----------|----------------|--------|
| Go (Linux/macOS) | ✅ Native | Recommended in CI |
| Go (Windows) | ⚠️ Requires GCC/MinGW | Not available locally |
| Android | N/A | Thread safety via AtomicLong, single-thread dispatcher |

### Alternatives on Windows

- `go vet ./...` — detects common concurrency issues
- Thread safety tests in AudioSessionTest (concurrent writes)
- Single-thread audio dispatcher guarantees thread safety

---

## Test Coverage Gaps

### Known Gaps (Documented)

| Area | Gap | Mitigation |
|------|-----|------------|
| Real audio capture | Requires microphone hardware | Instrumented tests (future) |
| Native JNI encoding | Requires native libraries | Instrumented tests (future) |
| Battery measurement | Requires physical device | Profiling runbook |
| Network switching | Requires physical device | Manual testing runbook |
| Long-duration stability | Requires running infrastructure | Endurance test scripts (future) |