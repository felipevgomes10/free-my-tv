#!/usr/bin/env bash
# Setup for the Free My TV Home. Prints the plan; add --apply to run it.
# Usage: scripts/setup.sh --serial 192.168.100.18:5555 [--apply]
exec python3 "$(dirname "$0")/tvsetup.py" setup "$@"
