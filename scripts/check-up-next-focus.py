#!/usr/bin/env python3
"""Check Up Next focus after scrolling cards offscreen; debug emulator only.

Temporarily seeds a long Up Next Tray, navigates out and back, and checks that Home
does not restart. Restores the emulator's preferences and restarts Home afterwards.
"""
import argparse
import json
from pathlib import Path
import subprocess
import time
import xml.etree.ElementTree as ET


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", default="emulator-5554")
    parser.add_argument("--cycles", type=int, default=10)
    parser.add_argument("--output", type=Path, default=Path("build/up-next-focus-validation"))
    args = parser.parse_args()
    if args.cycles < 1:
        parser.error("Use at least one cycle")
    adb = ["adb", "-s", args.serial]
    package = "com.freemytv.home"

    def command(*words):
        return subprocess.check_output(adb + list(words), timeout=30)

    def shell(*words):
        return command("shell", *words).decode().strip()

    if not args.serial.startswith("emulator-") or shell("getprop", "ro.kernel.qemu") != "1":
        raise SystemExit("Refusing a device that is not an emulator")

    def write_preferences(data):
        subprocess.run(adb + ["shell", f"run-as {package} sh -c 'cat > shared_prefs/home.xml'"],
                       input=data, check=True, timeout=20)

    shell("am", "force-stop", package)
    original = command("exec-out", "run-as", package, "cat", "shared_prefs/home.xml")
    root = ET.fromstring(original)
    entry = root.find("./string[@name='up_next']")
    if entry is None:
        entry = ET.SubElement(root, "string", name="up_next")
    entry.text = json.dumps([
        dict(key=f"focus-check:{i}", package=package if i % 2 else "com.android.vending",
             label="Home" if i % 2 else "Play Store", title=f"Focus check {i}",
             position=60_000, duration=3_600_000, watched=int(time.time() * 1000) - i * 1000)
        for i in range(20)
    ])
    args.output.mkdir(parents=True, exist_ok=True)
    try:
        write_preferences(ET.tostring(root, encoding="utf-8", xml_declaration=True))
        shell("am", "start", "-W", "-n", f"{package}/.MainActivity")
        time.sleep(1)
        pid = shell("pidof", package)
        for cycle in range(args.cycles):
            # Down to the cards, scroll past the first card, leave, then re-enter.
            keys = ["20"] + ["22"] * 12 + ["19", "19", "20", "20"]
            shell("input", "keyevent", *keys)
            time.sleep(.15)
            current = subprocess.run(adb + ["shell", "pidof", package], capture_output=True,
                                     text=True, timeout=10).stdout.strip()
            if current != pid:
                crash = command("logcat", "-b", "crash", "-d", f"--pid={pid}")
                (args.output / "crash.log").write_bytes(crash)
                raise SystemExit(f"FAIL: Home restarted in cycle {cycle + 1}; see {args.output / 'crash.log'}")
        (args.output / "navigation.png").write_bytes(command("exec-out", "screencap", "-p"))
        print(f"PASS: {args.cycles * len(keys)} directional inputs; Home PID {pid} stayed alive")
    finally:
        shell("am", "force-stop", package)
        write_preferences(original)
        shell("am", "start", "-W", "-n", f"{package}/.MainActivity")


if __name__ == "__main__":
    main()
