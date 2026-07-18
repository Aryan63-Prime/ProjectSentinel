package database

import "testing"

func TestNewRedisClientStoresOptions(t *testing.T) {
	client := NewRedisClient(RedisOptions{
		Host:     "redis",
		Port:     "6380",
		Password: "secret",
		DB:       2,
	})

	if client.Address() != "redis:6380" {
		t.Fatalf("expected redis:6380, got %s", client.Address())
	}

	if client.DB() != 2 {
		t.Fatalf("expected db 2, got %d", client.DB())
	}

	if !client.HasPassword() {
		t.Fatal("expected password to be configured")
	}
}

func TestNewRedisClientAllowsEmptyPassword(t *testing.T) {
	client := NewRedisClient(RedisOptions{
		Host: "localhost",
		Port: "6379",
	})

	if client.HasPassword() {
		t.Fatal("expected password to be empty")
	}
}
