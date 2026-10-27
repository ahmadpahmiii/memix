package app.memix.debug

/**
 * The deliberate crash of the Crashlytics hand check (P0-08, P1-15): `--ez memix.testCrash true` in a debug or
 * benchmark build. It is its own class so that R8 renames it in the benchmark build: the console shows
 * `app.memix.debug.MemixTestCrash` only when the uploaded mapping file was applied, and a short scrambled name when
 * it wasn't. Steps: docs/qa/phase-1/engineer-hand-checks.md -> P1-15.
 */
class MemixTestCrash : RuntimeException("Memix test crash")
