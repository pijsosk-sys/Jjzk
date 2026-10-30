// Package bridge is the gomobile surface of the relay. Everything crossing into Java is a string,
// a bool, an int or an error — gomobile binds nothing else cleanly, so the module tree travels as
// JSON and the log travels through a callback.
package bridge

import (
	"context"
	"strings"
	"sync"

	"github.com/pijsosk-sys/jjzk/proxy/core"
	"github.com/sandertv/gophertunnel/minecraft/protocol"
)

// Logger is implemented on the Java side.
type Logger interface {
	Log(line string)
}

var (
	mu       sync.Mutex
	relay    *core.Relay
	logger   Logger
	loginCtx context.CancelFunc
)

// Version reports the Minecraft version this build speaks. A client on another version will not
// get past the listener.
func Version() string {
	return protocol.CurrentVersion
}

// Protocol reports the protocol number for that version.
func Protocol() int { return protocol.CurrentProtocol }

// Init must be called once with a writable private directory before anything else.
func Init(dataDir string) {
	mu.Lock()
	defer mu.Unlock()
	if relay == nil {
		relay = core.NewRelay(dataDir, emit)
	}
}

// SetLogger installs the Java side's log sink. Passing nil detaches it.
func SetLogger(l Logger) {
	mu.Lock()
	logger = l
	mu.Unlock()
}

func emit(line string) {
	mu.Lock()
	l := logger
	mu.Unlock()
	if l != nil {
		l.Log(strings.TrimRight(line, "\n"))
	}
}

// Login runs the Microsoft device-code flow and blocks until it completes or CancelLogin is
// called. Run it on a background thread.
func Login() error {
	mu.Lock()
	r := relay
	ctx, cancel := context.WithCancel(context.Background())
	loginCtx = cancel
	mu.Unlock()
	defer cancel()
	return r.Login(ctx)
}

// CancelLogin aborts a login that is still waiting for the code to be entered.
func CancelLogin() {
	mu.Lock()
	cancel := loginCtx
	mu.Unlock()
	if cancel != nil {
		cancel()
	}
}

func LoggedIn() bool { return relay.LoggedIn() }

func Logout() error { return relay.Logout() }

// Start opens the local listener. remote is "host:port" of the real server, port is the local port
// to join (19132 is what Minecraft offers by default). online uses the signed-in account, which
// every public server and realm requires.
func Start(remote string, port int, online bool) error {
	return relay.Start(remote, port, online)
}

func Stop() { relay.Stop() }

func Running() bool { return relay.Running() }

// Connected reports whether a client is playing through the relay right now.
func Connected() bool { return relay.Connected() }

// ModulesJSON is the whole module tree with current values, for the UI to render.
func ModulesJSON() string { return relay.Registry.JSON() }

func Toggle(name string, on bool) error { return relay.Toggle(name, on) }

func SetOption(module, key, value string) error {
	return relay.Registry.SetOption(module, key, value)
}

// LocalAddresses returns this device's LAN addresses, newline separated, for joining from another
// device on the same network.
func LocalAddresses() string {
	return strings.Join(core.LocalAddresses(), "\n")
}
