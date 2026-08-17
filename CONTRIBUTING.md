# Contributing to Basetool Android

Thank you for considering a contribution! This is the native Android companion app of the
Profit Basetool. The project is currently in the **concept phase** — the Gradle scaffold and
CI land with Phase 1 of the [roadmap](docs/ANDROID_APP_PLAN.md#6-phased-roadmap); until then,
contributions are mostly documentation and review.

This guide is the compact contributor-facing companion to the binding project rules in
[`CLAUDE.md`](CLAUDE.md) (conventions, lint gates, privacy gate, design system). Where the
two ever disagree, `CLAUDE.md` and the specs win.

## Code of Conduct

This project follows the [Contributor Covenant 3.0](CODE_OF_CONDUCT.md). Report violations
to [lucas.greuloch@pm.me](mailto:lucas.greuloch@pm.me).

## Asking questions, reporting bugs, suggesting features

Use GitHub Issues for bugs and feature suggestions, GitHub Discussions for questions. For
bugs, include app version, Android version, device class (phone/tablet), and reproduction
steps — never include tokens, account data, or screenshots showing other members' data.

## Reporting a security vulnerability

**Do not open a public issue.** Use GitHub's private vulnerability reporting on this
repository (Security → Report a vulnerability) or mail
[lucas.greuloch@pm.me](mailto:lucas.greuloch@pm.me).

## Contributor License Agreement (CLA)

Before Your first contribution is merged, You must sign the
[Basetool Android Individual Contributor License Agreement](CLA.md). You retain copyright in
Your contributions — the CLA grants the Project the licenses needed to distribute them under
GPL-3.0 and defend against IP claims; it does not transfer ownership.

The CLA is signed **once** and covers all of Your present and future contributions. Two
signing paths, both documented in [§ 11 of the CLA](CLA.md#11-how-to-sign):

- **Signature PR** — open a PR titled `cla: sign — <your-github-handle>` that appends Your
  row to [`docs/cla-signatures.md`](docs/cla-signatures.md), with the verbatim acceptance
  sentence from § 11 in the PR description.
- **CLA-Assistant** — if enabled on the repository, sign electronically via the in-PR
  instructions.

The CLA applies to **every** contribution that ends up in a commit under Your authorship —
Kotlin code, Gradle configuration, Compose UI, string resources and translations, GitHub
Actions workflows, and documentation alike. Entity (employer-funded) contributions need a
separate Entity CLA — contact the maintainers. A signature to the Profit Basetool CLA does
**not** carry over to this Project.

## Developer Certificate of Origin (DCO) sign-off

In addition to the one-time CLA, **every individual commit** in a pull request must carry a
`Signed-off-by` trailer that certifies the
[Developer Certificate of Origin, version 1.1](https://developercertificate.org/).

**Why both?** The CLA is a one-time legal grant defining the IP terms; the DCO sign-off is a
per-commit attestation that this specific commit is Your own work (or third-party work You
are allowed to forward) — lightweight and auditable per commit.

By adding a `Signed-off-by` line, You certify:

> **Developer Certificate of Origin, Version 1.1**
>
> (a) The contribution was created in whole or in part by me and I
> have the right to submit it under the open-source license indicated
> in the file; or
>
> (b) The contribution is based upon previous work that, to the best
> of my knowledge, is covered under an appropriate open-source license
> and I have the right under that license to submit that work with
> modifications, whether created in whole or in part by me, under the
> same open-source license (unless I am permitted to submit under a
> different license), as indicated in the file; or
>
> (c) The contribution was provided directly to me by some other
> person who certified (a), (b), or (c) and I have not modified it.
>
> (d) I understand and agree that this project and the contribution
> are public and that a record of the contribution (including all
> personal information I submit with it, including my sign-off) is
> maintained indefinitely and may be redistributed consistent with
> this project or the open-source license(s) involved.

The canonical text lives at <https://developercertificate.org/>.

**How to sign off:** pass `-s` to `git commit` (opt in permanently with
`git config format.signOff true`). The trailer's name and email **must** match Your
`git config user.name` / `user.email` and be a real, reachable identity — `noreply` aliases
cannot be matched against Your CLA signature.

**Forgot the flag?** Last commit: `git commit --amend --signoff --no-edit`. Whole branch:
`git rebase --signoff main`, then `git push --force-with-lease origin <your-branch>` (never
force-push `main`).

**Enforcement:** a DCO check workflow (mirroring the main repo's `dco.yml`) becomes a
required status check on `main` when the Phase-1 CI lands; until then maintainers verify the
trailers manually before merging. Merge commits and well-known bot commits are exempt; every
human-authored commit is not.

## Commit messages and branches

- [Conventional Commits](https://www.conventionalcommits.org/): `type(scope): imperative
  summary` — e.g. `feat(missions): add check-in action to mission detail`.
- **English only** — commits, branches, PRs, issues, comments, KDoc. (UI strings are the
  exception: they live in the DE/EN resource bundles.)
- Branch names: `feat/…`, `fix/…`, `docs/…`, `chore/…` in kebab-case.

## Style and quality gates

The binding rules live in [`CLAUDE.md`](CLAUDE.md): Kotlin conventions, KDoc requirements,
the DAS KARTELL design system, the privacy gate (no dependency that sends user data off the
device and Basetool without owner approval), i18n, and testing expectations. Once the Gradle
scaffold exists: run `./gradlew spotlessApply` and get `./gradlew check` (unit tests, Android
Lint, detekt, ktlint) green before every push. Every new feature ships with tests.

## License

Basetool Android is licensed under the [GNU GPL v3.0](LICENSE.md). By contributing You agree
that Your contributions are distributed under GPL-3.0, alongside the license grants of the
[CLA](CLA.md).
