# Security Policy

Thank you for taking the time to look at the security of Basetool Android.
This document explains how to report a vulnerability, which versions receive
fixes, and what you can expect from us in return.

## Reporting a Vulnerability

**Please do NOT open a public GitHub Issue, Discussion, or Pull Request for
anything you believe is security-sensitive.** Public disclosure before a fix
is available puts every member running this app at risk.

The preferred channel is GitHub's **Private Vulnerability Reporting** via
Security Advisories:

> **[Report a vulnerability](https://github.com/krt-profit/basetool-android/security/advisories/new)**

That form gives us a private, structured place to triage the finding,
collaborate on a patch, request a CVE, and coordinate disclosure with you.

A good report typically includes:

- A clear description of the issue and its impact.
- The affected app version (or commit SHA) and the Android version/device it
  was observed on.
- The affected component (`app`, a `core:*` or `feature:*` module, the Gradle
  build configuration, a GitHub Actions workflow, a released APK, etc.).
- Reproduction steps, a proof of concept, or a minimal test case.
- Any relevant configuration (build flavor, app-lock enabled or not, backup
  state) needed to trigger the issue.
- Your assessment of severity (CVSS vector welcome but not required).
- Whether you intend to publish your own write-up, and on what timeline.

If you cannot use GitHub Security Advisories for some reason, please open a
minimal public issue that says only *"I would like to report a security
issue, please contact me"* — without any technical detail — and we will reach
out privately to arrange a channel.

**Server-side findings belong next door:** vulnerabilities in the Basetool
backend, its REST API, the Keycloak setup, the reverse proxy, or any other
server component are handled by the main repository's policy — please report
them via
[krt-profit/basetool → Security](https://github.com/krt-profit/basetool/security/advisories/new)
instead. If you are unsure which side a finding belongs to, report it here
and we will route it.

## What to Expect

This is a community-maintained project, so we cannot offer a commercial SLA,
but we aim for the following turnaround on every report:

|                          Step                          |          Target           |
|--------------------------------------------------------|---------------------------|
| Acknowledge receipt of the report                      | within 7 days             |
| Initial triage and severity assessment shared with you | within 14 days            |
| Fix in `main` for High / Critical issues               | within 90 days            |
| Coordinated public disclosure after a fix is available | within 14 days of release |

If we cannot meet one of these targets we will tell you why and propose a
new date in the advisory thread. We will credit you in the published
advisory and the [`CHANGELOG.md`](../CHANGELOG.md) unless you ask us not to.

## Supported Versions

Basetool Android is in the **pre-release concept/implementation phase** —
there are no published releases yet; findings against `main` are always
welcome. Once releases exist, only the **latest APK published on
[GitHub Releases](https://github.com/krt-profit/basetool-android/releases)**
receives security fixes; older releases do not get backports (Obtainium
users update automatically).

|            Version             |      Supported       |
|--------------------------------|----------------------|
| `main` (pre-release)           | :white_check_mark:   |
| Latest GitHub Release once cut | :white_check_mark:   |
| Older releases                 | :x: (please upgrade) |

## Scope

The following are **in scope** for this policy:

- Source code in this repository (`app/`, `core/*`, `feature/*`, Gradle build
  configuration).
- APKs published under this repository's GitHub Releases, and the release
  signing/publishing pipeline that produces them.
- GitHub Actions workflows under `.github/workflows/` and their pinned
  actions.

The following are typically **out of scope**:

- Anything server-side (backend API, Keycloak, reverse proxy, monitoring) —
  see the routing note above; the main repository's policy covers it.
- Vulnerabilities in third-party dependencies that do not have a viable fix
  path in this project. Please report those upstream first; you are still
  welcome to let us know so we can track an upgrade.
- Findings that require a rooted or already-compromised device, an
  already-stolen valid token, developer options deliberately weakening the
  platform, or physical access to an unlocked device. (A finding that
  *defeats a control we explicitly claim* — e.g. extracting the refresh
  token from a non-rooted device despite the Keystore design — is very much
  in scope.)
- Denial-of-service achievable only by a single authenticated user against
  their own data.
- Reports generated solely by automated scanners without a demonstrated,
  reproducible impact on this codebase.
- Social engineering of contributors or members, and any attack against
  infrastructure we do not control (Google, GitHub, the device vendor's OS,
  etc.).
- Best-practice suggestions without a concrete vulnerability (e.g. "you
  should add flag X"). These are very welcome as regular issues or pull
  requests instead.

Particularly interesting classes of issue, given the app's architecture:

- Token-storage bypass — any way to read the Keystore-encrypted refresh
  token (or its key) from a non-rooted device, including via Auto Backup or
  device-to-device transfer despite the exclusion rules.
- OAuth flow attacks — authorization-code or redirect interception (custom
  scheme vs App Links), PKCE downgrade, DPoP proof replay against the token
  endpoint, or the app accepting tokens from a wrong issuer.
- TLS weaknesses — trust of non-system anchors in release builds, pinning
  bypass once pinning ships, cleartext traffic of any kind.
- Data leakage on device — member data in app logs, exported components,
  world-readable files, notifications leaking content on the lock screen
  beyond what is configured, or FLAG_SECURE gaps on screens we mark secure.
- The app talking to any host other than the configured Basetool
  infrastructure — the project's privacy gate says there are none; proving
  otherwise is a finding.
- Supply-chain integrity issues affecting the build, signing, or release
  pipeline in this repository (including cache poisoning of the signing
  job and secret exposure to fork PRs).

## Coordinated Disclosure

We follow coordinated disclosure. Our default disclosure window is **90 days
from the date we acknowledge the report**, or sooner if a fix and a release
are already public. If a fix is not yet available after 90 days we will
agree on an extension with you in the advisory thread before any public
disclosure.

When the advisory is published we will:

1. Release a patched APK on GitHub Releases.
2. Publish the GitHub Security Advisory with details, affected versions,
   workarounds, and credit.
3. Add a `### Security` entry to [`CHANGELOG.md`](../CHANGELOG.md) referring
   to the advisory.

## Safe Harbor

We will not pursue or support legal action against researchers who:

- Make a good-faith effort to comply with this policy.
- Avoid privacy violations, data destruction, and service degradation
  against users of this app or operators of the Basetool.
- Use only their own test data, or data they have explicit permission to
  access, and stop as soon as they have demonstrated the issue.
- Give us reasonable time to remediate before any public disclosure.

If a third party initiates legal action against you for activity that
complied with this policy, we will make our position on safe harbor known.

This policy does not authorise you to test the security of any third-party
service (Google, GitHub, etc.) — **and it does not authorise testing
against the production Basetool server.** Testing the live API or Keycloak
instance requires the operator's explicit prior permission under the main
repository's policy; use a local test stack instead (the main repository
documents how to run one with throwaway credentials).

## Verifying Releases

Once releases exist, every published APK is signed with the project's
release key using APK Signature Scheme v3.1 (with a rotation lineage), and
each GitHub Release lists the SHA-256 digest of the APK and of the signing
certificate. Verify before reporting a finding against a downloaded
artifact:

```bash
apksigner verify --print-certs basetool-android.apk
```

A release SBOM (CycloneDX) is attached to each GitHub Release. A finding
that requires bypassing these verification steps is itself in scope.

## Thank You

Security research is real work and we appreciate it. If you report
something that turns out to be valid, we will credit you in the published
advisory and the changelog by name, handle, or anonymously — your choice.
