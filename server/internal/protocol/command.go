package protocol

// CommandMessage is the payload for COMMAND messages sent by Admin to Host.
type CommandMessage struct {
	TargetDeviceID string                 `json:"targetDeviceId"`
	Command        string                 `json:"command"`
	Params         map[string]interface{} `json:"params,omitempty"`
}

// CommandResultMessage is the payload for COMMAND_RESULT messages sent by Host back to Admin.
type CommandResultMessage struct {
	DeviceID string                 `json:"deviceId"`
	Command  string                 `json:"command"`
	Success  bool                   `json:"success"`
	Error    string                 `json:"error,omitempty"`
	Payload  map[string]interface{} `json:"payload,omitempty"`
}
