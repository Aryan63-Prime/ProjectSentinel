package gateway

import "sync"

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
func (m *Manager) SnapshotByDeviceID(deviceID string) (SessionSnapshot, bool) {
	m.mu.RLock()
	defer m.mu.RUnlock()

	for _, session := range m.sessions {
		snapshot := session.Snapshot()
		if snapshot.DeviceID == deviceID {
			return snapshot, true
		}
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
