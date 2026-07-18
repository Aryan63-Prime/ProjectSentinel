package gateway

import (
	"bufio"
	"context"
	"errors"
	"net"
	"net/http"
	"strings"
	"time"

	"github.com/google/uuid"
	"go.uber.org/zap"
)

const requestIDHeader = "X-Request-ID"

type requestIDContextKey struct {
}

func (g *Gateway) requestIDMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		requestID := requestIDFromHeader(r.Header.Get(requestIDHeader))
		if requestID == "" {
			requestID = uuid.NewString()
		}

		w.Header().Set(requestIDHeader, requestID)
		ctx := context.WithValue(r.Context(), requestIDContextKey{}, requestID)
		next.ServeHTTP(w, r.WithContext(ctx))
	})
}

func (g *Gateway) requestLoggingMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		started := time.Now()
		recorder := &responseRecorder{
			ResponseWriter: w,
			status:         http.StatusOK,
		}

		next.ServeHTTP(recorder, r)

		duration := time.Since(started)
		status := recorder.status
		path := metricPath(r.URL.Path)

		if g.metrics != nil {
			g.metrics.ObserveRequest(r.Method, path, status, duration)
		}

		g.logger.Info(
			"http request completed",
			zap.String("request_id", requestIDFromContext(r.Context())),
			zap.String("method", r.Method),
			zap.String("path", path),
			zap.Int("status", status),
			zap.Duration("duration", duration),
		)
	})
}

func requestIDFromHeader(value string) string {
	value = strings.TrimSpace(value)
	if len(value) > 128 {
		return ""
	}

	return value
}

func requestIDFromContext(ctx context.Context) string {
	requestID, _ := ctx.Value(requestIDContextKey{}).(string)
	return requestID
}

func metricPath(path string) string {
	switch {
	case path == "/health":
		return "/health"
	case path == "/metrics":
		return "/metrics"
	case path == "/ws":
		return "/ws"
	case path == "/devices":
		return "/devices"
	case strings.HasPrefix(path, "/devices/"):
		return "/devices/{deviceId}"
	default:
		return "unknown"
	}
}

type responseRecorder struct {
	http.ResponseWriter
	status      int
	wroteHeader bool
}

func (r *responseRecorder) WriteHeader(status int) {
	if r.wroteHeader {
		return
	}

	r.status = status
	r.wroteHeader = true
	r.ResponseWriter.WriteHeader(status)
}

func (r *responseRecorder) Write(data []byte) (int, error) {
	if !r.wroteHeader {
		r.WriteHeader(http.StatusOK)
	}

	return r.ResponseWriter.Write(data)
}

func (r *responseRecorder) Flush() {
	if flusher, ok := r.ResponseWriter.(http.Flusher); ok {
		flusher.Flush()
	}
}

func (r *responseRecorder) Hijack() (net.Conn, *bufio.ReadWriter, error) {
	hijacker, ok := r.ResponseWriter.(http.Hijacker)
	if !ok {
		return nil, nil, errors.New("response writer does not support hijacking")
	}

	return hijacker.Hijack()
}

func (r *responseRecorder) Push(target string, opts *http.PushOptions) error {
	pusher, ok := r.ResponseWriter.(http.Pusher)
	if !ok {
		return http.ErrNotSupported
	}

	return pusher.Push(target, opts)
}
