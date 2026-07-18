# Project Sentinel Coding Standards

Version: 1.0

Status: Frozen

---

# Purpose

This document defines the coding standards for the entire Project Sentinel codebase.

Every contributor, including AI coding assistants, must follow these rules.

Consistency is more important than personal preference.

---

# General Principles

Code should be:

Readable

Maintainable

Testable

Predictable

Simple

Avoid clever code.

Prefer explicit code over implicit code.

---

# Architecture Rules

Always follow

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

No circular imports.

---

# Package Responsibility

Each package has one responsibility.

Good

gateway

dispatcher

auth

location

audio

repository

Bad

utils

helpers

common

misc

Avoid generic packages.

---

# File Size

Target

100–300 lines

Maximum

500 lines

Split files before they become too large.

---

# Function Size

Preferred

20–40 lines

Maximum

75 lines

Extract helper functions when needed.

---

# Naming

Packages

Lowercase

Examples

gateway

dispatcher

location

Variables

camelCase

Exported Types

PascalCase

Interfaces

Descriptive names.

Avoid

ManagerInterface

IData

RepositoryImpl

Good

DeviceRepository

LocationRepository

---

# Constructors

Every major component must have a constructor.

Example

func New() *Gateway

Never expose partially initialized structs.

---

# Dependency Injection

Use constructor injection.

Never use global mutable state.

Bad

var db *sql.DB

Good

type Repository struct {
db *sql.DB
}

---

# Error Handling

Always return errors.

Never ignore errors silently.

Bad

_ = repository.Save()

Good

if err := repository.Save(); err != nil {
return err
}

Wrap errors with context.

Example

fmt.Errorf("register device: %w", err)

---

# Logging

Use Zap Logger.

Never use fmt.Println()

Never log secrets.

Log levels

Debug

Info

Warn

Error

Fatal only in main()

---

# Context

Pass context.Context through all request paths.

Example

ctx

↓

Gateway

↓

Service

↓

Repository

Never create background contexts inside business logic.

---

# JSON

Always define structs.

Never use map[string]interface{} unless unavoidable.

Good

type RegisterRequest struct {
DeviceID string
}

Bad

map[string]interface{}

---

# Concurrency

Protect shared state with mutexes.

Prefer channels for communication.

Never share mutable state without synchronization.

Always stop goroutines on shutdown.

---

# Channels

Never leave channels blocked.

Close channels only by the sender.

Avoid unbounded channel growth.

---

# WebSocket

One reader goroutine.

One writer goroutine.

Never write from multiple goroutines simultaneously.

Use heartbeat.

Handle reconnects.

---

# Database

All SQL belongs in repositories.

Never execute SQL inside services.

Never execute SQL inside handlers.

---

# Redis

Only repositories interact with Redis.

Business logic never accesses Redis directly.

---

# Configuration

Read configuration only once during startup.

Use Viper.

Never hardcode:

Ports

Secrets

Database URLs

Redis URLs

JWT Secret

---

# Testing

Every service must have unit tests.

Critical components require integration tests.

Target

80%+ coverage for business logic.

---

# Benchmarks

Benchmark

Audio routing

Dispatcher

Heartbeat

Database queries

---

# Comments

Comment WHY.

Do not comment WHAT.

Bad

// increment i

i++

Good

// Retry once because mobile networks often reconnect after brief signal loss.

---

# Formatting

Always run

go fmt ./...

go vet ./...

golangci-lint run

before committing.

---

# Git

Commit often.

One feature per commit.

Use descriptive commit messages.

Good

feat(auth): validate JWT token

Good

fix(gateway): prevent duplicate sessions

Bad

update

changes

fixes

---

# Pull Requests

Every PR should include:

Purpose

Files Changed

Testing

Known Limitations

---

# Documentation

Every exported type requires documentation.

Every exported function requires documentation.

Public packages require README if complex.

---

# Performance

Avoid unnecessary allocations.

Reuse buffers where possible.

Profile before optimizing.

Correctness first.

Optimization second.

---

# Security

Never log passwords.

Never log JWTs.

Never log API keys.

Validate every client message.

Reject malformed packets.

Never trust client input.

---

# AI Contribution Rules

AI-generated code must:

Compile

Pass go fmt

Pass go vet

Follow architecture

Not introduce new dependencies without approval

Not change architecture

Not modify protocol without updating PROTOCOL.md

Not modify database schema without updating DATABASE.md

---

# Definition of Done

A task is complete only if:

✓ Builds successfully

✓ Tests pass

✓ Documentation updated

✓ No linter warnings

✓ Architecture respected

✓ Git commit created

---

# Coding Standards Freeze

This document is mandatory.

Every implementation must follow these standards.

Changes require updating this document before implementation.