#!/usr/bin/env python3
"""Setup and Restore for the Free My TV Home, run from the owner's computer over ADB.

Setup disables the Stock Launcher and the owner-approved bloatware (always
`pm disable-user`, never uninstall) and grants the Home the access it needs.
Restore undoes exactly what Setup changed, from the journal Setup leaves on
this computer. Both print their plan and change nothing unless given --apply;
on a real TV, --apply also asks the owner to confirm at the terminal.

    python3 scripts/tvsetup.py setup --serial 192.168.100.18:5555
    python3 scripts/tvsetup.py setup --serial 192.168.100.18:5555 --apply
    python3 scripts/tvsetup.py restore --serial 192.168.100.18:5555 --apply
    python3 scripts/tvsetup.py candidates --serial 192.168.100.18:5555
"""

import argparse
import json
import subprocess
import sys
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Callable, Dict, List, Optional, Sequence, Set

HOME = "com.freemytv.home"
STOCK_LAUNCHER = "com.google.android.apps.tv.launcherx"
# Google TV's setup app. Once the Stock Launcher is gone it enables a RecoveryActivity that outranks
# any third-party Home, tries to bring the launcher back, fails and closes, over and over (seen on
# the owner's TV, 2026-10-23). Disabled together with the launcher, and before it.
SETUP_WRAITH = "com.google.android.tungsten.setupwraith"
STOCK_HOME = [SETUP_WRAITH, STOCK_LAUNCHER]
NOTIFICATION_LISTENER = f"{HOME}/{HOME}.android.HomeNotificationListener"
KEY_FILTER = f"{HOME}/{HOME}.android.KeyFilterService"
GRANTED_PERMISSIONS = [
    "android.permission.DUMP",
    "android.permission.PACKAGE_USAGE_STATS",
    "android.permission.WRITE_SECURE_SETTINGS",
]
# App-ops Setup allows, with what each is for.
ALLOWED_APP_OPS = {
    "GET_USAGE_STATS": "usage history",
    # APK Drop, Update Sources and the Home's own updates.
    "REQUEST_INSTALL_PACKAGES": "installing apps",
}

# Never disabled, whatever the approved list says (ADR 0002 and ticket 13).
PROTECTED = {
    "com.google.android.apps.mediashell": "Chromecast",
    "com.tcl.airplay2": "AirPlay",
    "com.amlogic.airplay": "AirPlay",
    "com.amlogic.airplay.daemon": "AirPlay",
    "com.tcl.miracast": "Miracast",
    "com.tcl.tvinput": "TV inputs",
    "com.tcl.tv": "TV player",
    "com.tcl.systemserver": "TCL system services",
    "com.tcl.guard": "TCL system guard",
    "com.google.android.apps.tv.dreamx": "screensaver",
    "com.google.android.katniss": "Assistant",
    "com.google.android.gms": "Google Play services (system updater)",
    "com.android.vending": "Play Store",
    "com.android.tv.settings": "Android Settings",
    "com.android.systemui": "system UI",
    "android": "Android",
    HOME: "the Home itself",
}

# TCL and Google TV extras seen on the owner's TV, with why they're candidates.
# Only a suggestion: the owner approves each one into config/bloatware-approved.txt.
CANDIDATES = {
    "com.tcl.waterfall.overseas": "TCL's content feed for its own home screen, unused with the Home",
    "com.tcl.tv.tclhome_passive": "TCL home screen helper, unused with the Home",
    "com.tcl.smartalexa": "Alexa integration",
    "com.tcl.esticker": "promotional stickers and banners",
    "com.tcl.exhibit": "store demo mode",
    "com.tcl.gamebar": "TCL game overlay",
    "com.tcl.channelplus": "TCL Channel+ free channels (an app, not the antenna)",
    "com.tcl.messagebox": "TCL promotional messages",
    "com.tcl.usercenter": "TCL account",
    "com.tcl.magiconnectfree": "TCL phone companion (Casting stays with Chromecast, AirPlay and Miracast)",
    "com.tcl.ocean.instructions": "built-in manual",
    "com.tcl.repairguide": "repair guide",
    "com.tcl.hotelmenu": "hotel mode menu",
    "com.tcl.logkit": "TCL log collection",
    "com.tcl.interactive": "interactive TV overlays",
}


