#!/usr/bin/env python3
"""Sinh dimen cho tablet từ res/values/dimens.xml (thiết kế điện thoại, ví dụ 360 × 800).

Chép file này thành scripts/gen_screen_dimens.py, điền CONFIG bên dưới rồi chạy:

    python3 scripts/gen_screen_dimens.py        # Windows: python scripts/gen_screen_dimens.py

Không sửa tay values-sw*/dimens.xml, values-sw*/integers.xml: sửa CONFIG rồi chạy lại.
Mọi giá trị trong values/dimens.xml phải là dp hoặc sp (số + đơn vị).

Cơ chế (xem 01_HOW_IT_WORKS.md):
- Mỗi nhóm nhân mọi dimen với `scale`.
- fixed:  giá trị chọn tay thay cho giá trị nhân scale (theo nhóm).
- PHONE:  dimen giữ nguyên giá trị điện thoại ở mọi nhóm.
- GROUPS: nhóm dimen dùng chung một hệ số riêng.
- STACKS: màn xếp dọc cố định, script cộng chiều cao và cảnh báo nếu tràn.
"""
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app/src/main/res"        # sửa nếu module khác

# ====================== CONFIG: điền cho từng app ======================

DESIGN_W = 360  # bề rộng thiết kế điện thoại (dp)

BUCKETS = {
    # Máy 7–8": 600 × 960. scale ≤ width / DESIGN_W.
    "sw600dp": {
        "width": 600,
        "usable_h": 888,    # cao máy - status bar 24 - nav bar 48
        "scale": 1.6,
        "fixed": {},        # {"ten_dimen": giá_trị_dp}: điền khi có cảnh báo "!"
        "columns": {},      # {"ten_integer": số_cột}: ví dụ {"grid_columns": 3}
    },
    # Máy 10–11": 800 × 1280
    "sw800dp": {
        "width": 800,
        "usable_h": 1208,
        "scale": 2.0,
        "fixed": {},
        "columns": {},
    },
}

# Dimen giữ nguyên giá trị điện thoại ở mọi nhóm (toạ độ dọc tuyệt đối...).
PHONE = set()

# Nhóm dimen có hệ số riêng: {"tên nhóm": {"dimens": [...], "factor": hàm(bucket_cfg) -> float}}
# Ví dụ lưới ô lấp đầy chiều ngang: hệ số = (width - 2*padding*scale) / (cột*ô + (cột-1)*khe).
# Ví dụ tab to hơn: factor = lambda b: b["scale"] * 1.13
GROUPS = {
    # "tab": {"dimens": ["tab_w", "tab_h"], "factor": lambda b: b["scale"] * 1.13},
    # "grid": {
    #     "dimens": ["item_w", "grid_gap_h", "grid_gap_v"],
    #     "factor": lambda b: (b["width"] - 2 * 16 * b["scale"])
    #                         / (b["columns"]["grid_columns"] * 160 + (b["columns"]["grid_columns"] - 1) * 8),
    # },
}

# Màn xếp dọc cố định (không cuộn, không co): {"màn": [tên dimen hoặc số dp tối thiểu, ...]}.
# Tổng chiều cao tính từ dưới status bar. Phần co giãn thì ghi số dp tối thiểu.
STACKS = {
    # "home": ["home_cards_top", "home_card_size", "home_card_gap", "home_card_size"],
    # "customize": ["content_top", "grid_gap", "bottom_panel_height", 300],
}

# Dimen tính từ dimen khác (sau khi đã nhân hệ số): hàm(values, columns) -> {tên: giá_trị_dp}
def derived(values, columns):
    return {
        # "grid_w": columns["grid_columns"] * values["item_w"] + (columns["grid_columns"] - 1) * values["grid_gap_h"],
    }

# Dimen rộng cần kiểm tra không vượt rộng màn (tên có trong `derived` hoặc dimens).
WIDTH_CHECKS = []   # ví dụ ["grid_w"]

# ======================== hết CONFIG ========================


def rnd(s):
    return round(s) if abs(s) >= 4 else round(s, 1)


def fmt(v):
    return str(int(v)) if float(v).is_integer() else str(v)


def read_phone():
    out = {}
    for el in ET.parse(RES / "values/dimens.xml").getroot().iter("dimen"):
        m = re.fullmatch(r"(-?[\d.]+)(dp|sp)", (el.text or "").strip())
        if not m:
            raise SystemExit(f"Không hiểu giá trị {el.get('name')}={el.text}")
        out[el.get("name")] = (float(m.group(1)), m.group(2))
    return out


def factors(b):
    out = {}
    for group in GROUPS.values():
        f = group["factor"](b)
        out.update(dict.fromkeys(group["dimens"], f))
    return out


def build(bucket, phone):
    b, f = BUCKETS[bucket], factors(BUCKETS[bucket])
    rows = {}
    for name, (v, unit) in phone.items():
        if name in PHONE:
            continue
        rows[name] = (b["fixed"].get(name, rnd(v * f.get(name, b["scale"]))), unit)
    values = {n: v for n, (v, _) in rows.items()}
    for n, v in derived(values, b["columns"]).items():
        rows[n] = (v, "dp")
    return rows


def check(bucket, rows):
    b, ok = BUCKETS[bucket], True
    for screen, parts in STACKS.items():
        total = sum(p if isinstance(p, (int, float)) else rows[p][0] for p in parts)
        if total > b["usable_h"]:
            ok = False
            print(f"  ! {bucket} {screen}: cao {total:.0f} > {b['usable_h']}")
    for name in WIDTH_CHECKS:
        if rows[name][0] > b["width"]:
            ok = False
            print(f"  ! {bucket} {name} {rows[name][0]} > {b['width']}")
    return ok


def write(path, header, lines):
    out = ['<?xml version="1.0" encoding="utf-8"?>', f"<!-- {header}",
           f"     FILE SINH TỰ ĐỘNG bởi scripts/{Path(__file__).name}, đừng sửa tay. -->", "<resources>"]
    out += lines + ["</resources>\n"]
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(out), encoding="utf-8")


def main():
    phone = read_phone()
    ok = True
    for bucket, b in BUCKETS.items():
        rows = build(bucket, phone)
        ok &= check(bucket, rows)
        write(RES / f"values-{bucket}/dimens.xml",
              f"Tablet >={b['width']}dp: dimen điện thoại x {b['scale']}."
              + (f" Giữ cỡ điện thoại: {', '.join(sorted(PHONE))}." if PHONE else ""),
              [f'    <dimen name="{n}">{fmt(v)}{u}</dimen>' for n, (v, u) in rows.items()])
        if b["columns"]:
            write(RES / f"values-{bucket}/integers.xml", f"Tablet >={b['width']}dp: số cột lưới.",
                  [f'    <integer name="{n}">{v}</integer>' for n, v in b["columns"].items()])
        print(f"{bucket}: {len(rows)} dimen")
    if not ok:
        sys.exit("Có màn tràn, chỉnh `fixed`, `GROUPS` hoặc `scale`.")


if __name__ == "__main__":
    main()
