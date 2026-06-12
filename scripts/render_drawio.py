#!/usr/bin/env python3
"""Render a .drawio (mxGraph XML) flux diagram to a static SVG.

The SVG is a *derived artifact* of the .drawio source -- regenerate it, never
hand-edit it (see docs HOWTO section 12). Supports the style subset used by
docs/architecture.drawio: rounded rects, pills, cylinders, text panels, and
orthogonal edges with exit/entry points, waypoints, arrowheads and labels.

Usage: python scripts/render_drawio.py docs/architecture.drawio docs/architecture.svg
"""
import sys
import xml.etree.ElementTree as ET
from xml.sax.saxutils import escape

IN = sys.argv[1] if len(sys.argv) > 1 else "docs/architecture.drawio"
OUT = sys.argv[2] if len(sys.argv) > 2 else "docs/architecture.svg"


def parse_style(s):
    d = {}
    for part in (s or "").split(";"):
        if not part:
            continue
        if "=" in part:
            k, v = part.split("=", 1)
            d[k] = v
        else:
            d[part] = True
    return d


root = ET.parse(IN).getroot()
cells = list(root.iter("mxCell"))
boxes = {}
for c in cells:
    if c.get("vertex") == "1":
        g = c.find("mxGeometry")
        if g is None or g.get("x") is None:
            continue
        boxes[c.get("id")] = dict(
            x=float(g.get("x")), y=float(g.get("y")),
            w=float(g.get("width")), h=float(g.get("height")),
            style=parse_style(c.get("style")), value=c.get("value") or "")


def fnum(s, k, default=0.5):
    v = s.get(k)
    return float(v) if v not in (None, True) else default


def boundary(bid, fx, fy):
    b = boxes[bid]
    return (b["x"] + fx * b["w"], b["y"] + fy * b["h"])


def wrap(text, width, fontsize):
    cw = fontsize * 0.55
    maxchars = max(4, int(width / cw))
    words, lines, cur = text.split(), [], ""
    for w in words:
        if cur and len(cur) + 1 + len(w) > maxchars:
            lines.append(cur)
            cur = w
        else:
            cur = (cur + " " + w).strip()
    if cur:
        lines.append(cur)
    return lines or [""]


# bounds (include waypoints) + margin
xs, ys = [], []
for b in boxes.values():
    xs += [b["x"], b["x"] + b["w"]]
    ys += [b["y"], b["y"] + b["h"]]
for c in cells:
    for p in c.findall(".//mxPoint"):
        if p.get("x"):
            xs.append(float(p.get("x")))
            ys.append(float(p.get("y")))
M = 20
minx, miny = min(xs) - M, min(ys) - M
W, H = max(xs) - minx + M, max(ys) - miny + M

svg = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{W:.0f}" height="{H:.0f}" '
       f'viewBox="{minx:.0f} {miny:.0f} {W:.0f} {H:.0f}" font-family="Helvetica,Arial,sans-serif">',
       f'<rect x="{minx:.0f}" y="{miny:.0f}" width="{W:.0f}" height="{H:.0f}" fill="#ffffff"/>',
       '<defs><marker id="arrow" markerWidth="9" markerHeight="9" refX="7" refY="3" orient="auto" '
       'markerUnits="userSpaceOnUse"><path d="M0,0 L7,3 L0,6 Z" fill="#000000"/></marker></defs>']


def text_lines(cx, cy, lines, fontsize, color, anchor="middle", bold=False):
    lh = fontsize + 3
    y0 = cy - (len(lines) - 1) * lh / 2 + fontsize / 3
    fw = ' font-weight="bold"' if bold else ""
    for i, ln in enumerate(lines):
        svg.append(f'<text x="{cx:.1f}" y="{y0 + i*lh:.1f}" font-size="{fontsize}" '
                   f'fill="{color}" text-anchor="{anchor}"{fw}>{escape(ln)}</text>')


def draw_node(b):
    s, x, y, w, h = b["style"], b["x"], b["y"], b["w"], b["h"]
    if s.get("shape") == "cylinder":
        return draw_node  # handled below
    fill = s.get("fillColor", "#ffffff")
    if fill == "none":
        fill = "none"
    stroke = s.get("strokeColor", "#000000")
    dash = ' stroke-dasharray="4 3"' if s.get("dashed") else ""
    rx = 14 if "30" in str(s.get("rounded", "")) else (8 if s.get("rounded") else 0)
    svg.append(f'<rect x="{x:.1f}" y="{y:.1f}" width="{w:.1f}" height="{h:.1f}" rx="{rx}" '
               f'fill="{fill}" stroke="{stroke}" stroke-width="1.5"{dash}/>')


# 1) mega boxes (big, behind everything) then their titles top-centered
megas = [b for b in boxes.values() if b["w"] >= 180 and b["h"] >= 150 and b["style"].get("strokeWidth") == "2"]
mega_ids = {id(b) for b in megas}
for b in megas:
    draw_node(b)
    fc = b["style"].get("fontColor", "#000000")
    text_lines(b["x"] + b["w"] / 2, b["y"] + 14, wrap(b["value"], b["w"], 13), 13, fc, bold=True)

