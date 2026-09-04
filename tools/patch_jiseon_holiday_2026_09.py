# -*- coding: utf-8 -*-
"""신정지선 휴일 주간 다이아 1·3·6 행로표 그림 개정 (2026-09 안내문).

원본 PDF가 없어 기존 벡터 렌더(webp)를 바탕으로 **바뀐 줄과 통계칸만** 다시 조립한다.
글자는 같은 세 그림 안의 글리프를 잘라 재사용하므로 글꼴이 원본과 픽셀 단위로 같다.

  다이아 1 후반시작  5586(12:55) -> 5581(12:41, 양천구청 교대)
  다이아 3 후반시작  5610(14:55) -> 5605(14:41, 양천구청 교대)
  다이아 6 전반종료  5586(12:55) -> 5581(12:41, 양천구청 교대)

원본은 항상 BASE_REV 에서 읽으므로 몇 번 돌려도 결과가 같다(멱등).

  py tools/patch_jiseon_holiday_2026_09.py            # 에셋 갱신 + 비교 PNG
  py tools/patch_jiseon_holiday_2026_09.py --check    # 결과 자체검사만
"""
import io
import os
import subprocess
import sys

import numpy as np
from PIL import Image

REPO = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
ROUTES = os.path.join(REPO, 'app', 'src', 'main', 'assets', 'routes')
PREVIEW = r'C:\Users\admin\Downloads\_미리보기_v1.6.86'

# 현재 행로표 webp(벡터 PDF 렌더)가 들어온 커밋. HEAD 를 쓰면 이 스크립트 커밋 뒤
# 재실행 시 '고친 그림'을 원본으로 읽게 되므로 고정 리비전을 쓴다.
BASE_REV = '75a197e'

QUALITY, METHOD = 85, 6

# ── 실측 상수 (bhol_1/3/6 공통, 2000x1420~1421) ───────────────────────────────
COL_L, COL_M, COL_R = 505, 828, 1151   # 가로선 끝 x: 신도림 / 양천구청 / 까치산
ZONE_L = (500, 512)                    # 세로 연결선이 사는 x 구간 (왼쪽 / 오른쪽)
ZONE_R = (1144, 1157)
BODY_X = (500, 1160)                   # 가로선 밴드 x 구간(파랑 시각 바깥)
TEXT_X = (400, 1260)                   # 본문 텍스트 밴드 x 구간
SLOT_PITCH = 52.333                    # 줄 간격(22칸 격자, 199 ~ 1298)
LINE_AA = (-1, 3)                      # 가로선 세로 두께: y-1(AA) y y+1 y+2(AA)
TEXT_DROP = 8                          # 글자 아래끝 = 가로선 y - 8
END_DROP = 45                          # 블록 끝 시각 아래끝 = 가로선 y + 45

# 5자리 파랑 시각의 ink 시작 x (열별) / 빨강 열번의 ink 시작 x (선 구간별)
BLUE_X = {'L': 412, 'M_left': 733, 'M_right': 841, 'R': 1164}
RED_X = {'LM': 624, 'LR': 786, 'MR': 947}
BLUE_W, RED_W = 97, 103                # 라벨 crop 폭 (ink x0 - 6 부터)
LBL_UP, LBL_DN = 36, 5                 # 라벨 crop 세로 (ink 아래끝 기준)


def sh(*args):
    return subprocess.run(args, capture_output=True, check=True).stdout


def load(name):
    """BASE_REV 시점의 원본 webp 를 RGB ndarray 로."""
    blob = sh('git', '-C', REPO, 'show',
              '%s:app/src/main/assets/routes/%s.webp' % (BASE_REV, name))
    return np.asarray(Image.open(io.BytesIO(blob)).convert('RGB')).copy()


# ── 원시 편집기 ──────────────────────────────────────────────────────────────
def erase(img, x0, y0, x1, y1):
    img[y0:y1, x0:x1] = 255


def blit(dst, src, box, at):
    """src[box] 를 dst 의 at 위치에 어둡게(min) 합성. 흰 여백이 이웃을 덮지 않는다."""
    x0, y0, x1, y1 = box
    x, y = at
    p = src[y0:y1, x0:x1]
    r = dst[y:y + p.shape[0], x:x + p.shape[1]]
    np.minimum(r, p, out=r)


def lbl_box(x0, bottom, w):
    return (x0 - 6, bottom - LBL_UP, x0 - 6 + w, bottom + LBL_DN)


