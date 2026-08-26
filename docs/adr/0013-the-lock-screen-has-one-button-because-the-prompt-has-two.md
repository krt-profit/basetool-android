# ADR-0013 — The lock screen keeps one button, because the system prompt already has two

> **Status:** Accepted · **Date:** 2026-08-26 · **Deciders:** @greluc
> **Design:** `docs/design/android/04 Auth.dc.html`, artboard 5 (App-lock — LockScreen)
> **Related:** `REQ-APP-AUTH-006`, [`0006-minsdk-30.md`](0006-minsdk-30.md)

## Context

Chapter 04's artboard 5 draws the lock screen with two actions: a filled CTA reading
**„MIT BIOMETRIE ENTSPERREN"**, and a quieter text button under it reading
**„GERÄTESPERRE VERWENDEN"**. The app draws one, labelled **„Entsperren"**.

The artboard's own annotation explains why the second one is there: *„Fallback = device credential
(PIN/pattern) via the same prompt."* Both buttons open the same sheet.

That sheet is `BiometricPrompt`, configured app-wide with
`BIOMETRIC_STRONG or DEVICE_CREDENTIAL` (`BiometricGate.kt`). The platform draws the device-credential
fallback **inside** it, as a control this app neither owns nor can style. A member who taps either
button lands on the same system sheet with the same two ways through it.

## Decision

**One button, labelled „Entsperren".**

A second button would open the sheet the first one opens, and its only effect would be to suggest
that the first one had not — that biometrics were the whole offer and the PIN needed a separate
door. It would teach a member something untrue about their own device.

The label follows from that. „Mit Biometrie entsperren" is the artboard's wording for a button that
sits *next to* a device-credential alternative; on a button that opens a prompt offering **both**,
it under-describes what happens next. Taking the artboard's label while dropping the artboard's
second button would follow the letter and lose the sense: the app would name one of two paths and
hide the other behind a word.

## Consequences

- The screen is one step further from its artboard than a screenshot comparison will show, and this
  ADR is where that shows instead.
- If the prompt is ever configured without `DEVICE_CREDENTIAL` — for a device with no credential
  set, say — the reasoning inverts and both the second button and the artboard's label become
  correct. The decision is contingent on the authenticator set, not on taste.
- The design session should be told, because artboard 5 currently draws a control the platform
  supplies. It is not wrong about the *behaviour*; it draws a system affordance as an app one.

## What was rejected

- **Drawing both buttons as designed.** Two controls, one destination, and an implication about the
  device that is false.
- **Keeping one button with the artboard's label.** Accurate to the picture, inaccurate to the
  member: the button also opens the PIN path, and the label would not say so.
- **Rendering an in-app PIN pad** so the second button has somewhere of its own to go. That is a
  credential this process could read, which is the thing the platform prompt exists to prevent.
