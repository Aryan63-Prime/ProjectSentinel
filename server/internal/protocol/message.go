package protocol

import (
	"encoding/json"
	"errors"
	"time"
)

const Version = 1

var (
	ErrInvalidMessage = errors.New("invalid message")
	ErrInvalidVersion = errors.New("invalid protocol version")
)

type Message struct {
	Type      MessageType     `json:"type"`
	Version   int             `json:"version"`
	Timestamp int64           `json:"timestamp"`
	Sequence  int64           `json:"sequence"`
	Data      json.RawMessage `json:"data"`
}

func DecodeMessage(data []byte) (Message, error) {
	var msg Message

	if err := json.Unmarshal(data, &msg); err != nil {
		return Message{}, err
	}

	if msg.Type == "" {
		return Message{}, ErrInvalidMessage
	}

	if msg.Version != Version {
		return Message{}, ErrInvalidVersion
	}

	if len(msg.Data) == 0 {
		msg.Data = json.RawMessage(`{}`)
	}

	return msg, nil
}

func (m Message) DecodeData(target any) error {
	if len(m.Data) == 0 {
		return nil
	}

	return json.Unmarshal(m.Data, target)
}

func NewMessage(messageType MessageType, sequence int64, data any) (*Message, error) {
	payload, err := json.Marshal(data)
	if err != nil {
		return nil, err
	}

	return &Message{
		Type:      messageType,
		Version:   Version,
		Timestamp: nowMilliseconds(),
		Sequence:  sequence,
		Data:      payload,
	}, nil
}

func NewError(sequence int64, code int, message string) *Message {
	payload, _ := json.Marshal(ErrorMessage{
		Code:    code,
		Message: message,
	})

	return &Message{
		Type:      TypeError,
		Version:   Version,
		Timestamp: nowMilliseconds(),
		Sequence:  sequence,
		Data:      payload,
	}
}

func nowMilliseconds() int64 {
	return time.Now().UnixNano() / int64(time.Millisecond)
}
