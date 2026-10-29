"""Writes seven Sponge v2 .schem builds, each testing something the earlier test builds didn't:

- castle-gate: two towers with crenellations, a wall with an arched gate (upside-down stairs),
  iron bars, wall torches.
- sky-bridge: a deck of slabs and wall railings on two pillars, 13 blocks of it over open air.
- pixel-art: a 16x16 wool creeper face standing on its edge (a tall flat wall, four colours).
- spiral-tower: a round tower with a spiral staircase round a centre post, glass-pane windows
  and a slab roof (stairs in every facing).
- garden: grass, saplings, flowers, leaf hedges, lanterns on fence posts and a fountain.
- storage-room: chests, barrels, furnaces and hoppers facing different ways inside log walls.
- modern-house: concrete and glass, a door, a bed, carpets, a flower pot, a ladder up to a
  trapdoor in the roof.

usage: make_builds.py [out_dir]   (default: test/builds next to this file)"""
import gzip, os, struct, sys

def nbt_string(s):
    b = s.encode('utf-8')
    return struct.pack('>H', len(b)) + b

def tag(kind, name, payload):
    return bytes([kind]) + nbt_string(name) + payload

def compound(entries):
    return b''.join(entries) + b'\x00'

def write(path, blocks):
    W = max(x for x, _, _ in blocks) + 1
    H = max(y for _, y, _ in blocks) + 1
    L = max(z for _, _, z in blocks) + 1
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
    with open(path, 'wb') as out:
        out.write(gzip.compress(tag(10, 'Schematic', root), mtime=0))
    print(f"{os.path.basename(path)}: {len(blocks)} blocks, {W}x{H}x{L}, {len(palette) - 1} kinds")

SB = 'minecraft:stone_bricks'

def stairs(block, facing, half='bottom'):
    return f'minecraft:{block}_stairs[facing={facing},half={half},shape=straight,waterlogged=false]'

def castle_gate():
    b = {}
    for tx in (0, 10):  # two 3x3 towers, 8 high, crenellated
        for x in range(tx, tx + 3):
            for z in range(1, 4):
                for y in range(8):
                    b[(x, y, z)] = SB
                if (x + z) % 2 == 0:
                    b[(x, 8, z)] = SB
    for x in range(3, 10):  # the wall, 6 high, with a gate
        for y in range(6):
            b[(x, y, 2)] = SB
        if x % 2 == 1:
            b[(x, 6, 2)] = SB
    for x in range(5, 8):
        for y in range(4):
            b.pop((x, y, 2), None)
    b[(5, 3, 2)] = stairs('stone_brick', 'west', 'top')
    b[(7, 3, 2)] = stairs('stone_brick', 'east', 'top')
    b[(6, 3, 2)] = 'minecraft:iron_bars[east=false,north=false,south=false,west=false,waterlogged=false]'
    for x in (4, 8):
        b[(x, 3, 1)] = 'minecraft:wall_torch[facing=north]'
    return b

def sky_bridge():
    b = {}
    for z in list(range(0, 2)) + list(range(15, 17)):  # pillars
        for x in range(3):
            for y in range(4):
                b[(x, y, z)] = SB
    for z in range(17):  # deck and railings
        for x in range(3):
            b[(x, 4, z)] = 'minecraft:smooth_stone_slab[type=bottom,waterlogged=false]' if 2 <= z <= 14 else SB
        for x in (0, 2):
            b[(x, 5, z)] = 'minecraft:stone_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]'
    return b

