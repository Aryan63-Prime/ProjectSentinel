package protocol

type FilesListReqMessage struct {
	DeviceID string `json:"deviceId"`
	Path     string `json:"path"`
}

type FileItem struct {
	Name         string `json:"name"`
	IsDir        bool   `json:"is_dir"`
	Size         int64  `json:"size"`
	LastModified int64  `json:"last_modified"`
}

type FilesListResMessage struct {
	Path  string     `json:"path"`
	Items []FileItem `json:"items"`
}

type FileDownloadReqMessage struct {
	DeviceID string `json:"deviceId"`
	Path     string `json:"path"`
	Offset   int64  `json:"offset"`
	Nonce    string `json:"nonce"`
}

type FileDownloadResMessage struct {
	Path    string `json:"path"`
	Success bool   `json:"success"`
	Size    int64  `json:"size"`
	Error   string `json:"error,omitempty"`
}

type FileChunkAckMessage struct {
	DeviceID string `json:"deviceId"`
	Path     string `json:"path"`
	Sequence int64  `json:"sequence"`
}

type FileStopReqMessage struct {
	DeviceID string `json:"deviceId"`
	Path     string `json:"path"`
}
