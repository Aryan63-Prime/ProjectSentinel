package gateway

import (
	"net/http"

	"github.com/gorilla/websocket"
)

var upgrader = websocket.Upgrader{
	ReadBufferSize:  4096,
	WriteBufferSize: 4096,

	CheckOrigin: func(r *http.Request) bool {
		return true
	},
}
