package location

import (
	"context"
	"errors"
	"math"
	"testing"
	"time"

	"github.com/xaiop/project-sentinel/server/internal/protocol"
)

func TestValidateAcceptsValidLocation(t *testing.T) {
	service := NewService()

	err := service.Validate(protocol.LocationMessage{
		Latitude:  28.6139,
		Longitude: 77.2090,
		Accuracy:  5.4,
		Battery:   81,
	})
	if err != nil {
		t.Fatalf("Validate returned error: %v", err)
	}
}

func TestValidateRejectsInvalidCoordinates(t *testing.T) {
	service := NewService()

	err := service.Validate(protocol.LocationMessage{
		Latitude:  91,
		Longitude: 77.2090,
		Accuracy:  5.4,
		Battery:   81,
	})
	if !errors.Is(err, ErrInvalidCoordinates) {
		t.Fatalf("expected ErrInvalidCoordinates, got %v", err)
	}
}

func TestValidateRejectsNonFiniteCoordinates(t *testing.T) {
	service := NewService()

	invalidLocations := []protocol.LocationMessage{
		{Latitude: math.NaN(), Longitude: 77.2090, Accuracy: 5.4, Battery: 81},
		{Latitude: math.Inf(1), Longitude: 77.2090, Accuracy: 5.4, Battery: 81},
		{Latitude: 28.6139, Longitude: math.NaN(), Accuracy: 5.4, Battery: 81},
		{Latitude: 28.6139, Longitude: math.Inf(-1), Accuracy: 5.4, Battery: 81},
	}

	for _, locationMessage := range invalidLocations {
		err := service.Validate(locationMessage)
		if !errors.Is(err, ErrInvalidCoordinates) {
			t.Fatalf("expected ErrInvalidCoordinates, got %v", err)
		}
	}
}

func TestValidateRejectsInvalidAccuracy(t *testing.T) {
	service := NewService()

	invalidLocations := []protocol.LocationMessage{
		{Latitude: 28.6139, Longitude: 77.2090, Accuracy: -1, Battery: 81},
		{Latitude: 28.6139, Longitude: 77.2090, Accuracy: float32(math.NaN()), Battery: 81},
		{Latitude: 28.6139, Longitude: 77.2090, Accuracy: float32(math.Inf(1)), Battery: 81},
	}

	for _, locationMessage := range invalidLocations {
		err := service.Validate(locationMessage)
		if !errors.Is(err, ErrInvalidAccuracy) {
			t.Fatalf("expected ErrInvalidAccuracy, got %v", err)
		}
	}
}

func TestValidateRejectsInvalidBattery(t *testing.T) {
	service := NewService()

	err := service.Validate(protocol.LocationMessage{
		Latitude:  28.6139,
		Longitude: 77.2090,
		Accuracy:  5.4,
		Battery:   101,
	})
	if !errors.Is(err, ErrInvalidBattery) {
		t.Fatalf("expected ErrInvalidBattery, got %v", err)
	}
}

func TestHandleReturnsValidatedUpdate(t *testing.T) {
	recordedAt := time.Date(2026, 7, 9, 11, 0, 0, 0, time.UTC)
	service := NewServiceWithDependencies(nil, nil, func() time.Time {
		return recordedAt
	})

	update, err := service.Handle(context.Background(), "HOST-0001", protocol.LocationMessage{
		Latitude:  28.6139,
		Longitude: 77.2090,
		Accuracy:  5.4,
		Battery:   81,
		Network:   "5G",
	})
	if err != nil {
		t.Fatalf("Handle returned error: %v", err)
	}

	if update.DeviceID != "HOST-0001" {
		t.Fatalf("expected HOST-0001, got %s", update.DeviceID)
	}

	if !update.RecordedAt.Equal(recordedAt) {
		t.Fatalf("expected recorded_at %s, got %s", recordedAt, update.RecordedAt)
	}
}

func TestHandleStoresAndBroadcastsUpdate(t *testing.T) {
	repository := &recordingRepository{}
	broadcaster := &recordingBroadcaster{}
	service := NewServiceWithDependencies(repository, broadcaster, time.Now)

	update, err := service.Handle(context.Background(), "HOST-0001", protocol.LocationMessage{
		Latitude:  28.6139,
		Longitude: 77.2090,
		Accuracy:  5.4,
		Battery:   81,
		Network:   "5G",
	})
	if err != nil {
		t.Fatalf("Handle returned error: %v", err)
	}

	if repository.saved.DeviceID != update.DeviceID {
		t.Fatalf("expected repository device %s, got %s", update.DeviceID, repository.saved.DeviceID)
	}

	if broadcaster.broadcast.DeviceID != update.DeviceID {
		t.Fatalf("expected broadcast device %s, got %s", update.DeviceID, broadcaster.broadcast.DeviceID)
	}
}

func TestHandleRejectsInvalidLocationWithoutSideEffects(t *testing.T) {
	repository := &recordingRepository{}
	broadcaster := &recordingBroadcaster{}
	service := NewServiceWithDependencies(repository, broadcaster, time.Now)

	_, err := service.Handle(context.Background(), "HOST-0001", protocol.LocationMessage{
		Latitude:  91,
		Longitude: 77.2090,
		Accuracy:  5.4,
		Battery:   81,
	})
	if !errors.Is(err, ErrInvalidCoordinates) {
		t.Fatalf("expected ErrInvalidCoordinates, got %v", err)
	}

	if repository.called {
		t.Fatal("expected repository not to be called")
	}

	if broadcaster.called {
		t.Fatal("expected broadcaster not to be called")
	}
}

type recordingRepository struct {
	called bool
	saved  Update
}

func (r *recordingRepository) SaveLatest(ctx context.Context, update Update) error {
	_ = ctx

	r.called = true
	r.saved = update
	return nil
}

type recordingBroadcaster struct {
	called    bool
	broadcast Update
}

func (b *recordingBroadcaster) BroadcastLocation(ctx context.Context, update Update) error {
	_ = ctx

	b.called = true
	b.broadcast = update
	return nil
}
