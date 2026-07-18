package heartbeat

import "time"

const (
	// Interval is the protocol heartbeat interval for active connections.
	Interval = 20 * time.Second
	// Timeout is the protocol heartbeat timeout before a session is stale.
	Timeout = 60 * time.Second
)

// Service tracks heartbeat timing and stale-session policy.
type Service struct {
	now      func() time.Time
	interval time.Duration
	timeout  time.Duration
}

// NewService creates a heartbeat service using protocol timing defaults.
func NewService() *Service {
	return NewServiceWithConfig(time.Now, Interval, Timeout)
}

// NewServiceWithClock creates a heartbeat service with protocol timing and a custom clock.
func NewServiceWithClock(now func() time.Time) *Service {
	return NewServiceWithConfig(now, Interval, Timeout)
}

// NewServiceWithConfig creates a heartbeat service with explicit timing.
func NewServiceWithConfig(now func() time.Time, interval time.Duration, timeout time.Duration) *Service {
	return &Service{
		now:      now,
		interval: interval,
		timeout:  timeout,
	}
}

// Timestamp returns the service clock's current time.
func (s *Service) Timestamp() time.Time {
	return s.now()
}

// Interval returns how often heartbeat checks should run.
func (s *Service) Interval() time.Duration {
	return s.interval
}

// Timeout returns the maximum allowed time since the last heartbeat.
func (s *Service) Timeout() time.Duration {
	return s.timeout
}

// IsStale reports whether the last heartbeat has reached the timeout.
func (s *Service) IsStale(lastHeartbeat time.Time) bool {
	if lastHeartbeat.IsZero() {
		return false
	}

	return !s.now().Before(lastHeartbeat.Add(s.timeout))
}
