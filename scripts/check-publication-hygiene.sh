#!/usr/bin/env sh
# Publication hygiene: no machine-local content in tracked files.
#
# Deterministic complement to the advisory documentation-QA review: a
# published repository must not carry the authoring machine's filesystem
# into its content. Two checks, both over tracked files only:
#
#   1. No per-machine tool state is tracked (.claude/settings.local.json
#      and kin) — such files carry absolute local paths by design.
#   2. No tracked text file contains a machine-local path: a macOS or
#      Windows home directory, or a Linux home directory with a
#      tell-tale personal segment. The Linux pattern is deliberately
#      narrow so container-internal paths (/home/appuser/data in a
#      Dockerfile) and URLs whose route contains /home/ never trip it.
#
# Deliberately published identities (a Maven developers id, an author
# line) match neither pattern. Documented commands should be
# location-independent — repo-relative, or anchored with
# "$(git rev-parse --show-toplevel)".
set -eu

status=0

tracked_local_state=$(git ls-files | grep -E '(^|/)\.claude/settings\.local\.json$' || true)
if [ -n "$tracked_local_state" ]; then
    echo "$tracked_local_state"
    echo "error: machine-local tool state is tracked — untrack and gitignore it" >&2
    status=1
fi

pattern='/Users/[A-Za-z0-9._-]+/|C:\\+Users\\+|/home/[A-Za-z0-9._-]+/(workspace|Desktop|Documents|Downloads|\.claude|\.config|\.m2|\.cargo)'
hits=$(git grep -n -I -E "$pattern" -- ':!scripts/check-publication-hygiene.sh' || true)
if [ -n "$hits" ]; then
    echo "$hits"
    echo "error: machine-local filesystem paths in tracked files — make them location-independent" >&2
    status=1
fi

exit $status
