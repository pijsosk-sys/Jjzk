"""Writes the bundled DonutSMP farms and bases (Sponge v2 .schem) into resources/assets/rex/schematics/.
Kelp, cactus and spawners are the top earners on DonutSMP. The kelp farms are already bundled
(kelp-compact-auto, kelp-blue-behemoth), so this adds cactus farms and spawner bases, small and big.

- donut-cactus-farm-small: one layer of 12 cacti. Each new cactus block grows into a fence and pops
  off, falls into a water stream, and the stream carries it over a line of hoppers into a chest.
  Needs no redstone.
- donut-cactus-farm-big: the same idea three layers high, 120 cacti, two hopper lines and two
  chests. Each layer's sand sits on the fence of the layer below.
- donut-spawner-base-small: a 9x9 cobbled deepslate bunker: door, bed, storage, ender chest,
  furnaces, anvil, and four marked pads (chiseled deepslate) to stand spawners on.
- donut-spawner-base-big: a 17x15 bunker with a front room (storage wall, furnaces, an enchanting
  table with 15 bookshelves) and a spawner hall with 12 pads and two beds.

Spawners are not part of the blueprints (they are bought, and stack): place them on the pads.

usage: make_donut.py [out_dir]"""
import os, sys
from collections import deque
from make_builds import write

GLASS = 'minecraft:glass'
FLOOR = 'minecraft:smooth_stone'
FENCE = 'minecraft:oak_fence[east=false,north=false,south=false,waterlogged=false,west=false]'
HOPPER_S = 'minecraft:hopper[enabled=true,facing=south]'

def flow(b, sources, y):
    """Water sources at height y, plus the flowing water they make on a flat floor (levels 1-7)."""
    seen = {}
    todo = deque()
    for x, z in sources:
        seen[(x, z)] = 0
        todo.append((x, z))
    while todo:
        x, z = todo.popleft()
        level = seen[(x, z)]
        if level == 7:
            continue
        for nx, nz in ((x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1)):
            if (nx, nz) not in seen and (nx, y, nz) not in b and (nx, y - 1, nz) in b:
                seen[(nx, nz)] = level + 1
                todo.append((nx, nz))
    for (x, z), level in seen.items():
        b[(x, y, z)] = f'minecraft:water[level={level}]'

def cactus_farm(W, L, layers, hopper_xs, source_xs, full_fence_lines):
    """Cacti on a checkerboard: layer 0 on (odd x, odd z), layer 1 on (even x, odd z), and so on.
    The farm floor spans x 0..W-1, z 0..L-1 inside a glass rim, shifted by one so the rim fits."""
    b = {}
    O = 1
    def put(x, y, z, state):
        b[(x + O, y, z + O)] = state
    for x in range(-1, W + 1):
        for z in range(-1, L + 1):
            put(x, 0, z, HOPPER_S if x in hopper_xs and 0 <= z < L else FLOOR)
    for hx in hopper_xs:
        put(hx, 0, L, 'minecraft:chest[facing=north,type=single,waterlogged=false]')
    rim_h = 4
    for x in range(-1, W + 1):
        for z in range(-1, L + 1):
            if x in (-1, W) or z in (-1, L):
                for y in range(1, rim_h + 1):
                    put(x, y, z, GLASS)
    A = [(x, z) for x in range(1, W - 1, 2) for z in range(1, L - 1, 2)]
    B = [(x, z) for x in range(2, W - 2, 2) for z in range(1, L - 1, 2)]
    for x, z in A:
        put(x, 1, z, 'minecraft:cobblestone')  # holds the sand up out of the water
    for layer in range(layers):
        base = 2 + 3 * layer  # sand at base, cactus at base+1, fences at base+2
        cells = A if layer % 2 == 0 else B
        for x, z in cells:
            put(x, base, z, 'minecraft:sand')
            put(x, base + 1, z, 'minecraft:cactus[age=0]')
        # fences beside every cell a cactus grows into
        fence_x = range(0, W, 2) if layer % 2 == 0 else range(1, W - 1, 2)
        for x in fence_x:
            for z in (range(0, L) if full_fence_lines else range(1, L - 1, 2)):
                put(x, base + 2, z, FENCE)
    flow(b, [(x + O, z + O) for x in source_xs for z in range(L) if (x + O, 1, z + O) not in b], 1)
    return b

def bunker(W, L, H, wall, floor, roof):
    b = {}
    for x in range(W):
        for z in range(L):
            b[(x, 0, z)] = floor
            b[(x, H, z)] = roof
            if x in (0, W - 1) or z in (0, L - 1):
                for y in range(1, H):
                    b[(x, y, z)] = wall
    return b

def door(b, x, z, facing):
    b[(x, 1, z)] = f'minecraft:oak_door[facing={facing},half=lower,hinge=left,open=false,powered=false]'
    b[(x, 2, z)] = f'minecraft:oak_door[facing={facing},half=upper,hinge=left,open=false,powered=false]'

