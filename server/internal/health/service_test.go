package health

import (
	"context"
	"errors"
	"testing"
)

type stubPinger struct {
	err error
}

func (s stubPinger) Ping(ctx context.Context) error {
	return s.err
}

func TestReport_ServerAlwaysOK(t *testing.T) {
	svc := NewService()
	report := svc.Report(context.Background())

	if report.Status != StatusOK {
		t.Errorf("expected status %q, got %q", StatusOK, report.Status)
	}
	serverComponent, ok := report.Components["server"]
	if !ok {
		t.Fatal("expected server component")
	}
	if serverComponent.Status != StatusOK {
		t.Errorf("expected server component %q, got %q", StatusOK, serverComponent.Status)
	}
}

func TestReport_NoCheckers(t *testing.T) {
	svc := NewService()
	report := svc.Report(context.Background())

	if report.Status != StatusOK {
		t.Errorf("expected %q, got %q", StatusOK, report.Status)
	}
	if len(report.Components) != 1 {
		t.Errorf("expected 1 component (server), got %d", len(report.Components))
	}
}

func TestReport_NilService(t *testing.T) {
	var svc *Service
	report := svc.Report(context.Background())

	if report.Status != StatusOK {
		t.Errorf("expected %q, got %q", StatusOK, report.Status)
	}
}

func TestReport_PingCheckerOK(t *testing.T) {
	checker := NewPingChecker("redis", stubPinger{err: nil})
	svc := NewService(checker)
	report := svc.Report(context.Background())

	if report.Status != StatusOK {
		t.Errorf("expected %q, got %q", StatusOK, report.Status)
	}
	redisComponent, ok := report.Components["redis"]
	if !ok {
		t.Fatal("expected redis component")
	}
	if redisComponent.Status != StatusOK {
		t.Errorf("expected %q, got %q", StatusOK, redisComponent.Status)
	}
}

func TestReport_PingCheckerUnavailable(t *testing.T) {
	checker := NewPingChecker("redis", stubPinger{err: errors.New("connection refused")})
	svc := NewService(checker)
	report := svc.Report(context.Background())

	if report.Status != StatusDegraded {
		t.Errorf("expected %q, got %q", StatusDegraded, report.Status)
	}
	redisComponent := report.Components["redis"]
	if redisComponent.Status != StatusUnavailable {
		t.Errorf("expected %q, got %q", StatusUnavailable, redisComponent.Status)
	}
}

func TestReport_PingCheckerNilPinger(t *testing.T) {
	checker := NewPingChecker("redis", nil)
	svc := NewService(checker)
	report := svc.Report(context.Background())

	if report.Status != StatusDegraded {
		t.Errorf("expected %q, got %q", StatusDegraded, report.Status)
	}
}

func TestReport_StaticChecker(t *testing.T) {
	checker := NewStaticChecker("postgresql", StatusConfigured)
	svc := NewService(checker)
	report := svc.Report(context.Background())

	if report.Status != StatusOK {
		t.Errorf("expected %q, got %q", StatusOK, report.Status)
	}
	pgComponent, ok := report.Components["postgresql"]
	if !ok {
		t.Fatal("expected postgresql component")
	}
	if pgComponent.Status != StatusConfigured {
		t.Errorf("expected %q, got %q", StatusConfigured, pgComponent.Status)
	}
}

func TestReport_MixedCheckers(t *testing.T) {
	healthy := NewPingChecker("redis", stubPinger{err: nil})
	unhealthy := NewPingChecker("database", stubPinger{err: errors.New("down")})
	svc := NewService(healthy, unhealthy)
	report := svc.Report(context.Background())

	if report.Status != StatusDegraded {
		t.Errorf("expected %q because one component is down, got %q", StatusDegraded, report.Status)
	}
	if report.Components["redis"].Status != StatusOK {
		t.Errorf("expected redis %q, got %q", StatusOK, report.Components["redis"].Status)
	}
	if report.Components["database"].Status != StatusUnavailable {
		t.Errorf("expected database %q, got %q", StatusUnavailable, report.Components["database"].Status)
	}
}

func TestReport_NilCheckerSkipped(t *testing.T) {
	svc := NewService(nil)
	report := svc.Report(context.Background())

	if report.Status != StatusOK {
		t.Errorf("expected %q, got %q", StatusOK, report.Status)
	}
	if len(report.Components) != 1 {
		t.Errorf("expected 1 component (server), got %d", len(report.Components))
	}
}

func TestNewServiceWithTimeout_NonPositive(t *testing.T) {
	svc := NewServiceWithTimeout(-1)
	if svc == nil {
		t.Fatal("expected non-nil service")
	}
}
