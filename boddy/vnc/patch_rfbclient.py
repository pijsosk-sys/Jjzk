#!/usr/bin/env python3
"""Adds ZRLE to the Boddy app's VNC client (com.boddy.os.vnc.RfbClient), in the app's disassembled code.
Usage: patch_rfbclient.py SMALI_DIR ZRLE_SMALI

Before, RfbClient asked Xvnc for CopyRect, Raw, Cursor and DesktopSize only, so every changed pixel came as
4 raw bytes. This asks for ZRLE first (Xvnc then uses it for every update) and decodes it with Zrle (see
Zrle.java, compiled into ZRLE_SMALI): a rectangle with encoding 16 goes to a new readZrle(), which hands it
to the connection's Zrle and tells the view what changed, as readRaw() does. Each edit must match exactly
once, so a changed app fails here instead of being patched wrongly."""
import sys,shutil
from pathlib import Path
smali,zrle=Path(sys.argv[1]),Path(sys.argv[2])
path=smali/'com/boddy/os/vnc/RfbClient.smali'
s=path.read_text()
def edit(old,new):
 global s
 n=s.count(old)
 if n!=1:sys.exit('RfbClient.smali: expected one match, found %d, for:\n%s'%(n,old))
 s=s.replace(old,new)
if 'readZrle' in s:sys.exit('RfbClient.smali already has ZRLE')
# The connection's ZRLE decoder, and the encoding's number next to the others.
edit(""".field private volatile width:I
""",""".field private volatile width:I

.field private zrle:Lcom/boddy/os/vnc/Zrle;

.field public static final ENC_ZRLE:I = 0x10
""")
# handshake(): a new decoder for each connection (its zlib stream starts over), and ZRLE first in the
# encodings sent to the server: {ZRLE, CopyRect (v9=1), Raw (v4=0), Cursor, DesktopSize}, 5 of them.
edit("""    const/16 v0, -0xef

    const/16 v1, -0xdf

    .line 160
    filled-new-array {v9, v4, v0, v1}, [I
""","""    new-instance v0, Lcom/boddy/os/vnc/Zrle;

    invoke-direct {v0}, Lcom/boddy/os/vnc/Zrle;-><init>()V

    iput-object v0, p0, Lcom/boddy/os/vnc/RfbClient;->zrle:Lcom/boddy/os/vnc/Zrle;

    const/16 v0, -0xef

    const/16 v1, -0xdf

    const/16 v2, 0x10

    .line 160
    filled-new-array {v2, v9, v4, v0, v1}, [I
""")
edit("""    const/4 v1, 0x4

    .line 162
    invoke-virtual {v3, v1}, Ljava/io/DataOutputStream;->writeShort(I)V
""","""    const/4 v1, 0x5

    .line 162
    invoke-virtual {v3, v1}, Ljava/io/DataOutputStream;->writeShort(I)V
""")
# readFramebufferUpdate(): encoding 16 (in v5) goes to readZrle with the rectangle (v9-v12, as readRaw gets it).
edit("""    if-eqz v5, :cond_98

    if-ne v5, v3, :cond_83
""","""    const/16 v6, 0x10

    if-eq v5, v6, :cond_zrle

    if-eqz v5, :cond_98

    if-ne v5, v3, :cond_83
""")
edit("""    :cond_98
    invoke-direct {p0, v9, v10, v11, v12}, Lcom/boddy/os/vnc/RfbClient;->readRaw(IIII)V

    goto :goto_b5
""","""    :cond_98
    invoke-direct {p0, v9, v10, v11, v12}, Lcom/boddy/os/vnc/RfbClient;->readRaw(IIII)V

    goto :goto_b5

    :cond_zrle
    invoke-direct {p0, v9, v10, v11, v12}, Lcom/boddy/os/vnc/RfbClient;->readZrle(IIII)V

    goto :goto_b5
""")
# readZrle(x, y, w, h): zrle.read(this, input, x, y, w, h), then listener.onUpdate(x, y, w, h).
edit(""".method private final readRaw(IIII)V""",""".method private final readZrle(IIII)V
    .registers 12

    iget-object v0, p0, Lcom/boddy/os/vnc/RfbClient;->zrle:Lcom/boddy/os/vnc/Zrle;

    move-object v1, p0

    iget-object v2, p0, Lcom/boddy/os/vnc/RfbClient;->input:Ljava/io/DataInputStream;

    move v3, p1

    move v4, p2

    move v5, p3

    move v6, p4

    invoke-virtual/range {v0 .. v6}, Lcom/boddy/os/vnc/Zrle;->read(Lcom/boddy/os/vnc/RfbClient;Ljava/io/DataInputStream;IIII)V

    iget-object v0, p0, Lcom/boddy/os/vnc/RfbClient;->listener:Lcom/boddy/os/vnc/RfbClient$Listener;

    invoke-interface {v0, p1, p2, p3, p4}, Lcom/boddy/os/vnc/RfbClient$Listener;->onUpdate(IIII)V

    return-void
.end method

.method private final readRaw(IIII)V""")
path.write_text(s)
shutil.copyfile(zrle,smali/'com/boddy/os/vnc/Zrle.smali')
print('Patched',path,'and added Zrle.smali')