def bed(b, x, z_head, z_foot, facing, color='red'):
    b[(x, 1, z_head)] = f'minecraft:{color}_bed[facing={facing},occupied=false,part=head]'
    b[(x, 1, z_foot)] = f'minecraft:{color}_bed[facing={facing},occupied=false,part=foot]'

PAD = 'minecraft:chiseled_deepslate'

def spawner_base_small():
    b = bunker(9, 9, 4, 'minecraft:cobbled_deepslate', 'minecraft:deepslate_tiles', 'minecraft:deepslate_tiles')
    door(b, 4, 0, 'south')
    for x, kind in zip(range(1, 8), ('chest', 'barrel', 'chest', 'ender_chest', 'chest', 'barrel', 'chest')):
        b[(x, 1, 7)] = {'chest': 'minecraft:chest[facing=north,type=single,waterlogged=false]',
                        'barrel': 'minecraft:barrel[facing=north,open=false]',
                        'ender_chest': 'minecraft:ender_chest[facing=north,waterlogged=false]'}[kind]
    b[(2, 2, 7)] = 'minecraft:lantern[hanging=false,waterlogged=false]'
    b[(6, 2, 7)] = 'minecraft:lantern[hanging=false,waterlogged=false]'
    b[(1, 1, 2)] = 'minecraft:crafting_table'
    b[(1, 1, 3)] = 'minecraft:furnace[facing=east,lit=false]'
    b[(1, 1, 4)] = 'minecraft:blast_furnace[facing=east,lit=false]'
    b[(1, 1, 5)] = 'minecraft:smoker[facing=east,lit=false]'
    bed(b, 7, 2, 3, 'north')
    b[(7, 1, 5)] = 'minecraft:anvil[facing=north]'
    for x in (3, 5):
        for z in (3, 5):
            b[(x, 0, z)] = PAD
    b[(4, 4, 4)] = 'minecraft:glowstone'
    return b

def spawner_base_big():
    W, L, H = 17, 15, 6
    b = bunker(W, L, H, 'minecraft:deepslate_bricks', 'minecraft:polished_deepslate', 'minecraft:deepslate_tiles')
    door(b, 8, 0, 'south')
    # wall between the front room and the spawner hall, with a doorway in the middle
    for x in range(1, W - 1):
        for y in range(1, H):
            if not (x == 8 and y in (1, 2)):
                b[(x, y, 7)] = 'minecraft:deepslate_bricks'
    # front room: storage wall on the west side, chests below and barrels above
    for z in range(1, 7):
        b[(1, 1, z)] = 'minecraft:chest[facing=east,type=single,waterlogged=false]' if z % 2 else 'minecraft:ender_chest[facing=east,waterlogged=false]' if z == 4 else 'minecraft:barrel[facing=east,open=false]'
        b[(1, 2, z)] = 'minecraft:barrel[facing=east,open=false]'
    # work row on the east side
    for z, state in zip(range(1, 7), ('minecraft:furnace[facing=west,lit=false]', 'minecraft:blast_furnace[facing=west,lit=false]',
                                       'minecraft:smoker[facing=west,lit=false]', 'minecraft:crafting_table',
                                       'minecraft:anvil[facing=north]', 'minecraft:smithing_table')):
        b[(15, 1, z)] = state
    # enchanting table in a ring of 15 bookshelves, open toward the door path
    b[(11, 1, 4)] = 'minecraft:enchanting_table'
    for x in range(9, 14):
        for z in range(2, 7):
            if (x in (9, 13) or z in (2, 6)) and (x, z) != (9, 4):
                b[(x, 1, z)] = 'minecraft:bookshelf'
    # spawner hall: 12 pads, two beds, an ender chest
    for x in range(3, 14, 2):
        for z in (10, 12):
            b[(x, 0, z)] = PAD
    bed(b, 1, 13, 12, 'south')
    bed(b, 2, 13, 12, 'south', 'blue')
    b[(15, 1, 13)] = 'minecraft:ender_chest[facing=west,waterlogged=false]'
    b[(15, 1, 12)] = 'minecraft:chest[facing=west,type=single,waterlogged=false]'
    for x in (4, 8, 12):
        for z in (3, 11):
            b[(x, H, z)] = 'minecraft:glowstone'
    return b

if __name__ == '__main__':
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'resources', 'assets', 'rex', 'schematics')
    os.makedirs(out, exist_ok=True)
    builds = {
        'donut-cactus-farm-small': lambda: cactus_farm(8, 9, 1, hopper_xs=(0,), source_xs=(7,), full_fence_lines=True),
        'donut-cactus-farm-big': lambda: cactus_farm(15, 13, 3, hopper_xs=(0, 14), source_xs=(7,), full_fence_lines=False),
        'donut-spawner-base-small': spawner_base_small,
        'donut-spawner-base-big': spawner_base_big,
    }
    for name, make in builds.items():
        write(os.path.join(out, name + '.schem'), make())
