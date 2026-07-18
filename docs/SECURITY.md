# Project Sentinel Security

Version: 1.0

Status: Frozen

---

# Objectives

Protect

Users

Devices

Locations

Audio

Sessions

---

# Transport Security

Production

TLS 1.3

WebSocket Secure (WSS)

No plaintext communication.

---

# Authentication

JWT

Expiration

24 Hours

Refresh

Supported

Algorithm

HS256

Future

RS256

---

# Authorization

Roles

ADMIN

HOST

Future

SUPER_ADMIN

---

# Device Identity

Each Host owns a permanent DeviceID.

ConnectionID changes.

DeviceID never changes.

---

# Password Storage

bcrypt

Minimum Cost

12

Never store plaintext passwords.

---

# Secrets

Never commit:

JWT Secret

Database Password

Redis Password

API Keys

Use environment variables.

---

# Rate Limiting

Authentication

10 requests/minute

Registration

5 requests/minute

Heartbeat

Ignored

---

# Validation

Validate:

JSON

Coordinates

Battery

JWT

DeviceID

Reject malformed packets.

---

# Logging

Never log:

Passwords

JWT

Audio

API Keys

Personal information

---

# Audio

Audio is forwarded only.

Not stored by default.

Recording requires explicit enablement.

---

# Security Headers

HTTP

HSTS

X-Content-Type-Options

X-Frame-Options

Content-Security-Policy

---

# Future

mTLS

Certificate Pinning

Hardware-backed Keys

WebAuthn

---

# Security Freeze

Security changes require updating this document.