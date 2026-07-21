package file

import (
	"context"
	"encoding/json"
	"fmt"

	"github.com/xaiop/project-sentinel/server/internal/protocol"
)

type Session interface {
	ConnectionID() string
	IsAdmin() bool
}

type SessionFinder interface {
	GetConnectionIDByDeviceID(deviceID string) (string, bool)
}

type Handler struct {
	service *Service
	finder  SessionFinder
}

func NewHandler(service *Service, finder SessionFinder) *Handler {
	return &Handler{
		service: service,
		finder:  finder,
	}
}

func (h *Handler) HandleFilesListReq(ctx context.Context, session Session, msg protocol.Message) (*protocol.Message, error) {
	var payload protocol.FilesListReqMessage
	if err := msg.DecodeData(&payload); err != nil {
		return nil, err
	}

	// 1. Mark this Admin as the "interactor" for this device so responses can be routed back.
	if err := h.service.StartInteracting(ctx, session.ConnectionID(), payload.DeviceID); err != nil {
		return nil, err
	}

	// 2. Find the Host's connection
	hostConnID, ok := h.finder.GetConnectionIDByDeviceID(payload.DeviceID)
	if !ok {
		return protocol.NewError(msg.Sequence, 404, "Device not found"), nil
	}

	// 3. Relay the request to the Host
	rawMsg, _ := json.Marshal(msg)
	if err := h.service.RouteText(ctx, hostConnID, rawMsg); err != nil {
		return nil, err
	}

	return nil, nil // No direct response to Admin; wait for Host's FILES_LIST_RES
}

func (h *Handler) HandleFilesListRes(ctx context.Context, deviceID string, msg protocol.Message) error {
	// Find the Admin who requested this
	adminConnID, ok, err := h.service.GetListener(ctx, deviceID)
	if err != nil || !ok {
		return err
	}

	rawMsg, _ := json.Marshal(msg)
	return h.service.RouteText(ctx, adminConnID, rawMsg)
}

func (h *Handler) HandleFileDownloadReq(ctx context.Context, session Session, msg protocol.Message) (*protocol.Message, error) {
	var payload protocol.FileDownloadReqMessage
	if err := msg.DecodeData(&payload); err != nil {
		return nil, err
	}

	if err := h.service.StartInteracting(ctx, session.ConnectionID(), payload.DeviceID); err != nil {
		return nil, err
	}

	hostConnID, ok := h.finder.GetConnectionIDByDeviceID(payload.DeviceID)
	if !ok {
		return protocol.NewError(msg.Sequence, 404, "Device not found"), nil
	}

	rawMsg, _ := json.Marshal(msg)
	return nil, h.service.RouteText(ctx, hostConnID, rawMsg)
}

func (h *Handler) HandleFileDownloadRes(ctx context.Context, deviceID string, msg protocol.Message) error {
	adminConnID, ok, err := h.service.GetListener(ctx, deviceID)
	if err != nil || !ok {
		return err
	}

	rawMsg, _ := json.Marshal(msg)
	return h.service.RouteText(ctx, adminConnID, rawMsg)
}

func (h *Handler) HandleFileChunkAck(ctx context.Context, session Session, msg protocol.Message) (*protocol.Message, error) {
	var payload protocol.FileChunkAckMessage
	if err := msg.DecodeData(&payload); err != nil {
		return nil, err
	}

	hostConnID, ok := h.finder.GetConnectionIDByDeviceID(payload.DeviceID)
	if !ok {
		return nil, fmt.Errorf("device not found")
	}

	rawMsg, _ := json.Marshal(msg)
	return nil, h.service.RouteText(ctx, hostConnID, rawMsg)
}

func (h *Handler) HandleFileStopReq(ctx context.Context, session Session, msg protocol.Message) (*protocol.Message, error) {
	var payload protocol.FileStopReqMessage
	if err := msg.DecodeData(&payload); err != nil {
		return nil, err
	}

	hostConnID, ok := h.finder.GetConnectionIDByDeviceID(payload.DeviceID)
	if !ok {
		return nil, fmt.Errorf("device not found")
	}

	rawMsg, _ := json.Marshal(msg)
	return nil, h.service.RouteText(ctx, hostConnID, rawMsg)
}

func (h *Handler) HandleBinaryChunk(ctx context.Context, deviceID string, chunk []byte) error {
	return h.service.RouteBinaryChunk(ctx, deviceID, chunk)
}
