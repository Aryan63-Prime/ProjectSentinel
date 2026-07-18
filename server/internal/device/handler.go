package device

import (
	"context"

	"github.com/xaiop/project-sentinel/server/internal/protocol"
)

type Session interface {
	AuthenticatedDeviceID() string
	IsRegistered() bool
	SetRegistered(message protocol.RegisterMessage)
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

	var payload protocol.RegisterMessage
	if err := message.DecodeData(&payload); err != nil {
		return nil, err
	}

	if err := h.service.ValidateRegistration(payload, session.AuthenticatedDeviceID(), session.IsRegistered()); err != nil {
		return nil, err
	}

	session.SetRegistered(payload)

	return protocol.NewMessage(
		protocol.TypeRegisterAck,
		message.Sequence,
		protocol.RegisterAckMessage{Success: true},
	)
}