class PlanError(Exception):
    """A plan that must not run."""


@dataclass(frozen=True)
class Step:
    """One ADB shell command and how to undo it; `undo` is None for steps with nothing to undo."""

    command: List[str]
    undo: Optional[List[str]]
    why: str


class Adb:
    """Runs `adb -s SERIAL shell ...`. `query` is for read-only commands, `run` for changes."""

    def __init__(self, serial: str):
        self.serial = serial

    def _shell(self, command: Sequence[str]) -> str:
        result = subprocess.run(["adb", "-s", self.serial, "shell", *command], capture_output=True, text=True)
        if result.returncode != 0:
            raise RuntimeError(f"adb failed: {' '.join(command)}\n{result.stderr.strip()}")
        return result.stdout

    def query(self, command: Sequence[str]) -> str:
        return self._shell(command)

    def run(self, command: Sequence[str]) -> str:
        return self._shell(command)


@dataclass
class DeviceState:
    """What the planner reads from the TV before deciding anything."""

    enabled: Set[str]
    disabled: Set[str]
    granted: Set[str]
    allowed_app_ops: Set[str]
    notification_listeners: List[str]
    accessibility_services: List[str]
    is_emulator: bool


def read_state(adb: Adb) -> DeviceState:
    def packages(flag: str) -> Set[str]:
        return {line.split(":", 1)[1].strip() for line in adb.query(["pm", "list", "packages", flag]).splitlines() if line.startswith("package:")}

    def tolerant(command: List[str]) -> str:
        # Before the Home is installed these commands fail; that just means nothing is granted yet.
        try:
            return adb.query(command)
        except RuntimeError:
            return ""

    dump = tolerant(["dumpsys", "package", HOME])
    granted = {p for p in GRANTED_PERMISSIONS if f"{p}: granted=true" in dump}

    def secure_list(name: str) -> List[str]:
        value = adb.query(["settings", "get", "secure", name]).strip()
        return [] if value in ("", "null") else [item for item in value.split(":") if item]

    return DeviceState(
        enabled=packages("-e"),
        disabled=packages("-d"),
        granted=granted,
        allowed_app_ops={op for op in ALLOWED_APP_OPS if f"{op}: allow" in tolerant(["appops", "get", HOME, op])},
        notification_listeners=secure_list("enabled_notification_listeners"),
        accessibility_services=secure_list("enabled_accessibility_services"),
        is_emulator=adb.query(["getprop", "ro.kernel.qemu"]).strip() == "1",
    )


def check_approved(approved: Sequence[str]) -> None:
    protected = sorted(p for p in approved if p in PROTECTED)
    if protected:
        names = ", ".join(f"{p} ({PROTECTED[p]})" for p in protected)
        raise PlanError(f"These packages are protected and are never disabled: {names}. Remove them from the approved list.")


def plan_setup(state: DeviceState, approved: Sequence[str]) -> List[Step]:
    """Only what isn't in place yet, so the journal records exactly what Setup changed."""
    check_approved(approved)
    if HOME not in state.enabled:
        raise PlanError(f"The Home ({HOME}) isn't installed on this device. Install it first.")
    steps: List[Step] = []
    for permission in GRANTED_PERMISSIONS:
        if permission not in state.granted:
            steps.append(Step(["pm", "grant", HOME, permission], ["pm", "revoke", HOME, permission], f"grant {permission.rsplit('.', 1)[1]}"))
    for op, what in ALLOWED_APP_OPS.items():
        if op not in state.allowed_app_ops:
            steps.append(Step(["appops", "set", HOME, op, "allow"], ["appops", "set", HOME, op, "default"], f"allow {what}"))
    if NOTIFICATION_LISTENER not in state.notification_listeners:
        steps.append(Step(["cmd", "notification", "allow_listener", NOTIFICATION_LISTENER], ["cmd", "notification", "disallow_listener", NOTIFICATION_LISTENER], "allow reading notifications"))
    for package in [*sorted(set(approved) - set(STOCK_HOME)), *STOCK_HOME]:
        if package in state.enabled:
            why = {
                STOCK_LAUNCHER: "disable the Stock Launcher",
                SETUP_WRAITH: "disable Google TV's setup app, whose recovery screen would take over Home",
            }.get(package) or f"disable {package} ({CANDIDATES.get(package, 'approved by the owner')})"
            steps.append(Step(["pm", "disable-user", "--user", "0", package], ["pm", "enable", package], why))
    # The Home turns its key filter on itself once it holds WRITE_SECURE_SETTINGS; opening it does that now.
    steps.append(Step(["am", "start", "-n", f"{HOME}/.MainActivity"], None, "open the Home, which turns on its key filter"))
    return steps


