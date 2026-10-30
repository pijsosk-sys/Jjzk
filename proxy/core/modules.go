package core

import (
	"fmt"
	"math"

	"github.com/go-gl/mathgl/mgl32"
	"github.com/sandertv/gophertunnel/minecraft/protocol"
	"github.com/sandertv/gophertunnel/minecraft/protocol/packet"
)

func defaultModules() []Module {
	return []Module{
		newPlayerESP(),
		newNightVision(),
		newAntiWeather(),
		newAutoWalk(),
	}
}

// ---------------------------------------------------------------- player esp

// playerESP draws a debug marker cube on every tracked actor. The client renders these itself when
// the server asks it to, which is why a proxy can draw at all.
type playerESP struct{ base }

func newPlayerESP() *playerESP {
	m := &playerESP{base{
		name: "playeresp", category: "visual",
		desc: "marker cubes on players and mobs, drawn by the client's own debug renderer",
		opts: []*Option{
			{Key: "range", Kind: "float", Value: "64", Min: 8, Max: 256},
			{Key: "mobs", Kind: "bool", Value: "false"},
			{Key: "names", Kind: "bool", Value: "true"},
			{Key: "head", Kind: "bool", Value: "true"},
		},
	}}
	return m
}

func (m *playerESP) Tick(s *Session) {
	limit := float32(m.Option("range").Float())
	mobs := m.Option("mobs").Bool()
	names := m.Option("names").Bool()
	head := m.Option("head").Bool()
	own := s.Position()

	for _, e := range s.Entities() {
		if e.RuntimeID == s.OwnRuntimeID() {
			continue
		}
		if !e.Player && !mobs {
			continue
		}
		distance := e.Pos.Sub(own).Len()
		if distance > limit {
			continue
		}
		// red fading to green with distance, so the nearest thing is the loudest
		fade := float32(math.Min(float64(distance/limit), 1))
		label := ""
		if names {
			label = fmt.Sprintf("%s %.0fm", displayName(e), distance)
		}
		m.cube(s, e.Pos, label, 1-fade, fade, 0.2)
		if head {
			m.cube(s, e.Pos.Add(mgl32.Vec3{0, 1, 0}), "", 1-fade, fade, 0.2)
		}
	}
}

func (m *playerESP) cube(s *Session, at mgl32.Vec3, text string, r, g, b float32) {
	s.ToClient(&packet.ClientBoundDebugRenderer{
		Type:     packet.ClientBoundDebugRendererAddCube,
		Text:     text,
		Position: at,
		Red:      r, Green: g, Blue: b, Alpha: 0.65,
		// outlives one relay tick by a hair so the markers do not blink between refreshes
		Duration: uint64(tickInterval.Milliseconds()) + 150,
	})
}

func (m *playerESP) OnDisable(s *Session) {
	s.ToClient(&packet.ClientBoundDebugRenderer{Type: packet.ClientBoundDebugRendererClear})
}

func (m *playerESP) OnEnable(*Session) {}

func displayName(e Entity) string {
	if e.Name != "" {
		return e.Name
	}
	if e.Type != "" {
		return e.Type
	}
	return "?"
}

// ------------------------------------------------------------- night vision

// effectNightVision is the Bedrock effect id. The effect is sent to the client only, so the server
// never learns the player can see.
const effectNightVision = 16

type nightVision struct{ base }

func newNightVision() *nightVision {
	return &nightVision{base{
		name: "nightvision", category: "visual",
		desc: "client-side night vision, refreshed before it expires",
	}}
}

func (m *nightVision) OnEnable(s *Session) { m.send(s, packet.MobEffectAdd) }

func (m *nightVision) OnDisable(s *Session) { m.send(s, packet.MobEffectRemove) }

func (m *nightVision) Tick(s *Session) {
	// 30 s of effect refreshed on the relay tick: cheap, and it survives a server-sent clear
	if s.Tick()%40 == 0 {
		m.send(s, packet.MobEffectModify)
	}
}

func (m *nightVision) send(s *Session, op byte) {
	s.ToClient(&packet.MobEffect{
		EntityRuntimeID: s.OwnRuntimeID(),
		Operation:       op,
		EffectType:      effectNightVision,
		Amplifier:       0,
		Particles:       false,
		Duration:        20 * 30,
	})
}

// ------------------------------------------------------------- anti weather

type antiWeather struct{ base }

func newAntiWeather() *antiWeather {
	return &antiWeather{base{
		name: "antiweather", category: "visual",
		desc: "swallows rain and thunder level events before the client sees them",
	}}
}

func (m *antiWeather) HandleServer(s *Session, pk packet.Packet) (packet.Packet, bool) {
	p, ok := pk.(*packet.LevelEvent)
	if !ok {
		return pk, true
	}
	switch p.EventType {
	case packet.LevelEventStartRaining, packet.LevelEventStartThunderstorm:
		return nil, false
	}
	return pk, true
}

func (m *antiWeather) OnEnable(s *Session) {
	s.ToClient(&packet.LevelEvent{EventType: packet.LevelEventStopRaining})
	s.ToClient(&packet.LevelEvent{EventType: packet.LevelEventStopThunderstorm})
}

func (m *antiWeather) OnDisable(*Session) {}

// ---------------------------------------------------------------- auto walk

// autoWalk rewrites the movement the client reports. PlayerAuthInput is client-sent, so the server
// reads our vector as the player's own.
type autoWalk struct{ base }

func newAutoWalk() *autoWalk {
	return &autoWalk{base{
		name: "autowalk", category: "movement",
		desc: "holds forward in PlayerAuthInput, optionally sprinting",
		opts: []*Option{
			{Key: "sprint", Kind: "bool", Value: "true"},
		},
	}}
}

func (m *autoWalk) HandleClient(s *Session, pk packet.Packet) (packet.Packet, bool) {
	p, ok := pk.(*packet.PlayerAuthInput)
	if !ok {
		return pk, true
	}
	p.MoveVector = mgl32.Vec2{0, 1}
	p.RawMoveVector = mgl32.Vec2{0, 1}
	p.AnalogueMoveVector = mgl32.Vec2{0, 1}

	flags := p.InputData
	if !flags.Present() {
		flags = protocol.NewInputFlags(packet.InputFlagCount)
	}
	flags.Set(packet.InputFlagUp)
	if m.Option("sprint").Bool() {
		flags.Set(packet.InputFlagSprinting)
		flags.Set(packet.InputFlagSprintDown)
	}
	p.InputData = flags
	return p, true
}
