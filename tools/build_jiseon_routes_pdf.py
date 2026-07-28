# -*- coding: utf-8 -*-
"""지선(신정지선) 행로표 에셋을 PDF 원본에서 생성.

기존 tools/build_jiseon_routes.py 는 사진 원본용. 이 스크립트는 벡터 PDF 원본용이며
페이지에서 근무표 테이블(다이아 1개)을 자동 검출해 webp 로 잘라 낸다.

  python tools/build_jiseon_routes_pdf.py          # 에셋 생성
  python tools/build_jiseon_routes_pdf.py --check  # 평평/평휴 동일성 + 자체 검사만

요구: PyMuPDF(fitz), Pillow
"""
import io
import os
import sys

from collections import Counter

import fitz
from PIL import Image, ImageOps

SRC = r'C:\Users\admin\Documents\카카오톡 받은 파일\신정지선 근무표'
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                   '..', 'app', 'src', 'main', 'assets', 'routes')

# 에셋 prefix -> (PDF 파일명, 기대 헤더표기, 기대 다이아 번호)
# bnhol(휴일 야간)은 '휴휴' 표가 존재하지 않아 사용자 확인에 따라 '휴평' 표를 사용한다.
MAP = [
    ('bwd',   '신정지선 평일 주간.pdf', '평일', range(1, 9)),
    ('bhol',  '신정지선 휴일 주간.pdf', '휴일', range(1, 9)),
    ('bnwd',  '신정지선 평평 야간.pdf', '평평', range(10, 15)),
    ('bnhol', '신정지선 휴평 야간.pdf', '휴평', range(10, 15)),
]

PAD = 14          # px, 잘라낸 뒤 흰 여백 (표 바로 위/아래 다른 다이아가 붙어 있어 pt 여백은 못 씀)
LONG_EDGE = 2000  # px, 기존 사진 에셋과 동일
QUALITY = 85


def tables(page):
    """페이지의 근무표 테이블 외곽 사각형 목록 (위->아래). 테두리 선 두께 포함."""
    ds = page.get_drawings()
    horiz = [d['rect'] for d in ds if d['rect'].height < 2 and d['rect'].width > 400]
    vert = [d['rect'] for d in ds if d['rect'].width < 2 and d['rect'].height > 50]
    if not horiz or not vert:
        return []
    x0 = min([r.x0 for r in horiz] + [r.x0 for r in vert])
    x1 = max([r.x1 for r in horiz] + [r.x1 for r in vert])
    # 가로 테두리선: 선 윗변 -> 선 아랫변
    hy = {}
    for r in horiz:
        k = round(r.y0, 1)
        hy[k] = max(hy.get(k, r.y1), r.y1)
    ys = sorted(hy)
    # 표 머리행: 22~23pt 아래에 또 다른 가로줄이 있는 가로줄이 표의 윗변
    tops = [y for y in ys if any(20 < y2 - y < 26 for y2 in ys)]
    # 마지막 표의 아랫변은 가로줄이 빠진 페이지가 있어 바깥 세로 테두리로 보정
    last = max(max(hy.values()), max(vert, key=lambda r: r.height).y1)
    return [fitz.Rect(x0, t, x1, hy[tops[i + 1]] if i + 1 < len(tops) else last)
            for i, t in enumerate(tops)]


def label_and_no(page, box):
    """(헤더표기, 다이아번호) — 표 왼쪽 첫 칸."""
    def txt(r):
        return ''.join(w[4] for w in page.get_text('words')
                       if fitz.Rect(w[:4]).intersects(r))
    return (txt(fitz.Rect(box.x0, box.y0 + 2, box.x0 + 44, box.y0 + 20)),
            txt(fitz.Rect(box.x0, box.y0 + 26, box.x0 + 44, box.y1 - 2)))


def scan(pdf):
    """[(page, box, 헤더표기, 다이아번호)] 전체 목록."""
    doc = fitz.open(os.path.join(SRC, pdf))
    out = []
    for page in doc:
        for box in tables(page):
            hdr, no = label_and_no(page, box)
            out.append((page, box, hdr, no))
    return doc, out


