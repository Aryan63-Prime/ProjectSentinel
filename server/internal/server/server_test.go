package server

import (
	"context"
	"net/http"
	"testing"
	"time"

	"github.com/xaiop/project-sentinel/server/internal/app"
	"github.com/xaiop/project-sentinel/server/internal/config"
	"github.com/xaiop/project-sentinel/server/internal/gateway"
)

func testApplication() *app.Application {
	return &app.Application{
		Config: &config.Config{
			Server: config.ServerConfig{Host: "127.0.0.1", Port: "0"},
		},
		Gateway: gateway.New(nil, nil, nil),
	}
}

func TestNew_ReturnsNonNil(t *testing.T) {
	srv := New(testApplication())
	if srv == nil {
		t.Fatal("expected non-nil server")
	}
}

func TestStartAndShutdown(t *testing.T) {
	srv := New(testApplication())

	errCh := make(chan error, 1)
	go func() {
		if err := srv.Start(); err != nil && err != http.ErrServerClosed {
			errCh <- err
		}
		close(errCh)
	}()

	// Allow the listener to start.
	time.Sleep(50 * time.Millisecond)

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	if err := srv.Shutdown(ctx); err != nil {
		t.Errorf("unexpected shutdown error: %v", err)
	}

	if err := <-errCh; err != nil {
		t.Errorf("unexpected start error: %v", err)
	}
}
