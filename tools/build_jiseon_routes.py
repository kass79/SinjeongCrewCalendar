"""지선 행로표 사진 -> assets/routes webp. (rotate_jiseon.py + slice_jiseon_day.py 대체)

각 사진: EXIF 세움 -> 기울기 보정 -> 표 분할선 검출 -> 다이아별 crop -> 긴변 2000 webp q85.
분할선은 '가로선 2개가 높이의 약 2.8% 간격으로 붙은' 쌍 패턴 중 중점이 화면 중앙에
가장 가까운 것으로 찾는다(접힌 자국에 안 속음). 위/아래 crop을 ±4% 겹치게 잘라
표 내용이 절대 잘리지 않게 한다.

좌우는 자르지 않는다. 사진마다 표가 프레임을 거의 꽉 채우고 있어서
자동 트리밍(테두리선 끝 / 종이 가장자리 둘 다 시도)이 주행계 열을 잘라먹었다.

사용법:  python build_jiseon_routes.py [--dump] [폴더명 ...]
  --dump  : 저장하지 않고 검출 결과만 출력
  폴더명   : 생략하면 LAYOUT 전체
"""
import sys, os, glob
import numpy as np
from PIL import Image, ImageOps, ImageFilter

sys.stdout.reconfigure(encoding='utf-8')

SRC = r"C:\Users\admin\Downloads\07_프로젝트\지선행로표"
DST = r"C:\Users\admin\Downloads\07_프로젝트\SinjeongCrewCalendar\app\src\main\assets\routes"
WORK_W, LONG_SIDE, QUALITY, OVERLAP = 900, 2000, 85, 0.04

# 폴더 -> (에셋 접두사, 사진별 다이아 번호). 20장 전부 육안 판독한 결과.
#
# 야간 4종 라벨(평평/평휴/휴평/휴휴)을 전수 대조한 결과 실질은 3종:
#   평평 == 평휴  (시각·인계편성 5개 다이아 전부 동일) -> bnwd
#   휴평 != 휴휴  (시각은 동일, 다이아 10/12/13의 '종료 편성 지N'이 다름)
# 그래서 휴평만 새 접두사 bnhp로 추가한다.
LAYOUT = {
    '지평일': ('bwd',   [(1, 2), (3, 4), (5, 6), (7, 8)]),
    '지휴일': ('bhol',  [(1, 2), (3, 4), (5, 6), (7, 8)]),
    '지펑평': ('bnwd',  [(10, 11), (12, 13), (14,)]),   # 헤더 '평평' = 당일 평일 야간
    '지휴휴': ('bnhol', [(10, 11), (12, 13), (14,)]),   # 헤더 '휴휴' = 당일·익일 모두 휴일
    '지휴평': ('bnhp',  [(10, 11), (12, 13), (14,)]),   # 헤더 '휴평' = 당일 휴일 + 익일 평일
    '지평휴': (None,    [(10, 11), (12, 13), (14,)]),   # 헤더 '평휴' - 평평과 완전 동일(미사용)
}


def flatten(im, width=WORK_W):
    g = ImageOps.grayscale(im)
    g = g.resize((width, int(g.height * width / g.width)), Image.LANCZOS)
    a = np.asarray(g, dtype=np.float32)
    bg = np.asarray(g.filter(ImageFilter.GaussianBlur(25)), dtype=np.float32)
    return a / np.maximum(bg, 1.0)


def rowproj(dark):
    W = dark.shape[1]
    return dark[:, int(W * .30):int(W * .70)].mean(axis=1)


def find_skew(dark):
    img = Image.fromarray((dark * 255).astype(np.uint8))
    best, best_a = -1.0, 0.0
    for a in np.arange(-4.0, 4.01, 0.25):
        r = np.asarray(img.rotate(a, Image.BILINEAR, fillcolor=0), dtype=np.float32) / 255.0
        s = float((rowproj(r) ** 2).sum())
        if s > best:
            best, best_a = s, float(a)
    return best_a


