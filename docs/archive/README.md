# Archive — implemented plans and executed one-time records

This folder collects the documents whose work is **done**: plans and briefs that have shipped, and
runbooks or verification runs that were written to be carried out **once** and have been. Each one
is a frozen historical record. It is kept because it explains *why* something was built the way it
was, and because code comments and specs still cite it by name, not because it describes the app
today.

The convention is the main `basetool` repository's (`docs/archive/README.md` there), applied to this
repository on 2026-09-22.

> **Nothing in here is current.** Every file opens with an **Archived** banner that says what it
> recorded and where the current truth lives. For what the app does now, read the requirements in
> [`docs/specs/`](../specs/INDEX.md), the decisions in [`docs/adr/`](../adr/README.md), the binding
> design specification in [`docs/design/android/`](../design/android/README.md), and the living
> documents listed at the end of this page.

## Rules for this folder

- **Archived documents are not edited to track new changes.** Only three kinds of edit are allowed:
  the Archived banner, fixing a link that moved, and removing something that must never be in the
  repository at all (a secret, personal data, a retired contact address).
- **A plan moves here in the PR that finishes it**, or as soon as it is found to be finished. So does
  a one-time runbook once it has been executed. It gets the Archived banner, an entry below, and
  every inbound link — in documents *and* in code comments — is re-pointed to
  `docs/archive/<NAME>.md` in the same change.
- **A procedure that is repeated in normal operations does not belong here**, even if it began life
  inside a one-time document. Move that part into a living document first, then archive the rest.
  That is why [`OWNER_RUNBOOK.md`](../OWNER_RUNBOOK.md) stays in `docs/`: most of its steps are
  done, but cutting a release (§ 4) and raising the served-version floor (§ 5) recur.

## Plans and briefs

| Document | What it planned | Outcome |
| --- | --- | --- |
| [`ANDROID_APP_DESIGN_PROMPT.md`](ANDROID_APP_DESIGN_PROMPT.md) | The Claude Design brief for the app's UI specification | Executed 2026-08-17; the delivered handoff is [`docs/design/android/`](../design/android/README.md) |

## One-time records

| Document | What it carried out | Executed |
| --- | --- | --- |
| [`TENANCY_VERIFICATION.md`](TENANCY_VERIFICATION.md) | Measuring what each caller may see across Staffeln and Spezialkommandos, against the isolated test stack | 2026-08-26 |

## Living plans and records (not archived)

These are still in use and stay in [`docs/`](../):

- [`ANDROID_APP_PLAN.md`](../ANDROID_APP_PLAN.md) — its roadmap is spent, but the decisions Q1–Q8 and
  the § 7 third-party inventory stay binding
- [`ANDROID_APP_SECURITY.md`](../ANDROID_APP_SECURITY.md),
  [`ANDROID_APP_PRIVACY_GDPR.md`](../ANDROID_APP_PRIVACY_GDPR.md),
  [`ANDROID_APP_DEV_CI.md`](../ANDROID_APP_DEV_CI.md) — the binding concept documents, including the
  pin-rotation runbook (security § 5.1)
- [`OWNER_RUNBOOK.md`](../OWNER_RUNBOOK.md) — the owner's procedures: releases, the signing key, the
  served-version floor
- [`DESIGN_PARITY_AUDIT.md`](../DESIGN_PARITY_AUDIT.md) — kept in sync until every row is closed;
  rows are still open
- [`GOOGLE_PLAY_DISTRIBUTION_PLAN.md`](../GOOGLE_PLAY_DISTRIBUTION_PLAN.md) and
  [`APPLE_PLATFORM_FEASIBILITY.md`](../APPLE_PLATFORM_FEASIBILITY.md) — a proposal and an assessment,
  neither decided nor carried out
