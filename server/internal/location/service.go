package location

import (
	"context"
	"errors"
	"fmt"
	"math"
	"time"

	"github.com/xaiop/project-sentinel/server/internal/protocol"
	"github.com/xaiop/project-sentinel/server/internal/repository"
)

var (
	ErrInvalidCoordinates = errors.New("invalid coordinates")
	ErrInvalidAccuracy    = errors.New("invalid accuracy")
	ErrInvalidBattery     = errors.New("invalid battery")
)

// Update represents a validated location update attached to a device.
type Update = repository.Location

// Repository stores the latest location without coupling the service to Redis.
type Repository = repository.LocationRepository

// Broadcaster publishes validated updates to interested clients.
type Broadcaster interface {
	BroadcastLocation(ctx context.Context, update Update) error
}

// Service validates and routes location updates.
type Service struct {
	repository  Repository
	broadcaster Broadcaster
	now         func() time.Time
}

// NewService creates a location service without external storage or broadcasting.
func NewService() *Service {
	return NewServiceWithDependencies(nil, nil, time.Now)
}

// NewServiceWithDependencies creates a location service with optional routing dependencies.
func NewServiceWithDependencies(repository Repository, broadcaster Broadcaster, now func() time.Time) *Service {
	if now == nil {
		now = time.Now
	}

	return &Service{
		repository:  repository,
		broadcaster: broadcaster,
		now:         now,
	}
}

// Handle validates, stores, and prepares broadcast for a location update.
func (s *Service) Handle(ctx context.Context, deviceID string, message protocol.LocationMessage) (Update, error) {
	if err := s.Validate(message); err != nil {
		return Update{}, err
	}

	update := Update{
		DeviceID:   deviceID,
		Latitude:   message.Latitude,
		Longitude:  message.Longitude,
		Accuracy:   message.Accuracy,
		Battery:    message.Battery,
		Network:    message.Network,
		RecordedAt: s.now(),
	}

	if s.repository != nil {
		if err := s.repository.SaveLatest(ctx, update); err != nil {
			return Update{}, fmt.Errorf("save latest location: %w", err)
		}
	}

	if s.broadcaster != nil {
		if err := s.broadcaster.BroadcastLocation(ctx, update); err != nil {
			return Update{}, fmt.Errorf("broadcast location: %w", err)
		}
	}

	return update, nil
}

// Validate checks GPS coordinates, accuracy, and battery fields.
func (s *Service) Validate(message protocol.LocationMessage) error {
	_ = s

	if math.IsNaN(message.Latitude) || math.IsInf(message.Latitude, 0) {
		return ErrInvalidCoordinates
	}

	if math.IsNaN(message.Longitude) || math.IsInf(message.Longitude, 0) {
		return ErrInvalidCoordinates
	}

	if message.Latitude < -90 || message.Latitude > 90 || message.Longitude < -180 || message.Longitude > 180 {
		return ErrInvalidCoordinates
	}

	if math.IsNaN(float64(message.Accuracy)) || math.IsInf(float64(message.Accuracy), 0) || message.Accuracy < 0 {
		return ErrInvalidAccuracy
	}

	if message.Battery < 0 || message.Battery > 100 {
		return ErrInvalidBattery
	}

	return nil
}
