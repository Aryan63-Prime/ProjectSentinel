package device

import (
	"errors"
	"strings"

	"github.com/xaiop/project-sentinel/server/internal/protocol"
)

var (
	ErrMissingDeviceID       = errors.New("missing device id")
	ErrMissingDeviceName     = errors.New("missing device name")
	ErrMissingAppVersion     = errors.New("missing app version")
	ErrMissingModel          = errors.New("missing model")
	ErrDeviceMismatch        = errors.New("device id does not match authenticated identity")
	ErrAlreadyRegistered     = errors.New("device already registered")
	ErrUnauthenticatedDevice = errors.New("device is not authenticated")
)

type Service struct {
}

func NewService() *Service {
	return &Service{}
}

func (s *Service) ValidateRegistration(message protocol.RegisterMessage, authenticatedDeviceID string, alreadyRegistered bool) error {
	_ = s

	if strings.TrimSpace(message.DeviceID) == "" {
		return ErrMissingDeviceID
	}

	if strings.TrimSpace(message.DeviceName) == "" {
		return ErrMissingDeviceName
	}

	if strings.TrimSpace(message.AppVersion) == "" {
		return ErrMissingAppVersion
	}

	if strings.TrimSpace(message.Model) == "" {
		return ErrMissingModel
	}

	if strings.TrimSpace(authenticatedDeviceID) == "" {
		return ErrUnauthenticatedDevice
	}

	if strings.TrimSpace(authenticatedDeviceID) != "" && message.DeviceID != authenticatedDeviceID {
		return ErrDeviceMismatch
	}

	if alreadyRegistered {
		return ErrAlreadyRegistered
	}

	return nil
}
