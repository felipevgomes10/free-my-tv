#!/usr/bin/env python3
"""Prepare recorded Weather ambience. Python 3 + ffmpeg; source downloads are opt-in.

python3 scripts/prepare-weather-audio.py --sources-dir /tmp/weather-recordings --download
Pinned source URLs and SHA-256 digests, licenses and all mix parameters live in
config/weather-audio.json. Originals stay outside the APK/repository.
"""
import argparse
import hashlib
import json
import math
from pathlib import Path
import subprocess
import tempfile
import urllib.request

ROOT = Path(__file__).resolve().parent.parent


def command(args):
    return subprocess.run(args, check=True, capture_output=True, text=True)


def verify_source(path, expected):
    if hashlib.sha256(path.read_bytes()).hexdigest() != expected:
        raise ValueError(f"Source checksum mismatch: {path}")


def render(name, scene, catalog, sources_dir, target, work):
    length = scene['duration_seconds']
    fade = catalog['crossfade_seconds']
    inputs, filters = [], []
    for index, layer in enumerate(scene['layers']):
        source = catalog['sources'][layer['source']]
        path = sources_dir / source['file']
        inputs += ['-i', str(path)]
        # No internal stream-loop: every layer must contain a full continuous excerpt.
        duration = float(json.loads(command(['ffprobe', '-v', 'error', '-show_entries',
            'format=duration', '-of', 'json', str(path)]).stdout)['format']['duration'])
        if layer['start_seconds'] + length + fade > duration:
            raise ValueError(f"{name}: excerpt exceeds the recording {path.name}")
        filters.append(
            f"[{index}:a]atrim=start={layer['start_seconds']}:duration={length + fade},asetpts=PTS-STARTPTS,"
            f"highpass=f={layer['highpass_hz']},lowpass=f={layer['lowpass_hz']},"
            f"loudnorm=I=-24:TP=-6:LRA=10,aresample={catalog['sample_rate']},"
            f"aformat=sample_fmts=fltp:channel_layouts=stereo,volume={layer['gain_db']}dB[l{index}]"
        )
    labels = ''.join(f'[l{i}]' for i in range(len(scene['layers'])))
    # Compress the recording peaks before the seam is made, to retain continuous gain at the join.
    filters.append(f"{labels}amix=inputs={len(scene['layers'])}:normalize=0:duration=shortest,"
        "acompressor=threshold=0.125:ratio=3:attack=30:release=700:knee=3,asplit[body][head]")
    filters += [
        f"[body]atrim=start={fade}:end={length + fade},asetpts=PTS-STARTPTS[main]",
        f"[head]atrim=end={fade},asetpts=PTS-STARTPTS[beginning]",
        f"[main][beginning]acrossfade=d={fade}:c1=qsin:c2=qsin[a]",
    ]
    mixed = work / (name + '.wav')
    command(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-y', *inputs,
        '-filter_complex_threads', '1', '-filter_complex', ';'.join(filters), '-map', '[a]',
        '-ar', str(catalog['sample_rate']), '-c:a', 'pcm_f32le', str(mixed)])
    analysis = command(['ffmpeg', '-hide_banner', '-i', str(mixed), '-af',
        'loudnorm=I=-24:TP=-4:LRA=50:print_format=json', '-f', 'null', '-']).stderr
    measured, _ = json.JSONDecoder().raw_decode(analysis[analysis.rindex('{'):])
    integrated, peak = float(measured['input_i']), float(measured['input_tp'])
    if not math.isfinite(integrated) or not math.isfinite(peak):
        raise ValueError(f"{name}: silent or invalid recording")
    # A fixed final gain preserves the seam; dynamic normalization after crossfade would not.
    gain = min(scene['loudness_lufs'] - integrated, -4 - peak)
    credits = '; '.join(f"{catalog['sources'][layer['source']]['title']} by "
        f"{catalog['sources'][layer['source']]['author']} ({catalog['sources'][layer['source']]['license']})"
        for layer in scene['layers'])
    # AAC's first transform window can start near zero even after priming is trimmed. A 5 ms
    # edge ramp prevents a click when low-frequency wind returns to that first window. It adds
    # no silence or extra time; the long ambience crossfade above supplies the actual loop.
    edge = 0.005
    finish = f'volume={gain}dB,afade=t=in:d={edge}:curve=qsin,afade=t=out:st={length - edge}:d={edge}:curve=qsin'
    command(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-y', '-i', str(mixed),
        '-af', finish, '-c:a', 'aac', '-b:a', catalog['bitrate'], '-ac', '2',
        '-ar', str(catalog['sample_rate']), '-movflags', '+faststart', '-metadata',
        'title=' + scene['description'], '-metadata', 'comment=' + credits,
        str(target / (name + '.m4a'))])
    print(f"Prepared {name}: {length}s, {integrated + gain:.1f} LUFS, peak <= {peak + gain:.1f} dBTP", flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sources-dir', type=Path, required=True)
    parser.add_argument('--download', action='store_true')
    args = parser.parse_args()
    catalog = json.loads((ROOT / 'config/weather-audio.json').read_text())
    args.sources_dir.mkdir(parents=True, exist_ok=True)
    for source in catalog['sources'].values():
        path = args.sources_dir / source['file']
        if not path.exists() and args.download:
            print('Downloading ' + source['title'], flush=True)
            urllib.request.urlretrieve(source['download'], path)
        if not path.exists():
            raise SystemExit(f'Missing {path}; supply the recording or add --download')
        verify_source(path, source['sha256'])
    target = ROOT / 'app/src/main/assets/weather'
    target.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='weather-audio-') as temporary:
        for name, scene in catalog['scenes'].items():
            render(name, scene, catalog, args.sources_dir, target, Path(temporary))
    credits = ['Weather ambient recordings', '', 'Adaptations: excerpts, filtering, mixing, peak control,',
        'loop crossfade, fixed loudness normalization and AAC encoding. No upstream app code is used.', '']
    for source in catalog['sources'].values():
        credits += [source['title'] + ' — ' + source['author'], source['page'],
            source['license'] + ': ' + source['license_url'],
            'Upstream edit/distribution: ' + (source['upstream_editor'] or 'Original public preview'), '']
    (target / 'CREDITS.txt').write_text('\n'.join(credits), encoding='utf-8')


if __name__ == '__main__':
    main()
