package audio

import (
	"encoding/binary"
	"errors"
)

// PacketTypeAudio identifies an Opus audio binary frame.
const PacketTypeAudio byte = 0x01

// headerSize is the fixed binary frame header: 1 (type) + 4 (sequence) + 8 (timestamp).
const headerSize = 13

var (
	// ErrFrameTooSmall indicates the binary frame is shorter than the header.
	ErrFrameTooSmall = errors.New("audio frame too small")

	// ErrInvalidPacketType indicates an unrecognized packet type byte.
	ErrInvalidPacketType = errors.New("invalid packet type")
)

// FrameHeader contains the parsed binary audio frame header.
type FrameHeader struct {
	PacketType byte
	Sequence   uint32
	Timestamp  int64
}

// ParseHeader extracts the header fields from a binary audio frame.
// The returned header does not copy the underlying data.
func ParseHeader(data []byte) (FrameHeader, error) {
	if len(data) < headerSize {
		return FrameHeader{}, ErrFrameTooSmall
	}

	return FrameHeader{
		PacketType: data[0],
		Sequence:   binary.BigEndian.Uint32(data[1:5]),
		Timestamp:  int64(binary.BigEndian.Uint64(data[5:13])),
	}, nil
}

// ValidateFrame checks that data is a well-formed audio binary frame.
func ValidateFrame(data []byte) error {
	if len(data) < headerSize {
		return ErrFrameTooSmall
	}
	if data[0] != PacketTypeAudio {
		return ErrInvalidPacketType
	}

	return nil
}
