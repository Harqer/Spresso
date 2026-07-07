package auth

import (
	"testing"
)

func TestClerkAuthenticatorInit(t *testing.T) {
	auth := NewClerkAuthenticator("https://clerk.aura.com", "insforge-api")
	if auth.FrontendAPI != "https://clerk.aura.com" {
		t.Errorf("Expected FrontendAPI to be set correctly")
	}
}

func TestVerifyTokenInvalid(t *testing.T) {
	auth := NewClerkAuthenticator("https://clerk.aura.com", "insforge-api")
	_, err := auth.VerifyToken("invalid-token")
	if err == nil {
		t.Errorf("Expected error for invalid token")
	}
}
