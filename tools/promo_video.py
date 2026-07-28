# -*- coding: utf-8 -*-
"""신정승무 캘린더 45초 세로형 홍보영상 렌더러.

python promo_video.py                    # 최종 렌더
python promo_video.py --scene 6          # 6번 씬만 렌더(빠른 확인)
python promo_video.py --stills 2,6,12,18 # 해당 초의 프레임을 PNG로 덤프
python promo_video.py --selfcheck        # 로직 자체 점검
"""
import argparse
import os
import subprocess
import sys
from PIL import Image, ImageDraw, ImageFilter, ImageFont

W, H, FPS, DUR = 1080, 1920, 30, 45.0

NAVY_T = (16, 38, 70)
NAVY_B = (7, 17, 34)
GREEN = (0, 168, 77)
BLUE = (15, 94, 168)
WHITE = (255, 255, 255)
MUTED = (159, 179, 206)

F_BD = "C:/Windows/Fonts/malgunbd.ttf"
F_RG = "C:/Windows/Fonts/malgun.ttf"
F_SL = "C:/Windows/Fonts/malgunsl.ttf"

SHOTS = os.path.join(
    os.environ.get("TEMP", "."),
    "claude", "C--Users-admin-Downloads-----",
    "0d8c5059-ac7c-490b-bc76-680d8216b3f6", "scratchpad", "shots",
)
OUT = r"C:\Users\admin\Downloads\신정승무캘린더_소개영상.mp4"

# (시작초, 길이초, [화면파일...], 제목, 부제)
SCENES = [
    (0.0, 4.0, [], "신정승무 캘린더", "신정승무사업소 승무원 전용"),
    (4.0, 5.0, ["01_login_id.png", "02_login_pin.png"], "이름·사번으로 한 번만", "다음부터는 4자리 비밀번호"),
    (9.0, 6.0, ["03_picker.png", "04_calendar_light.png"], "오늘 근무 하나만 고르면", "한 달 근무가 자동으로"),
    (15.0, 6.0, ["06_route.png", "07_route_full.png"], "날짜만 누르면 행로표", "열번·시각까지 원본 그대로"),
    (21.0, 5.0, ["08_tt_work.png", "09_tt_deadhead.png"], "근무시각표 · 편승시각표", "확대해서 바로 확인"),
    (26.0, 6.0, ["10_roster.png", "11_dial.png"], "동료 근무 확인하고", "이름 누르면 바로 전화"),
    (32.0, 5.0, ["04_calendar_light.png"], "이번 달 근무표를 카톡으로", "캡처 없이 이미지 한 장"),
    (37.0, 4.0, ["05_calendar_dark.png"], "야간 근무엔 다크 모드", "눈이 편한 어두운 화면"),
    (41.0, 4.0, [], "Google Play에서 검색", "신정승무 캘린더"),
]

# 개인정보 마스킹. 소스 확인 결과 개인 휴대전화번호가 렌더되는 화면은
# RosterScreen.kt 의 DialSheet(=11_dial) 뿐이라 하단 시트를 통째로 뭉갠다.
# 다른 화면에서 번호가 보이면 여기에 (x0,y0,x1,y1) 상대좌표를 추가하면 된다.
BLUR_RECTS = {
    "11_dial.png": [(0.0, 0.54, 1.0, 1.0)],
    "10_roster.png": [],
    "12_contacts.png": [],
}
FAKE_BUTTONS = {"11_dial.png"}  # 블러 위에 [전화][문자] 라벨을 직접 그린다

PW, PH, PX, PY = 620, 1300, 230, 400          # 폰 목업
BORDER, RAD = 14, 48
IW, IH = PW - BORDER * 2, PH - BORDER * 2

_fcache = {}


def font(path, size):
    k = (path, size)
    if k not in _fcache:
        _fcache[k] = ImageFont.truetype(path, size)
    return _fcache[k]


def fit(text, max_w, size, path):
    """max_w 안에 들어올 때까지 크기를 줄인 폰트."""
    d = ImageDraw.Draw(Image.new("RGB", (1, 1)))
    while size > 20 and d.textlength(text, font=font(path, size)) > max_w:
        size -= 2
    return font(path, size)


def ease(t):
    t = min(1.0, max(0.0, t))
    return 4 * t * t * t if t < 0.5 else 1 - (-2 * t + 2) ** 3 / 2


def fade(layer, a):
    if a >= 0.999:
        return layer
    out = layer.copy()
    out.putalpha(layer.getchannel("A").point(lambda v: int(v * a)))
    return out