# 2) other vertices (skip text-panel cells; draw those last)
panels = []
for b in boxes.values():
    if id(b) in mega_ids:
        continue
    if b["style"].get("text") or str(b["style"].get("text")) == "True" or "text" in b["style"] and b["style"].get("html") and b["style"].get("fillColor") in (None, "#FFFFFF", "#ffffff") and not b["style"].get("rounded"):
        # panels = title + legend (style begins with text;)
        pass
    style0 = b["style"]
    is_text = "text" in style0 and style0.get("rounded") is None and style0.get("shape") is None and style0.get("fillColor") in (None, "#FFFFFF", "#ffffff")
    if is_text:
        panels.append(b)
        continue
    s = b["style"]
    if s.get("shape") == "cylinder":
        x, y, w, h = b["x"], b["y"], b["w"], b["h"]
        fill, stroke = s.get("fillColor", "#fff"), s.get("strokeColor", "#000")
        ry = 7
        svg.append(f'<path d="M{x:.1f},{y+ry:.1f} a{w/2:.1f},{ry} 0 0,1 {w:.1f},0 l0,{h-2*ry:.1f} '
                   f'a{w/2:.1f},{ry} 0 0,1 {-w:.1f},0 Z" fill="{fill}" stroke="{stroke}" stroke-width="1.5"/>')
        svg.append(f'<path d="M{x:.1f},{y+ry:.1f} a{w/2:.1f},{ry} 0 0,0 {w:.1f},0" fill="none" stroke="{stroke}" stroke-width="1.5"/>')
        text_lines(x + w / 2, y + h / 2 + ry / 2, wrap(b["value"], w - 8, 10), 10, s.get("fontColor", "#000"))
    else:
        draw_node(b)
        fs = int(float(s.get("fontSize", 10)))
        text_lines(b["x"] + b["w"] / 2, b["y"] + b["h"] / 2, wrap(b["value"], b["w"] - 6, fs), fs, s.get("fontColor", "#000"))

# 3) edges
edge_labels = []
for c in cells:
    if c.get("edge") != "1":
        continue
    s = parse_style(c.get("style"))
    src, tgt = c.get("source"), c.get("target")
    if src not in boxes or tgt not in boxes:
        continue
    p0 = boundary(src, fnum(s, "exitX"), fnum(s, "exitY"))
    p1 = boundary(tgt, fnum(s, "entryX"), fnum(s, "entryY"))
    pts = [p0]
    g = c.find("mxGeometry")
    arr = g.find("Array") if g is not None else None
    if arr is not None:
        pts += [(float(p.get("x")), float(p.get("y"))) for p in arr.findall("mxPoint")]
    pts.append(p1)
    dash = ' stroke-dasharray="5 4"' if s.get("dashed") else ""
    d = "M" + " L".join(f"{x:.1f},{y:.1f}" for x, y in pts)
    start_marker = ' marker-start="url(#arrow)"' if s.get("startArrow") == "block" else ""
    svg.append(f'<path d="{d}" fill="none" stroke="#000000" stroke-width="1.5"{dash} '
               f'marker-end="url(#arrow)"{start_marker}/>')
    # label at path midpoint
    val = c.get("value")
    if val:
        seglens = [((pts[i+1][0]-pts[i][0])**2 + (pts[i+1][1]-pts[i][1])**2) ** 0.5 for i in range(len(pts)-1)]
        half = sum(seglens) / 2
        acc, mx, my = 0, pts[0][0], pts[0][1]
        for i, L in enumerate(seglens):
            if acc + L >= half:
                t = (half - acc) / L if L else 0
                mx = pts[i][0] + t * (pts[i+1][0]-pts[i][0])
                my = pts[i][1] + t * (pts[i+1][1]-pts[i][1])
                break
            acc += L
        edge_labels.append((mx, my, val))

# 4) edge labels (white bg, on top of lines)
for mx, my, val in edge_labels:
    fs = 9
    wpx = len(val) * fs * 0.55 + 6
    svg.append(f'<rect x="{mx-wpx/2:.1f}" y="{my-fs/2-2:.1f}" width="{wpx:.1f}" height="{fs+4}" '
               f'fill="#ffffff" opacity="0.92"/>')
    svg.append(f'<text x="{mx:.1f}" y="{my+fs/3:.1f}" font-size="{fs}" fill="#000000" text-anchor="middle">{escape(val)}</text>')

# 5) text panels (title, legend) on top
for b in panels:
    s = b["style"]
    if s.get("strokeColor") and not str(s.get("strokeColor")).startswith("#FFF"):
        dash = ' stroke-dasharray="4 3"' if s.get("dashed") else ""
        svg.append(f'<rect x="{b["x"]:.1f}" y="{b["y"]:.1f}" width="{b["w"]:.1f}" height="{b["h"]:.1f}" '
                   f'fill="{s.get("fillColor","none")}" stroke="{s.get("strokeColor")}" stroke-width="1"{dash}/>')
    fs = int(float(s.get("fontSize", 12)))
    bold = s.get("fontStyle") == "1"
    lines = []
    for raw in b["value"].replace("&#10;", "\n").split("\n"):
        lines += wrap(raw, b["w"] - 8, fs) if raw else [""]
    lh = fs + 3
    for i, ln in enumerate(lines):
        fw = ' font-weight="bold"' if (bold and i == 0) else ""
        tx = b["x"] + 6
        ty = b["y"] + fs + 4 + i * lh
        fc = s.get("fontColor", "#000")
        svg.append(f'<text x="{tx:.1f}" y="{ty:.1f}" font-size="{fs}" fill="{fc}"{fw}>{escape(ln)}</text>')

svg.append("</svg>")
with open(OUT, "w", encoding="utf-8") as f:
    f.write("\n".join(svg))
print(f"wrote {OUT}: {len(boxes)} vertices, {sum(1 for c in cells if c.get('edge')=='1')} edges")
