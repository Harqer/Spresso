package main

	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"net/url"
	"os"
	"sync"
	"time"

	"github.com/go-redis/redis/v8"
	"github.com/gorilla/websocket"
	"github.com/meta-wearable/aura-gateway/internal/auth"
	"github.com/meta-wearable/aura-gateway/internal/proxy"
	"github.com/meta-wearable/aura-gateway/internal/vault"
)

var upgrader = websocket.Upgrader{
	CheckOrigin: func(r *http.Request) bool { return true },
}

var ctx = context.Background()

type RateLimiter struct {
	client *redis.Client
}

func NewRateLimiter(redisUrl string) *RateLimiter {
	opt, err := redis.ParseURL(redisUrl)
	if err != nil {
		log.Fatalf("Invalid Redis URL: %v", err)
	}
	return &RateLimiter{client: redis.NewClient(opt)}
}

func (rl *RateLimiter) Allow(userID string) bool {
	key := fmt.Sprintf("rate_limit:%s:%d", userID, time.Now().Unix()/60)
	pipe := rl.client.Pipeline()
	incr := pipe.Incr(ctx, key)
	pipe.Expire(ctx, key, 2*time.Minute)
	_, err := pipe.Exec(ctx)
	if err != nil {
		log.Printf("Redis error: %v", err)
		return false
	}
	// Token bucket limit: 200 requests per minute
	return incr.Val() <= 200
}

func verifyTurnstile(token string, remoteIP string, secret string, expectedHostname string) bool {
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
	secretVault, err := vault.NewVault()
	if err != nil {
		log.Fatalf("Failed to initialize secret vault: %v", err)
	}

	frontendAPI := secretVault.GetSecret("NEXT_PUBLIC_CLERK_FRONTEND_API")
	audience := "insforge-api"
	
	cortexAddr := secretVault.GetSecret("SPRESSO_CORTEX_ADDR")
	if cortexAddr == "" {
		log.Fatalf("Critical Configuration Error: SPRESSO_CORTEX_ADDR is required")
	}
	
	merchantApiKey := secretVault.GetSecret("MERCHANT_API_KEY")

	redisUrl := secretVault.GetSecret("REDIS_URL")
	if redisUrl == "" {
		log.Fatalf("Critical Configuration Error: REDIS_URL is required")
	}

	turnstileSecret := secretVault.GetSecret("TURNSTILE_SECRET_KEY")
	spressoDomain := secretVault.GetSecret("SPRESSO_DOMAIN")

	authenticator := auth.NewClerkAuthenticator(frontendAPI, audience)
	cortexProxy := proxy.NewCortexProxy(cortexAddr)
	limiter := NewRateLimiter(redisUrl)

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

	// Redis rate limiter handles expiration natively, so no background cleanup needed for RateLimiter.

	http.HandleFunc("/discovery/chat", func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost {
			sendJSONError(w, "Method Not Allowed", http.StatusMethodNotAllowed)
			return
		}

		authHeader := r.Header.Get("Authorization")
		if authHeader == "" {
			sendJSONError(w, "Unauthorized", http.StatusUnauthorized)
			return
		}
		token := strings.TrimPrefix(authHeader, "Bearer ")
		identity, err := authenticator.VerifyToken(token)
		if err != nil {
			sendJSONError(w, "Unauthorized", http.StatusUnauthorized)
			return
		}
		if !limiter.Allow(identity.ID) {
			sendJSONError(w, "Rate Limit Exceeded", http.StatusTooManyRequests)
			return
		}

		bodyBytes, err := io.ReadAll(r.Body)
		if err != nil {
			sendJSONError(w, "Bad Request", http.StatusBadRequest)
			return
		}

		proxyReq, err := http.NewRequest(http.MethodPost, cortexAddr+"/discovery/chat", bytes.NewReader(bodyBytes))
		if err != nil {
			sendJSONError(w, "Internal Server Error", http.StatusInternalServerError)
			return
		}

		proxyReq.Header.Set("Content-Type", "application/json")
		proxyReq.Header.Set("X-API-Key", merchantApiKey)
		// Pass identity info to cortex securely
		proxyReq.Header.Set("X-Spresso-User-Id", identity.ID)
		proxyReq.Header.Set("X-Spresso-User-Tier", identity.Tier)
		
		client := &http.Client{Timeout: 60 * time.Second}
		resp, err := client.Do(proxyReq)
		if err != nil {
			log.Printf("Cortex /discovery/chat error: %v", err)
			sendJSONError(w, "Cortex Unreachable", http.StatusServiceUnavailable)
			return
		}
		defer resp.Body.Close()

		respBody, err := io.ReadAll(resp.Body)
		if err != nil {
			sendJSONError(w, "Internal Server Error", http.StatusInternalServerError)
			return
		}

		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(resp.StatusCode)
		w.Write(respBody)
	})

	http.HandleFunc("/discovery/trending", func(w http.ResponseWriter, r *http.Request) {
		// Production Strategy: Dynamic Discovery via Cortex
		resp, err := http.Get(cortexAddr + "/discovery/trending")
		if err != nil {
			log.Printf("Cortex Trending Error: %v", err)
			sendJSONError(w, "Cortex Unreachable", http.StatusServiceUnavailable)
			return
		}
		defer resp.Body.Close()

		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(resp.StatusCode)
		io.Copy(w, resp.Body)
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
			if !verifyTurnstile(turnstileToken, remoteIP, turnstileSecret, spressoDomain) {
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
			if !verifyTurnstile(turnstileToken, remoteIP, turnstileSecret, spressoDomain) {
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

		log.Printf("Spresso Live Tunnel Active: User %s (%s Tier)", identity.ID, identity.Tier)

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
