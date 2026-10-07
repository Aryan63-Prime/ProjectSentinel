package audio

import (
	"context"
	"errors"
	"fmt"
	"strings"
)

// ErrMissingDeviceID indicates a LISTEN or STOP without a target device.
var ErrMissingDeviceID = errors.New("missing device id")

// ListenerRepository manages audio listener mappings in storage.
type ListenerRepository interface {
	SetListener(ctx context.Context, deviceID string, connectionID string) error
	GetListener(ctx context.Context, deviceID string) (string, bool, error)
	RemoveListener(ctx context.Context, deviceID string) error
}

// FrameForwarder sends binary audio frames to a connected client.
type FrameForwarder interface {
	ForwardBinary(connectionID string, data []byte) error
}

// Service manages audio listener state and routes binary frames.
type Service struct {
	listeners ListenerRepository
	forwarder FrameForwarder
}

// NewService creates an audio service.
func NewService(listeners ListenerRepository, forwarder FrameForwarder) *Service {
	return &Service{
		listeners: listeners,
		forwarder: forwarder,
	}
}

// SetForwarder sets the frame forwarder after construction.
// This supports two-phase initialization when the gateway is created after the service.
func (s *Service) SetForwarder(forwarder FrameForwarder) {
	s.forwarder = forwarder
}

// StartListening registers an admin connection as the listener for a device.
func (s *Service) StartListening(ctx context.Context, adminConnectionID string, targetDeviceID string) error {
	cleanTarget := strings.TrimSpace(targetDeviceID)
	if cleanTarget == "" {
		return ErrMissingDeviceID
	}
	if strings.TrimSpace(adminConnectionID) == "" {
		return fmt.Errorf("start listening: missing admin connection id")
	}

	if s.listeners == nil {
		return nil
	}

	if err := s.listeners.SetListener(ctx, cleanTarget, adminConnectionID); err != nil {
		return err
	}

	// Register aliases so both generic token and distinct hardware callsigns route correctly
	if strings.HasPrefix(cleanTarget, "HOST-001-") {
		_ = s.listeners.SetListener(ctx, "HOST-001", adminConnectionID)
		suffix := strings.TrimPrefix(cleanTarget, "HOST-001-")
		_ = s.listeners.SetListener(ctx, "HOST-"+suffix, adminConnectionID)
	}
	if strings.Contains(cleanTarget, "_") {
		base := strings.SplitN(cleanTarget, "_", 2)[0]
		_ = s.listeners.SetListener(ctx, base, adminConnectionID)
	}

	return nil
}

// StopListening removes the listener for a device.
func (s *Service) StopListening(ctx context.Context, targetDeviceID string) error {
	cleanTarget := strings.TrimSpace(targetDeviceID)
	if cleanTarget == "" {
		return ErrMissingDeviceID
	}

	if s.listeners == nil {
		return nil
	}

	_ = s.listeners.RemoveListener(ctx, cleanTarget)
	if strings.HasPrefix(cleanTarget, "HOST-001-") {
		_ = s.listeners.RemoveListener(ctx, "HOST-001")
		suffix := strings.TrimPrefix(cleanTarget, "HOST-001-")
		_ = s.listeners.RemoveListener(ctx, "HOST-"+suffix)
	}
	if strings.Contains(cleanTarget, "_") {
		base := strings.SplitN(cleanTarget, "_", 2)[0]
		_ = s.listeners.RemoveListener(ctx, base)
	}

	return nil
}

// RouteFrame validates and forwards a binary audio frame to the listening admin.
// Returns nil silently if no one is listening (audio is lossy by nature).
func (s *Service) RouteFrame(ctx context.Context, sourceDeviceID string, frame []byte) error {
	if err := ValidateFrame(frame); err != nil {
		return err
	}

	if s.listeners == nil || s.forwarder == nil {
		return nil
	}

	connectionID, found, err := s.listeners.GetListener(ctx, sourceDeviceID)
	if err != nil {
		return fmt.Errorf("get listener: %w", err)
	}
	if !found {
		// Fallback to base "HOST-001" listener
		if strings.HasPrefix(sourceDeviceID, "HOST-001") {
			connectionID, found, _ = s.listeners.GetListener(ctx, "HOST-001")
		}
	}
	if !found && strings.Contains(sourceDeviceID, "_") {
		base := strings.SplitN(sourceDeviceID, "_", 2)[0]
		connectionID, found, _ = s.listeners.GetListener(ctx, base)
	}
	if !found {
		return nil
	}

	return s.forwarder.ForwardBinary(connectionID, frame)
}
