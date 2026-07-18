package repository

import (
	"context"
	"testing"
	"time"
)

type stubListenerStore struct {
	data   map[string][]byte
	setErr error
	getErr error
	delErr error
}

func newStubListenerStore() *stubListenerStore {
	return &stubListenerStore{data: make(map[string][]byte)}
}

func (s *stubListenerStore) Set(_ context.Context, key string, value []byte, _ time.Duration) error {
	if s.setErr != nil {
		return s.setErr
	}
	s.data[key] = value
	return nil
}

func (s *stubListenerStore) Get(_ context.Context, key string) ([]byte, bool, error) {
	if s.getErr != nil {
		return nil, false, s.getErr
	}
	v, ok := s.data[key]
	return v, ok, nil
}

func (s *stubListenerStore) Delete(_ context.Context, key string) error {
	if s.delErr != nil {
		return s.delErr
	}
	delete(s.data, key)
	return nil
}

func TestSetListener(t *testing.T) {
	store := newStubListenerStore()
	repo := NewRedisListenerRepository(store)

	if err := repo.SetListener(context.Background(), "HOST-0001", "conn-123"); err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if string(store.data["listener:HOST-0001"]) != "conn-123" {
		t.Error("expected listener key to be set")
	}
}

func TestGetListener_Found(t *testing.T) {
	store := newStubListenerStore()
	repo := NewRedisListenerRepository(store)
	_ = repo.SetListener(context.Background(), "HOST-0001", "conn-123")

	connID, found, err := repo.GetListener(context.Background(), "HOST-0001")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if !found {
		t.Fatal("expected listener to be found")
	}
	if connID != "conn-123" {
		t.Errorf("expected conn-123, got %s", connID)
	}
}

func TestGetListener_NotFound(t *testing.T) {
	store := newStubListenerStore()
	repo := NewRedisListenerRepository(store)

	_, found, err := repo.GetListener(context.Background(), "HOST-9999")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if found {
		t.Error("expected listener not to be found")
	}
}

func TestRemoveListener(t *testing.T) {
	store := newStubListenerStore()
	repo := NewRedisListenerRepository(store)
	_ = repo.SetListener(context.Background(), "HOST-0001", "conn-123")
	_ = repo.RemoveListener(context.Background(), "HOST-0001")

	_, found, _ := repo.GetListener(context.Background(), "HOST-0001")
	if found {
		t.Error("expected listener to be removed")
	}
}

func TestListenerRepository_NilSafe(t *testing.T) {
	var repo *RedisListenerRepository

	if err := repo.SetListener(context.Background(), "HOST-0001", "conn"); err != nil {
		t.Errorf("unexpected error on nil repo: %v", err)
	}

	_, found, err := repo.GetListener(context.Background(), "HOST-0001")
	if err != nil || found {
		t.Error("expected nil repo to return not found without error")
	}

	if err := repo.RemoveListener(context.Background(), "HOST-0001"); err != nil {
		t.Errorf("unexpected error on nil repo: %v", err)
	}
}
