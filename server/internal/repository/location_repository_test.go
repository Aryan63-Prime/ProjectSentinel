package repository

import (
	"context"
	"encoding/json"
	"errors"
	"testing"
	"time"
)

func TestRedisLocationRepositorySaveLatestStoresJSONWithTTL(t *testing.T) {
	store := &recordingRedisStore{}
	repository := NewRedisLocationRepository(store, 2*time.Minute)
	recordedAt := time.Date(2026, time.July, 9, 12, 0, 0, 0, time.UTC)

	err := repository.SaveLatest(context.Background(), Location{
		DeviceID:   "HOST-0001",
		Latitude:   28.6139,
		Longitude:  77.2090,
		Accuracy:   5.4,
		Battery:    81,
		Network:    "5G",
		RecordedAt: recordedAt,
	})
	if err != nil {
		t.Fatalf("SaveLatest returned error: %v", err)
	}

	if store.setKey != "location:HOST-0001" {
		t.Fatalf("expected location key, got %s", store.setKey)
	}

	if store.setTTL != 2*time.Minute {
		t.Fatalf("expected ttl 2m, got %s", store.setTTL)
	}

	var stored Location
	if err := json.Unmarshal(store.setValue, &stored); err != nil {
		t.Fatalf("stored value is not valid JSON: %v", err)
	}

	if stored.DeviceID != "HOST-0001" || stored.Latitude != 28.6139 || stored.Longitude != 77.2090 {
		t.Fatalf("stored location mismatch: %+v", stored)
	}

	if !stored.RecordedAt.Equal(recordedAt) {
		t.Fatalf("expected recordedAt %s, got %s", recordedAt, stored.RecordedAt)
	}
}

func TestRedisLocationRepositoryUsesDefaultTTL(t *testing.T) {
	store := &recordingRedisStore{}
	repository := NewRedisLocationRepository(store, 0)

	err := repository.SaveLatest(context.Background(), Location{
		DeviceID:   "HOST-0001",
		Latitude:   28.6139,
		Longitude:  77.2090,
		Accuracy:   5.4,
		Battery:    81,
		RecordedAt: time.Now(),
	})
	if err != nil {
		t.Fatalf("SaveLatest returned error: %v", err)
	}

	if store.setTTL != DefaultLocationTTL {
		t.Fatalf("expected default ttl %s, got %s", DefaultLocationTTL, store.setTTL)
	}
}

func TestRedisLocationRepositorySaveLatestReturnsRedisError(t *testing.T) {
	expected := errors.New("redis unavailable")
	repository := NewRedisLocationRepository(&recordingRedisStore{setErr: expected}, time.Minute)

	err := repository.SaveLatest(context.Background(), Location{DeviceID: "HOST-0001"})
	if !errors.Is(err, expected) {
		t.Fatalf("expected redis error, got %v", err)
	}
}

func TestRedisLocationRepositoryGetLatestReturnsStoredLocation(t *testing.T) {
	recordedAt := time.Date(2026, time.July, 9, 12, 0, 0, 0, time.UTC)
	location := Location{
		DeviceID:   "HOST-0001",
		Latitude:   28.6139,
		Longitude:  77.2090,
		Accuracy:   5.4,
		Battery:    81,
		Network:    "5G",
		RecordedAt: recordedAt,
	}
	payload, err := json.Marshal(location)
	if err != nil {
		t.Fatalf("marshal location: %v", err)
	}
	store := &recordingRedisStore{
		getValue: payload,
		getFound: true,
	}
	repository := NewRedisLocationRepository(store, time.Minute)

	got, found, err := repository.GetLatest(context.Background(), "HOST-0001")
	if err != nil {
		t.Fatalf("GetLatest returned error: %v", err)
	}
	if !found {
		t.Fatal("expected location to be found")
	}
	if store.getKey != "location:HOST-0001" {
		t.Fatalf("expected location key, got %s", store.getKey)
	}
	if got.DeviceID != location.DeviceID || !got.RecordedAt.Equal(recordedAt) {
		t.Fatalf("location mismatch: %+v", got)
	}
}

func TestRedisLocationRepositoryGetLatestHandlesMissingLocation(t *testing.T) {
	repository := NewRedisLocationRepository(&recordingRedisStore{}, time.Minute)

	location, found, err := repository.GetLatest(context.Background(), "HOST-0001")
	if err != nil {
		t.Fatalf("GetLatest returned error: %v", err)
	}
	if found {
		t.Fatal("expected missing location")
	}
	if location != (Location{}) {
		t.Fatalf("expected zero location, got %+v", location)
	}
}

func TestRedisLocationRepositoryGetLatestReturnsRedisError(t *testing.T) {
	expected := errors.New("redis unavailable")
	repository := NewRedisLocationRepository(&recordingRedisStore{getErr: expected}, time.Minute)

	_, _, err := repository.GetLatest(context.Background(), "HOST-0001")
	if !errors.Is(err, expected) {
		t.Fatalf("expected redis error, got %v", err)
	}
}

func TestRedisLocationRepositoryGetLatestRejectsInvalidJSON(t *testing.T) {
	repository := NewRedisLocationRepository(&recordingRedisStore{
		getValue: []byte("not-json"),
		getFound: true,
	}, time.Minute)

	_, _, err := repository.GetLatest(context.Background(), "HOST-0001")
	if err == nil {
		t.Fatal("expected invalid JSON error")
	}
}

func TestRedisLocationRepositoryHandlesNilClient(t *testing.T) {
	repository := NewRedisLocationRepository(nil, time.Minute)

	err := repository.SaveLatest(context.Background(), Location{DeviceID: "HOST-0001"})
	if err != nil {
		t.Fatalf("SaveLatest returned error: %v", err)
	}

	_, found, err := repository.GetLatest(context.Background(), "HOST-0001")
	if err != nil {
		t.Fatalf("GetLatest returned error: %v", err)
	}
	if found {
		t.Fatal("expected missing location")
	}
}

type recordingRedisStore struct {
	setKey   string
	setValue []byte
	setTTL   time.Duration
	setErr   error
	getKey   string
	getValue []byte
	getFound bool
	getErr   error
}

func (s *recordingRedisStore) Set(ctx context.Context, key string, value []byte, ttl time.Duration) error {
	_ = ctx

	s.setKey = key
	s.setValue = value
	s.setTTL = ttl

	return s.setErr
}

func (s *recordingRedisStore) Get(ctx context.Context, key string) ([]byte, bool, error) {
	_ = ctx

	s.getKey = key

	return s.getValue, s.getFound, s.getErr
}
