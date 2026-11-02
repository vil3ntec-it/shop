#!/bin/bash
# usage: a3-test.sh <worktree> <test files...>  — runs server tests on PGlite, prints pass/fail summary
wt=$1; shift
cd "$wt/server" || exit 2
out=$(TEST_DATABASE_URL=pglite:memory timeout 1200 node --test --test-concurrency=1 --test-reporter=tap "$@" 2>&1)
rc=$?
echo "$out" | grep -E '^# (tests|pass|fail|cancelled|skipped|todo) ' | tr '\n' ' '
echo "rc=$rc"
echo "$out" | grep -E '^not ok' | head -20
