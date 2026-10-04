# Local sync (NEW-1)

A local `main` can lag `origin/main` after a merge. Sync with:

```bash
git status --short          # confirm nothing uncommitted first
git fetch origin
git checkout main
git reset --hard origin/main
```

After this closure round: `origin/main` = `d28c3ff12f22fc6df1446756dd798cdf4bf405a4` (PR #8, "Phase 1 clean rewrite cleanup").

`git reset --hard` discards local-only work on the branch — run it only after `git status --short` is empty, as done in this round (tree was clean; HEAD went from `5f17369` to `d28c3ff`).