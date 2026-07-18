package dispatcher

import (
	"encoding/json"
	"testing"

	"github.com/xaiop/project-sentinel/server/internal/protocol"
)

type fakeBroadcaster struct {
	messages []json.RawMessage
}

func (f *fakeBroadcaster) BroadcastToAdmins(payload []byte) {
	f.messages = append(f.messages, json.RawMessage(payload))
}

func TestBroadcastDisconnect_EmitsDeviceUpdate(t *testing.T) {
	fb := &fakeBroadcaster{}
	d := New(nil, nil, nil, nil, nil)
	d.SetBroadcaster(fb)

	d.BroadcastDisconnect("HOST-001")

	if len(fb.messages) != 1 {
		t.Fatalf("expected 1 broadcast, got %d", len(fb.messages))
	}

	var msg protocol.Message
	if err := json.Unmarshal(fb.messages[0], &msg); err != nil {
		t.Fatalf("unmarshal: %v", err)
	}

	if msg.Type != protocol.TypeDeviceUpdate {
		t.Errorf("expected type %s, got %s", protocol.TypeDeviceUpdate, msg.Type)
	}

	var data protocol.DeviceUpdateMessage
	if err := json.Unmarshal(msg.Data, &data); err != nil {
		t.Fatalf("unmarshal data: %v", err)
	}

	if data.Event != protocol.EventDisconnected {
		t.Errorf("expected event %s, got %s", protocol.EventDisconnected, data.Event)
	}
	if data.DeviceID != "HOST-001" {
		t.Errorf("expected deviceId HOST-001, got %s", data.DeviceID)
	}
}

func TestBroadcastDisconnect_EmptyDeviceID_NoBroadcast(t *testing.T) {
	fb := &fakeBroadcaster{}
	d := New(nil, nil, nil, nil, nil)
	d.SetBroadcaster(fb)

	d.BroadcastDisconnect("")

	if len(fb.messages) != 0 {
		t.Errorf("expected no broadcast for empty deviceId, got %d", len(fb.messages))
	}
}

func TestBroadcastDisconnect_NilBroadcaster_NoPanic(t *testing.T) {
	d := New(nil, nil, nil, nil, nil)
	// No broadcaster set — should not panic
	d.BroadcastDisconnect("HOST-001")
}
