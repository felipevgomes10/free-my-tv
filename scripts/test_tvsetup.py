"""Tests for Setup and Restore, against a fake TV that carries out the ADB commands it is sent.

Run with: python3 -m unittest discover -s scripts
"""

import copy
import io
import json
import unittest
from contextlib import redirect_stdout
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest import mock

import tvsetup
from tvsetup import HOME, KEY_FILTER, NOTIFICATION_LISTENER, SETUP_WRAITH, STOCK_LAUNCHER, PlanError, Step

BLOAT = ["com.tcl.waterfall.overseas", "com.tcl.smartalexa", "com.tcl.esticker"]


class FakeTv:
    """The owner's TV as far as Setup can tell, before Setup ever ran."""

    def __init__(self, emulator=False):
        self.serial = "emulator-5554" if emulator else "192.168.100.18:5555"
        self.emulator = emulator
        self.enabled = {HOME, STOCK_LAUNCHER, SETUP_WRAITH, *BLOAT, "com.tcl.miracast", "com.google.android.apps.mediashell", "com.android.vending"}
        self.disabled = {"com.tcl.exhibit"}
        self.granted = set()
        self.appop = "default"
        self.secure = {
            "enabled_notification_listeners": "com.google.android.apps.tv.launcherx/.coreservices.notificationlistener.TvNotificationListenerService",
            "enabled_accessibility_services": "",
            "accessibility_enabled": "0",
        }
        self.ran = []


    def snapshot(self):
        return (sorted(self.enabled), sorted(self.disabled), sorted(self.granted), self.appop, dict(self.secure))

    def home(self):
        """What Home opens, as on the owner's TV: setupwraith's RecoveryActivity outranks any third-party Home once the launcher is gone."""
        if STOCK_LAUNCHER in self.enabled:
            return "com.google.android.apps.tv.launcherx/.home.HomeActivity"
        if SETUP_WRAITH in self.enabled:
            return "com.google.android.tungsten.setupwraith/.RecoveryActivity"
        return "com.freemytv.home/.MainActivity"

    def query(self, command):
        c = list(command)
        if c == ["pm", "list", "packages", "-e"]:
            return "".join(f"package:{p}\n" for p in sorted(self.enabled))
        if c == ["pm", "list", "packages", "-d"]:
            return "".join(f"package:{p}\n" for p in sorted(self.disabled))
        if c == ["dumpsys", "package", HOME]:
            return "".join(f"      {p}: granted=true\n" for p in sorted(self.granted))
        if c == ["appops", "get", HOME, "GET_USAGE_STATS"]:
            return f"GET_USAGE_STATS: {self.appop}\n"
        if c[:3] == ["settings", "get", "secure"]:
            return self.secure.get(c[3]) or "null"
        if c == ["getprop", "ro.kernel.qemu"]:
            return "1\n" if self.emulator else "\n"
        raise AssertionError(f"unexpected query {command}")

    def run(self, command):
        c = list(command)
        self.ran.append(c)
        if c[:3] == ["pm", "grant", HOME]:
            self.granted.add(c[3])
        elif c[:3] == ["pm", "revoke", HOME]:
            self.granted.discard(c[3])
        elif c[:4] == ["appops", "set", HOME, "GET_USAGE_STATS"]:
            self.appop = c[4]
        elif c[:3] == ["cmd", "notification", "allow_listener"]:
            self._listeners(lambda items: items + [c[3]])
        elif c[:3] == ["cmd", "notification", "disallow_listener"]:
            self._listeners(lambda items: [i for i in items if i != c[3]])
        elif c[:4] == ["pm", "disable-user", "--user", "0"]:
            self.enabled.discard(c[4])
            self.disabled.add(c[4])
        elif c[:2] == ["pm", "enable"]:
            self.disabled.discard(c[2])
            self.enabled.add(c[2])
        elif c[:3] == ["am", "start", "-n"]:
            # The Home turns its key filter on as soon as it can.
            if "android.permission.WRITE_SECURE_SETTINGS" in self.granted:
                self.secure["enabled_accessibility_services"] = KEY_FILTER
                self.secure["accessibility_enabled"] = "1"
        elif c[:3] == ["settings", "put", "secure"]:
            self.secure[c[3]] = c[4]
        elif c[:3] == ["settings", "delete", "secure"]:
            self.secure[c[3]] = ""
        else:
            raise AssertionError(f"unexpected command {command}")
        return ""

    def _listeners(self, change):
        items = [i for i in self.secure["enabled_notification_listeners"].split(":") if i]
        self.secure["enabled_notification_listeners"] = ":".join(change(items))


