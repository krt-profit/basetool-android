# Owner runbook — the steps nobody else can do

> **Doc type:** Living runbook · **Audience:** @greluc, alone
> **Related:** [`ANDROID_APP_DEV_CI.md`](ANDROID_APP_DEV_CI.md) § 4 (signing),
> [`ANDROID_APP_SECURITY.md`](ANDROID_APP_SECURITY.md) § 5.1 (pin rotation),
> main repo [`docs/API_VHOST_ROLLOUT_RUNBOOK.md`](https://github.com/krt-profit/basetool/blob/main/docs/API_VHOST_ROLLOUT_RUNBOOK.md)
> (Phase J)

Everything else in this project is automated, reviewed or testable. What is left here needs
production access, a secret, a repository setting, or a decision — and each of those is yours by
rule, not by convention.

**Read the whole of a step before starting it.** Two of them (the signing key, the vhost paste) are
hard to undo, and one of them is impossible to undo.

---

## 1. The vhost paste — opens phase 3 and phase 4 to production

**Status:** outstanding. **Effect until done:** the app's write paths and every phase-4 read answer
`404` from outside the network, and the nightly `edge-deny-probe` is red — correctly so, reporting
exactly the state this fixes.

The step-by-step lives in the **main repo's runbook, § Phase J**, together with the block to paste
and the checks to run afterwards. It is not duplicated here, because a second copy is a copy that
drifts.

You decided on 2026-08-24 to hold it until phase 4 closed. Phase 4 is closed (see § 6), so this is
now the next thing.

> One thing to know before reading the result: `GET /api/v1/app/version-policy` must answer **200**,
> not 401. It is the one anonymous path on that vhost and it is meant to be — an app too old to
> authenticate has to be able to learn that it is too old. The runbook's table says so too.

---

## 2. The release signing key — generate once, back up, never lose

**Status:** outstanding. **Undoable:** no. A lost key means no member can ever install an update
over their existing app; they would have to uninstall and lose their local state.

Do this **offline**, on a machine you trust, not on a runner and not in a repository directory.

```bash
keytool -genkeypair \
  -keystore basetool-release.p12 \
  -storetype PKCS12 \
  -alias basetool-release \
  -keyalg RSA -keysize 4096 \
  -validity 10000 \
  -dname "CN=Profit Basetool, O=DAS KARTELL, C=DE"
```

`-validity 10000` is about 27 years. A certificate that expires while the app is alive cannot be
renewed — Android identifies the signer by the certificate, so a new one is a new identity.

**Back it up before it is used for anything.** Two copies, offline, in different places. The
password too, in your password manager. There is no recovery path and no support channel; this is
the whole of the Q1 decision's cost.

Then note the fingerprint — you will publish it:

```bash
keytool -list -v -keystore basetool-release.p12 -storetype PKCS12 -alias basetool-release \
  | grep -A1 'SHA256:'
```

---

## 3. The `release` environment — where the key lives on GitHub

**Status:** outstanding. Settings → Environments → **New environment** → `release`.

Configure, in this order:

1. **Required reviewers:** yourself. Without it a tag push signs and publishes with no human in the
   loop.
2. **Deployment branches and tags:** *Selected* → add the tag rule `v*`. Without it a workflow on
   any branch can reach the signing secrets.
3. **Environment secrets** — four, exactly these names:

   | Secret | Value |
   |---|---|
   | `RELEASE_KEYSTORE_BASE64` | `base64 -w0 basetool-release.p12` |
   | `RELEASE_KEYSTORE_PASSWORD` | the store password |
   | `RELEASE_KEY_ALIAS` | `basetool-release` |
   | `RELEASE_KEY_PASSWORD` | the key password (same as the store password unless you set two) |

`base64 -w0` matters: a wrapped blob pasted into a secret field decodes to a truncated file, and
the failure surfaces much later as something that reads like a corrupt key. The workflow checks the
PKCS#12 magic bytes for exactly this reason and will tell you plainly if it happens.

**Do not commit the keystore, ever.** `.gitignore` already excludes `*.p12` and `*.jks`; that is a
safety net, not the plan.

---

## 4. The first release

**Status:** outstanding. Depends on §§ 2 and 3, and should follow § 8 — five minutes of repo
settings that are worth having in place before the first tag rather than after it.

1. Make sure `main` is where you want it and CI is green.
2. Tag and push:

   ```bash
   git tag -s v0.1.0 -m "v0.1.0"
   git push origin v0.1.0
   ```

3. The `Release` workflow starts and **waits for your approval** (the required reviewer). Approve
   it in the run's page.
4. It builds, signs, verifies the certificate against the key you configured, attests the
   provenance, exports the dependency SBOM and creates a **draft** release. Nothing is public yet.
5. **Read the draft.** It carries the APK SHA-256 and the certificate fingerprint. Check the
   fingerprint against what you noted in § 2 — if they differ, stop: a different key signed it.
6. Install the APK on a real device from that draft and open it once. This is the only step in the
   whole pipeline that a human has to do, and it is the one that catches "it builds, it signs, and
   it does not start".
7. Publish the release.
8. **Put the fingerprint in the README.** It currently says *published with the first release
   (v0.1.0)*; replace that line with the real digest and push it to `main`.

---

## 5. Raising the served-version floor — only when you need it

**Status:** not needed yet, and deliberately so. The floor defaults to `0`, which serves every
build.

You need this the day a contract change makes an old build unsafe or broken. On the production
host, in the `.env`:

```
APP_ANDROID_MINIMUM_VERSION_CODE=<the oldest versionCode you still serve>
APP_ANDROID_LATEST_VERSION_CODE=<the newest published versionCode>
```

then restart the backend. Nothing else changes; the app reads the new floor on its next start and
shows „Update erforderlich" below it.

**Set the floor to a version that already exists as a release.** A floor above every published
build locks out everybody, including you, and the only way back is another restart — which is fine,
but the members who tried in between saw a wall for no reason.

Keep the two numbers apart. `LATEST` above a member's build means "an update exists"; `MINIMUM`
above it means "you cannot run". Collapsing them makes every release a forced one.

---

## 6. Beförderung — the one deliberate gap

**Status:** withheld, by your decision on 2026-08-23 and again on 2026-08-24.

The screen is built and tested; its destination renders a placeholder because Beförderung has no
chapter in the design handoff, and a derived layout is not a followed one. Everything behind it
stays wired — repository, tests, the contract freeze, the allow-list lines — so this is a
one-commit change whenever a chapter exists.

`krt-profit/basetool-android#66` stays open. Nothing else waits on it.

---

## 7. The German wiki page

**Status:** drafted, in [`docs/wiki/App.md`](wiki/App.md) — but the wiki is a **separate git
repository** (`basetool.wiki`) that is not checked out here, so committing it is yours.

```bash
git clone https://github.com/krt-profit/basetool.wiki.git
cp <this repo>/docs/wiki/App.md basetool.wiki/App.md
cd basetool.wiki && git add App.md && git commit -s -m "Add the Android app page" && git push
```

German content, English commit message — the wiki is the one carve-out from the English-only rule,
and the carve-out is the *content*, not the commit.

**Do it with the release, not before.** The page tells members how to install something; until § 4
is done there is nothing to install, and a handbook page describing a file that does not exist is
worse than no page.

---

## 8. Close the two governance gaps — do this before § 4

**Status:** outstanding, both halves. **Undoable:** yes, trivially — these are two settings
screens. Neither is urgent on its own; both are worth doing before § 4, because a rule added
afterwards protects everything from then on and nothing from before.

### 8a. `main` accepts a merge with red CI

The `Protect main` ruleset requires a pull request, signed commits, no force-push and no
deletion — but it has **no `required_status_checks` rule at all**. Nothing stops a merge while
CI is red; the gate is the discipline of whoever presses the button. The main `basetool` repo
requires five checks, so this is the android repo drifting from your own standard rather than a
new policy. Scorecard has been saying so under `BranchProtectionID`: *"no status checks found to
merge onto branch 'main'"*.

Settings → Rules → **Protect main** → Add rule → **Require status checks to pass**, tick *Require
branches to be up to date before merging*, then add these eight by name:

```
Build, Test & Lint
Analyze (java-kotlin)
Analyze (actions)
Verify Signed-off-by on every commit
Workflow lint
gitleaks
Dependency review
Sign and verify a release APK
```

**Add only these eight.** `CodeQL`, `Submit the dependency graph` and `OpenSSF Scorecard` report
*skipped* on a pull request — they run on push to `main` or on a schedule. A required check that
never reports a real conclusion is the one configuration mistake here that is annoying to
diagnose, because the PR simply sits there with nothing marked red.

The ruleset keeps `OrganizationAdmin` and `RepositoryRole` as always-bypass actors, so this
never locks you out of an emergency merge — it makes the bypass a decision you take rather than
one you take by default.

### 8b. There is no tag protection on `v*`

The main `basetool` repo has a `Version` ruleset protecting `refs/tags/v*` and `refs/tags/V*`
against deletion and non-fast-forward. This repo has **no tag ruleset at all** — the ruleset list
holds exactly one entry, and it targets branches.

That gap is inert today and stops being inert at § 4. A `v*` tag that can be moved is one that can
be made to name different code *after* the release workflow signed an APK and attested its
provenance against the commit the tag named at build time. The attestation stays valid for what it
attested; it just stops describing what the tag now points at. Nobody would catch that by reading
the release page.

Settings → Rules → **New ruleset** → *Tag ruleset*, enforcement **Active**, target
`refs/tags/v*` and `refs/tags/V*`, and enable **Restrict deletions** and **Block force pushes** —
the same two rules the main repo uses, and nothing more.

---

---

## 9. The three Scorecard warnings that are not defects

Recorded so nobody spends an afternoon on them a second time.

- **`CodeReviewID` — 0/4 approved changesets, high.** A consequence of being the only
  maintainer: the ruleset asks for a pull request but zero approvals, because requiring an
  approver on a one-person repo means requiring a second account. Accepted, and worth revisiting
  the day somebody else commits.
- **`MaintainedID` — repository created within the last 90 days, high.** Time fixes this one; it
  will clear on its own around 2026-11.
- **`BinaryArtifactsID` — score 9, binary detected.** `gradle/wrapper/gradle-wrapper.jar`, the
  repo's only committed binary and one the wrapper cannot work without. The mitigation is
  checksum validation, which now runs in **every** workflow that executes it — `codeql.yml` was
  the last gap and is closed.