def pixel_art():
    face = ["........",
            "........",
            ".XX..XX.",
            ".XX..XX.",
            "...XX...",
            "..XXXX..",
            "..XXXX..",
            "..X..X.."]
    shades = ['minecraft:lime_wool', 'minecraft:green_wool', 'minecraft:lime_wool', 'minecraft:lime_concrete']
    b = {}
    for x in range(16):
        for y in range(16):
            row = face[7 - y // 2]
            if row[x // 2] == 'X':
                b[(x, y, 0)] = 'minecraft:black_wool'
            else:
                b[(x, y, 0)] = shades[(x * 7 + y * 3) % 4]
    return b

def spiral_tower():
    b = {}
    H = 11
    for y in range(H):
        for x in range(7):
            for z in range(7):
                d = (x - 3) ** 2 + (z - 3) ** 2
                if 6 <= d <= 10:
                    window = y % 4 == 2 and (x == 3 or z == 3)
                    b[(x, y, z)] = 'minecraft:glass_pane[east=false,north=false,south=false,waterlogged=false,west=false]' if window else SB
        b[(3, y, 3)] = 'minecraft:polished_andesite'
    for y in (0, 1):  # a way in
        b.pop((3, y, 0), None)
    ring = [((3, 2), 'east'), ((4, 2), 'south'), ((4, 3), 'south'), ((4, 4), 'west'),
            ((3, 4), 'west'), ((2, 4), 'north'), ((2, 3), 'north'), ((2, 2), 'east')]
    for step in range(H - 1):
        (x, z), facing = ring[step % len(ring)]
        b[(x, step, z)] = stairs('stone_brick', facing)
    for x in range(7):  # slab roof over everything
        for z in range(7):
            if (x - 3) ** 2 + (z - 3) ** 2 <= 10:
                b[(x, H, z)] = 'minecraft:smooth_stone_slab[type=bottom,waterlogged=false]'
    return b

def garden():
    b = {}
    for x in range(11):
        for z in range(11):
            b[(x, 0, z)] = 'minecraft:cobblestone' if x == 5 or z == 5 else 'minecraft:grass_block[snowy=false]'
            if (x in (0, 10) or z in (0, 10)) and not (x == 5 or z == 5):
                b[(x, 1, z)] = 'minecraft:oak_leaves[distance=1,persistent=true,waterlogged=false]'
    for x, z in ((2, 2), (8, 2), (2, 8), (8, 8)):
        b[(x, 1, z)] = 'minecraft:oak_sapling[stage=0]'
    flowers = ['minecraft:poppy', 'minecraft:dandelion', 'minecraft:cornflower', 'minecraft:oxeye_daisy']
    for i, (x, z) in enumerate(((1, 3), (3, 1), (7, 1), (9, 3), (1, 7), (3, 9), (7, 9), (9, 7))):
        b[(x, 1, z)] = flowers[i % 4]
    for x, z in ((4, 4), (6, 4), (4, 6), (6, 6)):
        b[(x, 1, z)] = 'minecraft:oak_fence[east=false,north=false,south=false,waterlogged=false,west=false]'
        b[(x, 2, z)] = 'minecraft:lantern[hanging=false,waterlogged=false]'
    # fountain: a ring of stone bricks round a water source on the path crossing
    for x, z in ((5, 4), (4, 5), (6, 5), (5, 6)):
        b[(x, 1, z)] = SB
    b[(5, 1, 5)] = 'minecraft:water[level=0]'
    return b

def storage_room():
    b = {}
    W, L = 9, 7
    for x in range(W):
        for z in range(L):
            b[(x, 0, z)] = 'minecraft:spruce_planks'
            edge = x in (0, W - 1) or z in (0, L - 1)
            if edge:
                for y in (1, 2, 3):
                    corner = x in (0, W - 1) and z in (0, L - 1)
                    b[(x, y, z)] = 'minecraft:oak_log[axis=y]' if corner else 'minecraft:spruce_planks'
    for y in (1, 2):  # doorway
        b.pop((4, y, 0), None)
    for x in (1, 3, 5, 7):
        b[(x, 1, 5)] = 'minecraft:chest[facing=north,type=single,waterlogged=false]'
    for x in (2, 4, 6):
        b[(x, 1, 5)] = 'minecraft:barrel[facing=north,open=false]'
    b[(1, 2, 5)] = 'minecraft:hopper[enabled=true,facing=down]'
    b[(3, 2, 5)] = 'minecraft:hopper[enabled=true,facing=down]'
    b[(5, 2, 5)] = 'minecraft:barrel[facing=up,open=false]'
    b[(5, 2, 4)] = 'minecraft:hopper[enabled=true,facing=south]'
    for z in (2, 3):
        b[(1, 1, z)] = 'minecraft:furnace[facing=east,lit=false]'
    b[(7, 1, 2)] = 'minecraft:crafting_table'
    b[(7, 1, 3)] = 'minecraft:smoker[facing=west,lit=false]'
    b[(2, 2, 1)] = 'minecraft:wall_torch[facing=south]'  # on the front wall beside the doorway
    return b

def modern_house():
    b = {}
    W, L, H = 9, 8, 5
    for x in range(W):
        for z in range(L):
            b[(x, 0, z)] = 'minecraft:gray_concrete'
            if x in (0, W - 1) or z in (0, L - 1):
                for y in range(1, H):
                    window = y == 2 and (2 <= x <= 6 and z in (0, L - 1) or 2 <= z <= 5 and x in (0, W - 1)) and x != 4
                    b[(x, y, z)] = 'minecraft:glass' if window else 'minecraft:white_concrete'
            b[(x, H, z)] = 'minecraft:smooth_quartz_slab[type=bottom,waterlogged=false]'
    b[(4, 1, 0)] = 'minecraft:oak_door[facing=south,half=lower,hinge=left,open=false,powered=false]'
    b[(4, 2, 0)] = 'minecraft:oak_door[facing=south,half=upper,hinge=left,open=false,powered=false]'
    # bed against the back wall, carpets in front of it
    b[(2, 1, 6)] = 'minecraft:red_bed[facing=south,occupied=false,part=head]'
    b[(2, 1, 5)] = 'minecraft:red_bed[facing=south,occupied=false,part=foot]'
    for x in (1, 2, 3):
        b[(x, 1, 4)] = 'minecraft:white_carpet'
    b[(1, 1, 6)] = 'minecraft:flower_pot'
    # ladder on the east wall up to a trapdoor in the roof
    for y in range(1, H + 1):
        b[(7, y, 6)] = 'minecraft:ladder[facing=west,waterlogged=false]'
    b[(7, H, 6)] = 'minecraft:oak_trapdoor[facing=west,half=bottom,open=false,powered=false,waterlogged=false]'
    b[(6, 3, 1)] = 'minecraft:lantern[hanging=false,waterlogged=false]'
    b[(6, 2, 1)] = 'minecraft:spruce_planks'
    return b

if __name__ == '__main__':
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), 'builds')
    os.makedirs(out, exist_ok=True)
    for name, make in (('castle-gate', castle_gate), ('sky-bridge', sky_bridge), ('pixel-art', pixel_art), ('spiral-tower', spiral_tower),
                       ('garden', garden), ('storage-room', storage_room), ('modern-house', modern_house)):
        write(os.path.join(out, name + '.schem'), make())