def setup_plan(tv, approved=BLOAT):
    return tvsetup.plan_setup(tvsetup.read_state(tv), approved)


class SetupTest(unittest.TestCase):

    def test_setup_grants_the_home_its_access_and_disables_the_stock_launcher_and_approved_bloatware(self):
        tv = FakeTv()

        tvsetup.apply(tv, setup_plan(tv), out=lambda _: None)

        self.assertEqual(set(tvsetup.GRANTED_PERMISSIONS), tv.granted)
        self.assertEqual("allow", tv.appop)
        self.assertIn(NOTIFICATION_LISTENER, tv.secure["enabled_notification_listeners"])
        self.assertTrue({STOCK_LAUNCHER, *BLOAT} <= tv.disabled)
        self.assertEqual(KEY_FILTER, tv.secure["enabled_accessibility_services"])
        self.assertEqual("com.freemytv.home/.MainActivity", tv.home())
        self.assertIn(SETUP_WRAITH, tv.disabled)

    def test_every_package_change_is_disable_user_and_nothing_is_ever_uninstalled(self):
        tv = FakeTv()
        setup = setup_plan(tv)
        tvsetup.apply(tv, setup, out=lambda _: None)
        restore = tvsetup.plan_restore(setup, tvsetup.read_state(tv))

        package_changes = [s.command for s in setup if s.command[0] == "pm" and s.command[1] not in ("grant", "revoke")]
        self.assertTrue(package_changes)
        self.assertTrue(all(c[:2] == ["pm", "disable-user"] for c in package_changes))
        for step in setup + restore:
            for command in (step.command, step.undo or []):
                self.assertNotIn("uninstall", command)

    def test_a_protected_package_in_the_approved_list_refuses_the_whole_plan(self):
        for protected in ["com.google.android.apps.mediashell", "com.tcl.airplay2", "com.tcl.miracast", "com.tcl.tvinput", "com.android.vending", HOME]:
            with self.subTest(protected=protected), self.assertRaises(PlanError):
                setup_plan(FakeTv(), BLOAT + [protected])

    def test_the_last_check_before_running_also_refuses_protected_packages_and_uninstalls(self):
        with self.assertRaises(PlanError):
            tvsetup.check_plan([Step(["pm", "disable-user", "--user", "0", "com.tcl.airplay2"], None, "x")])
        with self.assertRaises(PlanError):
            tvsetup.check_plan([Step(["pm", "uninstall", "com.tcl.esticker"], None, "x")])

    def test_setup_only_plans_what_is_not_in_place_yet(self):
        tv = FakeTv()
        tvsetup.apply(tv, setup_plan(tv), out=lambda _: None)

        again = setup_plan(tv)

        self.assertEqual([["am", "start", "-n", f"{HOME}/.MainActivity"]], [s.command for s in again])

    def test_setup_refuses_a_tv_without_the_home(self):
        tv = FakeTv()
        tv.enabled.discard(HOME)

        with self.assertRaises(PlanError):
            setup_plan(tv)


    def test_setup_disables_google_tvs_setup_app_before_the_stock_launcher(self):
        commands = [s.command for s in setup_plan(FakeTv())]

        wraith = commands.index(["pm", "disable-user", "--user", "0", SETUP_WRAITH])
        launcher = commands.index(["pm", "disable-user", "--user", "0", STOCK_LAUNCHER])
        self.assertLess(wraith, launcher)

    def test_with_only_the_launcher_disabled_home_lands_on_the_recovery_screen(self):
        tv = FakeTv()
        tv.run(["pm", "disable-user", "--user", "0", STOCK_LAUNCHER])

        self.assertEqual("com.google.android.tungsten.setupwraith/.RecoveryActivity", tv.home())


