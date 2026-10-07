package gateway

import (
	"strings"
	"sync"
)

type Manager struct {
	mu sync.RWMutex

	sessions map[string]*Session
}

func NewManager() *Manager {
	return &Manager{
		sessions: make(map[string]*Session),
	}
}

func (m *Manager) Add(session *Session) {
	m.mu.Lock()
	defer m.mu.Unlock()

	m.sessions[session.ConnectionID()] = session
}

func (m *Manager) Remove(connectionID string) {
	m.mu.Lock()
	defer m.mu.Unlock()

	delete(m.sessions, connectionID)
}

func (m *Manager) Get(connectionID string) (*Session, bool) {
	m.mu.RLock()
	defer m.mu.RUnlock()

	session, ok := m.sessions[connectionID]
	return session, ok
}

func (m *Manager) Count() int {
	m.mu.RLock()
	defer m.mu.RUnlock()

	return len(m.sessions)
}

// Snapshots returns read-only copies of all connected sessions.
func (m *Manager) Snapshots() []SessionSnapshot {
	m.mu.RLock()
	defer m.mu.RUnlock()

	snapshots := make([]SessionSnapshot, 0, len(m.sessions))
	for _, session := range m.sessions {
		snapshots = append(snapshots, session.Snapshot())
	}

	return snapshots
}

// SnapshotByDeviceID returns a read-only session copy for a device.
// Matches deviceID, connectionID, uniqueKey (e.g. HOST-001_SM-S928B),
// callsign suffix, model, or prefix.
// If multiple sessions share the same deviceID, it prioritizes the newest registered host session.
func (m *Manager) SnapshotByDeviceID(deviceID string) (SessionSnapshot, bool) {
	m.mu.RLock()
	defer m.mu.RUnlock()

	var registered SessionSnapshot
	foundRegistered := false

	cleanTarget := strings.TrimSpace(deviceID)
	var singleHostSession SessionSnapshot
	registeredHostCount := 0

	for _, session := range m.sessions {
		// Admin sessions (or unauthenticated/unregistered sessions) must NEVER be targeted as hosts
		if session.IsAdmin() || !session.IsRegistered() {
			continue
		}

		snapshot := session.Snapshot()
		registeredHostCount++
		singleHostSession = snapshot

		uniqueKey := snapshot.DeviceID
		if snapshot.Model != "" {
			uniqueKey = snapshot.DeviceID + "_" + snapshot.Model
		}

		// Exact matches
		matches := snapshot.DeviceID == cleanTarget ||
			snapshot.ConnectionID == cleanTarget ||
			uniqueKey == cleanTarget ||
			(cleanTarget != "" && strings.Contains(snapshot.DeviceName, cleanTarget))

		// Flexible / Prefix / Suffix / Model matching
		if !matches && cleanTarget != "" {
			// 1. Target is base ID (e.g. "HOST-001") and session is hardware-suffixed (e.g. "HOST-001-VIVO-2D2C")
			if strings.HasPrefix(snapshot.DeviceID, cleanTarget+"-") || strings.HasPrefix(snapshot.DeviceID, cleanTarget+"_") {
				matches = true
			}
			// 2. Target has model suffix (e.g. "HOST-001_I2401")
			if strings.Contains(cleanTarget, "_") {
				parts := strings.SplitN(cleanTarget, "_", 2)
				targetPrefix := parts[0]
				targetModel := parts[1]
				if (snapshot.Model == targetModel || strings.EqualFold(snapshot.Model, targetModel)) &&
					(snapshot.DeviceID == targetPrefix || strings.HasPrefix(snapshot.DeviceID, targetPrefix+"-") || targetPrefix == "HOST-001") {
					matches = true
				}
			}
			// 3. Target is callsign suffix (e.g. "HOST-VIVO-2D2C" or "VIVO-2D2C")
			if strings.HasSuffix(snapshot.DeviceID, cleanTarget) ||
				strings.HasSuffix(snapshot.DeviceID, strings.TrimPrefix(cleanTarget, "HOST-")) {
				matches = true
			}
		}

		if matches {
			if !foundRegistered || snapshot.ConnectedAt.After(registered.ConnectedAt) {
				registered = snapshot
				foundRegistered = true
			}
		}
	}

	if foundRegistered {
		return registered, true
	}

	// Fallback: If only 1 host is online and target is generic "HOST-001" or starts with "HOST-001"
	if registeredHostCount == 1 && (cleanTarget == "HOST-001" || strings.HasPrefix(cleanTarget, "HOST-001")) {
		return singleHostSession, true
	}

	return SessionSnapshot{}, false
}

// CloseAll cancels and closes every active session connection.
// Each readLoop's deferred cleanup handles channel close and manager removal.
func (m *Manager) CloseAll() {
	m.mu.RLock()
	defer m.mu.RUnlock()

	for _, session := range m.sessions {
		if session.client != nil {
			session.client.Cancel()
			_ = session.client.Conn.Close()
		}
	}
}

// ForEachAdmin calls fn for every authenticated admin session.
// Admin sessions are identified by being authenticated with no device ID.
func (m *Manager) ForEachAdmin(fn func(client *Client)) {
	m.mu.RLock()
	defer m.mu.RUnlock()

	for _, session := range m.sessions {
		if session.IsAdmin() && session.client != nil {
			fn(session.client)
		}
	}
}