def plan_restore(journal: List[Step], state: DeviceState) -> List[Step]:
    """The exact inverse of what Setup changed, last change first, then the key filter the Home turned on.

    The key filter goes last: once WRITE_SECURE_SETTINGS is revoked the Home can't turn it back on.
    """
    steps = [Step(step.undo, None, f"undo: {step.why}") for step in reversed(journal) if step.undo is not None]
    return steps + key_filter_off(state)


def key_filter_off(state: DeviceState) -> List[Step]:
    if KEY_FILTER not in state.accessibility_services:
        return []
    remaining = [s for s in state.accessibility_services if s != KEY_FILTER]
    if remaining:
        return [Step(["settings", "put", "secure", "enabled_accessibility_services", ":".join(remaining)], None, "turn off the Home's key filter")]
    return [
        Step(["settings", "delete", "secure", "enabled_accessibility_services"], None, "turn off the Home's key filter"),
        Step(["settings", "put", "secure", "accessibility_enabled", "0"], None, "no accessibility service left"),
    ]


def plan_restore_without_journal(state: DeviceState, approved: Sequence[str]) -> List[Step]:
    """When the journal is lost: undo whatever of Setup's changes is in place now."""
    check_approved(approved)
    steps: List[Step] = []
    for package in [STOCK_LAUNCHER, SETUP_WRAITH, *sorted(set(approved) - set(STOCK_HOME))]:
        if package in state.disabled:
            steps.append(Step(["pm", "enable", package], None, f"enable {package}"))
    if NOTIFICATION_LISTENER in state.notification_listeners:
        steps.append(Step(["cmd", "notification", "disallow_listener", NOTIFICATION_LISTENER], None, "stop reading notifications"))
    for op, what in reversed(list(ALLOWED_APP_OPS.items())):
        if op in state.allowed_app_ops:
            steps.append(Step(["appops", "set", HOME, op, "default"], None, f"reset access to {what}"))
    for permission in reversed(GRANTED_PERMISSIONS):
        if permission in state.granted:
            steps.append(Step(["pm", "revoke", HOME, permission], None, f"revoke {permission.rsplit('.', 1)[1]}"))
    return steps + key_filter_off(state)


def check_plan(steps: Sequence[Step]) -> None:
    """The rules no plan may break, checked again right before anything runs."""
    for step in steps:
        for command in (step.command, step.undo):
            if command is None:
                continue
            if command[:2] == ["pm", "uninstall"] or "uninstall" in command:
                raise PlanError(f"A plan never uninstalls anything: {' '.join(command)}")
            if command[:2] in (["pm", "disable-user"], ["pm", "disable"], ["pm", "hide"]) and command[-1] in PROTECTED:
                raise PlanError(f"A plan never touches a protected package: {' '.join(command)}")


def candidates(state: DeviceState) -> List[str]:
    lines = ["# Bloatware candidates", "", "Enabled packages on this TV that Setup may disable. Copy the ones you approve into config/bloatware-approved.txt, one per line.", ""]
    for package in sorted(p for p in CANDIDATES if p in state.enabled and p not in PROTECTED):
        lines.append(f"- `{package}`: {CANDIDATES[package]}")
    return lines


def journal_path(serial: str) -> Path:
    return Path.home() / ".freemytv" / f"setup-{serial.replace(':', '_')}.json"


def save_journal(serial: str, steps: Sequence[Step]) -> Path:
    """Adds to the journal: running Setup again must not forget what an earlier Setup changed."""
    path = journal_path(serial)
    path.parent.mkdir(parents=True, exist_ok=True)
    journal = (load_journal(serial) or []) + [s for s in steps if s.undo is not None]
    path.write_text(json.dumps([asdict(s) for s in journal], indent=2))
    return path


def load_journal(serial: str) -> Optional[List[Step]]:
    path = journal_path(serial)
    if not path.exists():
        return None
    return [Step(**raw) for raw in json.loads(path.read_text())]


