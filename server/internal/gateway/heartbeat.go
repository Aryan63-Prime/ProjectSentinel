package gateway

import (
	"time"

	"github.com/gorilla/websocket"
	"go.uber.org/zap"
)

const (
	WriteTimeout = 5 * time.Second
)

// HeartbeatPolicy provides heartbeat timing without coupling gateway to heartbeat services.
type HeartbeatPolicy interface {
	Interval() time.Duration
	Timeout() time.Duration
	IsStale(lastHeartbeat time.Time) bool
}

func (g *Gateway) heartbeat(client *Client) {

	ticker := time.NewTicker(g.heartbeatPolicy.Interval())

	defer ticker.Stop()

	for {
		select {
		case <-client.Context.Done():
			return

		case <-ticker.C:
		}

		if g.heartbeatPolicy.IsStale(client.Session.LastHeartbeat()) {
			g.logger.Warn(
				"stale session detected",
				zap.String("connection_id", client.ConnectionID),
				zap.Duration("timeout", g.heartbeatPolicy.Timeout()),
			)
			client.Cancel()
			_ = client.Conn.Close()
			return
		}

		err := client.Conn.WriteControl(
			websocket.PingMessage,
			nil,
			time.Now().Add(WriteTimeout),
		)

		if err != nil {
			client.Cancel()
			_ = client.Conn.Close()
			return
		}
	}
}
