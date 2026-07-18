package audio

import (
	"context"
	"encoding/binary"
	"errors"
	"sync"
	"sync/atomic"
	"testing"

	"github.com/xaiop/project-sentinel/server/internal/protocol"
)

// --- Frame tests ---

func testFrame(seq uint32, ts int64, opus []byte) []byte {
	buf := make([]byte, headerSize+len(opus))
	buf[0] = PacketTypeAudio
	binary.BigEndian.PutUint32(buf[1:5], seq)
	binary.BigEndian.PutUint64(buf[5:13], uint64(ts))
	copy(buf[13:], opus)
	return buf
}

func TestParseHeader_Valid(t *testing.T) {
	frame := testFrame(42, 1700000000000, []byte{0xAA, 0xBB})
	header, err := ParseHeader(frame)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if header.PacketType != PacketTypeAudio {
		t.Errorf("expected packet type %d, got %d", PacketTypeAudio, header.PacketType)
	}
	if header.Sequence != 42 {
		t.Errorf("expected sequence 42, got %d", header.Sequence)
	}
	if header.Timestamp != 1700000000000 {
		t.Errorf("expected timestamp 1700000000000, got %d", header.Timestamp)
	}
}

func TestParseHeader_TooSmall(t *testing.T) {
	_, err := ParseHeader([]byte{0x01, 0x02})
	if !errors.Is(err, ErrFrameTooSmall) {
		t.Errorf("expected ErrFrameTooSmall, got %v", err)
	}
}

func TestParseHeader_ExactHeaderSize(t *testing.T) {
	frame := testFrame(1, 0, nil)[:headerSize]
	_, err := ParseHeader(frame)
	if err != nil {
		t.Fatalf("header-only frame should be valid: %v", err)
	}
}

func TestValidateFrame_Valid(t *testing.T) {
	if err := ValidateFrame(testFrame(1, 0, []byte{0xFF})); err != nil {
		t.Errorf("expected valid frame: %v", err)
	}
}

func TestValidateFrame_TooSmall(t *testing.T) {
	if err := ValidateFrame(nil); !errors.Is(err, ErrFrameTooSmall) {
		t.Errorf("expected ErrFrameTooSmall, got %v", err)
	}
}

func TestValidateFrame_InvalidType(t *testing.T) {
	frame := testFrame(1, 0, nil)
	frame[0] = 0xFF
	if err := ValidateFrame(frame); !errors.Is(err, ErrInvalidPacketType) {
		t.Errorf("expected ErrInvalidPacketType, got %v", err)
	}
}

// --- Stubs ---

type stubListenerRepo struct {
	mu        sync.Mutex
	listeners map[string]string
	setErr    error
	getErr    error
	removeErr error
}

func newStubListenerRepo() *stubListenerRepo {
	return &stubListenerRepo{listeners: make(map[string]string)}
}

func (r *stubListenerRepo) SetListener(_ context.Context, deviceID string, connectionID string) error {
	if r.setErr != nil {
		return r.setErr
	}
	r.mu.Lock()
	r.listeners[deviceID] = connectionID
	r.mu.Unlock()
	return nil
}

func (r *stubListenerRepo) GetListener(_ context.Context, deviceID string) (string, bool, error) {
	if r.getErr != nil {
		return "", false, r.getErr
	}
	r.mu.Lock()
	connID, ok := r.listeners[deviceID]
	r.mu.Unlock()
	return connID, ok, nil
}

func (r *stubListenerRepo) RemoveListener(_ context.Context, deviceID string) error {
	if r.removeErr != nil {
		return r.removeErr
	}
	r.mu.Lock()
	delete(r.listeners, deviceID)
	r.mu.Unlock()
	return nil
}

type stubForwarder struct {
	forwarded []forwardedFrame
	err       error
}

type forwardedFrame struct {
	ConnectionID string
	Data         []byte
}

func (f *stubForwarder) ForwardBinary(connectionID string, data []byte) error {
	if f.err != nil {
		return f.err
	}
	f.forwarded = append(f.forwarded, forwardedFrame{
		ConnectionID: connectionID,
		Data:         data,
	})
	return nil
}

type stubSession struct {
	connectionID string
}

func (s *stubSession) ConnectionID() string {
	return s.connectionID
}

// --- Service tests ---

func TestStartListening_Success(t *testing.T) {
	repo := newStubListenerRepo()
	svc := NewService(repo, nil)

	if err := svc.StartListening(context.Background(), "conn-001", "HOST-0001"); err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if repo.listeners["HOST-0001"] != "conn-001" {
		t.Error("expected listener to be stored")
	}
}

