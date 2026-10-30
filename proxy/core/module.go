package core

import (
	"encoding/json"
	"fmt"
	"sort"
	"strconv"
	"sync"

	"github.com/sandertv/gophertunnel/minecraft/protocol/packet"
)

// Option is one user-facing setting. Values are kept as strings so the whole settings surface can
// cross the gomobile boundary as JSON without a type per setting.
type Option struct {
	Key   string  `json:"key"`
	Kind  string  `json:"kind"` // bool, float, int, string
	Value string  `json:"value"`
	Min   float64 `json:"min,omitempty"`
	Max   float64 `json:"max,omitempty"`
}

func (o *Option) Float() float64 { v, _ := strconv.ParseFloat(o.Value, 64); return v }
func (o *Option) Int() int       { v, _ := strconv.Atoi(o.Value); return v }
func (o *Option) Bool() bool     { return o.Value == "true" }

// Module is the common surface. Behaviour comes from the optional interfaces below: a module
// implements only the hooks it needs.
type Module interface {
	Name() string
	Category() string
	Desc() string
	Enabled() bool
	SetEnabled(bool)
	Options() []*Option
	Option(key string) *Option
}

// ServerHandler sees every packet travelling server → client. Returning false drops the packet.
type ServerHandler interface {
	HandleServer(s *Session, pk packet.Packet) (packet.Packet, bool)
}

// ClientHandler sees every packet travelling client → server. Returning false drops the packet.
type ClientHandler interface {
	HandleClient(s *Session, pk packet.Packet) (packet.Packet, bool)
}

// Ticker runs on the relay's own clock, independent of packet flow.
type Ticker interface {
	Tick(s *Session)
}

// Lifecycle is called when the module is switched, and when a session ends while it is on.
type Lifecycle interface {
	OnEnable(s *Session)
	OnDisable(s *Session)
}

// base carries the bookkeeping every module shares.
type base struct {
	name, category, desc string
	enabled              bool
	opts                 []*Option
	mu                   sync.RWMutex
}

func (b *base) Name() string     { return b.name }
func (b *base) Category() string { return b.category }
func (b *base) Desc() string     { return b.desc }

func (b *base) Enabled() bool {
	b.mu.RLock()
	defer b.mu.RUnlock()
	return b.enabled
}

func (b *base) SetEnabled(on bool) {
	b.mu.Lock()
	b.enabled = on
	b.mu.Unlock()
}

func (b *base) Options() []*Option { return b.opts }

func (b *base) Option(key string) *Option {
	for _, o := range b.opts {
		if o.Key == key {
			return o
		}
	}
	return nil
}

// Registry holds every module for the process. Modules are stateless about which session they are
// attached to, so one registry serves whichever client is connected.
type Registry struct {
	mu      sync.RWMutex
	modules map[string]Module
	order   []string
}

func NewRegistry() *Registry {
	r := &Registry{modules: make(map[string]Module)}
	for _, m := range defaultModules() {
		r.Add(m)
	}
	return r
}

func (r *Registry) Add(m Module) {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.modules[m.Name()] = m
	r.order = append(r.order, m.Name())
	sort.Strings(r.order)
}

func (r *Registry) Get(name string) Module {
	r.mu.RLock()
	defer r.mu.RUnlock()
	return r.modules[name]
}

func (r *Registry) All() []Module {
	r.mu.RLock()
	defer r.mu.RUnlock()
	out := make([]Module, 0, len(r.order))
	for _, name := range r.order {
		out = append(out, r.modules[name])
	}
	return out
}

func (r *Registry) Toggle(name string, on bool) error {
	m := r.Get(name)
	if m == nil {
		return fmt.Errorf("no module %q", name)
	}
	m.SetEnabled(on)
	return nil
}

func (r *Registry) SetOption(name, key, value string) error {
	m := r.Get(name)
	if m == nil {
		return fmt.Errorf("no module %q", name)
	}
	o := m.Option(key)
	if o == nil {
		return fmt.Errorf("module %q has no option %q", name, key)
	}
	switch o.Kind {
	case "bool":
		if value != "true" && value != "false" {
			return fmt.Errorf("%s.%s wants true or false", name, key)
		}
	case "int":
		if _, err := strconv.Atoi(value); err != nil {
			return fmt.Errorf("%s.%s wants a whole number", name, key)
		}
	case "float":
		if _, err := strconv.ParseFloat(value, 64); err != nil {
			return fmt.Errorf("%s.%s wants a number", name, key)
		}
	}
	o.Value = value
	return nil
}

// JSON is the whole module tree, which is what the Android UI renders.
func (r *Registry) JSON() string {
	type entry struct {
		Name     string    `json:"name"`
		Category string    `json:"category"`
		Desc     string    `json:"desc"`
		Enabled  bool      `json:"enabled"`
		Options  []*Option `json:"options"`
	}
	out := make([]entry, 0)
	for _, m := range r.All() {
		out = append(out, entry{m.Name(), m.Category(), m.Desc(), m.Enabled(), m.Options()})
	}
	b, _ := json.Marshal(out)
	return string(b)
}

// handleServer runs the server → client pipeline. A module that drops a packet ends the chain.
func (r *Registry) handleServer(s *Session, pk packet.Packet) (packet.Packet, bool) {
	for _, m := range r.All() {
		if !m.Enabled() {
			continue
		}
		h, ok := m.(ServerHandler)
		if !ok {
			continue
		}
		var keep bool
		if pk, keep = h.HandleServer(s, pk); !keep {
			return nil, false
		}
	}
	return pk, true
}

func (r *Registry) handleClient(s *Session, pk packet.Packet) (packet.Packet, bool) {
	for _, m := range r.All() {
		if !m.Enabled() {
			continue
		}
		h, ok := m.(ClientHandler)
		if !ok {
			continue
		}
		var keep bool
		if pk, keep = h.HandleClient(s, pk); !keep {
			return nil, false
		}
	}
	return pk, true
}

func (r *Registry) tick(s *Session) {
	for _, m := range r.All() {
		if !m.Enabled() {
			continue
		}
		if t, ok := m.(Ticker); ok {
			t.Tick(s)
		}
	}
}
