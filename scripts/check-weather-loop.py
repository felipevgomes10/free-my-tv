#!/usr/bin/env python3
"""Check real rendered-frame gaps at weather loop boundaries on an existing debug emulator.

Run after installing a debug APK: python3 scripts/check-weather-loop.py --serial emulator-5554
Never launches an emulator or targets a physical TV. Exit 1 means a visible boundary pause.
"""
import argparse
import re
import subprocess
import time


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", default="emulator-5554")
    parser.add_argument("--seconds", type=int, default=35)
    parser.add_argument("--weather-code", type=int, default=61)
    parser.add_argument("--night", action="store_true")
    args = parser.parse_args()
    adb = ["adb", "-s", args.serial]
    emulator = subprocess.check_output(adb + ["shell", "getprop", "ro.kernel.qemu"], text=True).strip()
    if emulator != "1":
        raise SystemExit("Refusing a device that is not an emulator")
    # Start logging before the activity, without clearing the user's log buffer.
    log = subprocess.Popen(adb + ["logcat", "-v", "brief", "-T", "1", "WeatherLoop:I", "*:S"], stdout=subprocess.PIPE, text=True)
    try:
        subprocess.run(adb + ["shell", "am", "start", "-W", "-S", "-n", "com.freemytv.home/.debug.WeatherPreviewActivity", "--ei", "weather_code", str(args.weather_code), "--ez", "is_day", str(not args.night).lower()], check=True, stdout=subprocess.DEVNULL)
        pid = subprocess.check_output(adb + ["shell", "pidof", "com.freemytv.home"], text=True).strip()
        time.sleep(args.seconds)
    finally:
        log.terminate()
    output = log.communicate(timeout=5)[0]
    output = "\n".join(line for line in output.splitlines() if re.search(r"\(\s*" + pid + r"\)", line))
    gaps = [float(gap) for gap in re.findall(r"boundary_gap_ms=([0-9.]+)", output)]
    print("Loop boundary frame gaps (ms):", gaps, flush=True)
    if len(gaps) < 2:
        raise SystemExit("FAIL: fewer than two loop boundaries measured")
    audio_decoders = output.count("decoder=audio")
    video_decoders = output.count("decoder=video")
    underruns = output.count("audio_underrun")
    audio_outputs = output.count("audio_output_started")
    print("Audio/video decoder initializations:", audio_decoders, video_decoders, "Audio underruns:", underruns)
    print("Audio output starts:", audio_outputs)
    if max(gaps) > 125:
        raise SystemExit("FAIL: a boundary held the picture for more than three frames (125 ms at 24 fps)")
    if audio_decoders != 1 or video_decoders != 1 or underruns or audio_outputs != 1:
        raise SystemExit("FAIL: playback restarted a decoder or starved the audio output")
    print("PASS: no visible boundary pause across", len(gaps), "loops")


if __name__ == "__main__":
    main()