def rounded_mask(size, r):
    m = Image.new("L", size, 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, size[0] - 1, size[1] - 1), r, fill=255)
    return m


def background():
    """딥네이비 세로 그라데이션 + 은은한 그린 글로우."""
    g = Image.new("RGB", (1, H))
    px = g.load()
    for y in range(H):
        k = y / (H - 1)
        px[0, y] = tuple(int(NAVY_T[i] + (NAVY_B[i] - NAVY_T[i]) * k) for i in range(3))
    bg = g.resize((W, H))
    glow = Image.new("RGB", (W, H), (0, 0, 0))
    ImageDraw.Draw(glow).ellipse((-300, -700, W + 300, 500), fill=(0, 60, 30))
    glow = glow.filter(ImageFilter.GaussianBlur(160))
    return Image.blend(bg, Image.blend(bg, glow, 0.5), 0.55)


# ---------------------------------------------------------------- 화면 로딩

def placeholder(name):
    """스크린샷이 아직 없을 때 쓰는 앱 색상 목업."""
    im = Image.new("RGB", (IW, IH), (16, 26, 44))
    d = ImageDraw.Draw(im)
    d.rectangle((0, 0, IW, 150), fill=GREEN)
    d.text((IW // 2, 96), "신정승무 캘린더", font=font(F_BD, 42), fill=WHITE, anchor="mm")
    y = 210
    for i in range(9):
        d.rounded_rectangle((40, y, IW - 40, y + 92), 18,
                            fill=(26, 40, 64) if i % 2 else (22, 34, 56))
        d.rounded_rectangle((64, y + 30, 64 + 120, y + 62), 10, fill=(40, 60, 92))
        y += 110
    cy = IH // 2
    d.rounded_rectangle((60, cy - 110, IW - 60, cy + 110), 28, fill=(9, 20, 38), outline=GREEN, width=4)
    d.text((IW // 2, cy - 30), "화면 없음", font=font(F_BD, 58), fill=WHITE, anchor="mm")
    d.text((IW // 2, cy + 40), name, font=font(F_RG, 34), fill=MUTED, anchor="mm")
    return im


def load_screen(name):
    """스크린샷을 폰 내부 크기에 비율 유지로 맞추고 개인정보를 가린다."""
    path = os.path.join(SHOTS, name)
    if not os.path.exists(path):
        return placeholder(name), False

    src = Image.open(path).convert("RGB")
    sw, sh = src.size
    for x0, y0, x1, y1 in BLUR_RECTS.get(name, []):
        box = (int(x0 * sw), int(y0 * sh), int(x1 * sw), int(y1 * sh))
        region = src.crop(box)
        r = max(14, int((box[2] - box[0]) * 0.045))
        src.paste(region.filter(ImageFilter.GaussianBlur(r)), box)

    k = min(IW / sw, IH / sh)
    fit_im = src.resize((max(1, int(sw * k)), max(1, int(sh * k))), Image.LANCZOS)
    im = Image.new("RGB", (IW, IH), (10, 18, 34))
    im.paste(fit_im, ((IW - fit_im.width) // 2, (IH - fit_im.height) // 2))

    if name in FAKE_BUTTONS:
        d = ImageDraw.Draw(im)
        by, bh, bw, gap = int(IH * 0.86), 84, 210, 28
        x0 = IW // 2 - bw - gap // 2
        d.rounded_rectangle((x0, by, x0 + bw, by + bh), 42, fill=GREEN)
        d.text((x0 + bw // 2, by + bh // 2), "전화", font=font(F_BD, 40), fill=WHITE, anchor="mm")
        x1 = IW // 2 + gap // 2
        d.rounded_rectangle((x1, by, x1 + bw, by + bh), 42, outline=WHITE, width=4)
        d.text((x1 + bw // 2, by + bh // 2), "문자", font=font(F_BD, 40), fill=WHITE, anchor="mm")
    return im, True


def phone(inner):
    """폰 프레임(그림자 + 흰 테두리)에 화면을 끼운 RGBA 레이어."""
    pad = 60
    lay = Image.new("RGBA", (PW + pad * 2, PH + pad * 2), (0, 0, 0, 0))
    sh = Image.new("RGBA", lay.size, (0, 0, 0, 0))
    ImageDraw.Draw(sh).rounded_rectangle(
        (pad, pad + 22, pad + PW, pad + PH + 22), RAD, fill=(0, 0, 0, 170))
    lay = Image.alpha_composite(lay, sh.filter(ImageFilter.GaussianBlur(28)))

    body = Image.new("RGBA", (PW, PH), (0, 0, 0, 0))
    ImageDraw.Draw(body).rounded_rectangle((0, 0, PW - 1, PH - 1), RAD, fill=WHITE + (255,))
    body.paste(inner, (BORDER, BORDER), rounded_mask((IW, IH), RAD - BORDER))
    lay.paste(body, (pad, pad), body)
    return lay, pad


def slide_inner(a, b, k):
    """화면 A -> B 가로 슬라이드."""
    im = Image.new("RGB", (IW, IH), (10, 18, 34))
    off = int(ease(k) * IW)
    im.paste(a, (-off, 0))
    im.paste(b, (IW - off, 0))
    return im


# ---------------------------------------------------------------- 텍스트/그래픽

def text_layer(title, sub, top):
    lay = Image.new("RGBA", (W, 300), (0, 0, 0, 0))
    d = ImageDraw.Draw(lay)
    d.text((W // 2, 62), title, font=fit(title, 920, 72 if top else 84, F_BD),
           fill=WHITE + (255,), anchor="mm")
    d.text((W // 2, 152), sub, font=fit(sub, 900, 44, F_SL),
           fill=MUTED + (255,), anchor="mm")
    return lay


def line_cap(d, x0, y, x1, w, color):
    if x1 - x0 < 1:
        return
    d.rounded_rectangle((x0, y - w // 2, x1, y + w // 2), w // 2, fill=color)


def progress(d, t):
    y, x0, x1 = 1800, 90, 990
    line_cap(d, x0, y, x1, 8, (30, 52, 84))
    line_cap(d, x0, y, x0 + int((x1 - x0) * min(1.0, t / DUR)), 8, GREEN)
    for st, _, _, _, _ in SCENES:
        cx = x0 + (x1 - x0) * (st / DUR)
        on = t >= st
        d.ellipse((cx - 10, y - 10, cx + 10, y + 10),
                  fill=GREEN if on else (18, 32, 54), outline=GREEN if on else (46, 68, 100), width=3)


def hero(d, k):
    """씬 1/9 의 노선 그래픽: 라인이 그어지고 정거장 노드가 켜진다."""
    y, x0, x1 = 760, 240, 840
    e = ease(k)
    line_cap(d, x0, y, x0 + int((x1 - x0) * e), 18, GREEN)
    for i, cx in enumerate((240, 540, 840)):
        if x0 + (x1 - x0) * e >= cx - 6:
            r = 30
            d.ellipse((cx - r, y - r, cx + r, y + r), fill=NAVY_B, outline=GREEN, width=9)
            if i == 1:
                d.ellipse((cx - 11, y - 11, cx + 11, y + 11), fill=WHITE)


def wipe(d, t):
    """씬 경계에서 초록 노선 라인이 화면을 훑고 지나간다."""
    for st, _, _, _, _ in SCENES[1:]:
        k = (t - st + 0.25) / 0.5
        if 0.0 <= k <= 1.0:
            y = int(ease(k) * (H + 60)) - 30
            d.rectangle((0, y - 3, W, y + 3), fill=GREEN)
            d.rectangle((0, y + 3, W, y + 40), fill=(0, 90, 44))


# ---------------------------------------------------------------- 렌더

def build():
    """씬별 캐시(텍스트 레이어, 폰 레이어)를 미리 만든다."""
    used, missing, cache = [], [], []
    for st, dur, files, title, sub in SCENES:
        phones = []
        for f in files:
            inner, ok = load_screen(f)
            (used if ok else missing).append(f)
            phones.append(inner)
        lay = [phone(p) for p in phones]
        cache.append(dict(st=st, dur=dur, inners=phones, phones=lay,
                          txt=text_layer(title, sub, bool(files))))
    return cache, used, missing


def frame(bg, cache, t):
    im = bg.copy()
    d = ImageDraw.Draw(im)
    progress(d, t)

    for i, sc in enumerate(cache):
        lt = t - sc["st"]
        pre = 0.0 if i == 0 else 0.35          # 앞 씬이 사라지는 동안 겹쳐서 들어온다
        if not (-pre <= lt <= sc["dur"] + 0.05):
            continue
        a_in = ease(min(1.0, max(0.0, (lt + pre) / 0.5)))
        a_out = 1.0 if i == len(cache) - 1 else ease(min(1.0, max(0.0, (sc["dur"] - lt) / 0.35)))
        a = a_in * a_out
        if a <= 0.01:
            continue
        has_phone = bool(sc["phones"])

        ty = (150 if has_phone else 900) + int((1 - a_in) * 45)
        im.paste(fade(sc["txt"], a), (0, ty), fade(sc["txt"], a))

        if has_phone:
            line_cap(d, 380, ty + 202, 380 + int(320 * ease(min(1.0, lt / 0.8)) * a), 8, GREEN)
            n = len(sc["phones"])
            sw_at, sw_len = sc["dur"] * 0.52, 0.55
            if n > 1 and sw_at <= lt < sw_at + sw_len:
                lay, pad = phone(slide_inner(sc["inners"][0], sc["inners"][1],
                                             (lt - sw_at) / sw_len))
            else:
                lay, pad = sc["phones"][-1 if n > 1 and lt >= sw_at else 0]
            py = PY - pad + int((1 - a_in) * 55)
            im.paste(fade(lay, a), (PX - pad, py), fade(lay, a))
        else:
            hero(d, min(1.0, lt / 1.2))

    wipe(d, t)
    return im


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=OUT)
    ap.add_argument("--scene", type=int, help="해당 씬만 렌더(1부터)")
    ap.add_argument("--stills", help="초 목록(쉼표), PNG로 덤프하고 종료")
    ap.add_argument("--scale", type=float, default=1.0)
    ap.add_argument("--selfcheck", action="store_true")
    a = ap.parse_args()

    if a.selfcheck:
        return selfcheck()

    cache, used, missing = build()
    print("실제 스크린샷:", sorted(set(used)) or "없음")
    print("플레이스홀더:", sorted(set(missing)) or "없음")
    bg = background()

    if a.stills:
        out_dir = os.path.join(os.path.dirname(os.path.abspath(a.out)), "_stills")
        os.makedirs(out_dir, exist_ok=True)
        for s in [float(x) for x in a.stills.split(",")]:
            p = os.path.join(out_dir, "t%05.1f.png" % s)
            frame(bg, cache, s).save(p)
            print(p)
        return 0

    t0, t1 = 0.0, DUR
    if a.scene:
        st, dur = SCENES[a.scene - 1][0], SCENES[a.scene - 1][1]
        t0, t1 = max(0.0, st - 0.5), min(DUR, st + dur + 0.5)
    n = int(round((t1 - t0) * FPS))
    ow, oh = int(W * a.scale) // 2 * 2, int(H * a.scale) // 2 * 2

    p = subprocess.Popen([
        "ffmpeg", "-y", "-loglevel", "error",
        "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", "%dx%d" % (ow, oh), "-r", str(FPS),
        "-i", "-", "-an", "-c:v", "libx264", "-preset", "medium", "-crf", "18",
        "-pix_fmt", "yuv420p", "-movflags", "+faststart", a.out], stdin=subprocess.PIPE)
    for i in range(n):
        im = frame(bg, cache, t0 + i / FPS)
        if (ow, oh) != (W, H):
            im = im.resize((ow, oh), Image.LANCZOS)
        p.stdin.write(im.tobytes())
        if i % 150 == 0:
            print("  %d/%d" % (i, n), flush=True)
    p.stdin.close()
    rc = p.wait()
    print("완료" if rc == 0 else "ffmpeg 실패 %d" % rc, a.out, "frames=%d" % n)
    return rc


def selfcheck():
    assert int(round(DUR * FPS)) == 1350
    assert SCENES[0][0] == 0.0 and SCENES[-1][0] + SCENES[-1][1] == DUR
    for i in range(len(SCENES) - 1):  # 빈틈/겹침 없이 이어지는가
        assert abs(SCENES[i][0] + SCENES[i][1] - SCENES[i + 1][0]) < 1e-9
    assert ease(0) == 0 and ease(1) == 1 and 0.49 < ease(0.5) < 0.51
    assert fit("가" * 40, 920, 72, F_BD).size < 72          # 긴 제목은 줄어든다
    assert fit("가나", 920, 72, F_BD).size == 72             # 짧으면 그대로
    ph = placeholder("x.png")
    assert ph.size == (IW, IH)
    lay, pad = phone(ph)
    assert PX - pad >= 0 and PY - pad >= 0 and PY + PH <= H - 120  # 세이프에어리어
    assert lay.size == (PW + pad * 2, PH + pad * 2)
    # 번호 노출 화면은 반드시 마스킹 설정이 존재해야 한다
    assert BLUR_RECTS["11_dial.png"] and "11_dial.png" in FAKE_BUTTONS
    print("selfcheck ok")
    return 0


if __name__ == "__main__":
    sys.exit(main())
