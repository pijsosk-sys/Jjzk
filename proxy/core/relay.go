package core

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"net"
	"os"
	"path/filepath"
	"sync"
	"time"

	"github.com/sandertv/gophertunnel/minecraft"
	"github.com/sandertv/gophertunnel/minecraft/auth"
	"github.com/sandertv/gophertunnel/minecraft/protocol/packet"
	"golang.org/x/oauth2"
)

// tickInterval is the relay's own clock. Modules that draw use it, so it is the refresh rate of
// everything client-side.
const tickInterval = 250 * time.Millisecond

// Relay is the whole proxy: one local listener the game joins, one dialled connection per client.
type Relay struct {
	Registry *Registry

	mu       sync.Mutex
	listener *minecraft.Listener
	session  *Session
	remote   string
	dataDir  string
	logf     func(string)
	cancel   context.CancelFunc
}

func NewRelay(dataDir string, logf func(string)) *Relay {
	return &Relay{Registry: NewRegistry(), dataDir: dataDir, logf: logf}
}

func (r *Relay) log(format string, args ...any) {
	if r.logf != nil {
		r.logf(fmt.Sprintf(format, args...))
	}
}

func (r *Relay) tokenPath() string { return filepath.Join(r.dataDir, "token.json") }

// LoggedIn reports whether a Microsoft token is cached. The token is a live credential: it stays in
// the app's private directory and is never sent anywhere but Microsoft and the server being joined.
func (r *Relay) LoggedIn() bool {
	_, err := os.Stat(r.tokenPath())
	return err == nil
}

func (r *Relay) Logout() error { return os.Remove(r.tokenPath()) }

// Login runs the Microsoft device-code flow. It blocks until the code is entered in a browser, so
// call it off the UI thread. The instructions are written to the log.
func (r *Relay) Login(ctx context.Context) error {
	token, err := auth.RequestLiveTokenContext(ctx, logWriter{r.log})
	if err != nil {
		return err
	}
	if err := os.MkdirAll(r.dataDir, 0o700); err != nil {
		return err
	}
	data, err := json.Marshal(token)
	if err != nil {
		return err
	}
	if err := os.WriteFile(r.tokenPath(), data, 0o600); err != nil {
		return err
	}
	r.log("signed in as the account you just authorised")
	return nil
}

func (r *Relay) tokenSource() (oauth2.TokenSource, error) {
	data, err := os.ReadFile(r.tokenPath())
	if err != nil {
		return nil, errors.New("not signed in")
	}
	token := new(oauth2.Token)
	if err := json.Unmarshal(data, token); err != nil {
		return nil, err
	}
	// RefreshTokenSourceWriter rewrites the cache whenever Microsoft hands back a new token
	file, err := os.OpenFile(r.tokenPath(), os.O_WRONLY|os.O_CREATE|os.O_TRUNC, 0o600)
	if err != nil {
		return nil, err
	}
	return auth.RefreshTokenSourceWriter(token, file), nil
}

// Running reports whether the local listener is up.
func (r *Relay) Running() bool {
	r.mu.Lock()
	defer r.mu.Unlock()
	return r.listener != nil
}

// Start brings up the listener on the given port. The game joins 127.0.0.1:<port>; everything it
// sends is relayed to remote, which is "host:port" of the real server.
func (r *Relay) Start(remote string, port int, online bool) error {
	r.mu.Lock()
	if r.listener != nil {
		r.mu.Unlock()
		return errors.New("already running")
	}
	r.mu.Unlock()

	var src oauth2.TokenSource
	if online {
		var err error
		if src, err = r.tokenSource(); err != nil {
			return err
		}
	}

	status := minecraft.NewStatusProvider("relay", remote)
	listener, err := minecraft.ListenConfig{
		StatusProvider:         status,
		AuthenticationDisabled: true,
		FlushRate:              -1,
	}.Listen("raknet", fmt.Sprintf("0.0.0.0:%d", port))
	if err != nil {
		return err
	}

	ctx, cancel := context.WithCancel(context.Background())
	r.mu.Lock()
	r.listener, r.remote, r.cancel = listener, remote, cancel
	r.mu.Unlock()

	r.log("listening on 0.0.0.0:%d → %s", port, remote)
	r.log("add a server in Minecraft pointing at 127.0.0.1 port %d", port)

	go r.accept(ctx, listener, src)
	return nil
}

func (r *Relay) Stop() {
	r.mu.Lock()
	listener, cancel := r.listener, r.cancel
	r.listener, r.session, r.cancel = nil, nil, nil
	r.mu.Unlock()

	if cancel != nil {
		cancel()
	}
	if listener != nil {
		_ = listener.Close()
		r.log("stopped")
	}
}

func (r *Relay) accept(ctx context.Context, listener *minecraft.Listener, src oauth2.TokenSource) {
	for {
		conn, err := listener.Accept()
		if err != nil {
			if ctx.Err() == nil {
				r.log("listener closed: %v", err)
			}
			return
		}
		go r.handle(ctx, listener, conn.(*minecraft.Conn), src)
	}
}

