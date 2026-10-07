package location

import (
	"context"
	"strings"

	"github.com/xaiop/project-sentinel/server/internal/protocol"
)

type Session interface {
	DeviceID() string
	AuthenticatedDeviceID() string
	SetLocation(message protocol.LocationMessage)
}

type Handler struct {
	service *Service
}

func NewHandler(service *Service) *Handler {
	return &Handler{
		service: service,
	}
}

func (h *Handler) Handle(ctx context.Context, session Session, message protocol.Message) (*protocol.Message, error) {
	_ = ctx

	var payload protocol.LocationMessage
	if err := message.DecodeData(&payload); err != nil {
		return nil, err
	}

	deviceID := strings.TrimSpace(session.DeviceID())
	if deviceID == "" {
		deviceID = strings.TrimSpace(session.AuthenticatedDeviceID())
	}

	if _, err := h.service.Handle(ctx, deviceID, payload); err != nil {
		return nil, err
	}

	// Also ensure alias lookup works so Redis key matches snapshot.DeviceID used in admin/service.go
	if authID := strings.TrimSpace(session.AuthenticatedDeviceID()); authID != "" && authID != deviceID {
		_, _ = h.service.Handle(ctx, authID, payload)
	}
	if strings.HasPrefix(deviceID, "HOST-001-") {
		_, _ = h.service.Handle(ctx, "HOST-001", payload)
		suffix := strings.TrimPrefix(deviceID, "HOST-001-")
		_, _ = h.service.Handle(ctx, "HOST-"+suffix, payload)
	}
	if strings.Contains(deviceID, "_") {
		base := strings.SplitN(deviceID, "_", 2)[0]
		_, _ = h.service.Handle(ctx, base, payload)
	}

	session.SetLocation(payload)

	return nil, nil
}
