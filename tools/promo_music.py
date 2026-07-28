# -*- coding: utf-8 -*-
"""신정승무캘린더 홍보영상 배경음악 생성기 (원본 합성, 외부 음원 없음).

numpy로 파형을 직접 합성해 정확히 45.000초짜리 48kHz/16bit 스테레오 WAV를 만든다.
D major, 106.667 BPM (= 마디 2.25초, 20마디 = 45.000초).

    python promo_music.py            -> promo_bgm.wav 생성
    python promo_music.py --selftest -> 검증만 수행
"""
import sys
import wave
from pathlib import Path

import numpy as np

SR = 48000
BPM = 160.0 / 1.5  # 106.666... -> 마디 2.25s, 20마디 = 45.000s 정확히
BEAT = 60.0 / BPM
BAR = 4 * BEAT
BARS = 20
DUR = BARS * BAR  # 45.000
N = int(round(DUR * SR))

OUT = Path(__file__).with_name("promo_bgm.wav")

TARGET_RMS_DB = -20.0
PEAK_CEIL_DB = -1.0


# ---------------------------------------------------------------- 기본 유틸
def midi_hz(m):
    return 440.0 * 2.0 ** ((m - 69) / 12.0)


def pan_gains(p):
    """p: -1(L) .. 0(C) .. +1(R), 등파워 패닝."""
    a = (p + 1.0) * (np.pi / 4.0)
    return np.cos(a), np.sin(a)


def harm_wave(freq, n, harmonics, detune_cents=0.0):
    """알리아싱 방지: 15kHz 넘는 배음은 버린다."""
    f0 = freq * 2.0 ** (detune_cents / 1200.0)
    t = np.arange(n, dtype=np.float64) / SR
    out = np.zeros(n)
    for k, amp in harmonics:
        f = f0 * k
        if f > 15000.0:
            continue
        out += amp * np.sin(2 * np.pi * f * t + (k * 0.7))
    return out


def _taper(e, ms=8.0):
    """끝단 클릭 방지용 짧은 테이퍼."""
    k = min(int(ms * SR / 1000.0), len(e))
    if k > 1:
        e[-k:] *= 0.5 + 0.5 * np.cos(np.pi * np.arange(k) / (k - 1))
    return e


def env_adsr(n, a, d, s, r):
    e = np.zeros(n)
    na = min(int(a * SR), n)
    nr = min(int(r * SR), n - na)
    nd = min(int(d * SR), n - na - nr)
    ns = n - na - nd - nr
    i = 0
    if na:
        e[:na] = 0.5 - 0.5 * np.cos(np.pi * np.arange(na) / na)
        i = na
    if nd:
        x = np.arange(nd) / nd
        e[i:i + nd] = s + (1.0 - s) * np.exp(-4.0 * x)
        i += nd
    if ns:
        e[i:i + ns] = s
        i += ns
    if nr:
        e[i:i + nr] = s * (0.5 + 0.5 * np.cos(np.pi * np.arange(nr) / nr))
    return _taper(e)


def env_pluck(n, attack=0.004, tau=0.35):
    t = np.arange(n) / SR
    e = np.exp(-t / tau)
    na = min(int(attack * SR), n)
    if na > 1:
        e[:na] *= 0.5 - 0.5 * np.cos(np.pi * np.arange(na) / na)
    return _taper(e)


class Bus:
    def __init__(self, n):
        self.l = np.zeros(n)
        self.r = np.zeros(n)

    def add(self, t0, sig, env, amp, pan):
        i0 = int(round(t0 * SR))
        if i0 >= len(self.l):
            return
        x = sig * env * amp
        x = x[: len(self.l) - i0]
        gl, gr = pan_gains(pan)
        self.l[i0:i0 + len(x)] += x * gl
        self.r[i0:i0 + len(x)] += x * gr


# ---------------------------------------------------------------- 음색
PAD_H = [(1, 1.0), (2, 0.30), (3, 0.13), (4, 0.055), (5, 0.022)]
PLUCK_H = [(1, 1.0), (2, 0.34), (4, 0.14), (7, 0.05)]      # 마림바/플럭 느낌
BELL_H = [(1, 1.0), (2, 0.22), (3, 0.10), (5, 0.06)]
BASS_H = [(1, 1.0), (2, 0.16), (3, 0.05)]


def add_pad(bus, t0, note, dur, amp):
    """살짝 디튠한 두 겹을 좌우로 벌려 넓은 패드."""
    n = int((dur + 1.4) * SR)
    env = env_adsr(n, a=0.55, d=0.5, s=0.75, r=1.1)
    for cents, pan in ((-4.5, -0.55), (+4.5, +0.55)):
        bus.add(t0, harm_wave(midi_hz(note), n, PAD_H, cents), env, amp, pan)


def add_pluck(bus, t0, note, dur, amp, pan):
    n = int(min(dur * 3.2, 1.2) * SR)
    bus.add(t0, harm_wave(midi_hz(note), n, PLUCK_H), env_pluck(n, tau=0.30), amp, pan)