def render(page, box, long_edge=LONG_EDGE, pad=PAD):
    inner = long_edge - 2 * pad
    z = 2.0 * inner / max(box.width, box.height)   # 2배로 뽑아 LANCZOS 축소 → 글자 선명
    pix = page.get_pixmap(clip=box, matrix=fitz.Matrix(z, z), alpha=False)
    im = Image.open(io.BytesIO(pix.tobytes('png'))).convert('RGB')
    im = im.resize((inner, round(im.height * inner / im.width)), Image.LANCZOS)
    return ImageOps.expand(im, border=pad, fill=(255, 255, 255))


def words(page, box):
    return sorted(w[4] for w in page.get_text('words')
                  if box.x0 <= (w[0] + w[2]) / 2 <= box.x1
                  and box.y0 <= (w[1] + w[3]) / 2 <= box.y1)


def compare(a_pdf, b_pdf):
    """두 PDF의 같은 번호 다이아를 셀 내용(텍스트) 대조. 헤더 표기는 제외."""
    da, ta = scan(a_pdf)
    db, tb = scan(b_pdf)
    ma = {no: (p, b, h) for p, b, h, no in ta}
    mb = {no: (p, b, h) for p, b, h, no in tb}
    rows = []
    for no in sorted(set(ma) | set(mb), key=lambda s: int(s)):
        if no not in ma or no not in mb:
            rows.append((no, False, '한쪽에만 존재')); continue
        # PDF 마다 text-run 묶음이 달라 공백 기준으로 다시 쪼갠다
        na = sorted(' '.join(x for x in words(*ma[no][:2]) if x != ma[no][2]).split())
        nb = sorted(' '.join(x for x in words(*mb[no][:2]) if x != mb[no][2]).split())
        ca, cb = Counter(na), Counter(nb)
        diff = {k: (ca[k], cb[k]) for k in set(ca) | set(cb) if ca[k] != cb[k]}
        rows.append((no, na == nb, diff))
    return rows


def main():
    check = '--check' in sys.argv
    if check:
        for a, b, note in [('신정지선 평평 야간.pdf', '신정지선 평휴 야간.pdf', '동일해야 정상'),
                           ('신정지선 평평 야간.pdf', '신정지선 휴평 야간.pdf', '달라야 정상')]:
            print('== %s vs %s (%s)' % (a[5:7], b[5:7], note))
            for no, same, diff in compare(a, b):
                print('  다이아 %-3s 동일=%-5s %s' % (no, same, diff if not same else ''))
        # 자체 검사: 모든 PDF 에서 표가 기대한 개수/번호로 잡히는지
        for prefix, pdf, hdr_exp, nums in MAP:
            doc, found = scan(pdf)
            got = sorted((no for _, _, _, no in found), key=int)
            assert got == [str(n) for n in nums], '%s: %s' % (pdf, got)
            assert all(h == hdr_exp for _, _, h, _ in found), pdf
            doc.close()
        print('OK: 표 검출/번호/헤더 표기 전부 기대값과 일치')
        return

    outdir = os.path.normpath(OUT)
    for prefix, pdf, hdr_exp, nums in MAP:
        doc, found = scan(pdf)
        by_no = {}
        for page, box, hdr, no in found:
            assert hdr == hdr_exp, '%s: 헤더 표기 %r (기대 %r)' % (pdf, hdr, hdr_exp)
            assert no not in by_no, '%s: 다이아 %s 중복' % (pdf, no)
            by_no[no] = (page, box)
        want = {str(n) for n in nums}
        assert set(by_no) == want, '%s: 다이아 %s (기대 %s)' % (pdf, sorted(by_no), sorted(want))
        for n in nums:
            im = render(*by_no[str(n)])
            path = os.path.join(outdir, '%s_%d.webp' % (prefix, n))
            im.save(path, 'WEBP', quality=QUALITY, method=6)
            print('%-14s %s  %dx%d  %d KB' % (pdf, os.path.basename(path),
                                              im.width, im.height,
                                              os.path.getsize(path) // 1024))
        doc.close()


if __name__ == '__main__':
    main()
