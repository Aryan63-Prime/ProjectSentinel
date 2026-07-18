package admin

import (
	"context"
	"errors"
	"testing"
	"time"

	"github.com/xaiop/project-sentinel/server/internal/repository"
)

func TestServiceListDevicesReturnsRealtimeState(t *testing.T) {
	now := time.Date(2026, time.July, 9, 12, 0, 0, 0, time.UTC)
	source := &fakeSessionSource{
		sessions: []SessionSnapshot{
			{
				ConnectionID:  "CONN-1",
				DeviceID:      "HOST-0001",
				Authenticated: true,
				Registered:    true,
				ConnectedAt:   now.Add(-time.Minute),
				LastHeartbeat: now,
				DeviceName:    "Pixel 9",
				AppVersion:    "1.0.0",
				Model:         "Google Pixel",
			},
			{
				ConnectionID: "CONN-2",
			},
		},
	}
	locations := &fakeLocationReader{
		locations: map[string]repository.Location{
			"HOST-0001": {
				DeviceID:   "HOST-0001",
				Latitude:   28.6139,
				Longitude:  77.2090,
				Accuracy:   5.4,
				Battery:    81,
				Network:    "5G",
				RecordedAt: now,
			},
		},
	}
	service := NewService(source, locations, fakeHeartbeatPolicy{stale: false})

	devices, err := service.ListDevices(context.Background())
	if err != nil {
		t.Fatalf("ListDevices returned error: %v", err)
	}

	if len(devices) != 1 {
		t.Fatalf("expected one device, got %d", len(devices))
	}

	device := devices[0]
	if device.DeviceID != "HOST-0001" {
		t.Fatalf("expected HOST-0001, got %s", device.DeviceID)
	}
	if device.RegistrationState != registrationStateRegistered {
		t.Fatalf("expected registered state, got %s", device.RegistrationState)
	}
	if device.HeartbeatStatus != heartbeatStatusOnline {
		t.Fatalf("expected online heartbeat, got %s", device.HeartbeatStatus)
	}
	if device.LatestLocation == nil || device.LatestLocation.Network != "5G" {
		t.Fatalf("expected latest Redis location, got %+v", device.LatestLocation)
	}
}

func TestServiceGetDeviceReturnsStaleUnregisteredDevice(t *testing.T) {
	now := time.Date(2026, time.July, 9, 12, 0, 0, 0, time.UTC)
	source := &fakeSessionSource{
		sessions: []SessionSnapshot{
			{
				ConnectionID:  "CONN-1",
				DeviceID:      "HOST-0001",
				Authenticated: true,
				Registered:    false,
				ConnectedAt:   now.Add(-time.Minute),
				LastHeartbeat: now.Add(-2 * time.Minute),
			},
		},
	}
	service := NewService(source, &fakeLocationReader{}, fakeHeartbeatPolicy{stale: true})

	device, found, err := service.GetDevice(context.Background(), "HOST-0001")
	if err != nil {
		t.Fatalf("GetDevice returned error: %v", err)
	}
	if !found {
		t.Fatal("expected device to be found")
	}
	if device.RegistrationState != registrationStateUnregistered {
		t.Fatalf("expected unregistered state, got %s", device.RegistrationState)
	}
	if device.HeartbeatStatus != heartbeatStatusStale {
		t.Fatalf("expected stale heartbeat, got %s", device.HeartbeatStatus)
	}
}

func TestServiceGetDeviceMissing(t *testing.T) {
	service := NewService(&fakeSessionSource{}, &fakeLocationReader{}, fakeHeartbeatPolicy{})

	_, found, err := service.GetDevice(context.Background(), "HOST-0001")
	if err != nil {
		t.Fatalf("GetDevice returned error: %v", err)
	}
	if found {
		t.Fatal("expected missing device")
	}
}

func TestServiceGetDeviceRejectsMissingDeviceID(t *testing.T) {
	service := NewService(&fakeSessionSource{}, &fakeLocationReader{}, fakeHeartbeatPolicy{})

	_, _, err := service.GetDevice(context.Background(), " ")
	if !errors.Is(err, ErrMissingDeviceID) {
		t.Fatalf("expected ErrMissingDeviceID, got %v", err)
	}
}

func TestServiceReturnsLocationReaderError(t *testing.T) {
	expected := errors.New("redis unavailable")
	source := &fakeSessionSource{
		sessions: []SessionSnapshot{{DeviceID: "HOST-0001"}},
	}
	service := NewService(source, &fakeLocationReader{err: expected}, fakeHeartbeatPolicy{})

	_, err := service.ListDevices(context.Background())
	if !errors.Is(err, expected) {
		t.Fatalf("expected location error, got %v", err)
	}
}

type fakeSessionSource struct {
	sessions []SessionSnapshot
}

func (s *fakeSessionSource) ListSessions() []SessionSnapshot {
	return append([]SessionSnapshot(nil), s.sessions...)
}

func (s *fakeSessionSource) GetSessionByDeviceID(deviceID string) (SessionSnapshot, bool) {
	for _, session := range s.sessions {
		if session.DeviceID == deviceID {
			return session, true
		}
	}

	return SessionSnapshot{}, false
}

type fakeLocationReader struct {
	locations map[string]repository.Location
	err       error
}

func (r *fakeLocationReader) GetLatest(ctx context.Context, deviceID string) (repository.Location, bool, error) {
	_ = ctx

	if r.err != nil {
		return repository.Location{}, false, r.err
	}

	location, ok := r.locations[deviceID]
	return location, ok, nil
}

type fakeHeartbeatPolicy struct {
	stale bool
}

func (p fakeHeartbeatPolicy) IsStale(lastHeartbeat time.Time) bool {
	_ = lastHeartbeat

	return p.stale
}
