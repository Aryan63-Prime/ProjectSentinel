# Project Sentinel Architecture

Version: 1.0

Status: Frozen

---

# Purpose

Project Sentinel is a real-time monitoring platform.

The system consists of three independent applications:

1. Server (Go)
2. Host Android Application
3. Admin Android Application

Hosts continuously transmit information to the server.

The Admin application can monitor hosts in real time.

Hosts NEVER communicate with each other.

All communication passes through the server.

---

# High Level Architecture

                    +-------------------+
                    |   Admin App       |
                    +---------+---------+
                              |
                              |
                       WebSocket (TLS)
                              |
                              |
+-------------+      +---------v---------+      +-------------+
| Host App 1  |----->|      Server       |<-----| Host App N  |
+-------------+      +---------+---------+      +-------------+
|
|
PostgreSQL + Redis

---

# Responsibilities

## Server

The server is responsible for:

- Authentication
- Session Management
- Device Registration
- Audio Routing
- Location Routing
- Heartbeat Monitoring
- Device Status
- Logging
- Metrics

The server NEVER records audio unless recording is explicitly enabled.

---

## Host Application

The Host application:

- Captures microphone audio
- Captures GPS location
- Reports battery status
- Reports network state
- Maintains WebSocket connection
- Runs as Foreground Service

The Host application NEVER receives audio.

---

## Admin Application

The Admin application:

- Shows connected devices
- Shows live locations
- Selects one device to monitor
- Plays received audio
- Displays battery/network information

---

# Server Layers

The server follows layered architecture.

Gateway

↓

Dispatcher

↓

Handlers

↓

Services

↓

Repositories

↓

Database

Each layer has exactly one responsibility.

---

# Dependency Rules

Allowed

Gateway

↓

Dispatcher

↓

Handlers

↓

Services

↓

Repositories

↓

Database

Forbidden

Repository → Gateway

Database → Gateway

Services → Gateway

Handlers → Database

Repositories → Dispatcher

---

# Session Model

Each connection has three identities.

ConnectionID

Random UUID

↓

SessionID

Temporary

↓

DeviceID

Permanent

DeviceID never changes.

ConnectionID changes after reconnect.

---

# Communication

Control Messages

JSON

REGISTER

AUTH

HEARTBEAT

LOCATION

LISTEN

STOP

ERROR

Audio

Binary WebSocket Frames

Opus Encoded

No Base64.

No JSON.

---

# Audio Flow

Host

↓

AudioRecord

↓

Opus Encoder

↓

WebSocket Binary Frame

↓

Server

↓

Selected Admin

↓

AudioTrack

The server only forwards audio.

No decoding.

No transcoding.

---

# Location Flow

Host

↓

Gateway

↓

Dispatcher

↓

Location Service

↓

Redis

↓

Admin

---

# Storage

PostgreSQL

Persistent data

Redis

Realtime data

---

# Security

TLS

JWT

Role Based Access

Device Identity

---

# Deployment

Docker

↓

Go Server

↓

PostgreSQL

↓

Redis

↓

Prometheus

↓

Grafana

---

# Coding Principles

Single Responsibility

No Circular Dependencies

Dependency Injection

No Global Mutable State

One Package = One Responsibility

---

# Architecture Freeze

This architecture is frozen.

Implementation must conform to this document.

Architecture changes require updating this document first.