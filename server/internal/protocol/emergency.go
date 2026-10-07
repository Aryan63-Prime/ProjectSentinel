package protocol

// EmergencySOSMessage represents incoming SOS data from host devices (e.g. fall detection).
type EmergencySOSMessage struct {
	TriggerReason string  `json:"triggerReason"`
	ImpactGForce  float64 `json:"impactGForce"`
	Latitude      float64 `json:"latitude"`
	Longitude     float64 `json:"longitude"`
	Accuracy      float64 `json:"accuracy"`
	Battery       int     `json:"battery"`
	Timestamp     int64   `json:"timestamp"`
}
