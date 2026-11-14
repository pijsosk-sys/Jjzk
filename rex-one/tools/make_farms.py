#!/usr/bin/env python3
"""Generates the bundled farm schematics (Sponge v2 .schem) into resources/assets/rex/schematics.

Kelp farm, one storey, looking along the rows (z across, y up). The pattern repeats every six blocks:

         trough  kelp    piston   core   piston  kelp    trough
    y3   (flow)  water   observ   dust   observ  water   (flow)     observers watch the third kelp block
    y2   hopper  water   piston   block  piston  water   hopper     pistons cut the kelp at its second block
    y1   block   KELP    block    block  block   KELP    block
    y0   floor   floor   floor    floor  floor   floor   floor      (the next storey's floor closes the top)

A kelp plant reaching three blocks trips its observer. The observer powers the dust behind it, the dust
powers the core block, and every piston along that double row fires. Cut kelp floats up to the top
water block, which spills sideways into the trough and carries the kelp over the hoppers. Each trough's
hoppers run to a collector line on the east side, which drops into the chests.

Water does not spread across the top of other water, so nothing here relies on a stream over the
kelp: the only moving water is the shallow spill above the hoppers.
"""
import gzip, io, os, struct, sys

AIR = "minecraft:air"

class Schem:
    def __init__(self, w, h, l):
        self.w, self.h, self.l = w, h, l
        self.cells = {}

    def set(self, x, y, z, block):
        assert 0 <= x < self.w and 0 <= y < self.h and 0 <= z < self.l, (x, y, z)
        self.cells[(x, y, z)] = block

    def get(self, x, y, z):
        return self.cells.get((x, y, z), AIR)

    def fill(self, x0, y0, z0, x1, y1, z1, block):
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                for z in range(z0, z1 + 1):
                    self.set(x, y, z, block)

    def count(self):
        out = {}
        for b in self.cells.values():
            if b != AIR:
                name = b.split("[")[0]
                out[name] = out.get(name, 0) + 1
        return out

    def write(self, path):
        palette = {AIR: 0}
        data = bytearray()
        for y in range(self.h):
            for z in range(self.l):
                for x in range(self.w):
                    b = self.get(x, y, z)
                    i = palette.setdefault(b, len(palette))
                    while i >= 0x80:
                        data.append(i & 0x7F | 0x80)
                        i >>= 7
                    data.append(i)
        out = io.BytesIO()
        def name(s):
            raw = s.encode()
            out.write(struct.pack(">H", len(raw)) + raw)
        def tag(kind, key):
            out.write(bytes([kind])); name(key)
        tag(10, "Schematic")
        tag(3, "Version"); out.write(struct.pack(">i", 2))
        tag(3, "DataVersion"); out.write(struct.pack(">i", 4671))
        tag(2, "Width"); out.write(struct.pack(">h", self.w))
        tag(2, "Height"); out.write(struct.pack(">h", self.h))
        tag(2, "Length"); out.write(struct.pack(">h", self.l))
        tag(3, "PaletteMax"); out.write(struct.pack(">i", len(palette)))
        tag(10, "Palette")
        for block, i in palette.items():
            tag(3, block); out.write(struct.pack(">i", i))
        out.write(b"\x00")
        tag(7, "BlockData"); out.write(struct.pack(">i", len(data)) + bytes(data))
        out.write(b"\x00")
        with open(path, "wb") as f:
            f.write(gzip.compress(out.getvalue(), mtime=0))


FLOOR = "minecraft:smooth_stone"
TRIM = "minecraft:stone_bricks"
GLASS = "minecraft:glass"
LIGHT = "minecraft:sea_lantern"
WATER = "minecraft:water[level=0]"
KELP = "minecraft:kelp[age=0]"
DUST = "minecraft:redstone_wire[east=side,north=side,power=0,south=side,west=side]"


def hopper(facing):
    return f"minecraft:hopper[enabled=true,facing={facing}]"