func (r *Relay) handle(ctx context.Context, listener *minecraft.Listener, client *minecraft.Conn, src oauth2.TokenSource) {
	r.log("client connected from %s", client.RemoteAddr())

	dialer := minecraft.Dialer{
		TokenSource:       src,
		ClientData:        client.ClientData(),
		EnableClientCache: false,
	}
	if src == nil {
		// offline target: keep the identity the client presented
		dialer.IdentityData = client.IdentityData()
	}

	server, err := dialer.DialTimeout("raknet", r.remote, 30*time.Second)
	if err != nil {
		r.log("dial %s failed: %v", r.remote, err)
		_ = listener.Disconnect(client, "relay could not reach the server: "+err.Error())
		return
	}

	var start sync.WaitGroup
	start.Add(2)
	var startErr error
	go func() {
		defer start.Done()
		if err := client.StartGame(server.GameData()); err != nil {
			startErr = err
		}
	}()
	go func() {
		defer start.Done()
		if err := server.DoSpawn(); err != nil {
			startErr = err
		}
	}()
	start.Wait()
	if startErr != nil {
		r.log("spawn failed: %v", startErr)
		_ = client.Close()
		_ = server.Close()
		return
	}

	session := newSession(client, server, func(line string) { r.log("%s", line) })
	r.mu.Lock()
	r.session = session
	r.mu.Unlock()

	// a module switched on before anyone joined still needs its OnEnable
	for _, m := range r.Registry.All() {
		if m.Enabled() {
			if l, ok := m.(Lifecycle); ok {
				l.OnEnable(session)
			}
		}
	}

	r.log("joined %s", r.remote)

	done := make(chan struct{}, 3)
	go r.pumpClient(session, done)
	go r.pumpServer(session, done)
	go r.ticker(ctx, session, done)

	<-done
	_ = client.Close()
	_ = server.Close()

	r.mu.Lock()
	if r.session == session {
		r.session = nil
	}
	r.mu.Unlock()
	r.log("session ended")
}

// pumpClient carries client → server, through every ClientHandler.
func (r *Relay) pumpClient(s *Session, done chan struct{}) {
	defer func() { done <- struct{}{} }()
	for {
		pk, err := s.client.ReadPacket()
		if err != nil {
			return
		}
		s.trackClient(pk)
		out, keep := r.Registry.handleClient(s, pk)
		if !keep {
			continue
		}
		if err := s.server.WritePacket(out); err != nil {
			return
		}
	}
}

// pumpServer carries server → client, through every ServerHandler.
func (r *Relay) pumpServer(s *Session, done chan struct{}) {
	defer func() { done <- struct{}{} }()
	for {
		pk, err := s.server.ReadPacket()
		if err != nil {
			var disconnect minecraft.DisconnectError
			if errors.As(err, &disconnect) {
				_ = s.client.WritePacket(&packet.Disconnect{Message: disconnect.Error()})
			}
			return
		}
		s.trackServer(pk)
		out, keep := r.Registry.handleServer(s, pk)
		if !keep {
			continue
		}
		if err := s.client.WritePacket(out); err != nil {
			return
		}
	}
}

func (r *Relay) ticker(ctx context.Context, s *Session, done chan struct{}) {
	defer func() { done <- struct{}{} }()
	t := time.NewTicker(tickInterval)
	defer t.Stop()
	for {
		select {
		case <-ctx.Done():
			return
		case <-t.C:
			r.Registry.tick(s)
			// both connections batch; nothing a module wrote this tick goes out without this
			if err := s.client.Flush(); err != nil {
				return
			}
			if err := s.server.Flush(); err != nil {
				return
			}
		}
	}
}

// Toggle switches a module and runs its lifecycle hook against the live session, if there is one.
func (r *Relay) Toggle(name string, on bool) error {
	if err := r.Registry.Toggle(name, on); err != nil {
		return err
	}
	r.mu.Lock()
	session := r.session
	r.mu.Unlock()
	if session == nil {
		return nil
	}
	if l, ok := r.Registry.Get(name).(Lifecycle); ok {
		if on {
			l.OnEnable(session)
		} else {
			l.OnDisable(session)
		}
	}
	return nil
}

// Connected reports whether a client is currently playing through the relay.
func (r *Relay) Connected() bool {
	r.mu.Lock()
	defer r.mu.Unlock()
	return r.session != nil
}

// LocalAddresses lists the addresses this device can be reached on, for joining from a console or
// another phone on the same network.
func LocalAddresses() []string {
	var out []string
	addrs, err := net.InterfaceAddrs()
	if err != nil {
		return out
	}
	for _, addr := range addrs {
		ipNet, ok := addr.(*net.IPNet)
		if !ok || ipNet.IP.IsLoopback() || ipNet.IP.To4() == nil {
			continue
		}
		out = append(out, ipNet.IP.String())
	}
	return out
}

// logWriter turns the device-code instructions gophertunnel writes into log lines.
type logWriter struct{ log func(string, ...any) }

func (w logWriter) Write(p []byte) (int, error) {
	w.log("%s", string(p))
	return len(p), nil
}
