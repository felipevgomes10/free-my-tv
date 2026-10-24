#!/usr/bin/env python3
"""Build the Weather backgrounds from the licensed clips in config/weather-media.json.

Usage: python3 scripts/prepare-weather-media.py --sources-dir /path/to/originals [--download]
Requires ffmpeg. Downloads are opt-in; originals and intermediate files stay outside the repo.
The original clips' sound is discarded. Each scene gets its own filtered white-noise bed.
"""
import argparse
import json
from pathlib import Path
import subprocess
import urllib.request

ROOT = Path(__file__).resolve().parent.parent


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sources-dir", type=Path, required=True)
    parser.add_argument("--download", action="store_true")
    args = parser.parse_args()
    args.sources_dir.mkdir(parents=True, exist_ok=True)
    target = ROOT / "app/src/main/assets/weather"
    target.mkdir(parents=True, exist_ok=True)
    catalog = json.loads((ROOT / "config/weather-media.json").read_text())
    for index, (name, item) in enumerate(catalog.items()):
        original = args.sources_dir / (name + ".mp4")
        if not original.exists() and args.download:
            urllib.request.urlretrieve(item["download"], original)
        if not original.exists():
            raise SystemExit(f"Missing {original}; supply the original or use --download")
        noise = item["noise"]
        # The loop starts at t=1 and cross-fades back into t=0..1, ending where it started.
        # Apply the same overlap to the noise: no click or silent gap at the loop boundary.
        filters = (
            "[0:v]fps=24,scale=1280:720:force_original_aspect_ratio=increase,"
            "crop=1280:720,setsar=1,format=yuv420p,trim=end=8,split[body][head];"
            "[body]trim=start=1:end=8,setpts=PTS-STARTPTS[main];"
            "[head]trim=end=1,setpts=PTS-STARTPTS[beginning];"
            "[main][beginning]xfade=transition=fade:duration=1:offset=6,format=yuv420p[v];"
            f"[1:a]highpass=f={noise['highpass_hz']},lowpass=f={noise['lowpass_hz']},"
            f"tremolo=f=0.17:d={noise['swell_depth']},asplit[abody][ahead];"
            "[abody]atrim=start=1:end=8,asetpts=PTS-STARTPTS[amain];"
            "[ahead]atrim=end=1,asetpts=PTS-STARTPTS[abeginning];"
            "[amain][abeginning]acrossfade=d=1:c1=tri:c2=tri[a]"
        )
        subprocess.run([
            "ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", str(original),
            "-f", "lavfi", "-i",
            f"anoisesrc=color=white:amplitude={noise['amplitude']}:sample_rate=44100:duration=8:seed={41 + index}",
            "-filter_complex_threads", "1", "-filter_complex", filters,
            "-map", "[v]", "-map", "[a]", "-t", "7", "-c:v", "libx264",
            "-preset", "medium", "-profile:v", "baseline", "-level:v", "3.1",
            "-crf", "27", "-maxrate", "1800k", "-bufsize", "3600k", "-g", "48",
            "-c:a", "aac", "-b:a", "64k", "-ac", "1", "-movflags", "+faststart",
            str(target / (name + ".mp4")),
        ], check=True)
        print(f"Prepared {name}", flush=True)


if __name__ == "__main__":
    main()