class RestoreTest(unittest.TestCase):

    def test_restore_from_the_journal_puts_the_tv_back_exactly_as_it_was(self):
        tv = FakeTv()
        before = copy.deepcopy(tv.snapshot())
        done = tvsetup.apply(tv, setup_plan(tv), out=lambda _: None)

        tvsetup.apply(tv, tvsetup.plan_restore(done, tvsetup.read_state(tv)), out=lambda _: None)

        self.assertEqual(before[:4], tv.snapshot()[:4])
        self.assertEqual("com.google.android.apps.tv.launcherx/.home.HomeActivity", tv.home())
        self.assertEqual(before[4]["enabled_notification_listeners"], tv.secure["enabled_notification_listeners"])
        self.assertEqual("", tv.secure["enabled_accessibility_services"])
        self.assertEqual("0", tv.secure["accessibility_enabled"])

    def test_restore_leaves_alone_what_was_already_that_way_before_setup(self):
        tv = FakeTv()
        tv.granted.add("android.permission.DUMP")
        done = tvsetup.apply(tv, setup_plan(tv), out=lambda _: None)

        tvsetup.apply(tv, tvsetup.plan_restore(done, tvsetup.read_state(tv)), out=lambda _: None)

        self.assertEqual({"android.permission.DUMP"}, tv.granted)
        self.assertIn("com.tcl.exhibit", tv.disabled)

    def test_restore_undoes_the_last_change_first_and_turns_the_key_filter_off_after_revoking_its_permission(self):
        tv = FakeTv()
        done = tvsetup.apply(tv, setup_plan(tv), out=lambda _: None)

        restore = tvsetup.plan_restore(done, tvsetup.read_state(tv))

        commands = [s.command for s in restore]
        self.assertEqual(["pm", "enable", STOCK_LAUNCHER], commands[0])
        self.assertEqual(["pm", "enable", SETUP_WRAITH], commands[1])
        revoke = commands.index(["pm", "revoke", HOME, "android.permission.WRITE_SECURE_SETTINGS"])
        key_filter = commands.index(["settings", "delete", "secure", "enabled_accessibility_services"])
        self.assertLess(revoke, key_filter)

    def test_restore_keeps_other_accessibility_services(self):
        tv = FakeTv()
        done = tvsetup.apply(tv, setup_plan(tv), out=lambda _: None)
        tv.secure["enabled_accessibility_services"] = f"com.example/.Reader:{KEY_FILTER}"

        tvsetup.apply(tv, tvsetup.plan_restore(done, tvsetup.read_state(tv)), out=lambda _: None)

        self.assertEqual("com.example/.Reader", tv.secure["enabled_accessibility_services"])

    def test_without_the_journal_restore_still_brings_back_the_stock_launcher_and_revokes_everything(self):
        tv = FakeTv()
        tvsetup.apply(tv, setup_plan(tv), out=lambda _: None)

        tvsetup.apply(tv, tvsetup.plan_restore_without_journal(tvsetup.read_state(tv), BLOAT), out=lambda _: None)

        self.assertTrue({STOCK_LAUNCHER, SETUP_WRAITH, *BLOAT} <= tv.enabled)
        self.assertEqual(set(), tv.granted)
        self.assertNotIn(NOTIFICATION_LISTENER, tv.secure["enabled_notification_listeners"])
        self.assertEqual("", tv.secure["enabled_accessibility_services"])


