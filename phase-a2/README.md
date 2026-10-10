# phase-a2/ — A2 evidence scaffold

Scaffold only — **fill on A2 execution day.**

- `evidence/` is intentionally empty. It is populated only when Phase A2 is executed on an arm64-v8a
  device (API 28+); the artifacts to produce are listed in `docs/phase-a2-plan.md` § A2.9.
- `.gitkeep` in `evidence/` exists so the empty directory survives the commit; it carries no evidence.
- The run order is `docs/phase-a2-plan.md` § A2.1–A2.9; the locked device spec is
  `docs/decisions/a2-device-spec.md`.
- Phase 2 evidence belongs in a separate `phase2/evidence/` directory and stays closed until A2 closes
  (`docs/phase2-plan.md`).
