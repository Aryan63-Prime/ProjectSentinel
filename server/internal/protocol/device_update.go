package protocol

// DeviceUpdateMessage is the data payload for DEVICE_UPDATE messages
// broadcast to authenticated admin sessions when device state changes.
//
// Only populated fields are sent. Zero-value pointer fields are omitted.
type DeviceUpdateMessage struct {
	Event      string   `json:"event"`
	DeviceID   string   `json:"deviceId"`
	Latitude   *float64 `json:"latitude,omitempty"`
	Longitude  *float64 `json:"longitude,omitempty"`
	Accuracy   *float32 `json:"accuracy,omitempty"`
	Battery    *int     `json:"battery,omitempty"`
	Network    *string  `json:"network,omitempty"`
	Timestamp  *string  `json:"timestamp,omitempty"`
	DeviceName *string  `json:"deviceName,omitempty"`
	AppVersion *string  `json:"appVersion,omitempty"`
	Model      *string  `json:"model,omitempty"`
}

// Device update event names.
const (
	EventConnected    = "connected"
	EventDisconnected = "disconnected"
	EventHeartbeat    = "heartbeat"
	EventLocation     = "location"
	EventBattery      = "battery"
	EventNetwork      = "network"
	EventMetadata     = "metadata"
)
