#!/usr/bin/env bash
# 合并用户密钥库（dictionaries/）与上游默认字典，输出到 app assets。
# 规则：用户密钥排在前面（实战中命中率高的先尝试），只保留纯 hex 行，去重。
# 用法: bash tools/merge_dictionaries.sh
set -e

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
USER_DIC="$ROOT/dictionaries"
UPSTREAM_DIC="$ROOT/third_party/rrg-tmp/client/dictionaries"
OUT="$ROOT/app/src/main/assets/pm3/dictionaries"

mkdir -p "$OUT"

# merge_dic <输出文件名> <hex长度过滤: 12 表示只要12位, any 表示任意偶数长度> <输入文件...>
merge_dic() {
    local out="$1"; local mode="$2"; shift 2
    local tmp; tmp=$(mktemp)
    for f in "$@"; do
        [ -f "$f" ] && cat "$f" >> "$tmp"
    done
    awk '
    {
        line = $0
        sub(/#.*/, "", line)          # 去掉行内注释
        gsub(/[ \t\r]/, "", line)
        if (line == "") next
        line = toupper(line)
        if (line !~ /^[0-9A-F]+$/) next
        if (mode == "12" && length(line) != 12) next
        if (mode == "even" && (length(line) % 2) != 0) next
        if (length(line) < 8 || length(line) > 64) next
        if (!(line in seen)) { seen[line] = 1; print line }
    }' mode="$mode" "$tmp" > "$OUT/$out"
    rm -f "$tmp"
    echo "$out: $(wc -l < "$OUT/$out") keys"
}

# M1 卡：本地积累库 + 用户默认库 + 上游默认库（用户在前）
merge_dic mfc_default_keys.dic 12 \
    "$USER_DIC/本地积累性密码库.dic" \
    "$USER_DIC/mfc_default_keys.dic" \
    "$UPSTREAM_DIC/mfc_default_keys.dic"

# 其余各类密钥：用户版与上游版合并
for name in iclass_default_keys.dic iclass_elite_keys.dic \
            mfc_keys_bmp_sorted.dic mfc_keys_icbmp_sorted.dic mfc_keys_mrzd_sorted.dic \
            mfdes_default_keys.dic mfp_default_keys.dic mfulc_default_keys.dic \
            t55xx_default_pwds.dic; do
    merge_dic "$name" even "$USER_DIC/$name" "$UPSTREAM_DIC/$name"
done

# Hitag2 默认密码（上游没有，直接用用户版）
if [ -f "$USER_DIC/ht2_default.dic" ]; then
    merge_dic ht2_default.dic even "$USER_DIC/ht2_default.dic"
fi

echo "==> done -> $OUT"
