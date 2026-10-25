#!/usr/bin/env python3
"""Check bundled recorded ambience: decode, duration, levels, provenance and decoded loop seams.

Uses only Python 3, ffmpeg and ffprobe. Does not connect to a TV or download anything.
"""
from array import array
import hashlib
import json
import math
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parent.parent


def pcm(path, seconds, start=0):
    data = subprocess.check_output(['ffmpeg', '-v', 'error', '-ss', str(start), '-i', str(path),
        '-t', str(seconds), '-ar', '44100', '-ac', '2', '-f', 'f32le', '-'])
    values = array('f')
    values.frombytes(data)
    return values


def main():
    catalog = json.loads((ROOT / 'config/weather-audio.json').read_text())
    target = ROOT / 'app/src/main/assets/weather'
    reports, digests = {}, set()
    assert len(catalog['scenes']) == 10
    for name, scene in catalog['scenes'].items():
        path = target / (name + '.m4a')
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        assert digest not in digests, f'{name}: duplicate sound'
        digests.add(digest)
        metadata = json.loads(subprocess.check_output(['ffprobe', '-v', 'error', '-show_entries',
            'format=duration:format_tags=comment:stream=codec_name,sample_rate,channels', '-of', 'json', str(path)]))
        stream = metadata['streams'][0]
        duration = float(metadata['format']['duration'])
        assert stream['codec_name'] == 'aac' and stream['sample_rate'] == '44100' and stream['channels'] == 2
        assert abs(duration - scene['duration_seconds']) < 0.02, (name, duration)
        for layer in scene['layers']:
            source = catalog['sources'][layer['source']]
            assert source['author'] in metadata['format']['tags']['comment']
            assert source['license'] in ('CC0-1.0', 'CC-BY-4.0', 'Public Domain')
        # Decode exactly the authored duration, honoring the container's AAC padding trim.
        head = pcm(path, 0.25)
        tail = pcm(path, 0.25, duration - 0.25)
        assert len(head) == len(tail) == 22050, (name, len(head), len(tail))
        adjacent = [samples[i] - samples[i - 2] for samples in (head, tail) for i in range(2, len(samples))]
        local_rms = math.sqrt(math.fsum(value * value for value in adjacent) / len(adjacent))
        jump = max(abs(head[channel] - tail[-2 + channel]) for channel in range(2))
        seam_ratio = jump / max(local_rms, 1e-9)
        assert seam_ratio < 8, f'{name}: click-sized discontinuity at the decoded seam ({seam_ratio:.2f})'
        analysis = subprocess.run(['ffmpeg', '-hide_banner', '-i', str(path), '-t', str(duration),
            '-af', 'volumedetect', '-f', 'null', '-'], text=True, capture_output=True, check=True).stderr
        peak = float(re.search(r'max_volume: ([-0-9.]+) dB', analysis).group(1))
        mean = float(re.search(r'mean_volume: ([-0-9.]+) dB', analysis).group(1))
        assert -50 < mean < -15 and peak <= -3, f'{name}: invalid level ({mean}, {peak})'
        reports[name] = dict(seconds=duration, bytes=path.stat().st_size, mean_db=mean, peak_db=peak,
                             seam_ratio=round(seam_ratio, 3), sha256=digest)
        print(f'PASS {name}: {duration:g}s, peak {peak:g} dBFS, seam {seam_ratio:.2f}× local sample differences', flush=True)
    output = ROOT / 'build/weather-audio-validation'
    output.mkdir(parents=True, exist_ok=True)
    (output / 'result.json').write_text(json.dumps(reports, indent=2) + '\n')
    print('PASS: ten distinct recorded ambiences, all decoded; total bytes:', sum(item['bytes'] for item in reports.values()))


if __name__ == '__main__':
    main()
