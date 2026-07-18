# ============================================================
# Stage 1: Build the Go binary
# ============================================================
FROM golang:1.26-alpine AS builder

RUN apk add --no-cache git ca-certificates

WORKDIR /src

# Cache dependencies
COPY go.mod go.sum ./
RUN go mod download

# Build
COPY . .
RUN CGO_ENABLED=0 GOOS=linux GOARCH=amd64 \
    go build -ldflags="-s -w" -o /sentinel ./cmd/server

# ============================================================
# Stage 2: Minimal runtime image
# ============================================================
FROM alpine:3.22

RUN apk add --no-cache ca-certificates tzdata \
    && addgroup -S sentinel \
    && adduser -S -G sentinel sentinel

WORKDIR /app

COPY --from=builder /sentinel .

# Non-root
USER sentinel

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/health || exit 1

ENTRYPOINT ["./sentinel"]
