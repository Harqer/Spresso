package proxy

import (
	"log"
	"net/http"
	"net/url"
	"os"
	"strings"
	"time"

	"github.com/gorilla/websocket"
)

const (
	// Billion-user scale: Optimize connection timeouts
	handshakeTimeout = 10 * time.Second
	writeWait        = 10 * time.Second
	pongWait         = 60 * time.Second
	pingPeriod       = (pongWait * 9) / 10
	maxMessageSize   = 1024 * 1024 // 1MB limit for frames
)

// CortexProxy handles the high-performance tunneling between the Go Gateway
// and the FastAPI AI Brain (Cortex).
type CortexProxy struct {
	CortexURL      *url.URL
	InternalSecret string
	dialer         *websocket.Dialer
}

func NewCortexProxy(cortexAddr string) *CortexProxy {
	u, err := url.Parse(cortexAddr)
	if err != nil {
		log.Fatalf("Invalid Cortex Address: %v", err)
	}

	secret := os.Getenv("VAULTIER_INTERNAL_SECRET")
	if secret == "" {
		log.Fatalf("Critical Security Alert: VAULTIER_INTERNAL_SECRET is missing. Secure tunnel cannot be established.")
	}

	return &CortexProxy{
		CortexURL:      u,
		InternalSecret: secret,
		dialer: &websocket.Dialer{
			Proxy:            http.ProxyFromEnvironment,
			HandshakeTimeout: handshakeTimeout,
		},
	}
}

// TunnelVision upgrades the connection and proxies multimodal frames to the Cortex.
// Propagates Clerk-administered Tier and Entitlements.
func (p *CortexProxy) TunnelVision(clientConn *websocket.Conn, userID string, userTier string, features []string) {
	cortexWSURL := *p.CortexURL
	cortexWSURL.Scheme = "ws"
	if p.CortexURL.Scheme == "https" {
		cortexWSURL.Scheme = "wss"
	}
	cortexWSURL.Path = "/discovery/live"

	header := http.Header{}
	header.Add("X-Vaultier-Internal-Key", p.InternalSecret)
	header.Add("X-Vaultier-User-ID", userID)
	header.Add("X-Vaultier-User-Tier", userTier)
	header.Add("X-Vaultier-User-Features", strings.Join(features, ","))

	cortexConn, _, err := p.dialer.Dial(cortexWSURL.String(), header)
	if err != nil {
		log.Printf("Cortex Handshake Failed: %v", err)
		return
	}
	defer cortexConn.Close()

	// Hardware scale limits
	clientConn.SetReadLimit(maxMessageSize)
	clientConn.SetReadDeadline(time.Now().Add(pongWait))
	clientConn.SetPongHandler(func(string) error { clientConn.SetReadDeadline(time.Now().Add(pongWait)); return nil })

	errChan := make(chan error, 2)

	// Cortex -> Client (Downstream)
	go func() {
		for {
			mt, message, err := cortexConn.ReadMessage()
			if err != nil {
				errChan <- err
				return
			}
			clientConn.SetWriteDeadline(time.Now().Add(writeWait))
			if err := clientConn.WriteMessage(mt, message); err != nil {
				errChan <- err
				return
			}
		}
	}()

	// Client -> Cortex (Upstream)
	// Expert Strategy: Adaptive Frame Sampling
	// Slashing multimodal token costs by only forwarding vision frames at 2fps
	go func() {
		lastFrameTime := time.Time{}
		frameInterval := 500 * time.Millisecond

		for {
			mt, message, err := clientConn.ReadMessage()
			if err != nil {
				errChan <- err
				return
			}

			// If message is a binary frame (JPEG from glasses), apply temporal sampling
			if mt == websocket.BinaryMessage {
				if time.Since(lastFrameTime) < frameInterval {
					continue // Expert Strategy: Drop redundant frames to save LLM tokens
				}
				lastFrameTime = time.Now()
			}

			if err := cortexConn.WriteMessage(mt, message); err != nil {
				errChan <- err
				return
			}
		}
	}()

	// Keepalive ticker
	ticker := time.NewTicker(pingPeriod)
	defer ticker.Stop()

	for {
		select {
		case <-errChan:
			return
		case <-ticker.C:
			clientConn.SetWriteDeadline(time.Now().Add(writeWait))
			if err := clientConn.WriteMessage(websocket.PingMessage, nil); err != nil {
				return
			}
		}
	}
}
