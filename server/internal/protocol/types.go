package protocol

type MessageType string

const (
	TypeAuth         MessageType = "AUTH"
	TypeAuthAck      MessageType = "AUTH_ACK"
	TypeRegister     MessageType = "REGISTER"
	TypeRegisterAck  MessageType = "REGISTER_ACK"
	TypeHeartbeat    MessageType = "HEARTBEAT"
	TypeHeartbeatAck MessageType = "HEARTBEAT_ACK"
	TypeLocation     MessageType = "LOCATION"
	TypeListen       MessageType = "LISTEN"
	TypeStop         MessageType = "STOP"
	TypePing         MessageType = "PING"
	TypePong         MessageType = "PONG"
	TypeError        MessageType = "ERROR"
	TypeDeviceUpdate MessageType = "DEVICE_UPDATE"

	// File operations
	TypeFilesListReq     MessageType = "FILES_LIST_REQ"
	TypeFilesListRes     MessageType = "FILES_LIST_RES"
	TypeFileDownloadReq  MessageType = "FILE_DOWNLOAD_REQ"
	TypeFileDownloadRes  MessageType = "FILE_DOWNLOAD_RES"
	TypeFileChunkAck     MessageType = "FILE_CHUNK_ACK"
	TypeFileStopReq      MessageType = "FILE_STOP_REQ"
)