def move_label(dst, src, sx0, sbot, tx0, tbot, w):
    """한 라벨(시각/열번)을 통째로 옮겨 찍는다."""
    blit(dst, src, lbl_box(sx0, sbot, w), (tx0 - 6, tbot - LBL_UP))


def blue(dst, src, sx0, sbot, tx0, tbot):
    move_label(dst, src, sx0, sbot, tx0, tbot, BLUE_W)


def red(dst, src, sx0, sbot, tx0, tbot):
    move_label(dst, src, sx0, sbot, tx0, tbot, RED_W)


# 5자리 시각의 글리프 칸(ink x0 기준 상대 x): 0-10 | 19-34 | 39-44(:) | 48-66 | 69-86
CELL5_CUT = 67          # 5번째 글리프 앞 여백
CELL5 = (68, 84)        # 5번째 글리프를 담는 상대 구간


def compose_last1(src_base, bx0, bbot, src_one, ox0, obot):
    """'..:.6' 같은 라벨의 끝자리를 '1' 로 갈아 끼운 crop 을 만든다."""
    out = src_base[bbot - LBL_UP:bbot + LBL_DN, bx0 - 6:bx0 - 6 + BLUE_W].copy()
    out[:, 6 + CELL5_CUT:] = 255
    one = src_one[obot - LBL_UP:obot + LBL_DN,
                  ox0 - 6 + 6 + CELL5[0]:ox0 - 6 + 6 + CELL5[1]]
    r = out[:, 6 + CELL5[0]:6 + CELL5[1]]
    np.minimum(r, one, out=r)
    return out


def put(dst, crop, tx0, tbot):
    r = dst[tbot - LBL_UP:tbot - LBL_UP + crop.shape[0],
            tx0 - 6:tx0 - 6 + crop.shape[1]]
    np.minimum(r, crop, out=r)


def hband(dst, src, sy, ty, x=BODY_X):
    """가로선 한 줄(밴드 y-3..y+6)을 옮긴다."""
    blit(dst, src, (x[0], sy - 3, x[1], sy + 6), (x[0], ty - 3))


def text_band(dst, src, sline, tline):
    """한 줄의 글자 밴드(선 기준 -44..-3)를 통째로 옮긴다."""
    blit(dst, src, (TEXT_X[0], sline - 44, TEXT_X[1], sline - 3),
         (TEXT_X[0], tline - 44))


def guard(src, rects):
    """지우기 사각형이 표 테두리(길이 900 이상 검은 런)를 건드리지 않는지."""
    d = src.mean(2) < 140
    hb = np.zeros_like(d)
    long_h = np.array([np.convolve(row, np.ones(900), 'same') >= 900 for row in d])
    long_v = np.array([np.convolve(col, np.ones(900), 'same') >= 900 for col in d.T]).T
    hb |= long_h & d
    hb |= long_v & d
    for x0, y0, x1, y1 in rects:
        assert not hb[y0:y1, x0:x1].any(), '지우기 영역이 표 테두리와 겹침: %s' % (
            (x0, y0, x1, y1),)


# ── 다이아 1 : 후반 블록 전체 재조립 + 통계 ──────────────────────────────────
def patch_1(src1, src3, src6):
    im = src1.copy()
    rects = [(395, 730, 1265, 1398),          # 후반 블록
             (1874, 461, 1902, 510),          # 계 끝자리
             (1874, 623, 1902, 671)]          # 운전 끝자리
    guard(src1, rects)
    for r in rects:
        erase(im, *r)

    # 아트워크: 전반 slot0~7 (선 199~565) 의 선/연결선 구조가 후반 slot13~20 과 동일(+680)
    for zx0, zx1 in (ZONE_L, ZONE_R):
        blit(im, src1, (zx0, 196, zx1, 614), (zx0, 876))
    for sy, ty in zip([199, 251, 303, 356, 408, 460, 513, 565],
                      [879, 931, 983, 1036, 1088, 1140, 1193, 1245]):
        hband(im, src1, sy, ty)
    # 마지막 줄(까치산->양천구청)은 bhol_3 전반 마지막 줄과 같은 모양
    blit(im, src3, (820, 666, 1160, 675), (820, 1294))

    # 1줄: 신도림 12:46 · 5581 · 양천구청 12:41
    blue(im, src6, BLUE_X['L'], 662, BLUE_X['L'], 871)             # 12:46
    red(im, src6, RED_X['LR'], 662, RED_X['LM'], 871)              # 5581
    put(im, compose_last1(src6, BLUE_X['L'], 662, src1, BLUE_X['R'], 871),
        BLUE_X['M_right'], 871)                                    # 12:41
    # 2줄: 신도림 12:50 · 5586 · 까치산 13:01
    blue(im, src6, BLUE_X['L'], 715, BLUE_X['L'], 923)             # 12:50
    red(im, src1, RED_X['MR'], 871, RED_X['LR'], 923)              # 5586
    blue(im, src1, BLUE_X['R'], 871, BLUE_X['R'], 923)             # 13:01
    # 3~8줄: 기존 2~7줄을 한 칸 아래로 (픽셀 그대로)
    for sline, tline in zip([931, 983, 1036, 1088, 1140, 1193],
                            [983, 1036, 1088, 1140, 1193, 1245]):
        text_band(im, src1, sline, tline)
    # 9줄: 까치산 14:35 · 5605 · 양천구청에서 끝(14:41)
    red(im, src1, RED_X['LR'], 1237, RED_X['MR'], 1289)            # 5605
    blue(im, src1, BLUE_X['R'], 1237, BLUE_X['R'], 1289)           # 14:35
    put(im, compose_last1(src1, BLUE_X['L'], 1237, src1, BLUE_X['R'], 871),
        BLUE_X['M_left'], 1297 + END_DROP)                         # 14:41

    # 통계: 계 7:32->7:31, 운전 4:12->4:11 (운전칸 B의 '1' 을 끝자리로)
    blit(im, src1, (1848, 623, 1874, 671), (1873, 461))
    blit(im, src1, (1848, 623, 1874, 671), (1873, 623))
    return im


