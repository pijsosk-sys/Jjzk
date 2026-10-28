"""Writes a small Sponge v2 .schem test build. Default: a house that exercises the planner: full blocks, a planks floor,
glass panes, a door (paired block), bottom and top slabs, stairs in four facings and a lantern.
usage: make_house.py out.schem [house|tower]
The tower is a hollow 5x5 stone-brick ring 14 high with no stairs: the top can only be reached
by pillaring."""
import gzip, struct, sys

def nbt_string(s):
    b = s.encode('utf-8')
    return struct.pack('>H', len(b)) + b

def tag(kind, name, payload):
    return bytes([kind]) + nbt_string(name) + payload

def compound(entries):
    return b''.join(entries) + b'\x00'

KIND = sys.argv[2] if len(sys.argv) > 2 else 'house'
W, H, L = (9, 6, 9) if KIND == 'house' else (5, 14, 5)
blocks = {}

def put(x, y, z, state):
    blocks[(x, y, z)] = state

if KIND == 'tower':
    for y in range(H):
        for x in range(W):
            for z in range(L):
                if x in (0, W - 1) or z in (0, L - 1):
                    put(x, y, z, 'minecraft:stone_bricks')

for x in range(W if KIND == 'house' else 0):
    for z in range(L):
        put(x, 0, z, 'minecraft:oak_planks')
for y in range(1, 4 if KIND == 'house' else 1):
    for x in range(W):
        for z in range(L):
            edge = x in (0, W - 1) or z in (0, L - 1)
            if not edge:
                continue
            corner = x in (0, W - 1) and z in (0, L - 1)
            if corner:
                put(x, y, z, 'minecraft:oak_log[axis=y]')
            elif y == 2 and (x in (2, 6) or z in (2, 6)):
                put(x, y, z, 'minecraft:glass')
            else:
                put(x, y, z, 'minecraft:stone_bricks')
if KIND == 'house':
    # a door in the south wall
    put(4, 1, L - 1, 'minecraft:oak_door[facing=north,half=lower,hinge=left,open=false,powered=false]')
    put(4, 2, L - 1, 'minecraft:oak_door[facing=north,half=upper,hinge=left,open=false,powered=false]')
    # roof: stairs around the rim, slabs inside
    for x in range(W):
        for z in range(L):
            if z == 0:
                s = 'minecraft:stone_brick_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]'
            elif z == L - 1:
                s = 'minecraft:stone_brick_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]'
            elif x == 0:
                s = 'minecraft:stone_brick_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]'
            elif x == W - 1:
                s = 'minecraft:stone_brick_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]'
            else:
                s = 'minecraft:smooth_stone_slab[type=bottom,waterlogged=false]'
            put(x, 4, z, s)
    # a top slab shelf and a lantern under the roof
    put(2, 3, 2, 'minecraft:smooth_stone_slab[type=top,waterlogged=false]')
    put(4, 5, 4, 'minecraft:lantern[hanging=false,waterlogged=false]')

palette = {'minecraft:air': 0}
for state in blocks.values():
    palette.setdefault(state, len(palette))

data = bytearray()
for y in range(H):
    for z in range(L):
        for x in range(W):
            v = palette[blocks.get((x, y, z), 'minecraft:air')]
            while True:
                b = v & 0x7F
                v >>= 7
                data.append(b | (0x80 if v else 0))
                if not v:
                    break

pal = compound([tag(3, k, struct.pack('>i', v)) for k, v in palette.items()])
root = compound([
    tag(3, 'Version', struct.pack('>i', 2)),
    tag(3, 'DataVersion', struct.pack('>i', 4671)),
    tag(2, 'Width', struct.pack('>h', W)),
    tag(2, 'Height', struct.pack('>h', H)),
    tag(2, 'Length', struct.pack('>h', L)),
    tag(3, 'PaletteMax', struct.pack('>i', len(palette))),
    tag(10, 'Palette', pal),
    tag(7, 'BlockData', struct.pack('>i', len(data)) + bytes(data)),
    tag(11, 'Offset', struct.pack('>i', 3) + struct.pack('>iii', 0, 0, 0)),
])
with gzip.open(sys.argv[1], 'wb') as out:
    out.write(tag(10, 'Schematic', root))
print(f"{len(blocks)} blocks, {len(palette)} palette entries -> {sys.argv[1]}")