def read_approved(path: Path) -> List[str]:
    if not path.exists():
        return []
    return [line.split("#", 1)[0].strip() for line in path.read_text().splitlines() if line.split("#", 1)[0].strip()]


def print_plan(title: str, steps: Sequence[Step], out: Callable[[str], None] = print) -> None:
    out(f"{title}: {len(steps)} step(s)")
    for i, step in enumerate(steps, 1):
        out(f"  {i:2}. adb shell {' '.join(step.command)}")
        out(f"      ({step.why})")


def confirm_on_tv(state: DeviceState, serial: str, ask: Callable[[str], str] = input) -> bool:
    if state.is_emulator:
        return True
    if not sys.stdin.isatty():
        print("Refusing to change a real TV without a person at the terminal.", file=sys.stderr)
        return False
    return ask(f"This changes the TV at {serial}. Type 'sim' to continue: ").strip().lower() == "sim"


class StepFailed(Exception):
    """A step failed; everything before it ran, and the step itself may or may not have."""

    def __init__(self, step: Step, cause: Exception):
        super().__init__(f"{' '.join(step.command)}: {cause}")
        self.step = step


def apply(adb: Adb, steps: Sequence[Step], out: Callable[[str], None] = print, record: Callable[[Step], None] = lambda _: None) -> List[Step]:
    """Runs the steps in order, recording each as it runs, and stops at the first failure.

    A failed step is recorded too: the connection can drop after the TV carried it out (it did on
    the owner's TV), and undoing a step that never happened is harmless.
    """
    check_plan(steps)
    done: List[Step] = []
    for step in steps:
        out(f"  running: adb shell {' '.join(step.command)}")
        try:
            adb.run(step.command)
        except RuntimeError as e:
            record(step)
            raise StepFailed(step, e) from e
        record(step)
        done.append(step)
    return done


def main(argv: Optional[Sequence[str]] = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("action", choices=["setup", "restore", "candidates"])
    parser.add_argument("--serial", required=True, help="the device, e.g. 192.168.100.18:5555 or emulator-5554")
    parser.add_argument("--apply", action="store_true", help="actually run the plan (default: only print it)")
    parser.add_argument("--approved", type=Path, default=Path(__file__).resolve().parent.parent / "config" / "bloatware-approved.txt")
    parser.add_argument("--without-journal", action="store_true", help="restore: undo everything Setup could have done")
    args = parser.parse_args(argv)

    adb = Adb(args.serial)
    state = read_state(adb)
    approved = read_approved(args.approved)

    try:
        if args.action == "candidates":
            print("\n".join(candidates(state)))
            return 0
        if args.action == "setup":
            steps = plan_setup(state, approved)
        else:
            journal = load_journal(args.serial)
            if journal is None and not args.without_journal:
                print(f"No Setup journal for {args.serial} at {journal_path(args.serial)}. Use --without-journal to undo everything Setup could have done.", file=sys.stderr)
                return 1
            steps = plan_restore(journal, state) if journal is not None else plan_restore_without_journal(state, approved)
        check_plan(steps)
    except PlanError as e:
        print(f"Plan refused: {e}", file=sys.stderr)
        return 1

    print_plan(f"{args.action.capitalize()} plan for {args.serial}", steps)
    if not args.apply:
        print("Dry run: nothing changed. Add --apply to run it.")
        return 0
    if not confirm_on_tv(state, args.serial):
        print("Nothing changed.")
        return 1
    record = (lambda step: save_journal(args.serial, [step])) if args.action == "setup" else (lambda _: None)
    try:
        apply(adb, steps, record=record)
    except StepFailed as e:
        print(f"\nStopped: {e}", file=sys.stderr)
        if args.action == "setup":
            print(f"What ran so far is in the journal ({journal_path(args.serial)}). Check the TV, then run Setup again to finish, or Restore to undo.", file=sys.stderr)
        else:
            print("Restore stopped part-way; the journal is kept. Check the TV and run Restore again.", file=sys.stderr)
        return 1
    if args.action == "setup":
        print(f"Setup applied. Journal: {journal_path(args.serial)}")
    else:
        journal_path(args.serial).unlink(missing_ok=True)
        print("Restore applied.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
