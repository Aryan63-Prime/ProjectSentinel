package protocol

import (
	"encoding/json"
	"errors"
	"testing"
)

func TestDecodeMessageValidEnvelope(t *testing.T) {
	data := []byte(`{"type":"REGISTER","version":1,"timestamp":1752050000,"sequence":2,"data":{"deviceId":"HOST-0001"}}`)

	message, err := DecodeMessage(data)
	if err != nil {
		t.Fatalf("DecodeMessage returned error: %v", err)
	}

	if message.Type != TypeRegister {
		t.Fatalf("expected type %s, got %s", TypeRegister, message.Type)
	}

	if message.Sequence != 2 {
		t.Fatalf("expected sequence 2, got %d", message.Sequence)
	}

	var register RegisterMessage
	if err := message.DecodeData(&register); err != nil {
		t.Fatalf("DecodeData returned error: %v", err)
	}

	if register.DeviceID != "HOST-0001" {
		t.Fatalf("expected device id HOST-0001, got %s", register.DeviceID)
	}
}

func TestDecodeMessageRejectsInvalidVersion(t *testing.T) {
	data := []byte(`{"type":"AUTH","version":2,"timestamp":1752050000,"sequence":1,"data":{}}`)

	_, err := DecodeMessage(data)
	if !errors.Is(err, ErrInvalidVersion) {
		t.Fatalf("expected ErrInvalidVersion, got %v", err)
	}
}

func TestNewMessageEncodesTypedData(t *testing.T) {
	message, err := NewMessage(TypePong, 9, PongMessage{})
	if err != nil {
		t.Fatalf("NewMessage returned error: %v", err)
	}

	raw, err := json.Marshal(message)
	if err != nil {
		t.Fatalf("Marshal returned error: %v", err)
	}

	var decoded Message
	if err := json.Unmarshal(raw, &decoded); err != nil {
		t.Fatalf("Unmarshal returned error: %v", err)
	}

	if decoded.Type != TypePong {
		t.Fatalf("expected type %s, got %s", TypePong, decoded.Type)
	}

	if decoded.Version != Version {
		t.Fatalf("expected version %d, got %d", Version, decoded.Version)
	}
}
