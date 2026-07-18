# Project Sentinel Communication Protocol

Version: 1.0

Status: Frozen

---

# Overview

All communication between the Host Application,
Admin Application, and Server follows this protocol.

The protocol consists of two independent channels.

1. Control Messages
2. Audio Stream

---

# Transport

Protocol: WebSocket

Security: TLS (WSS)

Encoding:

Control → JSON

Audio → Binary

---

# Connection Lifecycle

Host

↓

TCP Connection

↓

TLS Handshake

↓

WebSocket Upgrade

↓

AUTH

↓

AUTH_ACK

↓

REGISTER

↓

REGISTER_ACK

↓

Heartbeat

↓

Location Updates

↓

Audio Streaming

↓

Disconnect

---

# Control Messages

Every control message MUST follow this format.

{
"type": "MESSAGE_TYPE",
"version": 1,
"timestamp": 1752050000,
"sequence": 1,
"data": { }
}

Fields

type

Message type.

version

Protocol version.

timestamp

Unix timestamp in milliseconds.

sequence

Increasing message sequence number.

data

Payload.

---

# Message Types

AUTH

REGISTER

REGISTER_ACK

HEARTBEAT

HEARTBEAT_ACK

LOCATION

LISTEN

STOP

PING

PONG

ERROR

---

# AUTH

Host → Server

{
"type":"AUTH",
"version":1,
"timestamp":0,
"sequence":1,
"data":{
"token":"JWT_TOKEN"
}
}

Response

{
"type":"AUTH_ACK",
"version":1,
"timestamp":0,
"sequence":1,
"data":{
"success":true
}
}

---

# REGISTER

Host → Server

{
"type":"REGISTER",
"version":1,
"timestamp":0,
"sequence":2,
"data":{
"deviceId":"HOST-0001",
"deviceName":"Pixel 9",
"appVersion":"1.0.0",
"model":"Google Pixel"
}
}

Response

{
"type":"REGISTER_ACK",
"version":1,
"timestamp":0,
"sequence":2,
"data":{
"success":true
}
}

---

# HEARTBEAT

Host → Server

{
"type":"HEARTBEAT",
"version":1,
"timestamp":0,
"sequence":3,
"data":{
}
}

Server

↓

HEARTBEAT_ACK

Heartbeat Interval

20 Seconds

Connection Timeout

60 Seconds

---

# LOCATION

Host → Server

{
"type":"LOCATION",
"version":1,
"timestamp":0,
"sequence":4,
"data":{
"latitude":28.6139,
"longitude":77.2090,
"accuracy":5.4,
"battery":81,
"network":"5G"
}
}

---

# LISTEN

Admin → Server

{
"type":"LISTEN",
"version":1,
"timestamp":0,
"sequence":5,
"data":{
"deviceId":"HOST-0001"
}
}

---

# STOP

Admin → Server

{
"type":"STOP",
"version":1,
"timestamp":0,
"sequence":6,
"data":{
"deviceId":"HOST-0001"
}
}

---

# ERROR

Server → Client

{
"type":"ERROR",
"version":1,
"timestamp":0,
"sequence":7,
"data":{
"code":401,
"message":"Unauthorized"
}
}

---

# Audio Frames

Audio never uses JSON.

Audio is transmitted as binary WebSocket frames.

Packet Layout

+------------+
| 1 Byte     |
| PacketType |
+------------+

+------------+
| 4 Bytes    |
| Sequence   |
+------------+

+------------+
| 8 Bytes    |
| Timestamp  |
+------------+

+------------+
| Remaining  |
| Opus Data  |
+------------+

---

# Audio Codec

Codec

Opus

Sample Rate

48000 Hz

Channels

1 (Mono)

Frame Duration

20 ms

Bitrate

Adaptive

Initial Target

24 kbps

---

# Compression

No GZIP

No Base64

No JSON

Only raw Opus frames.

---

# Reconnection

If connection drops

↓

Reconnect

↓

AUTH

↓

REGISTER

↓

Resume Heartbeats

The server restores the existing Device Session using DeviceID.

---

# Versioning

Protocol Version

1

Future protocol changes MUST increment version.

Older versions remain supported whenever possible.

---

# Error Codes

400

Bad Request

401

Unauthorized

403

Forbidden

404

Not Found

409

Already Registered

500

Internal Server Error

---

# Sequence Numbers

Every outgoing message increments the sequence number.

Sequence numbers help detect:

Duplicate packets

Lost packets

Out-of-order packets

---

# Protocol Freeze

This document is frozen.

Neither server nor Android applications may introduce new message formats without updating this document.