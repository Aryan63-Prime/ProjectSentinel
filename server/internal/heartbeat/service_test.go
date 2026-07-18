package heartbeat

import (
	"testing"
	"time"
)

func TestTimestampUsesConfiguredClock(t *testing.T) {
	expected := time.Date(2026, 7, 9, 10, 0, 0, 0, time.UTC)
	service := NewServiceWithClock(func() time.Time {
		return expected
	})

	actual := service.Timestamp()
	if !actual.Equal(expected) {
		t.Fatalf("expected %s, got %s", expected, actual)
	}
}

func TestServiceUsesProtocolHeartbeatDurations(t *testing.T) {
	service := NewService()

	if service.Interval() != 20*time.Second {
		t.Fatalf("expected interval %s, got %s", 20*time.Second, service.Interval())
	}

	if service.Timeout() != 60*time.Second {
		t.Fatalf("expected timeout %s, got %s", 60*time.Second, service.Timeout())
	}
}

func TestIsStaleDetectsTimedOutHeartbeat(t *testing.T) {
	now := time.Date(2026, 7, 9, 10, 1, 0, 0, time.UTC)
	service := NewServiceWithClock(func() time.Time {
		return now
	})

	if !service.IsStale(now.Add(-Timeout)) {
		t.Fatal("expected heartbeat at timeout boundary to be stale")
	}
}

func TestIsStaleKeepsRecentHeartbeat(t *testing.T) {
	now := time.Date(2026, 7, 9, 10, 1, 0, 0, time.UTC)
	service := NewServiceWithClock(func() time.Time {
		return now
	})

	if service.IsStale(now.Add(-Timeout + time.Second)) {
		t.Fatal("expected recent heartbeat to remain active")
	}
}

func TestIsStaleIgnoresZeroHeartbeat(t *testing.T) {
	service := NewService()

	if service.IsStale(time.Time{}) {
		t.Fatal("expected zero heartbeat to remain active")
	}
}
