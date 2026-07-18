package bootstrap

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/golang-jwt/jwt/v5"
	"github.com/xaiop/project-sentinel/server/internal/admin"
	"github.com/xaiop/project-sentinel/server/internal/auth"
)

func TestBuildRegistersAdminDeviceRoutes(t *testing.T) {
	application, err := Build()
	if err != nil {
		t.Fatalf("Build returned error: %v", err)
	}

	request := httptest.NewRequest(http.MethodGet, "/devices", nil)
	request.Header.Set("Authorization", "Bearer "+signedBootstrapToken(t, application.Config.JWT.Secret))
	response := httptest.NewRecorder()

	application.Gateway.Handler().ServeHTTP(response, request)

	if response.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d", response.Code)
	}

	var payload admin.ListDevicesResponse
	if err := json.NewDecoder(response.Body).Decode(&payload); err != nil {
		t.Fatalf("Decode returned error: %v", err)
	}
	if len(payload.Devices) != 0 {
		t.Fatalf("expected no connected devices, got %d", len(payload.Devices))
	}
}

func signedBootstrapToken(t *testing.T, secret string) string {
	t.Helper()

	token := jwt.NewWithClaims(jwt.SigningMethodHS256, auth.Claims{
		DeviceID: "ADMIN-0001",
		RegisteredClaims: jwt.RegisteredClaims{
			ExpiresAt: jwt.NewNumericDate(time.Now().Add(time.Hour)),
		},
	})

	signed, err := token.SignedString([]byte(secret))
	if err != nil {
		t.Fatalf("SignedString returned error: %v", err)
	}

	return signed
}