# ── 다이아 3 : 후반 블록에 한 줄 추가(첫 줄) + 통계 ─────────────────────────
def patch_3(src1, src3, src6):
    im = src3.copy()
    rects = [(395, 788, 1265, 896),           # 새 1줄 + 기존 1줄
             (1740, 122, 1975, 178),          # 주행 계
             (1805, 309, 1910, 359),          # 후반 km
             (1806, 461, 1902, 510),          # 계
             (1806, 622, 1902, 671)]          # 운전
    guard(src3, rects)
    for r in rects:
        erase(im, *r)

    # 아트워크: bhol_1 전반 slot0(L-M)+slot1(L-R) 구조를 그대로 +627
    for zx0, zx1 in (ZONE_L, ZONE_R):
        blit(im, src1, (zx0, 196, zx1, 272), (zx0, 823))
    hband(im, src1, 199, 826)          # 새 1줄 (신도림~양천구청)
    hband(im, src1, 251, 878)          # 2줄 (신도림~까치산) — 원래 양천구청~까치산이었음

    # 1줄: 신도림 14:46 · 5605 · 양천구청 14:41
    blue(im, src1, BLUE_X['L'], 1237, BLUE_X['L'], 818)            # 14:46
    red(im, src1, RED_X['LR'], 1237, RED_X['LM'], 818)             # 5605
    put(im, compose_last1(src1, BLUE_X['L'], 1237, src1, BLUE_X['R'], 871),
        BLUE_X['M_right'], 818)                                    # 14:41
    # 2줄: 신도림 14:50 · 5610 · 까치산 15:01
    blue(im, src1, BLUE_X['L'], 1290, BLUE_X['L'], 871)            # 14:50
    red(im, src3, RED_X['MR'], 871, RED_X['LR'], 871)              # 5610
    blue(im, src3, BLUE_X['R'], 871, BLUE_X['R'], 871)             # 15:01

    # 통계
    blit(im, src1, (1740, 122, 1975, 178), (1740, 122))            # 96 -> 101.4 Km
    blit(im, src1, (1550, 309, 1655, 359), (1805, 309))            # 후반 48 -> 53.4
    blit(im, src3, (1806, 941, 1902, 990), (1806, 461))            # 계 <- 4:00
    erase(im, 1806, 461, 1841, 510)
    blit(im, src6, (1806, 462, 1841, 511), (1806, 461))            # 계 9:00
    blit(im, src3, (1806, 941, 1902, 990), (1806, 622))            # 운전 <- 4:00
    erase(im, 1848, 622, 1874, 671)
    blit(im, src1, (1848, 623, 1874, 671), (1848, 623))            # 운전 4:10
    return im


