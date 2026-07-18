package dispatcher

import (
	"context"
	"encoding/json"
	"errors"
	"time"

	"github.com/xaiop/project-sentinel/server/internal/audio"
	"github.com/xaiop/project-sentinel/server/internal/auth"
	"github.com/xaiop/project-sentinel/server/internal/device"
	"github.com/xaiop/project-sentinel/server/internal/heartbeat"
	"github.com/xaiop/project-sentinel/server/internal/location"
	"github.com/xaiop/project-sentinel/server/internal/protocol"
)

type Session interface {
	device.Session
	heartbeat.Session
	location.Session
	audio.Session

	IsAuthenticated() bool
	AuthenticatedDeviceID() string
}

// Broadcaster sends messages to authenticated admin sessions.
type Broadcaster interface {
	BroadcastToAdmins(payload []byte)
}

// Result describes a dispatched control response and connection side effects.
type Result struct {
	Message               *protocol.Message
	AuthenticatedDeviceID string
	CloseConnection       bool
}

type Dispatcher struct {
	auth        *auth.Handler
	device      *device.Handler
	heartbeat   *heartbeat.Handler
	location    *location.Handler
	audio       *audio.Handler
	broadcaster Broadcaster
}

func New(
	authHandler *auth.Handler,
	deviceHandler *device.Handler,
	heartbeatHandler *heartbeat.Handler,
	locationHandler *location.Handler,
	audioHandler *audio.Handler,
) *Dispatcher {
	return &Dispatcher{
		auth:      authHandler,
		device:    deviceHandler,
		heartbeat: heartbeatHandler,
		location:  locationHandler,
		audio:     audioHandler,
	}
}

// SetBroadcaster configures the admin broadcast target.
func (d *Dispatcher) SetBroadcaster(b Broadcaster) {
	d.broadcaster = b
}

func (d *Dispatcher) Dispatch(ctx context.Context, session Session, data []byte) Result {

	message, err := protocol.DecodeMessage(data)
	if err != nil {
		return Result{
			Message: protocol.NewError(0, 400, "Bad Request"),
		}
	}

	if message.Type == protocol.TypeAuth {
		return d.dispatchAuthenticated(ctx, session, message)
	}

	if !session.IsAuthenticated() {
		return Result{
			Message: protocol.NewError(message.Sequence, 401, "Unauthorized"),
		}
	}

	switch message.Type {
	case protocol.TypeRegister:
		response, err := d.device.Handle(ctx, session, message)
		result := d.dispatchWithErrors(response, err, message.Sequence)
		if err == nil {
			var reg protocol.RegisterMessage
			_ = message.DecodeData(&reg)
			d.broadcastDeviceUpdate(protocol.DeviceUpdateMessage{
				Event:      protocol.EventConnected,
				DeviceID:   reg.DeviceID,
				DeviceName: strPtr(reg.DeviceName),
				AppVersion: strPtr(reg.AppVersion),
				Model:      strPtr(reg.Model),
			})
		}
		return result

	case protocol.TypeHeartbeat:
		response, err := d.heartbeat.Handle(ctx, session, message)
		result := d.dispatchWithErrors(response, err, message.Sequence)
		if err == nil {
			ts := time.Now().UTC().Format(time.RFC3339)
			d.broadcastDeviceUpdate(protocol.DeviceUpdateMessage{
				Event:    protocol.EventHeartbeat,
				DeviceID: session.AuthenticatedDeviceID(),
				Timestamp: &ts,
			})
		}
		return result

	case protocol.TypeLocation:
		response, err := d.location.Handle(ctx, session, message)
		result := d.dispatchWithErrors(response, err, message.Sequence)
		if err == nil {
			var loc protocol.LocationMessage
			_ = message.DecodeData(&loc)
			d.broadcastDeviceUpdate(protocol.DeviceUpdateMessage{
				Event:     protocol.EventLocation,
				DeviceID:  session.AuthenticatedDeviceID(),
				Latitude:  &loc.Latitude,
				Longitude: &loc.Longitude,
				Accuracy:  &loc.Accuracy,
				Battery:   &loc.Battery,
				Network:   &loc.Network,
			})
		}
		return result

	case protocol.TypeListen:
		response, err := d.dispatchAudio(ctx, session, message)
		return d.dispatchWithErrors(response, err, message.Sequence)

	case protocol.TypeStop:
		response, err := d.dispatchStop(ctx, session, message)
		return d.dispatchWithErrors(response, err, message.Sequence)

	case protocol.TypePing:
		response, err := protocol.NewMessage(protocol.TypePong, message.Sequence, protocol.PongMessage{})
		if err != nil {
			return Result{
				Message: protocol.NewError(message.Sequence, 500, "Internal Server Error"),
			}
		}
		return Result{Message: response}

	default:
		return Result{
			Message: protocol.NewError(message.Sequence, 400, "Bad Request"),
		}
	}
}

