#!/usr/bin/env python3
"""사연/야담 티저용 시네마틱 사운드 자체 합성 (외부 음원·API 없음, 저작권 무료).

사용법:
    python3 make_bgm.py spec.json bgm.wav

spec.json에서 duration / captions[].start / lightning[] / endcard.start 를 읽어
자막 붐, 천둥, 심장박동, 엔드 임팩트를 자동 배치한다.
"""
import json
import sys
import wave

import numpy as np

SR = 44100


def lowpass(x, alpha):
    y = np.empty_like(x)
    acc = 0.0
    for k in range(len(x)):
        acc += alpha * (x[k] - acc)
        y[k] = acc
    return y


def env_ar(n, a, r):
    e = np.ones(n)
    na, nr = int(a * SR), int(r * SR)
    if na > 0:
        e[:na] = np.linspace(0, 1, na)
    if nr > 0:
        e[-nr:] *= np.linspace(1, 0, nr)
    return e


def build(spec, out_path):
    dur = float(spec["duration"])
    N = int(SR * dur)
    t = np.arange(N) / SR
    rng = np.random.default_rng(7)          # 결정적 — 매번 같은 소리
    mix = np.zeros(N)

    def sec(x):
        return int(x * SR)

    def add(sig, at):
        i = sec(at)
        if i < 0:
            sig, i = sig[-i:], 0
        j = min(i + len(sig), N)
        if j > i:
            mix[i:j] += sig[: j - i]

    end_start = float(spec.get("endcard", {}).get("start", dur - 2.6))

    # 1. 드론 베드
    f0 = float(spec.get("drone_hz", 55.0))
    drone = (np.sin(2 * np.pi * f0 * t) * 0.5
             + np.sin(2 * np.pi * (f0 * 1.007) * t) * 0.35
             + np.sin(2 * np.pi * (f0 * 1.5) * t) * 0.18
             + np.sin(2 * np.pi * (f0 * 0.5) * t) * 0.25)
    lfo = 0.75 + 0.25 * np.sin(2 * np.pi * 0.11 * t)
    peak_at = end_start - 3.2
    swell = 1.0 + 0.45 * np.exp(-((t - peak_at) ** 2) / 6.0)
    mix += drone * lfo * swell * env_ar(N, 1.8, 2.5) * 0.16

    # 2. 자막 서브 붐
    for c in spec.get("captions", []):
        n = sec(1.1)
        tt = np.arange(n) / SR
        fall = 58 * np.exp(-tt * 3.2) + 34
        add(np.sin(2 * np.pi * np.cumsum(fall) / SR) * np.exp(-tt * 4.2) * 0.30,
            float(c["start"]) - 0.05)

    # 3. 전환 라이저 (샷 시작 0.8초 전)
    for s in spec.get("shots", [])[1:]:
        n = sec(0.9)
        riser = lowpass(rng.standard_normal(n), 0.08) * np.linspace(0, 1, n) ** 2
        add(riser * 0.16, float(s["start"]) - 0.8)

    # 4. 빗소리 + 천둥
    if spec.get("lightning"):
        lo = min(spec["lightning"]) - 0.1
        hi = max(spec["lightning"]) + 3.0
        n = sec(min(hi, dur) - lo)
        if n > 0:
            rain = rng.standard_normal(n)
            rain = rain - lowpass(rain, 0.02)
            rain = lowpass(rain, 0.35) * env_ar(n, 0.7, 0.8)
            add(rain * 0.10, lo)
        for j, at in enumerate(spec["lightning"]):
            n = sec(2.2)
            tt = np.arange(n) / SR
            crack = rng.standard_normal(n) * np.exp(-tt * 9)
            rumble = lowpass(rng.standard_normal(n), 0.012) * np.exp(-tt * 1.6) * 6.0
            add((crack * 0.4 + rumble) * (0.55 if j == 0 else 0.35) * 0.5, at)

    # 5. 심장박동 가속 (엔드카드 2.4초 전부터)
    bt = end_start - 2.4
    gap = 0.62
    while bt < end_start - 0.1:
        for off, amp in ((0.0, 1.0), (0.16, 0.6)):
            n = sec(0.28)
            tt = np.arange(n) / SR
            fall = 62 * np.exp(-tt * 7) + 30
            add(np.sin(2 * np.pi * np.cumsum(fall) / SR) * np.exp(-tt * 11) * 0.34 * amp,
                bt + off)
        gap *= 0.88
        bt += max(gap, 0.34)

    # 6. 엔드 임팩트 + 여운
    n = sec(min(2.6, dur - end_start + 0.1))
    tt = np.arange(n) / SR
    fall = 70 * np.exp(-tt * 2.6) + 26
    impact = np.sin(2 * np.pi * np.cumsum(fall) / SR) * np.exp(-tt * 1.7)
    sub = np.sin(2 * np.pi * 30 * tt) * np.exp(-tt * 1.1)
    add((impact * 0.8 + sub * 0.5) * 0.5, end_start - 0.02)

    n = sec(min(2.4, dur - end_start))
    tt = np.arange(n) / SR
    shim = (np.sin(2 * np.pi * 440 * tt) + 0.6 * np.sin(2 * np.pi * 659.25 * tt))
    add(shim * env_ar(n, 1.2, 1.0) * 0.018, end_start + 0.4)

    # 마스터
    mix *= env_ar(N, 0.02, 1.0)
    mix = np.tanh(mix * 1.4) * 0.9
    mix = mix / max(np.max(np.abs(mix)), 1e-9) * 0.85

    d = sec(0.0006)
    stereo = np.empty((N, 2))
    stereo[:, 0] = mix
    stereo[:, 1] = np.concatenate([mix[d:], np.zeros(d)])
    pcm = (stereo * 32767).astype(np.int16)

    with wave.open(out_path, "wb") as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    print(f"{out_path} 생성 완료 ({dur}s)")


if __name__ == "__main__":
    with open(sys.argv[1], encoding="utf-8") as f:
        build(json.load(f), sys.argv[2] if len(sys.argv) > 2 else "bgm.wav")
