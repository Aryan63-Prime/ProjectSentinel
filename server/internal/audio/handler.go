package audio

import (
	"context"
	"encoding/json"

	"github.com/xaiop/project-sentinel/server/internal/protocol"
)

// Session provides the connection identity needed by audio handlers.
type Session interface {
	ConnectionID() string
}

// SessionFinder locates active sessions by device ID.
type SessionFinder interface {
	GetConnectionIDByDeviceID(deviceID string) (string, bool)
}

// Router forwards control text messages to connected clients.
type Router interface {
	ForwardText(connectionID string, data []byte) error
}

// FcmWaker wakes sleeping hosts via high-priority FCM.
type FcmWaker interface {
	WakeDevice(ctx context.Context, deviceID string) (string, error)
}

// Handler routes audio control messages and binary frames.
type Handler struct {
	service *Service
	finder  SessionFinder
	router  Router
	fcm     FcmWaker
}

// NewHandler creates an audio handler.
func NewHandler(service *Service) *Handler {
	return &Handler{service: service}
}

// SetFinder assigns a session finder for host device routing.
func (h *Handler) SetFinder(finder SessionFinder) {
	h.finder = finder
}

// SetRouter assigns a router for forwarding control messages to host devices.
func (h *Handler) SetRouter(router Router) {
	h.router = router
}

// SetFcmWaker assigns an FCM waker to auto-wake sleeping host devices.
func (h *Handler) SetFcmWaker(waker FcmWaker) {
	h.fcm = waker
}

// HandleListen processes LISTEN control messages from admins and notifies the Host device.
func (h *Handler) HandleListen(ctx context.Context, session Session, message protocol.Message) (*protocol.Message, error) {
	var payload protocol.ListenMessage
	if err := message.DecodeData(&payload); err != nil {
		return nil, err
	}

	if err := h.service.StartListening(ctx, session.ConnectionID(), payload.DeviceID); err != nil {
		return nil, err
	}

	// Relay LISTEN signal to the target Host device to initiate on-demand audio capture
	if h.finder != nil && h.router != nil {
		hostConnID, ok := h.finder.GetConnectionIDByDeviceID(payload.DeviceID)
		if !ok {
			if h.fcm != nil {
				go func() {
					_, _ = h.fcm.WakeDevice(context.Background(), payload.DeviceID)
				}()
			}
			return protocol.NewError(message.Sequence, 404, "Target host device not found or offline (FCM wake ping sent)"), nil
		}

		rawMsg, err := json.Marshal(message)
		if err == nil {
			_ = h.router.ForwardText(hostConnID, rawMsg)
		}
	}

	return nil, nil
}

// HandleStop processes STOP control messages from admins and notifies the Host device.
func (h *Handler) HandleStop(ctx context.Context, session Session, message protocol.Message) (*protocol.Message, error) {
	var payload protocol.StopMessage
	if err := message.DecodeData(&payload); err != nil {
		return nil, err
	}

	if err := h.service.StopListening(ctx, payload.DeviceID); err != nil {
		return nil, err
	}

	// Relay STOP signal to the target Host device to stop audio capture and release mic
	if h.finder != nil && h.router != nil {
		hostConnID, ok := h.finder.GetConnectionIDByDeviceID(payload.DeviceID)
		if ok {
			rawMsg, err := json.Marshal(message)
			if err == nil {
				_ = h.router.ForwardText(hostConnID, rawMsg)
			}
		}
	}

	return nil, nil
}

// HandleFrame routes a binary audio frame to the listening admin.
func (h *Handler) HandleFrame(ctx context.Context, deviceID string, frame []byte) error {
	return h.service.RouteFrame(ctx, deviceID, frame)
}
