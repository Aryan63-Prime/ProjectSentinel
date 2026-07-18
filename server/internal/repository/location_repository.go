package repository

import (
	"context"
	"encoding/json"
	"fmt"
	"time"
)

// DefaultLocationTTL is the Redis expiration for latest locations.
const DefaultLocationTTL = 5 * time.Minute

// Location contains the latest validated location state for a device.
type Location struct {
	DeviceID   string    `json:"deviceId"`
	Latitude   float64   `json:"latitude"`
	Longitude  float64   `json:"longitude"`
	Accuracy   float32   `json:"accuracy"`
	Battery    int       `json:"battery"`
	Network    string    `json:"network"`
	RecordedAt time.Time `json:"recordedAt"`
}

// LocationRepository stores realtime device locations.
type LocationRepository interface {
	SaveLatest(ctx context.Context, location Location) error
}

// RedisStore is the Redis operation subset required by location persistence.
type RedisStore interface {
	Set(ctx context.Context, key string, value []byte, ttl time.Duration) error
	Get(ctx context.Context, key string) ([]byte, bool, error)
}

// RedisLocationRepository stores latest locations in Redis.
type RedisLocationRepository struct {
	client RedisStore
	ttl    time.Duration
}

// NewRedisLocationRepository creates a Redis location repository.
func NewRedisLocationRepository(client RedisStore, ttl time.Duration) *RedisLocationRepository {
	if ttl <= 0 {
		ttl = DefaultLocationTTL
	}

	return &RedisLocationRepository{
		client: client,
		ttl:    ttl,
	}
}

// SaveLatest stores the latest location for a device.
func (r *RedisLocationRepository) SaveLatest(ctx context.Context, location Location) error {
	if r == nil || r.client == nil {
		return nil
	}

	payload, err := json.Marshal(location)
	if err != nil {
		return fmt.Errorf("marshal latest location: %w", err)
	}

	if err := r.client.Set(ctx, locationKey(location.DeviceID), payload, r.ttl); err != nil {
		return fmt.Errorf("save latest location to redis: %w", err)
	}

	return nil
}

// GetLatest returns the latest stored location for a device.
func (r *RedisLocationRepository) GetLatest(ctx context.Context, deviceID string) (Location, bool, error) {
	if r == nil || r.client == nil {
		return Location{}, false, nil
	}

	payload, found, err := r.client.Get(ctx, locationKey(deviceID))
	if err != nil {
		return Location{}, false, fmt.Errorf("get latest location from redis: %w", err)
	}
	if !found {
		return Location{}, false, nil
	}

	var location Location
	if err := json.Unmarshal(payload, &location); err != nil {
		return Location{}, false, fmt.Errorf("unmarshal latest location: %w", err)
	}

	return location, true, nil
}

func locationKey(deviceID string) string {
	return "location:" + deviceID
}