def kelp_farm(pairs, storeys, plants):
    """pairs: double rows side by side; storeys: floors; plants: kelp per row (dust carries 15 blocks)."""
    east = plants + 1                  # x of the collector line, inside the east wall
    width = plants + 2
    south = 6 * pairs + 2              # z of the south wall
    length = south + 2                 # one more row for the chests
    height = 4 * storeys + 1
    s = Schem(width, height, length)
    troughs = [1 + 6 * p for p in range(pairs + 1)]

    for storey in range(storeys + 1):
        y = 4 * storey
        roof = storey == storeys
        s.fill(0, y, 0, width - 1, y, south, GLASS if roof else FLOOR)
        for x in range(width):
            for z in (0, south):
                s.set(x, y, z, LIGHT if x % 4 == 0 and not roof else TRIM)
        for z in range(south + 1):
            for x in (0, width - 1):
                s.set(x, y, z, LIGHT if z % 6 == 0 and not roof else TRIM)
        if roof:
            break

        for yy in range(y + 1, y + 4):
            for x in range(width):
                s.set(x, yy, 0, GLASS); s.set(x, yy, south, GLASS)
            for z in range(south + 1):
                s.set(0, yy, z, GLASS); s.set(width - 1, yy, z, GLASS)
            for x, z in ((0, 0), (width - 1, 0), (0, south), (width - 1, south)):
                s.set(x, yy, z, TRIM)

        for x in range(1, plants + 1):
            for trough in troughs:
                s.set(x, y + 1, trough, FLOOR)
                s.set(x, y + 2, trough, hopper("east"))
            for pair in range(pairs):
                zb = 2 + 6 * pair
                core = zb + 2
                for kelp_z, piston_z, facing in ((zb, zb + 1, "north"), (zb + 4, zb + 3, "south")):
                    s.set(x, y + 1, kelp_z, KELP)
                    s.set(x, y + 2, kelp_z, WATER)
                    s.set(x, y + 3, kelp_z, WATER)
                    s.set(x, y + 1, piston_z, FLOOR)
                    s.set(x, y + 2, piston_z, f"minecraft:piston[extended=false,facing={facing}]")
                    s.set(x, y + 3, piston_z, f"minecraft:observer[facing={facing},powered=false]")
                s.set(x, y + 1, core, FLOOR)
                s.set(x, y + 2, core, FLOOR)
                s.set(x, y + 3, core, DUST)

        # collector: every trough empties east into this line, which runs south to the drop
        for z in range(1, south):
            s.set(east, y + 2, z, hopper("south"))
        s.set(east, y + 2, south, hopper("down"))

    # the drop: hoppers down the south-east corner into a double chest
    for yy in range(2, 4 * (storeys - 1) + 2):
        s.set(east, yy, south, hopper("down"))
    s.set(east, 1, south, hopper("south"))
    s.fill(east - 2, 0, south + 1, east, 0, south + 1, TRIM)
    s.set(east, 1, south + 1, "minecraft:chest[facing=south,type=left,waterlogged=false]")
    s.set(east - 1, 1, south + 1, "minecraft:chest[facing=south,type=right,waterlogged=false]")
    return s


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), "..", "resources", "assets", "rex", "schematics")
    os.makedirs(out, exist_ok=True)
    for name, farm in (("kelp-mega-farm", kelp_farm(4, 5, 16)), ("kelp-farm", kelp_farm(1, 2, 8)), ("kelp-test", kelp_farm(1, 1, 3))):
        if name.endswith("-test") and "--tests" not in sys.argv:
            continue
        farm.write(os.path.join(out, name + ".schem"))
        counts = farm.count()
        print(f"{name}: {farm.w}x{farm.h}x{farm.l}, {sum(counts.values())} blocks, {counts.get('minecraft:kelp', 0)} kelp plants")
        for block, n in sorted(counts.items(), key=lambda kv: -kv[1]):
            print(f"    {n:6d}  {block}")


if __name__ == "__main__":
    main()