def add_bell(bus, t0, note, dur, amp, pan=0.0):
    n = int((dur + 0.9) * SR)
    bus.add(t0, harm_wave(midi_hz(note), n, BELL_H), env_pluck(n, attack=0.02, tau=0.9), amp, pan)


def add_bass(bus, t0, note, dur, amp):
    n = int((dur + 0.25) * SR)
    env = env_adsr(n, a=0.03, d=0.25, s=0.8, r=0.22)
    bus.add(t0, harm_wave(midi_hz(note), n, BASS_H), env, amp, 0.0)


def add_kick(bus, t0, amp):
    n = int(0.42 * SR)
    t = np.arange(n) / SR
    f = 46.0 + 78.0 * np.exp(-t / 0.030)          # 124Hz -> 46Hz 피치 엔벌로프
    sig = np.sin(2 * np.pi * np.cumsum(f) / SR)
    env = np.exp(-t / 0.13)
    env[: int(0.003 * SR)] *= np.linspace(0, 1, int(0.003 * SR))
    bus.add(t0, sig, _taper(env), amp, 0.0)


_RNG = np.random.default_rng(7)


def add_hat(bus, t0, amp, pan):
    n = int(0.09 * SR)
    noise = _RNG.standard_normal(n)
    sig = np.diff(noise, prepend=0.0)             # 1차 차분 = 간이 하이패스
    sig = np.diff(sig, prepend=0.0) * 0.5
    t = np.arange(n) / SR
    env = np.exp(-t / 0.018)
    env[: int(0.001 * SR)] = 0.0
    bus.add(t0, sig / 4.0, _taper(env, 4.0), amp, pan)


def reverb(x, tau=1.15, predelay=0.022, seed=1):
    rng = np.random.default_rng(seed)
    m = int(1.7 * SR)
    t = np.arange(m) / SR
    ir = rng.standard_normal(m) * np.exp(-t / tau)
    ir = np.convolve(ir, np.ones(5) / 5.0, mode="same")   # 살짝 어둡게
    ir[: int(predelay * SR)] = 0.0
    ir /= np.sqrt(np.sum(ir ** 2))
    nf = 1 << (len(x) + m - 1).bit_length()
    y = np.fft.irfft(np.fft.rfft(x, nf) * np.fft.rfft(ir, nf), nf)[: len(x)]
    return y


# ---------------------------------------------------------------- 곡 구성
# D major. I=D  V=A  vi=Bm  IV=G  iii=F#m
CHORDS = {
    "D":   dict(pad=[62, 66, 69, 74], arp=[74, 78, 81, 86], bass=38),
    "A":   dict(pad=[61, 64, 69, 73], arp=[73, 76, 81, 85], bass=45),
    "Bm":  dict(pad=[59, 62, 66, 71], arp=[71, 74, 78, 83], bass=47),
    "G":   dict(pad=[59, 62, 67, 71], arp=[71, 74, 79, 83], bass=43),
    "F#m": dict(pad=[61, 66, 69, 73], arp=[73, 78, 81, 85], bass=42),
}

# 마디별 코드 (20마디)
PLAN = (
    ["D", "D"]                          # 0.0-4.5   인트로: 패드만, 페이드인
    + ["D", "A", "Bm", "G"]             # 4.5-13.5  아르페지오 + 베이스 진입
    + ["D", "A", "Bm", "G"]             # 13.5-22.5 리듬 진입
    + ["D", "A", "Bm", "G"]             # 22.5-31.5 본편
    + ["G", "A", "F#m", "Bm"]           # 31.5-40.5 변주: 코드 루프 B + 벨 멜로디
    + ["A", "D"]                        # 40.5-45.0 V -> I 해결, 페이드아웃
)
assert len(PLAN) == BARS

ARP_PATTERN = [0, 1, 2, 3, 2, 1, 2, 1]          # 8분음표 8개
BELL_LINE = [74, 73, 69, 66]                    # 변주 구간 하행 멜로디 (마디 14-17)


