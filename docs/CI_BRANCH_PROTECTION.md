# Branch protection — keep `main` un-breakable

**Status:** operations guidance (2026-07-18).

`main` has twice merged code that did not compile / parse, even though every
change was reviewed. Both slipped through the same hole: **a follow-up commit
landed on a PR branch without CI passing on that final commit.** This doc says
how to close the hole.

## What happened (two incidents, same cause)

1. **Copilot "potential fix" broke a test.** On PR #58, a Copilot
   auto-suggestion (`36aaad2 "Potential fix for pull request finding"`) wrapped an
   executor teardown in `try/finally` but dropped the test method's closing
   brace. `ems-pretrade:compileTestJava` then failed with *illegal start of
   expression*. The original gated commit was clean; the Copilot commit landed on
   top and the squash-merge did not wait for a green re-run.
2. **A malformed e2e spec merged.** `tests/e2e/esp.spec.ts` was missing its
   closing `});`. It merged because **CI runs no Playwright/e2e job**, and
   `npm run build` only type-checks `src/`, not `tests/` — so the file never
   parsed until run locally.

Neither was caught by review, because a human reviewer does not re-compile every
auto-applied suggestion. **Only a required, up-to-date CI check does.**

## Fix 1 — require the CI check before merge (highest value)

Turn on branch protection so a red or missing CI run blocks the merge button.

GitHub → **Settings → Branches → Add branch ruleset** (or *Add rule*) for `main`:

- ✅ **Require status checks to pass before merging**
  - Add these checks (exact job names from `.github/workflows/ci.yml`):
    - `Java build + unit tests`
    - `C++ build + unit tests`
    - `Phase-0 smoke (Aeron Cluster + Archive)`
    - `Schema lint`
    - `Lint (shell)`
- ✅ **Require branches to be up to date before merging** — this is the part that
  catches the incidents above: any *new* commit (a Copilot fix, a rebase) must
  re-run CI green before the button unlocks. Without this box, CI's earlier green
  on an *older* commit is accepted.
- ✅ **Require a pull request before merging** (keeps the review flow; direct
  pushes to `main` for genuine hotfixes can use an admin bypass or a short-lived
  exception).
- Optional: **Require conversation resolution**, **Require linear history**.

CLI equivalent (requires admin + `gh`):

```bash
gh api -X PUT repos/:owner/cross_asset_ems/branches/main/protection \
  -H "Accept: application/vnd.github+json" \
  -f 'required_status_checks[strict]=true' \
  -f 'required_status_checks[contexts][]=Java build + unit tests' \
  -f 'required_status_checks[contexts][]=C++ build + unit tests' \
  -f 'required_status_checks[contexts][]=Phase-0 smoke (Aeron Cluster + Archive)' \
  -f 'required_status_checks[contexts][]=Schema lint' \
  -f 'required_status_checks[contexts][]=Lint (shell)' \
  -F 'enforce_admins=false' \
  -F 'required_pull_request_reviews=null' \
  -F 'restrictions=null'
```

`strict=true` is the "require branches up to date" flag — the important one.

## Fix 2 — make CI actually parse the front-end (closes incident #2)

CI never looks at `ui/trader-desktop/tests/`, so a broken spec cannot fail it.
Cheapest closure — add a type-check step to the `java`/a `ui` job:

```yaml
      - name: UI typecheck (incl. tests)
        run: |
          npm --prefix ui/trader-desktop ci
          npx --prefix ui/trader-desktop tsc --noEmit -p ui/trader-desktop
```

`tsc --noEmit` over the whole project (not just `npm run build`, which is
`src`-only) parses the specs and would have caught the missing `});` in CI.
A fuller closure is a real Playwright job, but that needs the backend edge in CI;
the `tsc` step is the 90%-value, low-cost first move.

## Why this matters for the fleet workflow

Weak external workers (and Copilot suggestions) are used precisely because they
are cheap, and their output is trusted **only after a mechanical gate**. Branch
protection is that gate at the repository boundary: it guarantees the same
compile-plus-test bar the local fleet gate enforces, so no auto-generated change
— however well-reviewed — can reach `main` red.
