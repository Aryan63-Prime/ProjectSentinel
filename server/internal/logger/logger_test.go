package logger

import (
	"testing"
)

func TestNew_ReturnsValidLogger(t *testing.T) {
	log, err := New()
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if log == nil {
		t.Fatal("expected non-nil logger")
	}
}

func TestNew_LoggerCanSync(t *testing.T) {
	log, err := New()
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	// Sync should not panic. Production loggers write to stderr,
	// so Sync may return an error on some platforms, but must not panic.
	_ = log.Sync()
}
