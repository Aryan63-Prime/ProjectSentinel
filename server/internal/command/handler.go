package command

import (
	"context"
	"encoding/json"

	"github.com/xaiop/project-sentinel/server/internal/protocol"
)

type Session interface {
	ConnectionID() string
	IsAdmin() bool
	AuthenticatedDeviceID() string
}

type SessionFinder interface {
	GetConnectionIDByDeviceID(deviceID string) (string, bool)
}

type Router interface {
	ForwardText(connectionID string, data []byte) error
	BroadcastToAdmins(data []byte)
}

type FcmWaker interface {
	WakeDevice(ctx context.Context, deviceID string) (string, error)
}

type Handler struct {
	finder SessionFinder
	router Router
	fcm    FcmWaker
}

func NewHandler(finder SessionFinder, router Router) *Handler {
	return &Handler{
		finder: finder,
		router: router,
	}
}

// SetFcmWaker assigns an FCM waker to auto-ping sleeping devices.
func (h *Handler) SetFcmWaker(w FcmWaker) {
	h.fcm = w
}

// HandleCommand routes a COMMAND message from an Admin to the target Host device.
func (h *Handler) HandleCommand(ctx context.Context, session Session, msg protocol.Message) (*protocol.Message, error) {
	if !session.IsAdmin() {
		return protocol.NewError(msg.Sequence, 403, "Forbidden: Only admins can send commands"), nil
	}

	var payload protocol.CommandMessage
	if err := msg.DecodeData(&payload); err != nil {
		return protocol.NewError(msg.Sequence, 400, "Invalid command payload"), nil
	}

	hostConnID, ok := h.finder.GetConnectionIDByDeviceID(payload.TargetDeviceID)
	if !ok {
		if h.fcm != nil {
			go func() {
				_, _ = h.fcm.WakeDevice(context.Background(), payload.TargetDeviceID)
			}()
		}
		return protocol.NewError(msg.Sequence, 404, "Target host device not found or offline (FCM wake ping sent)"), nil
	}

	rawMsg, err := json.Marshal(msg)
	if err != nil {
		return nil, err
	}

	if err := h.router.ForwardText(hostConnID, rawMsg); err != nil {
		return protocol.NewError(msg.Sequence, 500, "Failed to deliver command to host"), nil
	}

	return nil, nil
}

// HandleCommandResult receives a COMMAND_RESULT from a Host device and broadcasts it to Admin sessions.
func (h *Handler) HandleCommandResult(ctx context.Context, session Session, msg protocol.Message) error {
	rawMsg, err := json.Marshal(msg)
	if err != nil {
		return err
	}

	h.router.BroadcastToAdmins(rawMsg)
	return nil
}
