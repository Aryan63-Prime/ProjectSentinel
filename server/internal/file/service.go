package file

import (
	"context"
	"errors"
	"fmt"
	"strings"
)

var ErrMissingDeviceID = errors.New("missing device id")

type ListenerRepository interface {
	SetListener(ctx context.Context, deviceID string, connectionID string) error
	GetListener(ctx context.Context, deviceID string) (string, bool, error)
	RemoveListener(ctx context.Context, deviceID string) error
}

type MessageForwarder interface {
	ForwardBinary(connectionID string, data []byte) error
	ForwardText(connectionID string, data []byte) error
}

type Service struct {
	listeners ListenerRepository
	forwarder MessageForwarder
}

func NewService(listeners ListenerRepository, forwarder MessageForwarder) *Service {
	return &Service{
		listeners: listeners,
		forwarder: forwarder,
	}
}

func (s *Service) SetForwarder(forwarder MessageForwarder) {
	s.forwarder = forwarder
}

func (s *Service) StartInteracting(ctx context.Context, adminConnectionID string, targetDeviceID string) error {
	if strings.TrimSpace(targetDeviceID) == "" {
		return ErrMissingDeviceID
	}
	if strings.TrimSpace(adminConnectionID) == "" {
		return fmt.Errorf("start interacting: missing admin connection id")
	}

	if s.listeners == nil {
		return nil
	}

	return s.listeners.SetListener(ctx, targetDeviceID, adminConnectionID)
}

func (s *Service) RouteBinaryChunk(ctx context.Context, sourceDeviceID string, chunk []byte) error {
	if s.listeners == nil || s.forwarder == nil {
		return nil
	}

	connectionID, found, err := s.listeners.GetListener(ctx, sourceDeviceID)
	if err != nil {
		return fmt.Errorf("get file listener: %w", err)
	}
	if !found {
		return nil
	}

	return s.forwarder.ForwardBinary(connectionID, chunk)
}

func (s *Service) RouteText(ctx context.Context, targetConnectionID string, payload []byte) error {
	if s.forwarder == nil {
		return nil
	}
	return s.forwarder.ForwardText(targetConnectionID, payload)
}

func (s *Service) GetListener(ctx context.Context, deviceID string) (string, bool, error) {
	if s.listeners == nil {
		return "", false, nil
	}
	return s.listeners.GetListener(ctx, deviceID)
}
