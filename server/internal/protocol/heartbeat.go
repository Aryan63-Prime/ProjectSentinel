package protocol

type HeartbeatMessage struct {
}

type HeartbeatAckMessage struct {
	Success bool `json:"success"`
}
