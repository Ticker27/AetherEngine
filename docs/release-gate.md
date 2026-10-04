# Release gate

- The `Release` job in `.github/workflows/aether-engine.yml` is gated on `refs/tags/v*`; on every non-tag run it reports **skipping**. That is the correct state, not a failure.
- The legacy `.github/workflows/release.yml` uses the same `v*` tag gate.
- The repository currently has **0 tags**, so no release artifact exists yet (observed on the d28c3ff runs: `Release skipping`).
- **DECISION 1 HOLD remains in force**: no `v*` tag is created by this PR, by any workflow step, or by any automated process. Tags are created manually by the Commander only.
- When a tag is pushed later, both release jobs run and publish to the same GitHub release using a view-then-upload-or-create pattern (`gh release view` → `upload --clobber`, else `create`).