# Project Sentinel API Specification

Version: 1.0

Status: Frozen

---

# Overview

The server exposes two interfaces:

1. HTTP API
2. WebSocket API

HTTP is used for:

- Health
- Metrics
- Administration
- Future REST endpoints

WebSocket is used for:

- Authentication
- Device Registration
- Heartbeats
- Location
- Audio
- Live Events

---

# Base URL

Development

http://localhost:8080

Production

https://api.projectsentinel.com

---

# HTTP Endpoints

## GET /health

Purpose

Health check.

Response

200 OK

{
"status":"ok"
}

---

## GET /metrics

Purpose

Prometheus metrics.

Authentication

Required

---

## GET /devices

Purpose

Returns connected devices with realtime state.

Authentication

Required

Header

Authorization: Bearer JWT

Response

200 OK

{
"devices":[
{
"deviceId":"HOST-0001",
"connectionId":"CONNECTION_ID",
"authenticated":true,
"registered":true,
"registrationState":"registered",
"heartbeatStatus":"online",
"connectedAt":"2026-07-09T12:00:00Z",
"lastHeartbeat":"2026-07-09T12:00:20Z",
"deviceName":"Pixel 9",
"appVersion":"1.0.0",
"model":"Google Pixel",
"latestLocation":{
"deviceId":"HOST-0001",
"latitude":28.6139,
"longitude":77.2090,
"accuracy":5.4,
"battery":81,
"network":"5G",
"recordedAt":"2026-07-09T12:00:10Z"
}
}
]
}

---

## GET /devices/{deviceId}

Purpose

Returns one connected device with realtime state.

Authentication

Required

Header

Authorization: Bearer JWT

Response

200 OK

{
"deviceId":"HOST-0001",
"connectionId":"CONNECTION_ID",
"authenticated":true,
"registered":true,
"registrationState":"registered",
"heartbeatStatus":"online",
"connectedAt":"2026-07-09T12:00:00Z",
"lastHeartbeat":"2026-07-09T12:00:20Z",
"deviceName":"Pixel 9",
"appVersion":"1.0.0",
"model":"Google Pixel",
"latestLocation":null
}

---

## GET /sessions

Purpose

Returns active sessions.

---

## GET /locations

Purpose

Returns latest locations.

---

# WebSocket

Endpoint

/ws

Protocol

WebSocket Secure (WSS)

---

# Authentication

Client

↓

AUTH

↓

AUTH_ACK

Only authenticated clients may continue.

---

# Request Flow

Connect

↓

AUTH

↓

REGISTER

↓

Heartbeat

↓

Location

↓

Audio

↓

Disconnect

---

# Response Codes

200

Success

400

Bad Request

401

Unauthorized

403

Forbidden

404

Not Found

409

Conflict

500

Internal Error

---

# Versioning

API Version

v1

Future versions must remain backward compatible whenever practical.

---

# API Freeze

Changes require updating this document.
