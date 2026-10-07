package fcm

import (
	"context"
	"fmt"
	"os"
	"sync"
	"time"

	firebase "firebase.google.com/go/v4"
	"firebase.google.com/go/v4/messaging"
	"go.uber.org/zap"
	"google.golang.org/api/option"
)

// Service manages FCM tokens and high-priority push messages to wake dormant Android devices.
type Service struct {
	client    *messaging.Client
	logger    *zap.Logger
	tokenLock sync.RWMutex
	tokens    map[string]string // deviceID -> fcmToken
}

// NewService instantiates Firebase Admin messaging client from service account credentials.
// It searches in order:
// 1. FCM_SERVICE_ACCOUNT_JSON environment variable (direct JSON string)
// 2. GOOGLE_APPLICATION_CREDENTIALS environment variable
// 3. /etc/secrets/serviceAccountKey.json (Render Secret File standard path)
// 4. credentialsFile parameter (e.g. local serviceAccountKey.json)
func NewService(credentialsFile string, logger *zap.Logger) (*Service, error) {
	var opt option.ClientOption
	ctx := context.Background()

	if rawJSON := os.Getenv("FCM_SERVICE_ACCOUNT_JSON"); rawJSON != "" {
		opt = option.WithCredentialsJSON([]byte(rawJSON))
		logger.Info("FCM service initialized via FCM_SERVICE_ACCOUNT_JSON environment variable")
	} else {
		targetPath := ""
		for _, candidate := range []string{
			os.Getenv("GOOGLE_APPLICATION_CREDENTIALS"),
			"/etc/secrets/serviceAccountKey.json",
			credentialsFile,
		} {
			if candidate != "" {
				if _, err := os.Stat(candidate); err == nil {
					targetPath = candidate
					break
				}
			}
		}

		if targetPath == "" {
			logger.Warn("FCM credentials not found (checked env FCM_SERVICE_ACCOUNT_JSON, /etc/secrets/serviceAccountKey.json, and local file). FCM push wakeup disabled.")
			return &Service{
				logger: logger,
				tokens: make(map[string]string),
			}, nil
		}

		opt = option.WithCredentialsFile(targetPath)
		logger.Info("FCM high-priority messaging service initialized from credentials file", zap.String("path", targetPath))
	}

	app, err := firebase.NewApp(ctx, nil, opt)
	if err != nil {
		return nil, fmt.Errorf("init firebase app: %w", err)
	}

	client, err := app.Messaging(ctx)
	if err != nil {
		return nil, fmt.Errorf("init messaging client: %w", err)
	}

	return &Service{
		client: client,
		logger: logger,
		tokens: make(map[string]string),
	}, nil
}

// RegisterToken maps a device ID or unique callsign to an FCM registration token.
func (s *Service) RegisterToken(deviceID, fcmToken string) {
	if deviceID == "" || fcmToken == "" {
		return
	}
	s.tokenLock.Lock()
	defer s.tokenLock.Unlock()
	s.tokens[deviceID] = fcmToken
	s.logger.Info("registered fcm token for device", zap.String("device_id", deviceID))
}

// GetToken returns the registered FCM token for a device ID.
func (s *Service) GetToken(deviceID string) (string, bool) {
	s.tokenLock.RLock()
	defer s.tokenLock.RUnlock()
	t, ok := s.tokens[deviceID]
	return t, ok
}

// WakeDevice dispatches a high-priority FCM data message to wake an Android host from Doze mode.
func (s *Service) WakeDevice(ctx context.Context, deviceID string) (string, error) {
	if s.client == nil {
		return "", fmt.Errorf("fcm client not initialized")
	}

	token, ok := s.GetToken(deviceID)
	if !ok || token == "" {
		return "", fmt.Errorf("no fcm token registered for device %s", deviceID)
	}

	msg := &messaging.Message{
		Token: token,
		Data: map[string]string{
			"action":    "WAKE_UP",
			"deviceId":  deviceID,
			"timestamp": fmt.Sprintf("%d", time.Now().UnixMilli()),
		},
		Android: &messaging.AndroidConfig{
			Priority: "high", // High priority is required to wake from Android Doze mode
		},
	}

	msgID, err := s.client.Send(ctx, msg)
	if err != nil {
		s.logger.Error("failed to send fcm wakeup message",
			zap.String("device_id", deviceID),
			zap.Error(err),
		)
		return "", err
	}

	s.logger.Info("sent high-priority fcm wakeup message",
		zap.String("device_id", deviceID),
		zap.String("message_id", msgID),
	)
	return msgID, nil
}
