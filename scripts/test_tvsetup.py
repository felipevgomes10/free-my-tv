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
from tvsetup import HOME, KEY_FILTER, NOTIFICATION_LISTENER, SETUP_WRAITH, STOCK_LAUNCHER, TMDB_FORGET, TMDB_SET, TMDB_STATE, PlanError, Step

BLOAT = ["com.tcl.waterfall.overseas", "com.tcl.smartalexa", "com.tcl.esticker"]
TOKEN = "eyFAKE.tmdb-read-token.for-tests"


class FakeTv:
    """The owner's TV as far as Setup can tell, before Setup ever ran."""

    def __init__(self, emulator=False):
        self.serial = "emulator-5554" if emulator else "192.168.100.18:5555"
        self.emulator = emulator
        self.enabled = {HOME, STOCK_LAUNCHER, SETUP_WRAITH, *BLOAT, "com.tcl.miracast", "com.google.android.apps.mediashell", "com.android.vending"}
        self.disabled = {"com.tcl.exhibit"}
        self.granted = set()
        self.appops = {"GET_USAGE_STATS": "default", "REQUEST_INSTALL_PACKAGES": "default", "WRITE_SETTINGS": "default"}
        self.secure = {
            "enabled_notification_listeners": "com.google.android.apps.tv.launcherx/.coreservices.notificationlistener.TvNotificationListenerService",
            "enabled_accessibility_services": "",
            "accessibility_enabled": "0",
        }
        self.system = {"screen_off_timeout": "2147483647"}
        self.global_settings = {"stay_on_while_plugged_in": "1"}
        self.secure.update({"sleep_timeout": "1200000", "screensaver_activate_on_sleep": "0", "screensaver_enabled": "1", "screensaver_components": "com.google.android.apps.tv.dreamx/.service.Backdrop"})
        self.ran = []
        self.tmdb_token = None
        self.has_screensaver = True


    def snapshot(self):
        return (sorted(self.enabled), sorted(self.disabled), sorted(self.granted), dict(self.appops), dict(self.secure))

    def home(self):
        """What Home opens, as on the owner's TV: setupwraith's RecoveryActivity outranks any third-party Home once the launcher is gone."""
        if STOCK_LAUNCHER in self.enabled:
            return "com.google.android.apps.tv.launcherx/.home.HomeActivity"
        if SETUP_WRAITH in self.enabled:
            return "com.google.android.tungsten.setupwraith/.RecoveryActivity"
        return "com.freemytv.home/.MainActivity"

    def settings(self, namespace):
        return {"secure": self.secure, "system": self.system, "global": self.global_settings}[namespace]

    def query(self, command):
        c = list(command)
        if c == ["pm", "list", "packages", "-e"]:
            return "".join(f"package:{p}\n" for p in sorted(self.enabled))
        if c == ["pm", "list", "packages", "-d"]:
            return "".join(f"package:{p}\n" for p in sorted(self.disabled))
        if c == ["dumpsys", "package", HOME]:
            return ("Service WeatherDreamService\n" if self.has_screensaver else "") + "".join(f"      {p}: granted=true\n" for p in sorted(self.granted))
        if c[:3] == ["appops", "get", HOME] and c[3] in self.appops:
            return f"{c[3]}: {self.appops[c[3]]}\n"
        if c[:2] == ["settings", "get"]:
            return self.settings(c[2]).get(c[3]) or "null"
        if c[:2] == ["am", "broadcast"] and TMDB_STATE in c:
            data = f' data="{tvsetup.sha256(self.tmdb_token)}"' if self.tmdb_token else ""
            return f"Broadcasting: Intent {{ act={TMDB_STATE} }}\nBroadcast completed: result=0{data}\n"
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
        elif c[:3] == ["appops", "set", HOME] and c[3] in self.appops:
            self.appops[c[3]] = c[4]
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
        elif c[:2] == ["am", "broadcast"] and TMDB_SET in c:
            self.tmdb_token = c[c.index("token") + 1]
        elif c[:2] == ["am", "broadcast"] and TMDB_FORGET in c:
            self.tmdb_token = None
        elif c[:2] == ["am", "broadcast"] and any(action in c for action in (tvsetup.SCREENSAVER_APPLY, tvsetup.SCREENSAVER_RESET)):
            pass
        elif c[:2] == ["settings", "put"]:
            self.settings(c[2])[c[3]] = c[4]
        elif c[:2] == ["settings", "delete"]:
            target = self.settings(c[2])
            if c[3] == "enabled_accessibility_services": target[c[3]] = ""
            else: target.pop(c[3], None)
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
        self.assertEqual({"GET_USAGE_STATS": "allow", "REQUEST_INSTALL_PACKAGES": "allow", "WRITE_SETTINGS": "allow"}, tv.appops)
        self.assertIn(NOTIFICATION_LISTENER, tv.secure["enabled_notification_listeners"])
        self.assertTrue({STOCK_LAUNCHER, *BLOAT} <= tv.disabled)
        self.assertEqual(KEY_FILTER, tv.secure["enabled_accessibility_services"])
        self.assertEqual("com.freemytv.home/.MainActivity", tv.home())
        self.assertIn(SETUP_WRAITH, tv.disabled)

    def test_setup_lets_the_home_read_what_apps_list_in_watch_next_and_restore_takes_it_back(self):
        tv = FakeTv()
        setup = setup_plan(tv)
        tvsetup.apply(tv, setup, out=lambda _: None)

        self.assertIn("android.permission.READ_TV_LISTINGS", tv.granted)
        tvsetup.apply(tv, tvsetup.plan_restore(setup, tvsetup.read_state(tv)), out=lambda _: None)
        self.assertNotIn("android.permission.READ_TV_LISTINGS", tv.granted)

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
        enables = [c for c in commands if c[:2] == ["pm", "enable"]]
        self.assertEqual(["pm", "enable", STOCK_LAUNCHER], enables[0])
        self.assertEqual(["pm", "enable", SETUP_WRAITH], enables[1])
        self.assertIn(tvsetup.SCREENSAVER_RESET, commands[0])
        revoke = commands.index(["pm", "revoke", HOME, "android.permission.WRITE_SECURE_SETTINGS"])
        key_filter = commands.index(["settings", "delete", "secure", "enabled_accessibility_services"])
        self.assertLess(revoke, key_filter)

    def test_restore_keeps_other_accessibility_services(self):
        tv = FakeTv()
        done = tvsetup.apply(tv, setup_plan(tv), out=lambda _: None)
        tv.secure["enabled_accessibility_services"] = f"com.example/.Reader:{KEY_FILTER}"

        tvsetup.apply(tv, tvsetup.plan_restore(done, tvsetup.read_state(tv)), out=lambda _: None)

        self.assertEqual("com.example/.Reader", tv.secure["enabled_accessibility_services"])

    def test_a_tv_set_up_before_installing_was_allowed_gets_only_that_step_and_restore_takes_it_back(self):
        tv = FakeTv()
        first = [s for s in setup_plan(tv) if "REQUEST_INSTALL_PACKAGES" not in s.command]
        tvsetup.apply(tv, first, out=lambda _: None)

        second = setup_plan(tv)
        tvsetup.apply(tv, second, out=lambda _: None)

        self.assertEqual([["appops", "set", HOME, "REQUEST_INSTALL_PACKAGES", "allow"]], [s.command for s in second if s.undo])
        self.assertEqual("allow", tv.appops["REQUEST_INSTALL_PACKAGES"])
        tvsetup.apply(tv, tvsetup.plan_restore(first + second, tvsetup.read_state(tv)), out=lambda _: None)
        self.assertEqual("default", tv.appops["REQUEST_INSTALL_PACKAGES"])

    def test_setup_gives_explorar_its_token_and_restore_takes_it_back(self):
        tv = FakeTv()
        setup = tvsetup.plan_setup(tvsetup.read_state(tv), BLOAT, TOKEN)
        tvsetup.apply(tv, setup, out=lambda _: None)
        self.assertEqual(TOKEN, tv.tmdb_token)

        tvsetup.apply(tv, tvsetup.plan_restore([s.public() for s in setup if s.undo], tvsetup.read_state(tv)), out=lambda _: None)

        self.assertIsNone(tv.tmdb_token)

    def test_the_token_is_sent_again_only_when_the_tv_holds_another_one(self):
        tv = FakeTv()
        tvsetup.apply(tv, tvsetup.plan_setup(tvsetup.read_state(tv), BLOAT, TOKEN), out=lambda _: None)

        again = tvsetup.plan_setup(tvsetup.read_state(tv), BLOAT, TOKEN)
        renewed = tvsetup.plan_setup(tvsetup.read_state(tv), BLOAT, TOKEN + "-new")

        self.assertFalse(any(TMDB_SET in s.command for s in again))
        self.assertTrue(any(TMDB_SET in s.command for s in renewed))

    def test_without_a_token_setup_does_everything_else(self):
        tv = FakeTv()

        steps = tvsetup.plan_setup(tvsetup.read_state(tv), BLOAT, None)

        self.assertFalse(any(TMDB_SET in s.command for s in steps))
        self.assertTrue(steps)

    def test_without_the_journal_restore_also_makes_explorar_forget_its_token(self):
        tv = FakeTv()
        tvsetup.apply(tv, tvsetup.plan_setup(tvsetup.read_state(tv), BLOAT, TOKEN), out=lambda _: None)

        tvsetup.apply(tv, tvsetup.plan_restore_without_journal(tvsetup.read_state(tv), BLOAT), out=lambda _: None)

        self.assertIsNone(tv.tmdb_token)

    def test_without_the_journal_restore_still_brings_back_the_stock_launcher_and_revokes_everything(self):
        tv = FakeTv()
        tvsetup.apply(tv, setup_plan(tv), out=lambda _: None)

        tvsetup.apply(tv, tvsetup.plan_restore_without_journal(tvsetup.read_state(tv), BLOAT), out=lambda _: None)

        self.assertTrue({STOCK_LAUNCHER, SETUP_WRAITH, *BLOAT} <= tv.enabled)
        self.assertEqual(set(), tv.granted)
        self.assertEqual({"GET_USAGE_STATS": "default", "REQUEST_INSTALL_PACKAGES": "default", "WRITE_SETTINGS": "default"}, tv.appops)
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

    def test_a_dropped_connection_mid_setup_keeps_the_journal_and_restore_still_undoes_everything(self):
        tv = FakeTv(emulator=True)
        before = copy.deepcopy(tv.snapshot())
        run = tv.run

        def drop_after_disabling_the_launcher(command):
            run(command)
            if command[-1] == STOCK_LAUNCHER and command[1] == "disable-user":
                raise RuntimeError("adb: device offline")
            return ""

        with TemporaryDirectory() as home:
            tv.run = drop_after_disabling_the_launcher
            code, _ = self.run_main(tv, "setup", "--apply", home=home)
            tv.run = run
            journal = json.loads((Path(home) / ".freemytv" / "setup-emulator-5554.json").read_text())
            restore_code, _ = self.run_main(tv, "restore", "--apply", home=home)

        self.assertEqual(1, code)
        self.assertIn(["pm", "disable-user", "--user", "0", STOCK_LAUNCHER], [e["command"] for e in journal])
        self.assertEqual(0, restore_code)
        self.assertEqual(before[:4], tv.snapshot()[:4])

    def test_the_token_is_never_printed_nor_written_to_the_journal(self):
        tv = FakeTv(emulator=True)
        with TemporaryDirectory() as home, mock.patch.dict("os.environ", {"TMDB_TOKEN": TOKEN}):
            dry, dry_out = self.run_main(tv, "setup", home=home)
            code, out = self.run_main(tv, "setup", "--apply", home=home)
            journal = (Path(home) / ".freemytv" / f"setup-{tv.serial.replace(':', '_')}.json").read_text()

        self.assertEqual((0, 0), (dry, code))
        self.assertEqual(TOKEN, tv.tmdb_token)
        self.assertIn("[token]", dry_out)
        for text in (dry_out, out, journal):
            self.assertNotIn(TOKEN, text)

    def test_restore_without_a_journal_asks_for_the_flag(self):
        tv = FakeTv()

        code, _ = self.run_main(tv, "restore")

        self.assertEqual(1, code)