# ── 다이아 6 : 전반 마지막 두 줄을 한 줄로 + 통계 ───────────────────────────
def patch_6(src1, src3, src6):
    im = src6.copy()
    rects = [(395, 622, 1265, 800),           # 전반 마지막 두 줄
             (1740, 122, 1975, 180),          # 주행 계
             (1550, 309, 1655, 359),          # 전반 km
             (1848, 462, 1874, 511), (1874, 462, 1902, 511),   # 계 B/C
             (1848, 623, 1874, 673), (1874, 623, 1902, 673),   # 운전 B/C
             (1806, 941, 1902, 991)]          # 대기
    guard(src6, rects)
    for r in rects:
        erase(im, *r)

    # 아트워크: 오른쪽 연결선(9줄->10줄)은 bhol_1 의 같은 모양 연결선에서, 가로선은 자기 첫 줄에서
    blit(im, src1, (ZONE_R[0], 569, ZONE_R[1], 623), (ZONE_R[0], 622))
    blit(im, src6, (820, 196, 1144, 205), (820, 667))

    # 10줄: 까치산 12:35 · 5581 · 양천구청에서 끝(12:41)
    red(im, src6, RED_X['LR'], 662, RED_X['MR'], 662)              # 5581
    blue(im, src6, BLUE_X['R'], 662, BLUE_X['R'], 662)             # 12:35
    put(im, compose_last1(src6, BLUE_X['L'], 662, src1, BLUE_X['R'], 871),
        BLUE_X['M_left'], 670 + END_DROP)                          # 12:41

    # 통계 — 주행 계 108 -> 102.6 (bhol_1 의 '101.4 Km' 를 깔고 3·5번째 자리 교체)
    blit(im, src1, (1740, 122, 1975, 178), (1740, 122))
    erase(im, 1810, 122, 1836, 178)
    blit(im, src1, (1874, 461, 1902, 510), (1810, 126))            # '2'
    erase(im, 1844, 122, 1872, 178)
    blit(im, src6, (1570, 309, 1604, 359), (1838, 125))            # '6'
    # 전반 60 -> 54.6 (bhol_1 의 '53.4' 를 깔고 2·4번째 자리 교체)
    blit(im, src1, (1550, 309, 1655, 359), (1550, 309))
    erase(im, 1583, 309, 1612, 359)
    blit(im, src1, (1844, 126, 1874, 175), (1583, 309))            # '4'
    erase(im, 1618, 309, 1648, 359)
    blit(im, src6, (1570, 309, 1604, 359), (1612, 308))            # '6'
    # 계 9:19 -> 9:06 / 운전 4:30 -> 4:16 / 대기 3:59 -> 4:00
    blit(im, src6, (1848, 1068, 1874, 1117), (1848, 462))          # 계 B '0'
    blit(im, src6, (1570, 309, 1604, 359), (1867, 461))            # 계 C '6'
    blit(im, src6, (1848, 462, 1874, 511), (1848, 624))            # 운전 B '1'
    blit(im, src6, (1570, 309, 1604, 359), (1867, 623))            # 운전 C '6'
    blit(im, src3, (1806, 941, 1902, 990), (1806, 942))            # 대기 4:00
    return im


# ── 자체검사 ────────────────────────────────────────────────────────────────
def runs(v, gap=0):
    out, s, prev = [], None, None
    for i, x in enumerate(v):
        if x:
            if s is None:
                s = i
            prev = i
        elif s is not None and i - prev > gap:
            out.append((s, prev))
            s = None
    return out + ([(s, prev)] if s is not None else [])


def structure(img):
    """(가로선 [(y, x0, x1)], 파랑 라벨 [(y0,y1,x0,x1)], 빨강 라벨 [...])"""
    a = img.astype(int)
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    blu = (b > 100) & (b - r > 60) & (b - g > 60)
    red_ = (r > 100) & (r - g > 60) & (r - b > 60)
    dark = (r < 140) & (g < 140) & (b < 140) & ~blu & ~red_
    lines = []
    for y in range(120, img.shape[0] - 20):
        for x0, x1 in runs(dark[y, 200:1460], gap=3):
            if 100 < x1 - x0 < 1200 and not (lines and lines[-1][0] >= y - 2):
                lines.append((y, x0 + 200, x1 + 200))
    out = []
    for m in (blu, red_):
        got = []
        sub = m[:, 190:1465]
        for y0, y1 in runs(sub.sum(1) > 0, gap=1):
            cols = sub[y0:y1 + 1].sum(0) > 0
            for x0, x1 in runs(cols, gap=12):
                got.append((y0, y1, x0 + 190, x1 + 190))
        out.append(got)
    return lines, out[0], out[1]


