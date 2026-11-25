#!/usr/bin/env python3
"""从 pm3 fullimage/bootrom ELF 中定位并抽取 Xilinx FPGA 位流镜像。"""
import struct
import sys

BIT_MAGIC = bytes([0x00, 0x09, 0x0F, 0xF0, 0x0F, 0xF0, 0x0F, 0xF0, 0x0F, 0xF0, 0x00, 0x00, 0x01])


def load_elf_flat(path):
    with open(path, "rb") as f:
        elf = f.read()
    assert elf[:4] == b"\x7fELF", "not an ELF"
    e_phoff = struct.unpack_from("<I", elf, 0x1C)[0]
    e_phentsize, e_phnum = struct.unpack_from("<HH", elf, 0x2A)
    segs = []
    for i in range(e_phnum):
        p_type, p_offset, p_vaddr, p_paddr, p_filesz = struct.unpack_from("<IIIII", elf, e_phoff + i * e_phentsize)
        if p_type == 1 and p_filesz > 0:  # PT_LOAD
            segs.append((p_paddr, elf[p_offset:p_offset + p_filesz]))
    segs.sort()
    base = segs[0][0]
    flat = b""
    for addr, data in segs:
        flat += b"\x00" * (addr - base - len(flat)) + data
    return flat, base


def scan(path):
    print(f"=== {path} ===")
    flat, base = load_elf_flat(path)
    print(f"flat image: {len(flat)} bytes @ 0x{base:08X}")

    # 打印所有可能的版本/文件名字符串
    for tag in (b"fpga", b"vq100", b".ncd"):
        off = 0
        while True:
            i = flat.find(tag, off)
            if i < 0:
                break
            s = flat[max(0, i - 60):i + 60]
            txt = "".join(chr(c) if 32 <= c < 127 else "." for c in s)
            print(f"  str @{i:#x}: ...{txt}...")
            off = i + 1
            if off > i + 200:
                break

    # 找 Xilinx 位流魔数
    found = []
    off = 0
    while True:
        i = flat.find(BIT_MAGIC, off)
        if i < 0:
            break
        found.append(i)
        off = i + 1
    print(f"bitstream magic count: {len(found)}")
    for n, i in enumerate(found):
        # bit 头字段: 13B magic, 'a' + len + name, 'b' + len + part, 'c' date, 'd' time, 'e' + 4B len
        p = i + 13
        try:
            fields = {}
            for key, name in ((b"a", "design"), (b"b", "part"), (b"c", "date"), (b"d", "time")):
                assert flat[p] == key[0]
                ln = struct.unpack_from(">H", flat, p + 1)[0]
                fields[name] = flat[p + 3:p + 3 + ln - 1].decode("ascii", "replace")
                p += 3 + ln
            assert flat[p] == 0x65  # 'e'
            dlen = struct.unpack_from(">I", flat, p + 1)[0]
            data_off = p + 5
            print(f"  [{n}] @{i:#x} design={fields['design']!r} part={fields['part']!r} "
                  f"date={fields['date']} time={fields['time']} data={dlen} bytes")
            out = f"{path.rsplit('/', 1)[-1]}.fpga{n}.bit"
            with open(out, "wb") as f:
                f.write(flat[i:data_off + dlen])
            print(f"      -> {out}")
        except Exception as e:
            print(f"  [{n}] @{i:#x} header parse failed: {e}")


for p in sys.argv[1:]:
    scan(p)
