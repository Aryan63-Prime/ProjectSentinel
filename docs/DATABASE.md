# Project Sentinel Database Design

Version: 1.0

Status: Frozen

---

# Overview

Project Sentinel uses two storage systems.

1. PostgreSQL
2. Redis

Responsibilities

PostgreSQL

Persistent storage

Redis

Realtime storage

---

# PostgreSQL

Purpose

Persistent application data.

Stores

Users

Devices

Sessions

Location History

Audio Recording Metadata

Audit Logs

Application Configuration

---

# Redis

Purpose

Fast in-memory storage.

Stores

Connected Devices

Current Sessions

Current Locations

Heartbeat Status

Selected Audio Listener

Temporary Authentication Cache

Rate Limiting

---

# Entity Relationship

User

↓

Device

↓

Session

↓

Location History

↓

Audit Logs

Audio Recordings (optional)

---

# users

Purpose

Stores administrator accounts.

Columns

id

UUID

Primary Key

username

TEXT

Unique

password_hash

TEXT

role

TEXT

Default

ADMIN

created_at

TIMESTAMP

updated_at

TIMESTAMP

---

# devices

Purpose

Stores every Host device.

Columns

id

UUID

Primary Key

device_id

TEXT

Unique

Permanent Identity

device_name

TEXT

manufacturer

TEXT

model

TEXT

android_version

TEXT

app_version

TEXT

status

TEXT

ONLINE

OFFLINE

LAST_SEEN

created_at

TIMESTAMP

updated_at

TIMESTAMP

Indexes

device_id

status

---

# sessions

Purpose

Stores every connection.

Columns

id

UUID

Primary Key

device_id

UUID

FK → devices

connection_id

TEXT

authenticated

BOOLEAN

connected_at

TIMESTAMP

disconnected_at

TIMESTAMP

disconnect_reason

TEXT

Indexes

device_id

connected_at

---

# locations

Purpose

Stores historical GPS locations.

Columns

id

BIGSERIAL

Primary Key

device_id

UUID

latitude

DOUBLE PRECISION

longitude

DOUBLE PRECISION

accuracy

REAL

battery

INTEGER

network

TEXT

recorded_at

TIMESTAMP

Indexes

device_id

recorded_at

---

# recordings

Purpose

Metadata only.

Audio files are NOT stored inside PostgreSQL.

Columns

id

UUID

device_id

UUID

started_at

TIMESTAMP

ended_at

TIMESTAMP

duration_seconds

INTEGER

storage_url

TEXT

Indexes

device_id

started_at

---

# audit_logs

Purpose

Stores important events.

Columns

id

BIGSERIAL

event_type

TEXT

device_id

UUID

user_id

UUID

details

JSONB

created_at

TIMESTAMP

Indexes

event_type

device_id

created_at

---

# Redis Keys

device:{deviceId}

Current device state.

Example

device:HOST-0001

Contains

ConnectionID

Authenticated

Battery

Network

Current Location

Last Heartbeat

---

session:{connectionId}

Active session.

TTL

Automatically expires.

---

location:{deviceId}

Latest GPS location.

TTL

5 Minutes

---

listener:{deviceId}

Current Admin listening to Host.

Example

listener:HOST-0001

Value

ADMIN-0003

---

heartbeat:{deviceId}

Last heartbeat timestamp.

TTL

60 Seconds

---

# Data Retention

Locations

90 Days

Audit Logs

180 Days

Sessions

30 Days

Recordings Metadata

365 Days

Redis

Automatic Expiration

---

# Transactions

Required For

Authentication

Device Registration

Recording Metadata

Not Required For

Heartbeat

Realtime Location

Audio Routing

---

# Migration Strategy

All schema changes use versioned SQL migrations.

Example

001_initial_schema.sql

002_add_locations.sql

003_add_recordings.sql

Never modify an existing migration.

Always create a new migration.

---

# Repository Pattern

Each table has its own repository.

UserRepository

DeviceRepository

SessionRepository

LocationRepository

RecordingRepository

AuditRepository

Repositories contain SQL only.

No business logic.

---

# Performance

Indexes

device_id

connection_id

recorded_at

status

Partition large historical tables if necessary.

---

# Backup

PostgreSQL

Daily Full Backup

Hourly WAL Archive

Redis

Persistence Enabled

RDB Snapshot

Append Only File

---

# Future Scaling

Read Replicas

Supported

Sharding

Not Required

Partitioning

Location History

Audit Logs

---

# Database Freeze

This schema is frozen.

Changes require updating this document before implementation.