func TestStartListening_MissingDeviceID(t *testing.T) {
	svc := NewService(newStubListenerRepo(), nil)

	if err := svc.StartListening(context.Background(), "conn-001", ""); !errors.Is(err, ErrMissingDeviceID) {
		t.Errorf("expected ErrMissingDeviceID, got %v", err)
	}
}

func TestStartListening_MissingConnectionID(t *testing.T) {
	svc := NewService(newStubListenerRepo(), nil)

	if err := svc.StartListening(context.Background(), "", "HOST-0001"); err == nil {
		t.Error("expected error for missing connection ID")
	}
}

func TestStopListening_Success(t *testing.T) {
	repo := newStubListenerRepo()
	svc := NewService(repo, nil)
	_ = svc.StartListening(context.Background(), "conn-001", "HOST-0001")

	if err := svc.StopListening(context.Background(), "HOST-0001"); err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if _, ok := repo.listeners["HOST-0001"]; ok {
		t.Error("expected listener to be removed")
	}
}

func TestStopListening_MissingDeviceID(t *testing.T) {
	svc := NewService(newStubListenerRepo(), nil)

	if err := svc.StopListening(context.Background(), "  "); !errors.Is(err, ErrMissingDeviceID) {
		t.Errorf("expected ErrMissingDeviceID, got %v", err)
	}
}

func TestRouteFrame_Success(t *testing.T) {
	repo := newStubListenerRepo()
	fwd := &stubForwarder{}
	svc := NewService(repo, fwd)
	repo.listeners["HOST-0001"] = "admin-conn-001"

	frame := testFrame(1, 1700000000000, []byte{0xAA})
	if err := svc.RouteFrame(context.Background(), "HOST-0001", frame); err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if len(fwd.forwarded) != 1 {
		t.Fatalf("expected 1 forwarded frame, got %d", len(fwd.forwarded))
	}
	if fwd.forwarded[0].ConnectionID != "admin-conn-001" {
		t.Errorf("expected admin-conn-001, got %s", fwd.forwarded[0].ConnectionID)
	}
}

func TestRouteFrame_NoListener(t *testing.T) {
	svc := NewService(newStubListenerRepo(), &stubForwarder{})

	frame := testFrame(1, 0, []byte{0xAA})
	if err := svc.RouteFrame(context.Background(), "HOST-UNKNOWN", frame); err != nil {
		t.Errorf("expected nil for no listener, got %v", err)
	}
}

func TestRouteFrame_InvalidFrame(t *testing.T) {
	svc := NewService(newStubListenerRepo(), &stubForwarder{})

	if err := svc.RouteFrame(context.Background(), "HOST-0001", []byte{0x01}); !errors.Is(err, ErrFrameTooSmall) {
		t.Errorf("expected ErrFrameTooSmall, got %v", err)
	}
}

func TestRouteFrame_InvalidPacketType(t *testing.T) {
	svc := NewService(newStubListenerRepo(), &stubForwarder{})

	frame := testFrame(1, 0, []byte{0xAA})
	frame[0] = 0xFF
	if err := svc.RouteFrame(context.Background(), "HOST-0001", frame); !errors.Is(err, ErrInvalidPacketType) {
		t.Errorf("expected ErrInvalidPacketType, got %v", err)
	}
}

func TestRouteFrame_NilDependencies(t *testing.T) {
	svc := NewService(nil, nil)

	frame := testFrame(1, 0, []byte{0xAA})
	if err := svc.RouteFrame(context.Background(), "HOST-0001", frame); err != nil {
		t.Errorf("expected nil for nil dependencies, got %v", err)
	}
}

func TestRouteFrame_ForwarderError(t *testing.T) {
	repo := newStubListenerRepo()
	repo.listeners["HOST-0001"] = "admin-conn-001"
	fwd := &stubForwarder{err: errors.New("send failed")}
	svc := NewService(repo, fwd)

	frame := testFrame(1, 0, []byte{0xAA})
	if err := svc.RouteFrame(context.Background(), "HOST-0001", frame); err == nil {
		t.Error("expected error from forwarder")
	}
}

func TestRouteFrame_ListenerLookupError(t *testing.T) {
	repo := newStubListenerRepo()
	repo.getErr = errors.New("redis down")
	svc := NewService(repo, &stubForwarder{})

	frame := testFrame(1, 0, []byte{0xAA})
	if err := svc.RouteFrame(context.Background(), "HOST-0001", frame); err == nil {
		t.Error("expected error from listener lookup")
	}
}

