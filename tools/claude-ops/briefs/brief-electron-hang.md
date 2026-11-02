# CI: «آزمون‌های سرور زیرِ Electron» intermittently hangs until the 45-min cap
Evidence: main 171c7d65 run 41416339557 attempt 1 — silent after test 2418 (sync-epoch.test.js), passed on attempt 2 (35 min).
Batch-9 #826 d7754aee run 41425897417 job 125212302467 — silent after test 140 (last test of api.test.js) at 10:15:59, cancelled 11:00.
Runner: server/scripts/test-pglite.js (spawnSync node --test --test-concurrency=1 --test-force-exit; header comment documents
earlier hangs: reauth, staff-code, admin-ops-image-bank, migrate-eol; earlier root cause = PGlite re-created after closeDb).
All server timers in src/lib/* are unref'd (checked). Hang = after a file's last test ⇒ suspect an after()/teardown hook that
never resolves (e.g. closeDb/h.stop awaiting PGlite under Electron) — --test-force-exit only fires after hooks finish.
Goal: (1) make a hang fail FAST and NAMED: per-file watchdog (no output for N min ⇒ print file + pending hook, kill, exit 1),
or per-hook timeouts in the shared test helper (h.stop/closeDb with a cap + diagnostic); (2) find and fix the real cause if
reproducible (Electron 38 ELECTRON_RUN_AS_NODE, PGlite close); never skip/disable tests; keep PG/PGlite jobs unaffected.
