# -*- coding: utf-8 -*-
"""assets/routes 의 행로표 webp 바깥 여백 트리밍.

인라인 행로표는 폭 100%(FillWidth)로 그려지므로 이미지 안의 좌우 흰 여백만큼
표가 그대로 작아진다. 내용 바운딩박스 + PAD 만 남기고 잘라 다시 저장한다.

  python tools/trim_routes.py --dry-run                # 계측만 (전/후 여백 표)
  python tools/trim_routes.py "wd_*.webp" "hol_*.webp" # 제자리 트리밍
  python tools/trim_routes.py --self-check             # 로직 자체 검사

잘림 방지: 배경색(네 모서리 최빈색)과 채널 편차 TOL 이내로 완전히 균일한 바깥
행/열만 여백으로 센다. 즉 잘려나가는 띠에는 정의상 내용 픽셀이 없다. 거기에
PAD 만큼 다시 남기므로 안티에일리어싱 끝단도 보존된다.

지선(b*)은 PDF 생성(build_jiseon_routes_pdf.py) 시 이미 표 외곽선 + 14px 로 타이트하게
잘려 있어(여백 3.3%) MIN_GAIN 에 걸려 자동으로 건너뛴다. 원본 webp 는 git 이력에 있다
(되돌리려면 git checkout).
"""
import os
import sys
import glob
import collections

import numpy as np
from PIL import Image

ROUTES = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                      '..', 'app', 'src', 'main', 'assets', 'routes')
TOL = 8        # 배경으로 볼 채널 편차
PAD = 6        # px, 내용 바깥에 남길 여백
MIN_GAIN = 20  # px, 한 변이라도 이만큼 줄지 않으면 재인코딩 안 함
QUALITY = 85


def content_box(im):
    """(l, t, r, b) 내용 바운딩박스(끝 배타) + 배경색."""
    a = np.asarray(im.convert('RGB'), dtype=np.int16)
    h, w = a.shape[:2]
    corners = [tuple(a[y, x]) for y in (0, h - 1) for x in (0, w - 1)]
    bg = collections.Counter(corners).most_common(1)[0][0]
    mask = np.abs(a - np.array(bg, dtype=np.int16)).max(axis=2) > TOL
    if not mask.any():
        return (0, 0, w, h), bg
    rows, cols = mask.any(axis=1), mask.any(axis=0)
    t, b = int(rows.argmax()), h - int(rows[::-1].argmax())
    l, r = int(cols.argmax()), w - int(cols[::-1].argmax())
    return (l, t, r, b), bg


def plan(path):
    """(현재크기, 자를박스, 제거px) — 자를 필요 없으면 자를박스 None."""
    im = Image.open(path)
    w, h = im.size
    (l, t, r, b), _ = content_box(im)
    box = (max(0, l - PAD), max(0, t - PAD), min(w, r + PAD), min(h, b + PAD))
    cut = (box[0], box[1], w - box[2], h - box[3])
    return (w, h), (box if max(cut) >= MIN_GAIN else None), cut


def waste(path):
    im = Image.open(path)
    w, h = im.size
    (l, t, r, b), _ = content_box(im)
    return 100.0 * (1 - (r - l) * (b - t) / float(w * h))


def self_check():
    """흰 배경 + 알려진 위치의 검은 사각형 -> 계측이 그 사각형을 정확히 찾는지."""
    im = Image.new('RGB', (200, 100), (255, 255, 255))
    im.paste((0, 0, 0), (30, 10, 150, 80))
    assert content_box(im)[0] == (30, 10, 150, 80), content_box(im)[0]
    # 배경이 흰색이 아니어도(엷은 색지) 동작해야 한다
    im2 = Image.new('RGB', (200, 100), (240, 248, 240))
    im2.paste((10, 10, 10), (5, 40, 60, 90))
    assert content_box(im2)[0] == (5, 40, 60, 90), content_box(im2)[0]
    # 잘리는 띠에 내용이 없음을 실제 픽셀로 재확인
    a = np.asarray(im.convert('RGB'), dtype=np.int16)
    assert np.abs(a[:10] - 255).max() <= TOL
    print('self-check OK')


def main():
    args = [a for a in sys.argv[1:] if not a.startswith('-')]
    dry = '--dry-run' in sys.argv
    if '--self-check' in sys.argv:
        return self_check()

    root = os.path.normpath(ROUTES)
    files = sorted({f for pat in (args or ['*.webp'])
                    for f in glob.glob(os.path.join(root, pat))})
    print('%-16s %-10s %-10s %-18s %6s %6s %7s' %
          ('file', 'before', 'after', 'cut L/T/R/B', 'w%전', 'w%후', 'KB'))
    done = kb0 = kb1 = 0
    for p in files:
        (w, h), box, cut = plan(p)
        before = waste(p)
        if box is None:
            print('%-16s %-10s %-10s %-18s %6.1f %6.1f  (skip)' %
                  (os.path.basename(p), '%dx%d' % (w, h), '-',
                   '/'.join(map(str, cut)), before, before))
            continue
        size0 = os.path.getsize(p)
        im = Image.open(p).crop(box)
        if not dry:
            im.save(p, 'WEBP', quality=QUALITY, method=6)
        size1 = os.path.getsize(p)
        after = waste(p) if not dry else None
        kb0 += size0
        kb1 += size1
        done += 1
        print('%-16s %-10s %-10s %-18s %6.1f %6s %7.1f' %
              (os.path.basename(p), '%dx%d' % (w, h), '%dx%d' % im.size,
               '/'.join(map(str, cut)), before,
               '-' if after is None else '%.1f' % after, size1 / 1024.0))
    print('\n%d개 %s  %.0f KB -> %.0f KB' %
          (done, '계획(dry-run)' if dry else '트리밍', kb0 / 1024.0, kb1 / 1024.0))


if __name__ == '__main__':
    main()
