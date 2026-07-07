package main

import (
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"net/url"
	"os"
	"sync"
	"time"

	"github.com/gorilla/websocket"
	"github.com/meta-wearable/aura-gateway/internal/auth"
	"github.com/meta-wearable/aura-gateway/internal/proxy"
)

var upgrader = websocket.Upgrader{
	CheckOrigin: func(r *http.Request) bool { return true },
}

type RateLimiter struct {
	sync.RWMutex
	limits map[string]int
}

func (rl *RateLimiter) Allow(userID string) bool {
	rl.Lock()
	defer rl.Unlock()
	if rl.limits[userID] > 200 {
		return false
	}
	rl.limits[userID]++
	return true
}

func verifyTurnstile(token string, remoteIP string) bool {
	secret := os.Getenv("TURNSTILE_SECRET_KEY")
	expectedHostname := os.Getenv("VAULTIER_DOMAIN")

	if secret == "" {
		log.Fatalf("Critical Security Alert: TURNSTILE_SECRET_KEY is missing. Server shutting down.")
	}
	if token == "" {
		return false
	}

	data := url.Values{}
	data.Set("secret", secret)
	data.Set("response", token)
	if remoteIP != "" {
		data.Set("remoteip", remoteIP)
	}

	resp, err := http.PostForm("https://challenges.cloudflare.com/turnstile/v0/siteverify", data)
	if err != nil {
		log.Printf("Turnstile verification error: %v", err)
		return false
	}
	defer resp.Body.Close()

	var result struct {
		Success  bool     `json:"success"`
		Hostname string   `json:"hostname"`
		Errors   []string `json:"error-codes"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return false
	}

	// Industrial Grade: Verify hostname if provided in env
	if result.Success && expectedHostname != "" && result.Hostname != expectedHostname {
		log.Printf("Security Alert: Turnstile hostname mismatch. Got %s, expected %s", result.Hostname, expectedHostname)
		return false
	}

	if !result.Success {
		log.Printf("Turnstile failed: %v", result.Errors)
	}

	return result.Success
}

type ProductCache struct {
	sync.RWMutex
	data       []byte
	expiration time.Time
}

var globalProductCache = &ProductCache{}

func sendJSONError(w http.ResponseWriter, message string, code int) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(code)
	json.NewEncoder(w).Encode(map[string]string{"error": message})
}

func main() {
	frontendAPI := os.Getenv("NEXT_PUBLIC_CLERK_FRONTEND_API")
	audience := "insforge-api"
	cortexAddr := os.Getenv("VAULTIER_CORTEX_ADDR")
	if cortexAddr == "" {
		cortexAddr = "http://localhost:8000"
	}

	authenticator := auth.NewClerkAuthenticator(frontendAPI, audience)
	cortexProxy := proxy.NewCortexProxy(cortexAddr)
	limiter := &RateLimiter{limits: make(map[string]int)}

	http.HandleFunc("/products", func(w http.ResponseWriter, r *http.Request) {
		globalProductCache.RLock()
		if time.Now().Before(globalProductCache.expiration) && globalProductCache.data != nil {
			w.Header().Set("Content-Type", "application/json")
			w.Header().Set("X-Cache", "HIT")
			w.Header().Set("Cache-Control", "public, max-age=60")
			w.Write(globalProductCache.data)
			globalProductCache.RUnlock()
			return
		}
		globalProductCache.RUnlock()

		// Proxy to Cortex FastAPI
		resp, err := http.Get(cortexAddr + "/products")
		if err != nil {
			sendJSONError(w, "Cortex Unreachable", http.StatusServiceUnavailable)
			return
		}
		defer resp.Body.Close()

		body, err := io.ReadAll(resp.Body)
		if err != nil {
			sendJSONError(w, "Internal Server Error", http.StatusInternalServerError)
			return
		}

		globalProductCache.Lock()
		globalProductCache.data = body
		globalProductCache.expiration = time.Now().Add(1 * time.Minute)
		globalProductCache.Unlock()

		w.Header().Set("Content-Type", "application/json")
		w.Header().Set("X-Cache", "MISS")
		w.Header().Set("Cache-Control", "public, max-age=60")
		w.WriteHeader(resp.StatusCode)
		w.Write(body)
	})

	go func() {
		for {
			time.Sleep(1 * time.Minute)
			limiter.Lock()
			limiter.limits = make(map[string]int)
			limiter.Unlock()
		}
	}()

	http.HandleFunc("/discovery/trending", func(w http.ResponseWriter, r *http.Request) {
		// In a production scenario, this would query a database or a recommendation engine.
		// For now, we return a curated list that matches the UI's VideoItem structure.
		trending := []map[string]interface{}{
			{
				"id":       "v1",
				"videoUrl": "https://cdn.vaultier.com/samples/vto_chrome_1.mp4",
				"product": map[string]interface{}{
					"id":      "prod_1",
					"name":    "Vaultier Harmony",
					"tagline": "Listen naturally.",
					"price":   429,
				},
				"style": "Chrome Glassium",
				"world": "Biophilic Futurism",
			},
			{
				"id":       "v2",
				"videoUrl": "https://cdn.vaultier.com/samples/vto_pastel_2.mp4",
				"product": map[string]interface{}{
					"id":      "prod_2",
					"name":    "Vaultier Epoch",
					"tagline": "Moments, not minutes.",
					"price":   349,
				},
				"style": "Indie Pastel",
				"world": "Liminal Mirage",
			},
		}

		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(trending)
	})

	http.HandleFunc("/discovery/live", func(w http.ResponseWriter, r *http.Request) {
		token := r.URL.Query().Get("token")
		turnstileToken := r.Header.Get("X-Turnstile-Token")

		// Identity Verification
		identity, err := authenticator.VerifyToken(token)
		if err != nil {
			log.Printf("Security Alert: Blocked unauthorized connection.")
			sendJSONError(w, "Unauthorized", http.StatusUnauthorized)
			return
		}

		if !limiter.Allow(identity.ID) {
			sendJSONError(w, "Rate Limit Exceeded", http.StatusTooManyRequests)
			return
		}

		// Security Verification based on platform
		clientPlatform := r.Header.Get("X-Client-Platform")
		remoteIP := r.Header.Get("X-Forwarded-For")
		if remoteIP == "" {
			remoteIP = r.RemoteAddr
		}

		if clientPlatform == "web" {
			if !verifyTurnstile(turnstileToken, remoteIP) {
				log.Printf("Security Alert: Turnstile verification failed for user %s", identity.ID)
				sendJSONError(w, "Forbidden: Security Check Failed", http.StatusForbidden)
				return
			}
		} else if clientPlatform == "android" {
			// Native Android trust model: Clerk JWT is sufficient as it is issued
			// via native auth flows. No Turnstile required.
			log.Printf("Native Mobile Connection Verified: %s", identity.ID)
		} else {
			// Default to strict: unknown clients must pass Turnstile or be rejected
			if !verifyTurnstile(turnstileToken, remoteIP) {
				log.Printf("Security Alert: Unknown client failed bot check.")
				sendJSONError(w, "Forbidden: Security Check Required", http.StatusForbidden)
				return
			}
		}

		conn, err := upgrader.Upgrade(w, r, nil)
		if err != nil {
			log.Printf("Upgrade Failed: %v", err)
			return
		}
		defer conn.Close()

		log.Printf("Vaultier Live Tunnel Active: User %s (%s Tier)", identity.ID, identity.Tier)

		cortexProxy.TunnelVision(conn, identity.ID, identity.Tier, identity.Features)
	})

	port := os.Getenv("PORT")
	if port == "" {
		port = "8080"
	}

	fmt.Printf("Industrial Gateway (Go) hardened on port %s\n", port)
	if err := http.ListenAndServe(":"+port, nil); err != nil {
		log.Fatal(err)
	}
}
