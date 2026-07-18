package bootstrap

import (
	"time"

	"github.com/xaiop/project-sentinel/server/internal/admin"
	"github.com/xaiop/project-sentinel/server/internal/app"
	"github.com/xaiop/project-sentinel/server/internal/audio"
	"github.com/xaiop/project-sentinel/server/internal/auth"
	"github.com/xaiop/project-sentinel/server/internal/config"
	"github.com/xaiop/project-sentinel/server/internal/database"
	"github.com/xaiop/project-sentinel/server/internal/device"
	"github.com/xaiop/project-sentinel/server/internal/dispatcher"
	"github.com/xaiop/project-sentinel/server/internal/gateway"
	"github.com/xaiop/project-sentinel/server/internal/health"
	"github.com/xaiop/project-sentinel/server/internal/heartbeat"
	"github.com/xaiop/project-sentinel/server/internal/location"
	"github.com/xaiop/project-sentinel/server/internal/logger"
	"github.com/xaiop/project-sentinel/server/internal/metrics"
	"github.com/xaiop/project-sentinel/server/internal/repository"
)

func Build() (*app.Application, error) {

	cfg, err := config.Load()
	if err != nil {
		return nil, err
	}

	log, err := logger.New()
	if err != nil {
		return nil, err
	}

	authService := auth.New(cfg.JWT.Secret)
	authHandler := auth.NewHandler(authService)
	deviceHandler := device.NewHandler(device.NewService())
	heartbeatService := heartbeat.NewService()
	heartbeatHandler := heartbeat.NewHandler(heartbeatService)
	redisClient := database.NewRedisClient(database.RedisOptions{
		Host:     cfg.Redis.Host,
		Port:     cfg.Redis.Port,
		Password: cfg.Redis.Password,
		DB:       cfg.Redis.DB,
	})
	locationRepository := repository.NewRedisLocationRepository(redisClient, time.Duration(cfg.Redis.LocationTTLSeconds)*time.Second)
	locationService := location.NewServiceWithDependencies(locationRepository, nil, nil)
	locationHandler := location.NewHandler(locationService)
	listenerRepository := repository.NewRedisListenerRepository(redisClient)
	audioService := audio.NewService(listenerRepository, nil)
	audioHandler := audio.NewHandler(audioService)
	dispatch := dispatcher.New(authHandler, deviceHandler, heartbeatHandler, locationHandler, audioHandler)
	gw := gateway.New(dispatch, heartbeatService, log)
	dispatch.SetBroadcaster(gw)
	audioService.SetForwarder(gw)
	gw.SetHealthService(health.NewService(componentCheckers(cfg, redisClient)...))
	adminService := admin.NewService(adminSessionSource{gateway: gw}, locationRepository, heartbeatService)
	adminHandler := admin.NewHandler(adminService, authService)
	metricsHandler := metrics.NewHandler(gw.MetricsCollector(), authService)
	gw.HandleFunc("/metrics", metricsHandler.ServeHTTP)
	gw.HandleFunc("/devices", adminHandler.ListDevices)
	gw.HandleFunc("/devices/", adminHandler.GetDevice)

	application := &app.Application{
		Config:  cfg,
		Logger:  log,
		Gateway: gw,
	}
	application.RegisterCloser(redisClient)

	return application, nil
}

type adminSessionSource struct {
	gateway *gateway.Gateway
}

func (s adminSessionSource) ListSessions() []admin.SessionSnapshot {
	snapshots := s.gateway.SessionSnapshots()
	adminSnapshots := make([]admin.SessionSnapshot, 0, len(snapshots))

	for _, snapshot := range snapshots {
		adminSnapshots = append(adminSnapshots, toAdminSessionSnapshot(snapshot))
	}

	return adminSnapshots
}

func (s adminSessionSource) GetSessionByDeviceID(deviceID string) (admin.SessionSnapshot, bool) {
	snapshot, ok := s.gateway.SessionSnapshotByDeviceID(deviceID)
	if !ok {
		return admin.SessionSnapshot{}, false
	}

	return toAdminSessionSnapshot(snapshot), true
}

func toAdminSessionSnapshot(snapshot gateway.SessionSnapshot) admin.SessionSnapshot {
	return admin.SessionSnapshot{
		ConnectionID:  snapshot.ConnectionID,
		DeviceID:      snapshot.DeviceID,
		Authenticated: snapshot.Authenticated,
		Registered:    snapshot.Registered,
		ConnectedAt:   snapshot.ConnectedAt,
		LastHeartbeat: snapshot.LastHeartbeat,
		DeviceName:    snapshot.DeviceName,
		AppVersion:    snapshot.AppVersion,
		Model:         snapshot.Model,
	}
}

func componentCheckers(cfg *config.Config, redisClient *database.RedisClient) []health.Checker {
	checkers := []health.Checker{
		health.NewPingChecker("redis", redisClient),
	}

	if postgresConfigured(cfg.Database) {
		checkers = append(checkers, health.NewStaticChecker("postgresql", health.StatusConfigured))
	}

	return checkers
}

func postgresConfigured(cfg config.DatabaseConfig) bool {
	return cfg.Host != "" && cfg.Port != "" && cfg.Name != ""
}