def runs(mask, min_len=1):
    idx = np.flatnonzero(mask)
    if idx.size == 0:
        return []
    brk = np.flatnonzero(np.diff(idx) > 1)
    starts, ends = np.r_[idx[0], idx[brk + 1]], np.r_[idx[brk], idx[-1]] + 1
    return [(int(s), int(e)) for s, e in zip(starts, ends) if e - s >= min_len]


def measure(path):
    """반환: (세운 이미지, 기울기, 분할선 0~1 또는 None, 표하단 0~1 또는 None)

    분할선: 두 표 사이는 '가로선 2개(위 표 아래 테두리 + 아래 표 헤더 구분선)'가
    높이의 약 2.8% 간격으로 붙어 있다. 이 쌍 패턴 중 중점이 화면 중앙에
    가장 가까운 것을 고른다 -> 접힌 자국 같은 잡음 선에 속지 않는다.
    """
    im = ImageOps.exif_transpose(Image.open(path))
    dark = flatten(im) < 0.86
    ang = find_skew(dark)
    if abs(ang) > 0.1:
        d = np.asarray(Image.fromarray((dark * 255).astype(np.uint8))
                       .rotate(ang, Image.BILINEAR, fillcolor=0)) > 127
    else:
        d = dark
    H = d.shape[0]
    rp = rowproj(d)
    lines = [(s + e) // 2 for s, e in runs(rp > 0.45)]
    pairs = sorted((abs((a + b) / 2 - H * .5), a) for i, a in enumerate(lines)
                   for b in lines[i + 1:] if H * .020 <= b - a <= H * .042)
    split = pairs[0][1] / H if pairs else None
    # 표가 1개인 사진: 중앙~하단 구간의 마지막 가로선 = 표 아래 테두리
    tail = [y for y in lines if H * .35 <= y <= H * .72]
    bottom = tail[-1] / H if tail else None
    return im, ang, split, bottom


def upright(im, ang):
    return im.rotate(ang, Image.BICUBIC, expand=False, fillcolor=(255, 255, 255)) if abs(ang) > 0.1 else im


def save(im, box, out):
    c = im.crop(box)
    k = LONG_SIDE / max(c.size)
    c = c.resize((round(c.width * k), round(c.height * k)), Image.LANCZOS)
    c.save(out, 'WEBP', quality=QUALITY, method=6)
    return c.size


if __name__ == '__main__':
    dump = '--dump' in sys.argv
    only = [a for a in sys.argv[1:] if not a.startswith('--')]
    rows = []
    for folder, (prefix, layout) in LAYOUT.items():
        if only and folder not in only:
            continue
        files = sorted(glob.glob(os.path.join(SRC, folder, '*.jpg')))
        assert len(files) == len(layout), f"{folder}: {len(files)} files vs {len(layout)} layout"
        for f, dias in zip(files, layout):
            im, ang, split, bottom = measure(f)
            if dump:
                print(f"{folder}/{os.path.basename(f)} dias={dias} skew={ang:+.2f} "
                      f"split={split and round(split,3)} bot={bottom and round(bottom,3)}")
                continue
            im = upright(im, ang)
            W, H = im.size
            # 좌우는 자르지 않는다: 사진마다 표가 프레임을 거의 꽉 채워
            # 자동 검출로 트리밍하면 주행계 열이 잘릴 위험이 실제로 있었다.
            if len(dias) == 2:
                assert split and 0.44 <= split <= 0.58, f"{f}: 분할선 이상 {split}"
                y = int(split * H); ov = int(H * OVERLAP)
                boxes = [(0, 0, W, min(H, y + ov)), (0, max(0, y - ov), W, H)]
            else:
                assert bottom, f"{f}: 표 하단선 미검출"
                boxes = [(0, 0, W, min(H, int((bottom + .03) * H)))]
            for dia, box in zip(dias, boxes):
                out = os.path.join(DST, f"{prefix}_{dia}.webp")
                sz = save(im, box, out)
                rows.append((os.path.basename(out), folder, os.path.basename(f), sz,
                             os.path.getsize(out) // 1024))
                print(f"  {os.path.basename(out):<14} <- {folder}/{os.path.basename(f)} {sz} {rows[-1][4]}KB")
    if rows:
        print(f"\n총 {len(rows)}개 저장")
