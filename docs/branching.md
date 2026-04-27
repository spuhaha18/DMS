# Branching Policy

## Phase 1 (complete)

All Phase 1 work landed directly on `main` — solo development, no remote.
The `phase1-pilot` tag marks the end of that period (2026-04-27).

## Phase 2 onwards

### Trunk

- `main` is always deployable. The pilot environment runs whatever is at `main`.
- Direct pushes to `main` are reserved for trivial fixes (typos, doc tweaks).
- Anything that touches code → feature branch + PR.

### Feature branches

Naming: `<type>/<short-description>`, lowercase, hyphens.

| Type     | Use for                                              | Example                          |
|----------|------------------------------------------------------|----------------------------------|
| `feat`   | New capability                                       | `feat/checkout-workflow`         |
| `fix`    | Bug fix tied to an issue                             | `fix/numbering-race-on-rollback` |
| `chore`  | Tooling, deps, infra                                 | `chore/upgrade-fastapi-0.115`    |
| `docs`   | Docs-only                                            | `docs/runbook-disaster-recovery` |
| `refactor` | Behavior-preserving restructure                    | `refactor/extract-pdf-pipeline`  |
| `test`   | Test-only additions                                  | `test/e2e-checkout`              |

Branch from latest `main`, rebase onto `main` before opening PR.

### Pull requests

- One logical change per PR. Split if review takes >30 min.
- Title follows the same `<type>: <imperative>` convention as commit messages.
- Description: what changed, why, how to verify. Link issue if applicable.
- CI must pass (when CI exists). Until CI is set up: run `pytest` and `npm run build` locally before requesting review.
- Squash-merge by default. Keep merge commits only for milestone integrations (e.g., Phase boundary merges).
- Delete branch after merge.

### Tags

- Phase milestones: `phase<N>-<name>` (e.g., `phase1-pilot`, `phase2-checkout`).
- Pilot deployments: `pilot-YYYY-MM-DD`.
- Production releases (Phase 3+): `vMAJOR.MINOR.PATCH` (semver).

### Commits

- Conventional commits (`feat:`, `fix:`, `chore:`, `docs:`, `refactor:`, `test:`, `perf:`).
- Imperative mood (`add`, not `added`).
- First line ≤72 chars, body wrapped at ~80.
- Reference issues with `Refs #N` or `Fixes #N` in body.
- Co-author trailers preserved when AI-assisted.

### Hotfix

For production incidents (Phase 3+):

1. Branch from the deployed tag: `git switch -c hotfix/<issue> <tag>`
2. Fix + test on the hotfix branch.
3. PR to `main`. After merge, tag a new patch release.
4. Cherry-pick or rebase the fix into any active feature branches.
