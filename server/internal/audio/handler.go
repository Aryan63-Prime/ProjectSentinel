package audio

import (
	"context"

	"github.com/xaiop/project-sentinel/server/internal/protocol"
)

// Session provides the connection identity needed by audio handlers.
type Session interface {
	ConnectionID() string
}

// Handler routes audio control messages and binary frames.
type Handler struct {
	service *Service
}

// NewHandler creates an audio handler.
func NewHandler(service *Service) *Handler {
	return &Handler{service: service}
}

// HandleListen processes LISTEN control messages from admins.
func (h *Handler) HandleListen(ctx context.Context, session Session, message protocol.Message) (*protocol.Message, error) {
	var payload protocol.ListenMessage
	if err := message.DecodeData(&payload); err != nil {
		return nil, err
	}

	if err := h.service.StartListening(ctx, session.ConnectionID(), payload.DeviceID); err != nil {
		return nil, err
	}

	return nil, nil
}

// HandleStop processes STOP control messages from admins.
func (h *Handler) HandleStop(ctx context.Context, session Session, message protocol.Message) (*protocol.Message, error) {
	var payload protocol.StopMessage
	if err := message.DecodeData(&payload); err != nil {
		return nil, err
	}

	if err := h.service.StopListening(ctx, payload.DeviceID); err != nil {
		return nil, err
	}

	return nil, nil
}

// HandleFrame routes a binary audio frame to the listening admin.
func (h *Handler) HandleFrame(ctx context.Context, deviceID string, frame []byte) error {
	return h.service.RouteFrame(ctx, deviceID, frame)
}