EXPECT = {
    # 이름: (바뀐 블록의 (선y, x0, x1) 목록, 그 블록 라벨 ink x0 목록)
    'bhol_1': [(879, 505, 828), (931, 505, 1151), (983, 505, 1151), (1036, 505, 1151),
               (1088, 505, 1151), (1140, 505, 1151), (1193, 505, 1151),
               (1245, 505, 1151), (1297, 828, 1151)],
    'bhol_3': [(826, 505, 828), (878, 505, 1151), (931, 505, 1151), (983, 505, 1151),
               (1035, 505, 1151), (1087, 505, 1151), (1140, 505, 1151),
               (1192, 505, 1151), (1244, 505, 1151), (1297, 505, 828)],
    'bhol_6': [(199, 828, 1151), (251, 505, 1151), (304, 505, 1151), (356, 505, 1151),
               (408, 505, 1151), (461, 505, 1151), (513, 505, 1151), (565, 505, 1151),
               (618, 505, 1151), (670, 828, 1151)],
}


def check(name, img):
    lines, blues, reds = structure(img)
    want = EXPECT[name]
    got = [l for l in lines if want[0][0] - 2 <= l[0] <= want[-1][0] + 2]
    assert len(got) == len(want), '%s: 줄 수 %d (기대 %d)' % (name, len(got), len(want))
    for (gy, gx0, gx1), (wy, wx0, wx1) in zip(got, want):
        assert abs(gy - wy) <= 2 and abs(gx0 - wx0) <= 2 and abs(gx1 - wx1) <= 2, \
            '%s: 선 %s (기대 %s)' % (name, (gy, gx0, gx1), (wy, wx0, wx1))
    ok = set(BLUE_X.values())
    for y0, y1, x0, x1 in blues:
        if want[0][0] - 60 <= y1 <= want[-1][0] + 60 and x0 < 1300:
            assert min(abs(x0 - v) for v in ok) <= 5, '%s: 파랑 라벨 x0=%d 이상' % (name, x0)
    for y0, y1, x0, x1 in reds:
        if want[0][0] - 60 <= y1 <= want[-1][0] + 60:
            assert min(abs(x0 - v) for v in RED_X.values()) <= 3, \
                '%s: 빨강 열번 x0=%d 이상' % (name, x0)
    return len(got), len(blues), len(reds)


# ── 비교 PNG ────────────────────────────────────────────────────────────────
CROP = {'bhol_1': (330, 780, 1310, 1370), 'bhol_3': (330, 750, 1310, 1000),
        'bhol_6': (330, 555, 1310, 790)}
LABEL = {'bhol_1': '지1', 'bhol_3': '지3', 'bhol_6': '지6'}


def previews(before, after):
    if not os.path.isdir(PREVIEW):
        os.makedirs(PREVIEW)
    for name in before:
        a, b = Image.fromarray(before[name]), Image.fromarray(after[name])
        w, h = a.size
        pair = Image.new('RGB', (w * 2 + 24, h), (200, 200, 200))
        pair.paste(a, (0, 0))
        pair.paste(b, (w + 24, 0))
        pair.save(os.path.join(PREVIEW, '행로표_%s_전후.png' % LABEL[name]))
        box = CROP[name]
        ca, cb = a.crop(box), b.crop(box)
        cw, ch = ca.size
        zoom = Image.new('RGB', (cw * 2, ch * 4 + 24), (200, 200, 200))
        zoom.paste(ca.resize((cw * 2, ch * 2), Image.NEAREST), (0, 0))
        zoom.paste(cb.resize((cw * 2, ch * 2), Image.NEAREST), (0, ch * 2 + 24))
        zoom.save(os.path.join(PREVIEW, '행로표_%s_변경부_2배.png' % LABEL[name]))
    print('비교 PNG: %s' % PREVIEW)


def main():
    src1, src3, src6 = load('bhol_1'), load('bhol_3'), load('bhol_6')
    out = {'bhol_1': patch_1(src1, src3, src6),
           'bhol_3': patch_3(src1, src3, src6),
           'bhol_6': patch_6(src1, src3, src6)}
    before = {'bhol_1': src1, 'bhol_3': src3, 'bhol_6': src6}
    for name, im in out.items():
        assert im.shape == before[name].shape, name
        print('%-7s %s' % (name, check(name, im)))
    if '--check' in sys.argv:
        return
    for name, im in out.items():
        path = os.path.join(ROUTES, '%s.webp' % name)
        old = os.path.getsize(path)
        Image.fromarray(im).save(path, 'WEBP', quality=QUALITY, method=METHOD)
        new = os.path.getsize(path)
        print('%-7s %d -> %d bytes (%+.1f%%)' % (name, old, new, (new - old) * 100.0 / old))
    previews(before, out)


if __name__ == '__main__':
    main()
