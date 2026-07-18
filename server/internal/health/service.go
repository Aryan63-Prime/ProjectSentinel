package health

import (
	"context"
	"time"
)

const (
	// StatusOK means a component is healthy.
	StatusOK = "ok"
	// StatusConfigured means a component is configured but has no active checker yet.
	StatusConfigured = "configured"
	// StatusUnavailable means a component check failed.
	StatusUnavailable = "unavailable"
	// StatusDegraded means at least one component is unavailable.
	StatusDegraded = "degraded"
)

const defaultCheckTimeout = 500 * time.Millisecond

// Component describes one health-checked component.
type Component struct {
	Status string `json:"status"`
}

// Report is the /health response body.
type Report struct {
	Status     string               `json:"status"`
	Components map[string]Component `json:"components"`
}

// Checker reports the health of one component.
type Checker interface {
	Name() string
	Check(ctx context.Context) Component
}

// Service aggregates component health.
type Service struct {
	checkers []Checker
	timeout  time.Duration
}

// NewService creates a health service with default check timing.
func NewService(checkers ...Checker) *Service {
	return NewServiceWithTimeout(defaultCheckTimeout, checkers...)
}

// NewServiceWithTimeout creates a health service with explicit check timing.
func NewServiceWithTimeout(timeout time.Duration, checkers ...Checker) *Service {
	if timeout <= 0 {
		timeout = defaultCheckTimeout
	}

	return &Service{
		checkers: checkers,
		timeout:  timeout,
	}
}

// Report returns aggregate component health.
func (s *Service) Report(ctx context.Context) Report {
	components := map[string]Component{
		"server": {Status: StatusOK},
	}
	status := StatusOK

	if s == nil {
		return Report{
			Status:     status,
			Components: components,
		}
	}

	for _, checker := range s.checkers {
		if checker == nil {
			continue
		}

		checkCtx, cancel := context.WithTimeout(ctx, s.timeout)
		component := checker.Check(checkCtx)
		cancel()

		components[checker.Name()] = component
		if component.Status == StatusUnavailable {
			status = StatusDegraded
		}
	}

	return Report{
		Status:     status,
		Components: components,
	}
}

// Pinger checks component availability with a ping operation.
type Pinger interface {
	Ping(ctx context.Context) error
}

type pingChecker struct {
	name   string
	pinger Pinger
}

// NewPingChecker creates a checker backed by a ping operation.
func NewPingChecker(name string, pinger Pinger) Checker {
	return pingChecker{
		name:   name,
		pinger: pinger,
	}
}

func (c pingChecker) Name() string {
	return c.name
}

func (c pingChecker) Check(ctx context.Context) Component {
	if c.pinger == nil {
		return Component{Status: StatusUnavailable}
	}

	if err := c.pinger.Ping(ctx); err != nil {
		return Component{Status: StatusUnavailable}
	}

	return Component{Status: StatusOK}
}

type staticChecker struct {
	name   string
	status string
}

// NewStaticChecker creates a checker with a fixed status.
func NewStaticChecker(name string, status string) Checker {
	return staticChecker{
		name:   name,
		status: status,
	}
}

func (c staticChecker) Name() string {
	return c.name
}

func (c staticChecker) Check(ctx context.Context) Component {
	_ = ctx

	return Component{Status: c.status}
}
