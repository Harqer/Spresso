package vault

import (
	"context"
	"fmt"
	"log"
	"os"
	"sync"

	infisical "github.com/infisical/go-sdk"
)

type Vault struct {
	client    infisical.InfisicalClientInterface
	projectID string
	env       string
	cache     map[string]string
	cacheMu   sync.RWMutex
}

func NewVault() (*Vault, error) {
	clientID := os.Getenv("INFISICAL_CLIENT_ID")
	clientSecret := os.Getenv("INFISICAL_CLIENT_SECRET")
	projectID := os.Getenv("INFISICAL_PROJECT_ID")
	env := os.Getenv("INFISICAL_ENV")
	siteUrl := os.Getenv("INFISICAL_URL") // Optional, defaults to app.infisical.com

	if clientID == "" || clientSecret == "" || projectID == "" {
		return nil, fmt.Errorf("Missing required Infisical environment variables: INFISICAL_CLIENT_ID, INFISICAL_CLIENT_SECRET, INFISICAL_PROJECT_ID")
	}

	if env == "" {
		env = "prod"
	}
	
	config := infisical.Config{}
	if siteUrl != "" {
		config.SiteUrl = siteUrl
	}

	client := infisical.NewInfisicalClient(context.Background(), config)
	
	_, err := client.Auth().UniversalAuthLogin(infisical.UniversalAuthLoginOptions{
		ClientId:     clientID,
		ClientSecret: clientSecret,
	})
	if err != nil {
		return nil, fmt.Errorf("Infisical auth failed: %v", err)
	}

	return &Vault{
		client:    client,
		projectID: projectID,
		env:       env,
		cache:     make(map[string]string),
	}, nil
}

func (v *Vault) GetSecret(name string) string {
	v.cacheMu.RLock()
	if val, ok := v.cache[name]; ok {
		v.cacheMu.RUnlock()
		return val
	}
	v.cacheMu.RUnlock()

	secret, err := v.client.Secrets().Retrieve(infisical.RetrieveSecretOptions{
		Environment: v.env,
		ProjectID:   v.projectID,
		SecretPath:  "/",
		SecretName:  name,
	})
	
	if err != nil {
		log.Printf("Failed to retrieve secret %s: %v", name, err)
		return ""
	}

	v.cacheMu.Lock()
	v.cache[name] = secret.SecretValue
	v.cacheMu.Unlock()

	return secret.SecretValue
}