def render():
    pad = Bus(N)
    lead = Bus(N)      # 아르페지오 + 벨
    low = Bus(N)       # 베이스 + 킥
    perc = Bus(N)      # 하이햇

    for b, name in enumerate(PLAN):
        t = b * BAR
        ch = CHORDS[name]

        # --- 패드: 전 구간. 인트로/아웃트로는 조금 크게, 본편은 뒤로 물림
        pad_amp = 0.115 if b < 2 or b >= 18 else 0.088
        pad_dur = BAR * (2.0 if b >= 18 else 1.0)
        for note in ch["pad"]:
            add_pad(pad, t, note, pad_dur, pad_amp)

        # --- 아르페지오: 마디 2~18
        if 2 <= b <= 18:
            ramp = 0.55 if b == 2 else 1.0
            for i, step in enumerate(ARP_PATTERN):
                if b == 18 and i >= 4:            # 해결부에서 자연스럽게 빠짐
                    break
                note = ch["arp"][step]
                accent = 1.0 if i % 2 == 0 else 0.72
                pan = -0.32 if i % 2 == 0 else 0.32
                add_pluck(lead, t + i * BEAT / 2, note, BEAT / 2,
                          0.105 * accent * ramp, pan)

        # --- 서브 베이스: 마디 2~18
        if 2 <= b <= 18:
            add_bass(low, t, ch["bass"], BEAT * 2, 0.155)
            add_bass(low, t + BEAT * 2, ch["bass"], BEAT * 1.6, 0.125)

        # --- 킥: 마디 4~17 (1박, 3박) + 3.5박 고스트
        if 4 <= b <= 17:
            add_kick(low, t, 0.30)
            add_kick(low, t + BEAT * 2, 0.24)
            if b % 4 == 3:
                add_kick(low, t + BEAT * 3.5, 0.13)
        elif b == 18:
            add_kick(low, t, 0.22)

        # --- 하이햇: 마디 6~17, 8분음표, 아주 절제
        if 6 <= b <= 17:
            for i in range(8):
                amp = 0.055 if i % 2 == 0 else 0.034
                add_hat(perc, t + i * BEAT / 2, amp, -0.22 if i % 2 else 0.22)

        # --- 변주 구간 추가 레이어: 벨 멜로디 (마디 14~17)
        if 14 <= b <= 17:
            add_bell(lead, t + BEAT * 0.5, BELL_LINE[b - 14], BAR * 0.8, 0.075, 0.12)

    # 마지막 해결 코드 위에 얹는 벨 (D)
    add_bell(lead, 18 * BAR + BEAT * 2, 74, BAR, 0.06, -0.1)
    add_bell(lead, 19 * BAR, 78, BAR * 1.5, 0.055, 0.15)

    dry_l = pad.l + lead.l + low.l + perc.l
    dry_r = pad.r + lead.r + low.r + perc.r

    # 리버브는 패드/리드에만 (베이스·킥은 드라이하게 중앙 유지)
    wet_src_l = pad.l + lead.l
    wet_src_r = pad.r + lead.r
    mix_l = dry_l + 0.20 * reverb(wet_src_l, seed=11)
    mix_r = dry_r + 0.20 * reverb(wet_src_r, seed=29)

    # --- 마스터 페이드
    t = np.arange(N) / SR
    fade = np.ones(N)
    fin = t < 3.2
    fade[fin] = (0.5 - 0.5 * np.cos(np.pi * t[fin] / 3.2)) ** 1.3
    fo0 = DUR - 3.6
    fout = t >= fo0
    fade[fout] = (0.5 + 0.5 * np.cos(np.pi * (t[fout] - fo0) / 3.6)) ** 1.2
    fade[-1] = 0.0
    mix_l *= fade
    mix_r *= fade

    # --- 정규화: RMS -20 dBFS 목표, 피크는 -1 dBFS 아래로 강제
    rms = np.sqrt(np.mean(mix_l ** 2 + mix_r ** 2) / 2.0)
    g = 10 ** (TARGET_RMS_DB / 20.0) / rms
    peak = max(np.abs(mix_l).max(), np.abs(mix_r).max()) * g
    ceil = 10 ** (PEAK_CEIL_DB / 20.0)
    if peak > ceil:
        g *= ceil / peak
    return mix_l * g, mix_r * g


def db(x):
    return 20 * np.log10(x) if x > 0 else -np.inf


def write_wav(path, l, r):
    stereo = np.empty(len(l) * 2, dtype=np.float64)
    stereo[0::2] = l
    stereo[1::2] = r
    pcm = np.clip(np.round(stereo * 32767.0), -32768, 32767).astype("<i2")
    with wave.open(str(path), "wb") as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())


def selftest(l, r):
    peak = max(np.abs(l).max(), np.abs(r).max())
    rms = np.sqrt(np.mean(l ** 2 + r ** 2) / 2.0)
    assert len(l) == len(r) == N == 2_160_000, (len(l), N)
    assert abs(N / SR - 45.0) < 1e-9
    assert db(peak) <= PEAK_CEIL_DB + 1e-6, db(peak)
    assert -24.0 <= db(rms) <= -18.0, db(rms)
    assert abs(l[0]) < 1e-6 and abs(l[-1]) < 1e-6, "양끝이 0이 아님(클릭 위험)"
    # 5..40초 구간에 긴 무음이 없는지: 0.25초 창 RMS가 전부 -55dBFS 위
    w = int(0.25 * SR)
    seg = (l[5 * SR:40 * SR] + r[5 * SR:40 * SR]) / 2.0
    blocks = seg[: len(seg) // w * w].reshape(-1, w)
    quiet = db(np.sqrt((blocks ** 2).mean(axis=1)).min())
    assert quiet > -55.0, f"중간 무음 의심: {quiet:.1f} dBFS"
    print(f"selftest OK  peak={db(peak):+.2f} dBFS  rms={db(rms):+.2f} dBFS  "
          f"quietest_250ms={quiet:+.1f} dBFS  dur={N / SR:.3f}s")


if __name__ == "__main__":
    L, R = render()
    selftest(L, R)
    if "--selftest" not in sys.argv:
        write_wav(OUT, L, R)
        print(f"wrote {OUT} ({OUT.stat().st_size:,} bytes)")