func TestSetForwarder(t *testing.T) {
	repo := newStubListenerRepo()
	svc := NewService(repo, nil) // forwarder set later
	repo.listeners["HOST-0001"] = "admin-conn-001"

	// Without forwarder, frames are silently dropped.
	frame := testFrame(1, 0, []byte{0xAA})
	if err := svc.RouteFrame(context.Background(), "HOST-0001", frame); err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	// Set forwarder, frames should now be delivered.
	fwd := &stubForwarder{}
	svc.SetForwarder(fwd)
	if err := svc.RouteFrame(context.Background(), "HOST-0001", frame); err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if len(fwd.forwarded) != 1 {
		t.Errorf("expected 1 forwarded frame after SetForwarder")
	}
}

// --- Handler tests ---

func TestHandleListen(t *testing.T) {
	repo := newStubListenerRepo()
	svc := NewService(repo, nil)
	handler := NewHandler(svc)
	session := &stubSession{connectionID: "admin-001"}

	msg := listenMessage(t, "HOST-0001", 5)
	response, err := handler.HandleListen(context.Background(), session, msg)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if response != nil {
		t.Error("expected nil response for LISTEN")
	}
	if repo.listeners["HOST-0001"] != "admin-001" {
		t.Error("expected listener to be stored")
	}
}

func TestHandleStop(t *testing.T) {
	repo := newStubListenerRepo()
	svc := NewService(repo, nil)
	handler := NewHandler(svc)
	session := &stubSession{connectionID: "admin-001"}

	repo.listeners["HOST-0001"] = "admin-001"
	msg := stopMessage(t, "HOST-0001", 6)
	response, err := handler.HandleStop(context.Background(), session, msg)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if response != nil {
		t.Error("expected nil response for STOP")
	}
	if _, ok := repo.listeners["HOST-0001"]; ok {
		t.Error("expected listener to be removed")
	}
}

func TestHandleFrame(t *testing.T) {
	repo := newStubListenerRepo()
	fwd := &stubForwarder{}
	svc := NewService(repo, fwd)
	handler := NewHandler(svc)
	repo.listeners["HOST-0001"] = "admin-001"

	frame := testFrame(1, 0, []byte{0xAA, 0xBB, 0xCC})
	if err := handler.HandleFrame(context.Background(), "HOST-0001", frame); err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if len(fwd.forwarded) != 1 {
		t.Fatal("expected frame to be forwarded")
	}
}

// --- Concurrent tests ---

func TestRouteFrame_ConcurrentClients(t *testing.T) {
	repo := newStubListenerRepo()
	var forwarded atomic.Int64
	fwd := &atomicForwarder{count: &forwarded}
	svc := NewService(repo, fwd)
	repo.listeners["HOST-0001"] = "admin-001"

	frame := testFrame(1, 0, []byte{0xAA})
	const numClients = 50
	var wg sync.WaitGroup
	wg.Add(numClients)

	for i := 0; i < numClients; i++ {
		go func() {
			defer wg.Done()
			_ = svc.RouteFrame(context.Background(), "HOST-0001", frame)
		}()
	}

	wg.Wait()
	if forwarded.Load() != numClients {
		t.Errorf("expected %d forwards, got %d", numClients, forwarded.Load())
	}
}

type atomicForwarder struct {
	count *atomic.Int64
}

func (f *atomicForwarder) ForwardBinary(_ string, _ []byte) error {
	f.count.Add(1)
	return nil
}

func TestStartStopListening_ConcurrentAccess(t *testing.T) {
	repo := newStubListenerRepo()
	svc := NewService(repo, nil)

	const numOps = 100
	var wg sync.WaitGroup
	wg.Add(numOps * 2)

	for i := 0; i < numOps; i++ {
		go func() {
			defer wg.Done()
			_ = svc.StartListening(context.Background(), "admin-001", "HOST-0001")
		}()
		go func() {
			defer wg.Done()
			_ = svc.StopListening(context.Background(), "HOST-0001")
		}()
	}

	wg.Wait()
}

// --- Helpers ---

func listenMessage(t *testing.T, deviceID string, sequence int64) protocol.Message {
	t.Helper()
	msg, err := protocol.NewMessage(protocol.TypeListen, sequence, protocol.ListenMessage{DeviceID: deviceID})
	if err != nil {
		t.Fatalf("NewMessage error: %v", err)
	}
	return *msg
}

func stopMessage(t *testing.T, deviceID string, sequence int64) protocol.Message {
	t.Helper()
	msg, err := protocol.NewMessage(protocol.TypeStop, sequence, protocol.StopMessage{DeviceID: deviceID})
	if err != nil {
		t.Fatalf("NewMessage error: %v", err)
	}
	return *msg
}
