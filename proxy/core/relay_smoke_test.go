package core

import (
	"encoding/json"
	"testing"
	"time"
)

func TestListenerLifecycle(t *testing.T) {
	var lines []string
	r := NewRelay(t.TempDir(), func(s string) { lines = append(lines, s) })

	if err := r.Start("127.0.0.1:19999", 19132, false); err != nil {
		t.Fatalf("start: %v", err)
	}
	if !r.Running() {
		t.Fatal("not running after start")
	}
	time.Sleep(200 * time.Millisecond)
	r.Stop()
	if r.Running() {
		t.Fatal("still running after stop")
	}

	var tree []struct {
		Name    string `json:"name"`
		Enabled bool   `json:"enabled"`
		Options []struct {
			Key   string `json:"key"`
			Value string `json:"value"`
		} `json:"options"`
	}
	if err := json.Unmarshal([]byte(r.Registry.JSON()), &tree); err != nil {
		t.Fatalf("module json: %v", err)
	}
	if len(tree) != 4 {
		t.Fatalf("expected 4 modules, got %d", len(tree))
	}
	if err := r.Toggle("playeresp", true); err != nil {
		t.Fatalf("toggle: %v", err)
	}
	if !r.Registry.Get("playeresp").Enabled() {
		t.Fatal("playeresp did not enable")
	}
	if err := r.Registry.SetOption("playeresp", "range", "128"); err != nil {
		t.Fatalf("set option: %v", err)
	}
	if got := r.Registry.Get("playeresp").Option("range").Float(); got != 128 {
		t.Fatalf("range = %v", got)
	}
	if err := r.Registry.SetOption("playeresp", "range", "abc"); err == nil {
		t.Fatal("bad float accepted")
	}
	if err := r.Toggle("nope", true); err == nil {
		t.Fatal("unknown module accepted")
	}
}
