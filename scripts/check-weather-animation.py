#!/usr/bin/env python3
"""Exercise the native Weather Animations port on an existing debug emulator; never a physical TV.

Checks GLES errors, submission cadence, a forecast change without rebuilding the GPU context,
and gapless audio output. Screenshots capture actual composited output for visual review.
"""
import argparse
import json
from pathlib import Path
import re
import subprocess
import time


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", default="emulator-5554")
    parser.add_argument("--seconds", type=int, default=65)
    parser.add_argument("--output", type=Path, default=Path("build/weather-animation-validation"))
    args = parser.parse_args()
    if args.seconds < 65:
        parser.error("Use at least 65 seconds to exercise the recorded rain loop")
    adb = ["adb", "-s", args.serial]
    def shell(*words):
        return subprocess.check_output(adb + ["shell", *words], text=True, timeout=20).strip()
    if shell("getprop", "ro.kernel.qemu") != "1":
        raise SystemExit("Refusing a device that is not an emulator")
    args.output.mkdir(parents=True, exist_ok=True)
    log = subprocess.Popen(adb + ["logcat", "-v", "brief", "-T", "1", "WeatherAnimation:I", "WeatherScene:W", "AndroidRuntime:E", "*:S"], stdout=subprocess.PIPE, text=True)
    def start(code, day, restart=False):
        shell("am", "start", "-W", *( ["-S"] if restart else ["--activity-single-top"] ),
              "-n", "com.freemytv.home/.debug.WeatherPreviewActivity", "--ei", "weather_code", str(code),
              "--ez", "is_day", str(day).lower(), "--ez", "sound_muted", "false")
    def capture(name):
        data = subprocess.check_output(adb + ["exec-out", "screencap", "-p"], timeout=20)
        (args.output / (name + ".png")).write_bytes(data)
    pid = ""
    try:
        shell("input", "keyevent", "224")
        shell("input", "keyevent", "4")
        start(61, True, restart=True)
        pid = shell("pidof", "com.freemytv.home")
        time.sleep(args.seconds)
        capture("rain")
        # The Activity, Compose node and GPU context must survive the forecast change.
        # onNewIntent itself pauses/resumes a resumed Activity. A debug-only broadcast models
        # the real forecast flow without lifecycle changes, which must legitimately release EGL.
        shell("am", "broadcast", "-a", "com.freemytv.home.debug.WEATHER_FORECAST",
              "-p", "com.freemytv.home", "--ei", "weather_code", "71", "--ez", "is_day", "true")
        time.sleep(4)
        capture("snow")
    finally:
        log.terminate()
    output = log.communicate(timeout=5)[0]
    output = "\n".join(line for line in output.splitlines() if re.search(r"\(\s*" + re.escape(pid) + r"\)", line))
    (args.output / "animation.log").write_text(output + "\n")
    fps = [float(value) for value in re.findall(r"fps=([0-9.]+)", output)]
    sizes = [(int(w), int(h)) for w, h in re.findall(r"output=(\d+)x(\d+)", output)]
    result = dict(fps=fps, renderer_initializations=output.count("renderer_ready"),
                  audio_repeats=output.count("audio_repeat"), audio_decoders=output.count("audio_decoder"), audio_outputs=output.count("audio_output_started"), audio_underruns=output.count("audio_underrun"),
                  graphics_errors=any(error in output for error in ("Graphics unavailable", "gl_error=", "AndroidRuntime")),
                  changed_scene="scene=snow:day" in output,
                  bounded_output=bool(sizes) and all(w <= 640 and h <= 360 for w, h in sizes))
    (args.output / "result.json").write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps(result, indent=2))
    # Initial warm-up interval is reported but excluded from cadence gating.
    if len(fps) < 3 or min(fps[1:]) < 24:
        raise SystemExit("FAIL: native submission cadence below 24 fps; inspect logs and device load")
    if result['renderer_initializations'] != 1 or result['graphics_errors'] or not result['changed_scene'] or not result['bounded_output']:
        raise SystemExit("FAIL: graphics failed, changed forecast was missed, or the surface restarted")
    if result['audio_repeats'] < 1 or result['audio_underruns'] or result['audio_decoders'] != 1 or result['audio_outputs'] != 2:
        raise SystemExit("FAIL: audio decoder/output restarted during a loop, did not change with the scene, or was starved")
    print("PASS: native rain and snow, one GPU context and continuous audio, no GLES errors")


if __name__ == "__main__":
    main()
