#!/usr/bin/env python3
"""Generate PixelPals' original, royalty-free promotional soundtrack.

The mix is intentionally gentle: a warm musical bed, small UI cues, a purr
for Ginger, an airy shimmer for Bloop, and soft outdoor ambience for the
adventure scene. No samples or third-party music are used.
"""

from __future__ import annotations

import argparse
import math
import wave
from pathlib import Path

import numpy as np


SAMPLE_RATE = 48_000
DURATION_SECONDS = 28.0


def envelope(length: int, attack: float, release: float) -> np.ndarray:
    env = np.ones(length, dtype=np.float64)
    attack_samples = min(length, int(attack * SAMPLE_RATE))
    release_samples = min(length, int(release * SAMPLE_RATE))
    if attack_samples:
        env[:attack_samples] = np.linspace(0.0, 1.0, attack_samples, endpoint=False)
    if release_samples:
        env[-release_samples:] *= np.linspace(1.0, 0.0, release_samples)
    return env


def add_tone(
    mix: np.ndarray,
    start: float,
    duration: float,
    frequency: float,
    amplitude: float,
    pan: float = 0.0,
    attack: float = 0.08,
    release: float = 0.25,
    shimmer: float = 0.0,
) -> None:
    begin = int(start * SAMPLE_RATE)
    length = min(int(duration * SAMPLE_RATE), len(mix) - begin)
    if length <= 0:
        return
    t = np.arange(length, dtype=np.float64) / SAMPLE_RATE
    vibrato = shimmer * np.sin(2.0 * math.pi * 4.2 * t)
    phase = 2.0 * math.pi * frequency * t + vibrato
    signal = np.sin(phase) + 0.22 * np.sin(2.0 * phase) + 0.07 * np.sin(3.0 * phase)
    signal *= amplitude * envelope(length, attack, release)
    left = math.sqrt((1.0 - pan) / 2.0)
    right = math.sqrt((1.0 + pan) / 2.0)
    mix[begin : begin + length, 0] += signal * left
    mix[begin : begin + length, 1] += signal * right


def add_bell(mix: np.ndarray, start: float, frequency: float, amplitude: float, pan: float) -> None:
    duration = 1.35
    begin = int(start * SAMPLE_RATE)
    length = min(int(duration * SAMPLE_RATE), len(mix) - begin)
    if length <= 0:
        return
    t = np.arange(length, dtype=np.float64) / SAMPLE_RATE
    decay = np.exp(-3.7 * t)
    signal = (
        np.sin(2.0 * math.pi * frequency * t)
        + 0.42 * np.sin(2.0 * math.pi * frequency * 2.01 * t)
        + 0.18 * np.sin(2.0 * math.pi * frequency * 3.97 * t)
    )
    signal *= amplitude * decay * envelope(length, 0.008, 0.1)
    left = math.sqrt((1.0 - pan) / 2.0)
    right = math.sqrt((1.0 + pan) / 2.0)
    mix[begin : begin + length, 0] += signal * left
    mix[begin : begin + length, 1] += signal * right


def add_tap(mix: np.ndarray, start: float, amplitude: float = 0.045) -> None:
    begin = int(start * SAMPLE_RATE)
    length = min(int(0.09 * SAMPLE_RATE), len(mix) - begin)
    if length <= 0:
        return
    t = np.arange(length, dtype=np.float64) / SAMPLE_RATE
    click = (
        np.sin(2.0 * math.pi * 560.0 * t)
        + 0.35 * np.sin(2.0 * math.pi * 1_140.0 * t)
    ) * np.exp(-48.0 * t)
    mix[begin : begin + length] += (amplitude * click)[:, None]


def add_chirp(mix: np.ndarray, start: float, pan: float = 0.0) -> None:
    """A tiny, friendly non-verbal pet chirp for the ensemble scene."""
    begin = int(start * SAMPLE_RATE)
    duration = 0.22
    length = min(int(duration * SAMPLE_RATE), len(mix) - begin)
    if length <= 0:
        return
    t = np.arange(length, dtype=np.float64) / SAMPLE_RATE
    phase = 2.0 * math.pi * (820.0 * t + 1_350.0 * t * t)
    signal = 0.031 * np.sin(phase) * np.sin(math.pi * t / duration) ** 2
    left = math.sqrt((1.0 - pan) / 2.0)
    right = math.sqrt((1.0 + pan) / 2.0)
    mix[begin : begin + length, 0] += signal * left
    mix[begin : begin + length, 1] += signal * right


def add_soft_woof(mix: np.ndarray, start: float, rng: np.random.Generator) -> None:
    """A restrained synthetic puppy cue, mixed low enough to avoid cartoon harshness."""
    begin = int(start * SAMPLE_RATE)
    duration = 0.24
    length = min(int(duration * SAMPLE_RATE), len(mix) - begin)
    if length <= 0:
        return
    t = np.arange(length, dtype=np.float64) / SAMPLE_RATE
    sweep = 210.0 - 55.0 * (t / duration)
    phase = 2.0 * math.pi * np.cumsum(sweep) / SAMPLE_RATE
    breath = moving_average(rng.normal(0.0, 1.0, length), 38)[:length]
    signal = (np.sin(phase) + 0.24 * breath) * np.exp(-13.0 * t)
    signal *= 0.024 * envelope(length, 0.012, 0.08)
    mix[begin : begin + length, 0] += signal * 0.95
    mix[begin : begin + length, 1] += signal


def moving_average(signal: np.ndarray, window: int) -> np.ndarray:
    if window <= 1:
        return signal
    padded = np.pad(signal, (window, 0), mode="edge")
    summed = np.cumsum(padded, dtype=np.float64)
    return (summed[window:] - summed[:-window]) / window


