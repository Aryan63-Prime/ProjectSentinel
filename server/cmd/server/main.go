package main

import (
	"context"
	"errors"
	"fmt"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"go.uber.org/zap"

	"github.com/xaiop/project-sentinel/server/internal/bootstrap"
	"github.com/xaiop/project-sentinel/server/internal/server"
)

const shutdownTimeout = 15 * time.Second

func main() {

	application, err := bootstrap.Build()
	if err != nil {
		// Logger may not be ready; write to stderr and exit.
		fmt.Fprintf(os.Stderr, "fatal: %v\n", err)
		os.Exit(1)
	}

	logger := application.Logger
	defer func() { _ = logger.Sync() }()

	srv := server.New(application)

	errCh := make(chan error, 1)
	go func() {
		logger.Info("server started",
			zap.String("host", application.Config.Server.Host),
			zap.String("port", application.Config.Server.Port),
		)
		if err := srv.Start(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			errCh <- err
		}
	}()

	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)

	select {
	case sig := <-quit:
		logger.Info("shutdown signal received", zap.String("signal", sig.String()))
	case err := <-errCh:
		logger.Error("server error", zap.Error(err))
	}

	ctx, cancel := context.WithTimeout(context.Background(), shutdownTimeout)
	defer cancel()

	logger.Info("shutting down", zap.Duration("timeout", shutdownTimeout))

	if err := srv.Shutdown(ctx); err != nil {
		logger.Error("http shutdown error", zap.Error(err))
	}

	application.Shutdown()

	logger.Info("server stopped")
}
