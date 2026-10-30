package core

import (
	"sync"

	"github.com/go-gl/mathgl/mgl32"
	"github.com/sandertv/gophertunnel/minecraft"
	"github.com/sandertv/gophertunnel/minecraft/protocol/packet"
)

// Entity is the slice of an actor's state the modules need: where it is, what it is called and
// whether it is a player. Everything else stays in the packets.
type Entity struct {
	RuntimeID uint64
	Type      string
	Name      string
	Player    bool
	Pos       mgl32.Vec3
	Yaw       float32
}

// Session is one client sitting behind the relay: both connections, the tracked world state and
// the write paths a module uses.
type Session struct {
	client *minecraft.Conn
	server *minecraft.Conn

	log func(string)

	mu       sync.RWMutex
	entities map[uint64]*Entity
	ownID    uint64
	pos      mgl32.Vec3
	yaw      float32
	tick     uint64
}

func newSession(client, server *minecraft.Conn, log func(string)) *Session {
	return &Session{
		client:   client,
		server:   server,
		log:      log,
		entities: make(map[uint64]*Entity),
		ownID:    server.GameData().EntityRuntimeID,
	}
}

// ToClient sends a packet to the game as if the server had sent it. This is where ESP, effects and
// every other client-side illusion goes out.
func (s *Session) ToClient(pk packet.Packet) { _ = s.client.WritePacket(pk) }

// ToServer sends a packet as if the player had sent it.
func (s *Session) ToServer(pk packet.Packet) { _ = s.server.WritePacket(pk) }

func (s *Session) Log(line string) { s.log(line) }

func (s *Session) OwnRuntimeID() uint64 { return s.ownID }

func (s *Session) Position() mgl32.Vec3 {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return s.pos
}

func (s *Session) Yaw() float32 {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return s.yaw
}

func (s *Session) Tick() uint64 {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return s.tick
}

// Entities returns a copy, so a module can walk it without holding the lock while it writes.
func (s *Session) Entities() []Entity {
	s.mu.RLock()
	defer s.mu.RUnlock()
	out := make([]Entity, 0, len(s.entities))
	for _, e := range s.entities {
		out = append(out, *e)
	}
	return out
}

// trackServer keeps the entity table in step with what the server tells the client. It runs before
// the module pipeline, so a module always sees current positions.
func (s *Session) trackServer(pk packet.Packet) {
	s.mu.Lock()
	defer s.mu.Unlock()

	switch p := pk.(type) {
	case *packet.AddPlayer:
		s.entities[p.EntityRuntimeID] = &Entity{
			RuntimeID: p.EntityRuntimeID, Type: "minecraft:player", Name: p.Username,
			Player: true, Pos: p.Position, Yaw: p.Yaw,
		}
	case *packet.AddActor:
		s.entities[p.EntityRuntimeID] = &Entity{
			RuntimeID: p.EntityRuntimeID, Type: p.EntityType, Pos: p.Position, Yaw: p.Yaw,
		}
	case *packet.RemoveActor:
		for id, e := range s.entities {
			if e.UniqueMatches(p.EntityUniqueID) {
				delete(s.entities, id)
			}
		}
	case *packet.MoveActorAbsolute:
		if e, ok := s.entities[p.EntityRuntimeID]; ok {
			e.Pos, e.Yaw = p.Position, p.Rotation[1]
		}
	case *packet.MoveActorDelta:
		if e, ok := s.entities[p.EntityRuntimeID]; ok {
			if x, set := p.PositionX.Value(); set {
				e.Pos[0] = x
			}
			if y, set := p.PositionY.Value(); set {
				e.Pos[1] = y
			}
			if z, set := p.PositionZ.Value(); set {
				e.Pos[2] = z
			}
			if yaw, set := p.RotationY.Value(); set {
				e.Yaw = yaw
			}
		}
	case *packet.MovePlayer:
		if p.EntityRuntimeID == s.ownID {
			s.pos, s.yaw = p.Position, p.Yaw
		} else if e, ok := s.entities[p.EntityRuntimeID]; ok {
			e.Pos, e.Yaw = p.Position, p.Yaw
		}
	}
}

// UniqueMatches is the one place the unique/runtime id split is papered over: RemoveActor carries
// the unique id, and for players and most actors the server hands out the same number for both.
func (e *Entity) UniqueMatches(unique int64) bool { return uint64(unique) == e.RuntimeID }

// trackClient follows the player's own movement, which only the client ever reports.
func (s *Session) trackClient(pk packet.Packet) {
	if p, ok := pk.(*packet.PlayerAuthInput); ok {
		s.mu.Lock()
		s.pos, s.yaw, s.tick = p.Position, p.HeadYaw, p.Tick
		s.mu.Unlock()
	}
}