def add_purr(mix: np.ndarray, start: float, duration: float, rng: np.random.Generator) -> None:
    begin = int(start * SAMPLE_RATE)
    length = min(int(duration * SAMPLE_RATE), len(mix) - begin)
    t = np.arange(length, dtype=np.float64) / SAMPLE_RATE
    noise = rng.normal(0.0, 1.0, length)
    soft_noise = moving_average(noise, 180)[:length]
    pulse = 0.5 + 0.5 * np.sin(2.0 * math.pi * 25.0 * t)
    body = 0.65 * np.sin(2.0 * math.pi * 52.0 * t) + 0.35 * soft_noise
    purr = 0.022 * body * (0.3 + 0.7 * pulse) * envelope(length, 0.45, 0.6)
    mix[begin : begin + length, 0] += purr * 0.9
    mix[begin : begin + length, 1] += purr


def add_wind(mix: np.ndarray, start: float, duration: float, rng: np.random.Generator) -> None:
    begin = int(start * SAMPLE_RATE)
    length = min(int(duration * SAMPLE_RATE), len(mix) - begin)
    t = np.arange(length, dtype=np.float64) / SAMPLE_RATE
    raw = rng.normal(0.0, 1.0, length)
    wide = moving_average(raw, 95)[:length]
    slow = moving_average(raw, 1_100)[:length]
    breeze = (wide - slow) * (0.72 + 0.28 * np.sin(2.0 * math.pi * 0.19 * t))
    breeze *= 0.032 * envelope(length, 0.8, 0.9)
    mix[begin : begin + length, 0] += breeze
    mix[begin : begin + length, 1] += np.roll(breeze, 1_100)


def build_soundtrack() -> np.ndarray:
    frames = int(DURATION_SECONDS * SAMPLE_RATE)
    mix = np.zeros((frames, 2), dtype=np.float64)
    rng = np.random.default_rng(20_260_920)

    # Warm chord bed. The slow attacks keep the soundtrack companion-like,
    # leaving space for the small character sounds.
    chords = [
        (0.0, 4.7, (261.63, 329.63, 392.00, 493.88)),
        (4.4, 4.5, (220.00, 261.63, 329.63, 392.00)),
        (8.6, 4.8, (174.61, 220.00, 261.63, 329.63)),
        (13.0, 5.3, (196.00, 246.94, 293.66, 392.00)),
        (18.0, 5.4, (174.61, 220.00, 261.63, 349.23)),
        (23.1, 4.9, (261.63, 329.63, 392.00, 493.88)),
    ]
    for chord_start, chord_duration, notes in chords:
        for index, note in enumerate(notes):
            add_tone(
                mix,
                chord_start,
                chord_duration,
                note / 2.0,
                0.017,
                pan=(-0.45 + index * 0.3),
                attack=0.7,
                release=1.0,
                shimmer=0.018,
            )

    # Transition bells and character signatures.
    for start, note, pan in [
        (0.15, 659.25, -0.2),
        (2.45, 783.99, 0.25),
        (6.95, 659.25, -0.3),
        (12.95, 880.00, 0.35),
        (17.95, 587.33, -0.15),
        (23.05, 698.46, 0.2),
        (24.55, 783.99, -0.35),
        (24.85, 987.77, 0.25),
        (25.15, 1_174.66, -0.05),
        (26.45, 783.99, -0.1),
        (26.72, 987.77, 0.2),
    ]:
        add_bell(mix, start, note, 0.052, pan)

    add_purr(mix, 3.0, 3.4, rng)
    add_bell(mix, 5.45, 1_174.66, 0.026, 0.25)  # Ginger's dream sparkle.

    for tap_at in (8.05, 9.35, 11.15):
        add_tap(mix, tap_at)
    for shimmer_at, note, pan in [
        (7.35, 1_046.50, -0.35),
        (10.35, 1_318.51, 0.25),
        (11.0, 1_567.98, -0.05),
        (13.75, 987.77, -0.3),
        (14.55, 1_174.66, 0.35),
        (16.1, 1_318.51, -0.1),
    ]:
        add_bell(mix, shimmer_at, note, 0.026, pan)

    add_wind(mix, 17.0, 5.0, rng)
    add_bell(mix, 19.2, 1_046.50, 0.018, -0.55)
    add_bell(mix, 20.8, 1_318.51, 0.015, 0.5)
    add_tap(mix, 21.55, 0.03)

    # The full-cast party gets a tiny chorus: Corgi's soft greeting and a
    # duck/penguin-like reply. These remain quieter than the music.
    add_soft_woof(mix, 24.72, rng)
    add_chirp(mix, 25.02, -0.32)
    add_chirp(mix, 25.28, 0.36)

    # Gentle limiter. The final EBU loudness pass happens in ffmpeg.
    peak = float(np.max(np.abs(mix)))
    if peak > 0.82:
        mix *= 0.82 / peak
    return mix


def write_wave(output: Path, samples: np.ndarray) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    pcm = np.clip(samples, -1.0, 1.0)
    pcm = (pcm * 32_767.0).astype("<i2")
    with wave.open(str(output), "wb") as destination:
        destination.setnchannels(2)
        destination.setsampwidth(2)
        destination.setframerate(SAMPLE_RATE)
        destination.writeframes(pcm.tobytes())


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    write_wave(args.output, build_soundtrack())
    print(f"Wrote {args.output} ({DURATION_SECONDS:.1f}s, original stereo mix)")


if __name__ == "__main__":
    main()
