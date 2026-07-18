package gateway

import (
	"testing"
	"time"
)

func TestSession_IsAdmin_AuthenticatedNoDeviceID(t *testing.T) {
	s := NewSession("conn-1", time.Now(), nil)
	s.SetAuthenticated("")

	if !s.IsAdmin() {
		t.Error("expected IsAdmin=true for authenticated session with no deviceID")
	}
}

func TestSession_IsAdmin_NotAuthenticated(t *testing.T) {
	s := NewSession("conn-1", time.Now(), nil)

	if s.IsAdmin() {
		t.Error("expected IsAdmin=false for unauthenticated session")
	}
}

func TestSession_IsAdmin_HostSession(t *testing.T) {
	s := NewSession("conn-1", time.Now(), nil)
	s.SetAuthenticated("HOST-001")

	if s.IsAdmin() {
		t.Error("expected IsAdmin=false for host session with deviceID")
	}
}