class CommandLineTest(unittest.TestCase):

    def run_main(self, tv, *args, home=None):
        out = io.StringIO()
        with TemporaryDirectory() as tmp, mock.patch("tvsetup.Adb", return_value=tv), mock.patch("pathlib.Path.home", return_value=Path(home or tmp)):
            approved = Path(tmp) / "approved.txt"
            approved.write_text("# approved by the owner\n" + "\n".join(BLOAT) + "\n")
            with redirect_stdout(out):
                code = tvsetup.main([*args, "--serial", tv.serial, "--approved", str(approved)])
        return code, out.getvalue()

    def test_without_apply_the_plan_is_printed_and_nothing_changes(self):
        tv = FakeTv()

        code, out = self.run_main(tv, "setup")

        self.assertEqual(0, code)
        self.assertIn(f"pm disable-user --user 0 {STOCK_LAUNCHER}", out)
        self.assertIn("Dry run: nothing changed.", out)
        self.assertEqual([], tv.ran)

    def test_restore_also_prints_its_plan_without_apply(self):
        tv = FakeTv(emulator=True)
        with TemporaryDirectory() as home:
            self.run_main(tv, "setup", "--apply", home=home)
            code, out = self.run_main(tv, "restore", home=home)

        self.assertEqual(0, code)
        self.assertIn(f"pm enable {STOCK_LAUNCHER}", out)
        self.assertIn("Dry run: nothing changed.", out)

    def test_apply_on_a_real_tv_needs_a_person_at_the_terminal(self):
        tv = FakeTv(emulator=False)

        with mock.patch("sys.stdin.isatty", return_value=False):
            code, _ = self.run_main(tv, "setup", "--apply")

        self.assertEqual(1, code)
        self.assertEqual([], tv.ran)

    def test_apply_on_an_emulator_runs_and_leaves_a_journal_for_restore(self):
        tv = FakeTv(emulator=True)
        with TemporaryDirectory() as home:
            journal = Path(home) / ".freemytv" / "setup-emulator-5554.json"
            code, out = self.run_main(tv, "setup", "--apply", home=home)
            self.assertEqual(0, code)
            self.assertTrue(journal.exists())
            code, _ = self.run_main(tv, "restore", "--apply", home=home)
            self.assertEqual(0, code)
            self.assertFalse(journal.exists())
        self.assertIn(STOCK_LAUNCHER, tv.enabled)

    def test_running_setup_again_adds_to_the_journal_and_restore_still_undoes_everything(self):
        tv = FakeTv(emulator=True)
        before = copy.deepcopy(tv.snapshot())
        with TemporaryDirectory() as home:
            self.run_main(tv, "setup", "--apply", home=home)
            journal = Path(home) / ".freemytv" / "setup-emulator-5554.json"
            # As if the first Setup came from the version that left Google TV's setup app enabled.
            entries = [e for e in json.loads(journal.read_text()) if e["command"][-1] != SETUP_WRAITH]
            journal.write_text(json.dumps(entries))
            tv.run(["pm", "enable", SETUP_WRAITH])
            self.run_main(tv, "setup", "--apply", home=home)
            self.assertEqual(len(entries) + 1, len(json.loads(journal.read_text())))
            code, _ = self.run_main(tv, "restore", "--apply", home=home)

        self.assertEqual(0, code)
        self.assertEqual(before[:4], tv.snapshot()[:4])
        self.assertEqual("com.google.android.apps.tv.launcherx/.home.HomeActivity", tv.home())

    def test_restore_without_a_journal_asks_for_the_flag(self):
        tv = FakeTv()

        code, _ = self.run_main(tv, "restore")

        self.assertEqual(1, code)


class CandidatesTest(unittest.TestCase):

    def test_candidates_list_only_enabled_extras_with_a_reason_and_never_protected_ones(self):
        tv = FakeTv()

        lines = tvsetup.candidates(tvsetup.read_state(tv))

        listed = [line for line in lines if line.startswith("- ")]
        self.assertEqual(sorted(BLOAT), [line.split("`")[1] for line in listed])
        self.assertTrue(all(": " in line for line in listed))
        self.assertFalse(set(tvsetup.CANDIDATES) & set(tvsetup.PROTECTED))


if __name__ == "__main__":
    unittest.main()
