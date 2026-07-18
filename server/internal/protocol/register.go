package protocol

type RegisterMessage struct {
	DeviceID   string `json:"deviceId"`
	DeviceName string `json:"deviceName"`
	AppVersion string `json:"appVersion"`
	Model      string `json:"model"`
}

type RegisterAckMessage struct {
	Success bool `json:"success"`
}