class ScreensaverPlanTest(unittest.TestCase):
    def test_an_old_apk_cannot_disable_the_default_screensaver_before_its_replacement_is_installed(self):
        tv = FakeTv()
        tv.has_screensaver = False
        with self.assertRaises(PlanError): setup_plan(tv)
        self.assertEqual("1", tv.secure["screensaver_enabled"])
        self.assertEqual([], tv.ran)

    def test_setup_disables_the_default_screensaver_and_preserves_the_receivers_package(self):
        tv = FakeTv()
        tv.enabled.add("com.google.android.apps.tv.dreamx")
        tvsetup.apply(tv, setup_plan(tv), out=lambda _: None)
        self.assertEqual("0", tv.secure["screensaver_enabled"])
        self.assertEqual(tvsetup.SCREENSAVER_COMPONENT, tv.secure["screensaver_components"])
        self.assertIn("com.google.android.apps.tv.dreamx", tv.enabled)
        self.assertEqual("allow", tv.appops["WRITE_SETTINGS"])

    def test_restore_recovers_every_original_setting_after_the_owner_changes_the_delay(self):
        for journal in (True, False):
            with self.subTest(journal=journal):
                tv = FakeTv()
                original = {name: tv.settings(namespace).get(name) for name, namespace in tvsetup.SCREENSAVER_SETTINGS.items()}
                done = tvsetup.apply(tv, setup_plan(tv), out=lambda _: None)
                tv.system["screen_off_timeout"] = "120000"
                tv.global_settings["stay_on_while_plugged_in"] = "0"
                tv.secure.update(sleep_timeout="-1", screensaver_activate_on_sleep="1", screensaver_enabled="1")
                state = tvsetup.read_state(tv)
                steps = tvsetup.plan_restore(done, state) if journal else tvsetup.plan_restore_without_journal(state, BLOAT)
                tvsetup.apply(tv, steps, out=lambda _: None)
                actual = {name: tv.settings(namespace).get(name) for name, namespace in tvsetup.SCREENSAVER_SETTINGS.items()}
                self.assertEqual(original, actual)
                self.assertFalse(any(k.startswith(tvsetup.SCREENSAVER_BACKUP) for k in tv.secure))

    def test_rerunning_setup_preserves_the_original_backups_and_an_enabled_weather_screensaver(self):
        tv = FakeTv()
        tvsetup.apply(tv, setup_plan(tv), out=lambda _: None)
        before = dict(tv.secure)
        tv.secure["screensaver_enabled"] = "1"
        tv.system["screen_off_timeout"] = "600000"
        again = setup_plan(tv)
        self.assertEqual([["am", "start", "-n", f"{HOME}/.MainActivity"]], [s.command for s in again])
        self.assertEqual(before[tvsetup.SCREENSAVER_BACKUP + "screen_off_timeout"], tv.secure[tvsetup.SCREENSAVER_BACKUP + "screen_off_timeout"])

    def test_originally_unset_settings_and_a_previously_disabled_screensaver_are_restored_as_such(self):
        tv = FakeTv()
        tv.secure["screensaver_enabled"] = "0"
        tv.secure.pop("sleep_timeout")
        done = tvsetup.apply(tv, setup_plan(tv), out=lambda _: None)
        tv.secure["sleep_timeout"] = "-1"
        tvsetup.apply(tv, tvsetup.plan_restore(done, tvsetup.read_state(tv)), out=lambda _: None)
        self.assertEqual("0", tv.secure["screensaver_enabled"])
        self.assertNotIn("sleep_timeout", tv.secure)


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
