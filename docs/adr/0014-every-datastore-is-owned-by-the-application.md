# ADR-0014 — Every DataStore is owned by the Application, and a test enforces it

> **Status:** Accepted · **Date:** 2026-08-26 · **Deciders:** @greluc
> **Related:** `REQ-APP-NOTIF-014`, [`0010-screenshot-protection-is-member-switchable.md`](0010-screenshot-protection-is-member-switchable.md)

## Context

DataStore permits exactly one active instance per file per process and enforces it by throwing:

```
java.lang.IllegalStateException: There are multiple DataStores active for the same file:
  …/files/datastore/krt_settings.preferences_pb
```

The throw is fatal, and it lands where the store is first *read* — never where the second instance
was created. From a member's side the app simply vanishes to the home screen, so it reads as a crash
in whatever screen happened to be opening.

This has now happened twice, in the same shape:

1. **The token store.** `AuthContainer` documented itself as "built once per process" while hanging
   off the activity, so it was built once per *activity*. A language change recreates the activity,
   and the app died. Fixed by moving it to `BasetoolApplication`.
2. **The settings store.** `ScreenCapturePreference.createStore(this)` was called from
   `MainActivity` and from `SettingsPreviewActivity`, both as `by lazy` on the activity. It died on
   the path a member takes most often: **tapping a notification**. That `PendingIntent` carries
   `FLAG_ACTIVITY_NEW_TASK`; Navigation's `handleDeepLink` responds by rebuilding the task through
   `TaskStackBuilder` and finishing the current activity; the replacement opened a second store on
   `krt_settings` and the process died before the inbox was drawn.

The second one is the interesting failure. The first fix was correct, was well explained in the
`BasetoolApplication` KDoc, and *changed nothing about how the next store would be written*, because
nothing carried the rule forward into the build. A lesson recorded only in prose next to the code it
already fixed is a lesson available to whoever reads that file — and the second store was added in
a different file.

It also hid behind the emulator. The crash needs an activity **recreation**, so it never appears in
a fresh launch, which is how a screen is opened during development. It took a deep link fired at a
running app to surface it.

## Decision

**Every DataStore-backed object is a property of `BasetoolApplication` and is read from there.**
Activities hold no store, and neither do view models. Where the *effect* is per-activity — the
screen-capture window flag is — the flag stays with the activity and the store behind it does not.

**A test enforces it rather than a comment.** `ProcessStoreOwnershipTest` reads the Kotlin sources
and fails when a store is opened outside the application, naming the offending file and where the
store belongs. It is deliberately about the class of defect: a store added next year gets the guard
without anyone remembering this ADR exists.

The test matches source text. That is crude, and it is the right crudeness: the question is "does
this file open a store", which is a syntactic fact about the source. A reflective check could only
see the store it managed to reach, and the whole failure mode is a store nobody reached until a
member did.

## Consequences

- A new store costs one property on `BasetoolApplication`; the guard's failure message says so.
- The guard maintains an allow-list of the files that *define* the openers. Renaming one of those
  files without updating the list fails the build — noisy in the right direction.
- The guard cannot catch a store opened through an indirection it cannot see textually. That is
  accepted: it catches the shape both real occurrences had.
- `SettingsPreviewActivity` (dev flavour) reads the application's instance, so the preview screen
  keeps having the real effect it claims.
