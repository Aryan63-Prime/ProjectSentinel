package heartbeat

import (
	"context"
	"time"

	"github.com/xaiop/project-sentinel/server/internal/protocol"
)

type Session interface {
	SetLastHeartbeat(timestamp time.Time)
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

	var payload protocol.HeartbeatMessage
	if err := message.DecodeData(&payload); err != nil {
		return nil, err
	}

	session.SetLastHeartbeat(h.service.Timestamp())

	return protocol.NewMessage(
		protocol.TypeHeartbeatAck,
		message.Sequence,
		protocol.HeartbeatAckMessage{Success: true},
	)
}