// DispatchBinary routes a binary audio frame to the audio handler.
func (d *Dispatcher) DispatchBinary(ctx context.Context, session Session, data []byte) error {
	if !session.IsAuthenticated() {
		return nil
	}

	if d.audio == nil {
		return nil
	}

	return d.audio.HandleFrame(ctx, session.AuthenticatedDeviceID(), data)
}

func (d *Dispatcher) dispatchAudio(ctx context.Context, session Session, message protocol.Message) (*protocol.Message, error) {
	if d.audio == nil {
		return nil, nil
	}

	return d.audio.HandleListen(ctx, session, message)
}

func (d *Dispatcher) dispatchStop(ctx context.Context, session Session, message protocol.Message) (*protocol.Message, error) {
	if d.audio == nil {
		return nil, nil
	}

	return d.audio.HandleStop(ctx, session, message)
}

func (d *Dispatcher) dispatchAuthenticated(ctx context.Context, session Session, message protocol.Message) Result {
	_ = session

	result, err := d.auth.Handle(ctx, message)
	if err != nil {
		return d.dispatchAuthenticationError(err, message.Sequence)
	}

	return Result{
		Message:               result.Response,
		AuthenticatedDeviceID: result.DeviceID,
	}
}

func (d *Dispatcher) dispatchWithErrors(response *protocol.Message, err error, sequence int64) Result {
	if err == nil {
		return Result{Message: response}
	}

	if errors.Is(err, device.ErrDeviceMismatch) {
		return Result{
			Message: protocol.NewError(sequence, 403, "Forbidden"),
		}
	}

	if errors.Is(err, device.ErrAlreadyRegistered) {
		return Result{
			Message: protocol.NewError(sequence, 409, "Already Registered"),
		}
	}

	return Result{
		Message: protocol.NewError(sequence, 400, "Bad Request"),
	}
}

func (d *Dispatcher) dispatchAuthenticationError(err error, sequence int64) Result {
	if auth.IsAuthenticationError(err) {
		return Result{
			Message:         protocol.NewError(sequence, 401, "Unauthorized"),
			CloseConnection: true,
		}
	}

	return Result{
		Message:         protocol.NewError(sequence, 400, "Bad Request"),
		CloseConnection: true,
	}
}

// broadcastDeviceUpdate serializes a DEVICE_UPDATE message and sends it to all admin sessions.
// Failures are silently ignored — broadcast is best-effort.
func (d *Dispatcher) broadcastDeviceUpdate(update protocol.DeviceUpdateMessage) {
	if d.broadcaster == nil {
		return
	}

	msg, err := protocol.NewMessage(protocol.TypeDeviceUpdate, 0, update)
	if err != nil {
		return
	}

	payload, err := json.Marshal(msg)
	if err != nil {
		return
	}

	d.broadcaster.BroadcastToAdmins(payload)
}

// BroadcastDisconnect broadcasts a device disconnection event to admin sessions.
// Called from the gateway readLoop cleanup when a host session disconnects.
func (d *Dispatcher) BroadcastDisconnect(deviceID string) {
	if deviceID == "" {
		return
	}

	d.broadcastDeviceUpdate(protocol.DeviceUpdateMessage{
		Event:    protocol.EventDisconnected,
		DeviceID: deviceID,
	})
}

func strPtr(s string) *string {
	if s == "" {
		return nil
	}
	return &s
}
