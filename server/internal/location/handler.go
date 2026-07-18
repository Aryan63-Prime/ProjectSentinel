package location

import (
	"context"

	"github.com/xaiop/project-sentinel/server/internal/protocol"
)

type Session interface {
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

	if _, err := h.service.Handle(ctx, session.AuthenticatedDeviceID(), payload); err != nil {
		return nil, err
	}

	session.SetLocation(payload)

	return nil, nil
}
