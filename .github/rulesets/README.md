# `nextgen` repository ruleset

The versioned payload in `nextgen.json` is the required protection contract for
the fork's `nextgen` branch. It blocks branch deletion and force pushes,
requires a linear history, permits only pull-request-based rebase merges with
resolved threads, and requires the `quality-gate` and `release-build` job checks
from `build.yml`.

Applying the file is deliberately an external repository-admin action. After
this change is merged and both job names have appeared at least once, an
administrator must open **Settings > Rules > Rulesets > New ruleset > Import a
ruleset**, select `nextgen.json`, review it, and create it as **Active**. The
same payload can be inspected without applying it using GitHub's
repository-ruleset API documentation.

Also set **Settings > General > Pull Requests** to enable rebase merging.
Disable merge commits and squash merging so the repository-level choices agree
with the ruleset's rebase-only policy. Do not add bypass actors.

Verification after the external action:

1. Open **Settings > Rules > Rulesets** and confirm `Protect nextgen` is Active
   for `refs/heads/nextgen`.
2. Confirm `quality-gate` and `release-build` are shown as required status
   checks.
3. Confirm `.github/CODEOWNERS` requests `@AlexanderDotH`, while required
   approvals remain zero and last-push approval remains disabled. Alex is the
   repository's only collaborator, so either approval requirement would
   deadlock Alex-authored pull requests.
4. Confirm a direct update, force push, and branch deletion are rejected for an
   account without admin permissions.
5. Confirm an up-to-date pull request can merge only with the rebase method
   after both checks pass and every review thread is resolved.

The local `verifyRepositoryPolicy` task validates the payload and its
relationship to CI. It cannot prove that the ruleset has been imported into
GitHub, so final activation remains explicitly external.
