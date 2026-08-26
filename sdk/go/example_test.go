package flux_wa_test

import (
	"context"
	"errors"
	"fmt"
	"log"
	"time"

	01flux-wa "github.com/rmyndharis/01flux-wa/sdk/go"
)

func ExampleNew() {
	client, err := 01flux-wa.New("http://localhost:2785", "flx_k1_…")
	if err != nil {
		log.Fatal(err)
	}

	ctx := context.Background()
	if _, err := client.Sessions.Start(ctx, "my-session"); err != nil {
		log.Fatal(err)
	}

	res, err := client.Messages.SendText(ctx, "my-session", 01flux-wa.SendTextRequest{
		ChatID: "628123456789@c.us",
		Text:   "Hello from the 01flux WA Go SDK!",
	})
	if err != nil {
		log.Fatal(err)
	}
	fmt.Println(res.MessageID)
}

func ExampleClient_typedErrors() {
	client, _ := 01flux-wa.New("http://localhost:2785", "flx_k1_…")

	_, err := client.Messages.SendText(context.Background(), "my-session", 01flux-wa.SendTextRequest{
		ChatID: "628123456789@c.us",
		Text:   "hi",
	})
	switch {
	case errors.Is(err, 01flux-wa.ErrConflict):
		// Engine not ready (409) — retry after the session reaches "ready".
	case errors.Is(err, 01flux-wa.ErrNotFound):
		// Unknown session (404).
	case err != nil:
		var apiErr *01flux-wa.APIError
		if errors.As(err, &apiErr) {
			log.Printf("API %d: %s", apiErr.StatusCode, apiErr.Message)
		}
	}
}

func ExampleWithRetry() {
	// Opt into automatic retries with exponential backoff, and inject a custom
	// per-request timeout — dependencies flow through functional options.
	client, _ := 01flux-wa.New("http://localhost:2785", "flx_k1_…",
		01flux-wa.WithRetry(01flux-wa.DefaultRetryPolicy()),
		01flux-wa.WithTimeout(15*time.Second),
	)
	_ = client
}

func ExampleClient_webhookEvents() {
	client, _ := 01flux-wa.New("http://localhost:2785", "flx_k1_…")

	// Subscribe to the group and call events with the Event* constants — they
	// are the exact wire values, so a typo is a compile error, not a silent
	// no-delivery.
	_, err := client.Webhooks.Create(context.Background(), "my-session", 01flux-wa.CreateWebhookRequest{
		URL: "https://example.com/hook",
		Events: []string{
			01flux-wa.EventGroupJoin,
			01flux-wa.EventGroupLeave,
			01flux-wa.EventGroupUpdate,
			01flux-wa.EventCallReceived,
		},
	})
	if err != nil {
		log.Fatal(err)
	}
}
