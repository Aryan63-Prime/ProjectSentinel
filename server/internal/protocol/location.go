package protocol

type LocationMessage struct {
	Latitude  float64 `json:"latitude"`
	Longitude float64 `json:"longitude"`
	Accuracy  float32 `json:"accuracy"`
	Battery   int     `json:"battery"`
	Network   string  `json:"network"`
}
