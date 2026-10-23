#!/usr/bin/env bash
# Restore for the Free My TV Home: undoes what Setup changed. Prints the plan; add --apply to run it.
# Usage: scripts/restore.sh --serial 192.168.100.18:5555 [--apply] [--without-journal]
exec python3 "$(dirname "$0")/tvsetup.py" restore "$@"
