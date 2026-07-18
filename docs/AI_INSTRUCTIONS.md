# AI Instructions

Project: Project Sentinel

Version: 1.0

Status: Mandatory

---

# Purpose

This document defines how AI coding assistants must contribute to Project Sentinel.

All generated code must follow these instructions.

These instructions override default AI behavior.

---

# Project Goal

Project Sentinel is a production-grade realtime monitoring platform.

Components

- Go Backend
- Android Host Application
- Android Admin Application

Primary Goals

- Reliable
- Secure
- Low Latency
- Battery Efficient
- Scalable
- Maintainable

---

# Required Reading

Before implementing any feature, ALWAYS read:

ARCHITECTURE.md

PROTOCOL.md

DATABASE.md

ROADMAP.md

CODING_STANDARDS.md

API.md

SECURITY.md

TESTING.md

These documents are the source of truth.

---

# Never Do These Things

Do NOT change architecture.

Do NOT change protocol.

Do NOT change database schema.

Do NOT rename packages without approval.

Do NOT introduce new dependencies without approval.

Do NOT create circular imports.

Do NOT hardcode secrets.

Do NOT bypass repositories.

Do NOT skip tests.

Do NOT create placeholder implementations.

Do NOT leave TODO comments.

Do NOT leave code that does not compile.

---

# Architecture Rules

Follow this dependency graph exactly.

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

Never violate dependency direction.

---

# Implementation Rules

Every feature must:

Compile

Pass go fmt

Pass go vet

Pass tests

Follow coding standards

Update documentation if required

Be production ready

---

# Package Rules

Each package has exactly one responsibility.

Example

gateway

dispatcher

audio

location

auth

repository

Never create generic packages like

helpers

misc

common

utils

unless explicitly approved.

---

# Feature Development

Implement one feature at a time.

Never combine unrelated features.

Each implementation should be a complete vertical slice.

Example

Authentication

↓

Gateway

↓

Handler

↓

Service

↓

Repository

↓

Tests

↓

Documentation

---

# Testing

Every new service requires:

Unit Tests

Integration Tests when applicable

No feature is complete without tests.

---

# Performance

Prefer readability.

Optimize only after measurement.

Avoid unnecessary allocations.

Reuse buffers where possible.

---

# Security

Never trust client input.

Validate everything.

Never log

JWT

Passwords

Secrets

API Keys

Audio Data

---

# Logging

Use Zap.

Never use fmt.Println().

Log useful context.

Do not log sensitive information.

---

# Database

SQL only inside repositories.

Never execute SQL inside services.

Never execute SQL inside handlers.

---

# WebSocket

One reader goroutine.

One writer goroutine.

Heartbeat required.

Graceful shutdown required.

Reconnect supported.

---

# Audio

Use Opus.

Never Base64 encode audio.

Never send audio inside JSON.

Use binary WebSocket frames.

---

# Git

One feature per commit.

Small commits.

Descriptive commit messages.

Examples

feat(auth): implement JWT authentication

feat(location): add GPS update handler

fix(gateway): prevent duplicate sessions

---

# Documentation

If architecture changes

Update ARCHITECTURE.md

If protocol changes

Update PROTOCOL.md

If database changes

Update DATABASE.md

If API changes

Update API.md

Documentation is part of implementation.

---

# Build Requirements

Before finishing a task

Run

go fmt ./...

go vet ./...

go test ./...

go build ./...

Project must build successfully.

---

# Definition of Done

A task is complete only if

✓ Builds successfully

✓ Tests pass

✓ Documentation updated

✓ No architecture violations

✓ No compile warnings

✓ Git commit created

---

# AI Workflow

For every request

1. Read documentation

2. Understand architecture

3. Identify affected modules

4. Minimize changes

5. Implement

6. Test

7. Verify build

8. Update documentation

9. Produce commit message

Never skip steps.

---

# Long-Term Vision

Project Sentinel should be maintainable for many years.

Prefer clean architecture over shortcuts.

Prefer correctness over cleverness.

Prefer simplicity over complexity.

Every change should improve the project.

---

# Final Rule

If a request conflicts with these instructions,

follow the documentation first,

then ask for clarification before changing architecture or project direction.
# Self Review (Mandatory)

Before returning your answer:

1. Review your own implementation.

2. Fix every issue found.

3. Run

go fmt ./...

go vet ./...

go test ./...

go build ./...

4. Review again for

- architecture
- security
- concurrency
- duplication
- race conditions
- websocket lifecycle

5. Only then present the implementation.

Never ask for a second review.
Return only after the implementation satisfies all acceptance criteria.

Do not stop halfway.

Do not ask for another implementation step.

Complete the entire sprint before responding.