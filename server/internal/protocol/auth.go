package protocol

type AuthMessage struct {
	Token string `json:"token"`
}

type AuthAckMessage struct {
	Success bool `json:"success"`
}
