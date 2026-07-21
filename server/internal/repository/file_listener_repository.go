package repository

import (
	"context"
	"fmt"
)

type RedisFileListenerRepository struct {
	client ListenerStore
}

func NewRedisFileListenerRepository(client ListenerStore) *RedisFileListenerRepository {
	return &RedisFileListenerRepository{client: client}
}

func (r *RedisFileListenerRepository) SetListener(ctx context.Context, deviceID string, connectionID string) error {
	if r == nil || r.client == nil {
		return nil
	}

	if err := r.client.Set(ctx, fileListenerKey(deviceID), []byte(connectionID), 0); err != nil {
		return fmt.Errorf("set file listener in redis: %w", err)
	}

	return nil
}

func (r *RedisFileListenerRepository) GetListener(ctx context.Context, deviceID string) (string, bool, error) {
	if r == nil || r.client == nil {
		return "", false, nil
	}

	payload, found, err := r.client.Get(ctx, fileListenerKey(deviceID))
	if err != nil {
		return "", false, fmt.Errorf("get file listener from redis: %w", err)
	}
	if !found {
		return "", false, nil
	}

	return string(payload), true, nil
}

func (r *RedisFileListenerRepository) RemoveListener(ctx context.Context, deviceID string) error {
	if r == nil || r.client == nil {
		return nil
	}

	if err := r.client.Delete(ctx, fileListenerKey(deviceID)); err != nil {
		return fmt.Errorf("remove file listener from redis: %w", err)
	}

	return nil
}

func fileListenerKey(deviceID string) string {
	return "file_listener:" + deviceID
}
