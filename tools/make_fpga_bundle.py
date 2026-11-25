#!/usr/bin/env python3
"""把厂商 fullimage.elf 里内嵌的 FPGA 镜像加工成 mainline armsrc 可嵌入的格式。

输出（到 pm3-fw/armsrc/prebuilt/）：
  fpga_all.bit.z      —— [4B LE len][LZ4 block] 分块压缩格式（单镜像，无交错）
  fpga_version_info.c —— 版本信息表（g_fpga_bitstream_num=1）

处理内容：
  1. 从厂商 fullimage.elf 抽取 FPGA blob（头部到镜像末尾）
  2. 把被厂商改坏的 13 字节位流前导修复为标准 Xilinx magic
     （armsrc fpgaloader 的 reset_fpga_stream 会校验它）
"""
import struct
import sys
import lz4.block

SRC_ELF = "D:/20260415MAX/client/fullimage.elf"
OUT_DIR = "D:/ProxPocket/third_party/pm3-fw/armsrc/prebuilt"

XILINX_MAGIC = bytes([0x00, 0x09, 0x0F, 0xF0, 0x0F, 0xF0, 0x0F, 0xF0, 0x0F, 0xF0, 0x00, 0x00, 0x01])
RING = 30720  # FPGA_RING_BUFFER_BYTES


def load_flat(path):
    elf = open(path, "rb").read()
    e_phoff = struct.unpack_from("<I", elf, 0x1C)[0]
    phsize, phnum = struct.unpack_from("<HH", elf, 0x2A)
    segs = []
    for i in range(phnum):
        t, off, va, pa, fsz = struct.unpack_from("<IIIII", elf, e_phoff + i * phsize)
        if t == 1 and fsz:
            segs.append((pa, elf[off:off + fsz]))
    segs.sort()
    base = segs[0][0]
    flat = b""
    for a, d in segs:
        flat += b"\x00" * (a - base - len(flat)) + d
    return flat


def main():
    flat = load_flat(SRC_ELF)

    # 用 'a' 字段定位位流头
    idx = flat.find(b"\x61\x00\x13fpga_pmimax_hf.ncd\x00")
    assert idx > 13, "fpga blob not found"
    hdr = idx - 13
    blob = bytearray(flat[hdr:])
    print(f"blob: {len(blob)} bytes @ flat {hdr:#x}")

    # 解析字段，确认结构完好
    p = 13
    fields = {}
    for key in b"abcd":
        ln = struct.unpack_from(">H", blob, p + 1)[0]
        fields[chr(key)] = blob[p + 3:p + 3 + ln - 1].decode()
        p += 3 + ln
    assert blob[p] == 0x65
    print("fields:", fields)

    # 修复前导为标准 Xilinx magic（厂商改过，loader 会校验）
    print("old preamble:", bytes(blob[:13]).hex())
    blob[:13] = XILINX_MAGIC
    print("new preamble:", bytes(blob[:13]).hex())

    # 同步字必须存在
    si = bytes(blob).find(bytes.fromhex("aa995566"))
    assert si > 0, "sync word missing"
    print(f"sync word @ blob+{si}")

    # 分块独立压缩（每块无字典依赖，运行时 continue 解码兼容）
    out = bytearray()
    pos = 0
    n = 0
    while pos < len(blob):
        chunk = bytes(blob[pos:pos + RING])
        cmped = lz4.block.compress(chunk, mode="high_compression", compression=12, store_size=False)
        out += struct.pack("<I", len(cmped)) + cmped
        pos += len(chunk)
        n += 1
    with open(f"{OUT_DIR}/fpga_all.bit.z", "wb") as f:
        f.write(out)
    print(f"fpga_all.bit.z: {len(out)} bytes in {n} chunk(s)")

    ver = f'{fields["a"]} image {fields["b"]} {fields["c"].replace("/", "-")} {fields["d"]}'
    with open(f"{OUT_DIR}/fpga_version_info.c", "w") as f:
        f.write("""#include "fpga.h"

const int g_fpga_bitstream_num = 1;
const FPGA_VERSION_INFORMATION g_fpga_version_information[1] = {
    { "%s", FPGA_BITSTREAM_HF },
};
""" % ver)
    print(f"fpga_version_info.c: \"{ver}\"")


if __name__ == "__main__":
    main()
