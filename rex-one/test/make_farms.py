"""Writes three small farm test builds (Sponge v2 .schem). They were bundled up to one.22; one.23 took
them out of the library for the DonutSMP farms (make_donut.py), and they stay as tests for grown plants.
usage: make_farms.py [out_dir]   (default: test/builds next to this file)
- sweet-berry-farm: rows of grown berry bushes on grass with stone brick walkways between them, so
  the builder never has to walk through a thorn bush, inside a cobblestone rim.
- cactus-farm: a sand floor with three-high cacti on every other cell both ways, the spacing a
  cactus needs (no block beside it).
- nether-wart-farm: soul sand rows of grown nether wart with stone brick walkways.
Grown plants are saved grown; the builder places them young and counts them as built."""
import gzip, os, struct, sys

def nbt_string(s):
    b = s.encode('utf-8')
    return struct.pack('>H', len(b)) + b

def tag(kind, name, payload):
    return bytes([kind]) + nbt_string(name) + payload

def compound(entries):
    return b''.join(entries) + b'\x00'

def write(path, size, blocks):
    W, H, L = size
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
    # mtime=0 keeps the file identical from run to run
    with open(path, 'wb') as out:
        out.write(gzip.compress(tag(10, 'Schematic', root), mtime=0))
    print(f"{os.path.basename(path)}: {len(blocks)} blocks, {len(palette)} palette entries")

def sweet_berry_farm():
    W, H, L = 11, 2, 9
    b = {}
    for x in range(W):
        for z in range(L):
            rim = x in (0, W - 1) or z in (0, L - 1)
            if rim:
                b[(x, 0, z)] = 'minecraft:cobblestone'
            elif z % 2 == 1:
                b[(x, 0, z)] = 'minecraft:grass_block[snowy=false]'
                b[(x, 1, z)] = 'minecraft:sweet_berry_bush[age=3]'
            else:
                b[(x, 0, z)] = 'minecraft:stone_bricks'
    return (W, H, L), b

def cactus_farm():
    W, H, L = 9, 4, 9
    b = {}
    for x in range(W):
        for z in range(L):
            b[(x, 0, z)] = 'minecraft:sand'
            if x % 2 == 1 and z % 2 == 1:
                for y in (1, 2, 3):
                    b[(x, y, z)] = 'minecraft:cactus[age=0]'
    return (W, H, L), b

def nether_wart_farm():
    W, H, L = 9, 2, 7
    b = {}
    for x in range(W):
        for z in range(L):
            if z % 2 == 1:
                b[(x, 0, z)] = 'minecraft:soul_sand'
                b[(x, 1, z)] = 'minecraft:nether_wart[age=3]'
            else:
                b[(x, 0, z)] = 'minecraft:stone_bricks'
    return (W, H, L), b

out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), 'builds')
os.makedirs(out, exist_ok=True)
for name, make in (('sweet-berry-farm', sweet_berry_farm), ('cactus-farm', cactus_farm), ('nether-wart-farm', nether_wart_farm)):
    size, blocks = make()
    write(os.path.join(out, name + '.schem'), size, blocks)
