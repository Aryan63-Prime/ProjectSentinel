package gateway

import (
	"testing"
	"time"
)

func TestManager_ForEachAdmin_OnlyCallsAdminSessions(t *testing.T) {
	m := NewManager()

	adminSend := make(chan outgoingMessage, 10)
	hostSend := make(chan outgoingMessage, 10)

	adminSession := NewSession("admin-1", time.Now(), &Client{
		ConnectionID: "admin-1",
		Send:         adminSend,
	})
	adminSession.SetAuthenticated("")

	hostSession := NewSession("host-1", time.Now(), &Client{
		ConnectionID: "host-1",
		Send:         hostSend,
	})
	hostSession.SetAuthenticated("HOST-001")

	unauthSession := NewSession("unauth-1", time.Now(), &Client{
		ConnectionID: "unauth-1",
		Send:         make(chan outgoingMessage, 10),
	})

	m.Add(adminSession)
	m.Add(hostSession)
	m.Add(unauthSession)

	called := make([]string, 0)
	m.ForEachAdmin(func(client *Client) {
		called = append(called, client.ConnectionID)
	})

	if len(called) != 1 {
		t.Fatalf("expected 1 admin session, got %d: %v", len(called), called)
	}
	if called[0] != "admin-1" {
		t.Errorf("expected admin-1, got %s", called[0])
	}
}

func TestManager_ForEachAdmin_NoAdmins(t *testing.T) {
	m := NewManager()

	hostSession := NewSession("host-1", time.Now(), &Client{
		ConnectionID: "host-1",
		Send:         make(chan outgoingMessage, 10),
	})
	hostSession.SetAuthenticated("HOST-001")
	m.Add(hostSession)

	count := 0
	m.ForEachAdmin(func(client *Client) {
		count++
	})

	if count != 0 {
		t.Errorf("expected 0 admin calls, got %d", count)
	}
}
