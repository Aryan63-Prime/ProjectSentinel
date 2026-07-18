package app

import (
	"io"

	"go.uber.org/zap"

	"github.com/xaiop/project-sentinel/server/internal/config"
	"github.com/xaiop/project-sentinel/server/internal/gateway"
)

// Application holds the top-level dependencies for the server.
type Application struct {
	Config  *config.Config
	Logger  *zap.Logger
	Gateway *gateway.Gateway
	closers []io.Closer
}

// RegisterCloser adds a resource that should be closed during shutdown.
func (a *Application) RegisterCloser(c io.Closer) {
	if c != nil {
		a.closers = append(a.closers, c)
	}
}

// Shutdown releases all application resources in reverse registration order.
func (a *Application) Shutdown() {
	if a.Gateway != nil {
		a.Gateway.Shutdown()
	}

	for i := len(a.closers) - 1; i >= 0; i-- {
		_ = a.closers[i].Close()
	}
}
