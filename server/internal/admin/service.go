package admin

import (
	"context"
	"errors"
	"fmt"
	"strings"
	"time"

	"github.com/xaiop/project-sentinel/server/internal/repository"
)

const (
	heartbeatStatusOnline = "online"
	heartbeatStatusStale  = "stale"

	registrationStateRegistered   = "registered"
	registrationStateUnregistered = "unregistered"
)

var ErrMissingDeviceID = errors.New("missing device id")

// SessionSnapshot is the live session state required by the admin API.
type SessionSnapshot struct {
	ConnectionID  string
	DeviceID      string
	Authenticated bool
	Registered    bool
	ConnectedAt   time.Time
	LastHeartbeat time.Time
	DeviceName    string
	AppVersion    string
	Model         string
}

// SessionSource provides read-only access to connected device sessions.
type SessionSource interface {
	ListSessions() []SessionSnapshot
	GetSessionByDeviceID(deviceID string) (SessionSnapshot, bool)
}

// LocationReader reads the latest realtime location for a device.
type LocationReader interface {
	GetLatest(ctx context.Context, deviceID string) (repository.Location, bool, error)
}

// HeartbeatPolicy reports whether a session heartbeat is stale.
type HeartbeatPolicy interface {
	IsStale(lastHeartbeat time.Time) bool
}

// Device is the admin API representation of a connected device.
type Device struct {
	DeviceID          string               `json:"deviceId"`
	ConnectionID      string               `json:"connectionId"`
	Authenticated     bool                 `json:"authenticated"`
	Registered        bool                 `json:"registered"`
	RegistrationState string               `json:"registrationState"`
	HeartbeatStatus   string               `json:"heartbeatStatus"`
	ConnectedAt       time.Time            `json:"connectedAt"`
	LastHeartbeat     time.Time            `json:"lastHeartbeat"`
	DeviceName        string               `json:"deviceName,omitempty"`
	AppVersion        string               `json:"appVersion,omitempty"`
	Model             string               `json:"model,omitempty"`
	LatestLocation    *repository.Location `json:"latestLocation"`
}

// Service builds admin-facing device views from live sessions and Redis state.
type Service struct {
	sessions  SessionSource
	locations LocationReader
	heartbeat HeartbeatPolicy
}

// NewService creates an admin API service.
func NewService(sessions SessionSource, locations LocationReader, heartbeat HeartbeatPolicy) *Service {
	return &Service{
		sessions:  sessions,
		locations: locations,
		heartbeat: heartbeat,
	}
}

// ListDevices returns connected devices with their realtime state.
func (s *Service) ListDevices(ctx context.Context) ([]Device, error) {
	if s.sessions == nil {
		return []Device{}, nil
	}

	snapshots := s.sessions.ListSessions()
	devices := make([]Device, 0, len(snapshots))

	for _, snapshot := range snapshots {
		if strings.TrimSpace(snapshot.DeviceID) == "" {
			continue
		}

		device, err := s.deviceFromSnapshot(ctx, snapshot)
		if err != nil {
			return nil, err
		}

		devices = append(devices, device)
	}

	return devices, nil
}

// GetDevice returns one connected device by its permanent device id.
func (s *Service) GetDevice(ctx context.Context, deviceID string) (Device, bool, error) {
	deviceID = strings.TrimSpace(deviceID)
	if deviceID == "" {
		return Device{}, false, ErrMissingDeviceID
	}

	if s.sessions == nil {
		return Device{}, false, nil
	}

	snapshot, ok := s.sessions.GetSessionByDeviceID(deviceID)
	if !ok {
		return Device{}, false, nil
	}

	device, err := s.deviceFromSnapshot(ctx, snapshot)
	if err != nil {
		return Device{}, false, err
	}

	return device, true, nil
}

func (s *Service) deviceFromSnapshot(ctx context.Context, snapshot SessionSnapshot) (Device, error) {
	device := Device{
		DeviceID:          snapshot.DeviceID,
		ConnectionID:      snapshot.ConnectionID,
		Authenticated:     snapshot.Authenticated,
		Registered:        snapshot.Registered,
		RegistrationState: registrationState(snapshot.Registered),
		HeartbeatStatus:   s.heartbeatStatus(snapshot.LastHeartbeat),
		ConnectedAt:       snapshot.ConnectedAt,
		LastHeartbeat:     snapshot.LastHeartbeat,
		DeviceName:        snapshot.DeviceName,
		AppVersion:        snapshot.AppVersion,
		Model:             snapshot.Model,
	}

	if s.locations == nil {
		return device, nil
	}

	location, found, err := s.locations.GetLatest(ctx, snapshot.DeviceID)
	if err != nil {
		return Device{}, fmt.Errorf("get latest location: %w", err)
	}
	if found {
		device.LatestLocation = &location
	}

	return device, nil
}

func (s *Service) heartbeatStatus(lastHeartbeat time.Time) string {
	if s.heartbeat != nil && s.heartbeat.IsStale(lastHeartbeat) {
		return heartbeatStatusStale
	}

	return heartbeatStatusOnline
}

func registrationState(registered bool) string {
	if registered {
		return registrationStateRegistered
	}

	return registrationStateUnregistered
}
