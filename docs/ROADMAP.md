# Project Sentinel Roadmap

Version: 1.0

Status: Active

---

# Vision

Project Sentinel is a production-grade real-time monitoring platform.

The project consists of:

- Go Backend Server
- Android Host Application
- Android Admin Application

The primary objective is:

- Reliable
- Low Latency
- Battery Efficient
- Secure
- Scalable

---

# Current Status

Architecture

✅ Complete

Protocol

✅ Complete

Database Design

✅ Complete

Coding Standards

⏳ Pending

Implementation

In Progress

---

# Phase 1 — Foundation

Goal

Build a stable backend foundation.

Milestones

✅ Project Structure

✅ Configuration

✅ Logging

✅ Bootstrap

✅ HTTP Server

✅ WebSocket Gateway

✅ Session Manager

✅ Dispatcher

Deliverables

Working server

Health endpoint

WebSocket endpoint

Session management

Dispatcher

Status

Completed

---

# Phase 2 — Communication

Goal

Implement request/response protocol.

Milestones

Authentication

Device Registration

Heartbeat

Location Updates

Ping/Pong

Deliverables

AUTH

REGISTER

LOCATION

HEARTBEAT

Error handling

Status

Completed

---

# Phase 3 — Audio

Goal

Realtime voice monitoring.

Milestones

Opus Encoder

Binary Frames

Server Routing

Admin Playback

Packet Recovery

Deliverables

Host → Server

Server → Admin

Low latency

Status

Planned

---

# Phase 4 — Persistence

Goal

Persistent storage.

Milestones

PostgreSQL

Redis

Repositories

Migrations

Audit Logs

Deliverables

Persistent devices

Historical locations

Sessions

Metadata

Status

Planned

---

# Phase 5 — Android Host

Goal

Battery-efficient foreground service.

Modules

Foreground Service

Audio Engine

Location Engine

Connection Manager

Reconnect Logic

Battery Optimization

Deliverables

Always-on monitoring

Automatic reconnect

Background operation

Status

Planned

---

# Phase 6 — Android Admin

Goal

Monitoring interface.

Modules

Authentication

Dashboard

Google Maps

Device List

Audio Player

Notifications

Deliverables

Live monitoring

Device selection

Realtime audio

Status

Planned

---

# Phase 7 — Security

Goal

Production security.

Tasks

JWT

TLS

Role Based Access

Rate Limiting

API Validation

Audit Logging

Deliverables

Secure communication

Protected endpoints

Status

Planned

---

# Phase 8 — Production

Goal

Deploy production environment.

Tasks

Docker

Docker Compose

NGINX

HTTPS

Monitoring

Metrics

Deliverables

Production deployment

Status

Planned

---

# Phase 9 — Observability

Goal

Monitor server health.

Tools

Prometheus

Grafana

Structured Logging

Performance Metrics

Deliverables

Realtime dashboards

Alerting

Status

Planned

---

# Phase 10 — Optimization

Goal

Improve scalability.

Tasks

Connection Pooling

Worker Pools

Redis Optimization

Database Indexing

Memory Optimization

CPU Optimization

Deliverables

Support thousands of devices

Status

Planned

---

# Future Features

Device Groups

Live Recording

Push Notifications

Web Dashboard

Video Streaming

Geofencing

SOS Alerts

Offline Sync

OTA Updates

Device Commands

Multi-Tenant Support

---

# Definition of Done

Every feature must satisfy:

✓ Compiles

✓ Unit Tested

✓ go vet passes

✓ golangci-lint passes

✓ Documentation updated

✓ Git Commit created

✓ No architecture violations

---

# Git Strategy

Branch

main

Stable production code.

Feature Branch

feature/<feature-name>

Examples

feature/auth

feature/location

feature/audio

Bug Fix

bugfix/<issue>

Example

bugfix/websocket-timeout

---

# Commit Format

Examples

feat(auth): implement JWT validation

feat(location): add GPS update handler

fix(gateway): reconnect heartbeat

refactor(dispatcher): split handlers

docs(protocol): update packet format

test(audio): add routing tests

---

# Release Tags

v0.1.0

Foundation

v0.2.0

Communication

v0.3.0

Audio

v0.4.0

Persistence

v0.5.0

Android Host

v0.6.0

Android Admin

v0.7.0

Production Ready

---

# Success Criteria

The project is considered complete when:

✓ Multiple Hosts connect simultaneously

✓ Admin monitors locations in realtime

✓ Admin listens to selected Host

✓ Automatic reconnect works

✓ Heartbeats detect disconnects

✓ Authentication is enforced

✓ PostgreSQL stores historical data

✓ Redis stores realtime data

✓ Docker deployment works

✓ Production monitoring is available

---

# Roadmap Ownership

This roadmap is the authoritative implementation plan.

Features are implemented in roadmap order.

Major deviations require updating this document before implementation.
