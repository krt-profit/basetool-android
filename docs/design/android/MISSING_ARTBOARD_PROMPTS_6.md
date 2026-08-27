# Round 6 — the Fan Kit band has to carry a third element

> Written 2026-08-27. This round is **one request**, and it is a legal one, not a preference.
> Round 5 and its answers: [`MISSING_ARTBOARD_PROMPTS_5.md`](MISSING_ARTBOARD_PROMPTS_5.md).
> Earlier rounds: [`_1`](MISSING_ARTBOARD_PROMPTS.md), [`_2`](MISSING_ARTBOARD_PROMPTS_2.md),
> [`_3`](MISSING_ARTBOARD_PROMPTS_3.md), [`_4`](MISSING_ARTBOARD_PROMPTS_4.md).

---

Thank you for the round-5 bundle — every finding is answered and the three artboards that disagreed
with their endpoints now agree with them. Chapter 09's 15–19, chapter 11's 3–5 and chapter 12's 4–8
are exactly what was needed, and the README's new rules 8–11 settle the wording questions for good.

This round has nothing to do with parity. **Two CIG documents bind this project and they apply
cumulatively.** The bundle's Fan Kit band (chapter 02 §9) satisfies one of them. The other asks for
a second, much longer notice that appears nowhere in the app today.

## What is missing, and where it comes from

| Document | Requires | State |
| --- | --- | --- |
| Fan Kit **Guidelines** §2b | the short trademark line | ✅ drawn, built, byte-exact, test-pinned |
| Fankit **Agreement** clause 2(g) | a separate, longer notice | ❌ absent from the drawing and from the app |

Clause 2(g), verbatim from `06_Fankit_Agreement_2025_11_19.pdf`:

> This site is not endorsed by or affiliated with the Cloud Imperium or Roberts Space Industries
> group of companies. All game content and materials are copyright Cloud Imperium Rights LLC and
> Cloud Imperium Rights Ltd.. Star Citizen®, Squadron 42®, Roberts Space Industries®, and Cloud
> Imperium® are registered trademarks of Cloud Imperium Rights LLC. All rights reserved.

**370 characters** — roughly five times the line the band carries today. That is the whole reason
this is a design request rather than a one-line change: it alters the band's height, and the band is
drawn on the login screen, which has no scroll headroom to spare.

The clause places it *"in a reasonably prominent location … wherever materials, trademarks, or
properties owned by CIG are located"*. In this app that is precisely where the band already is.

## Three details that look like mistakes and are not

I verified all three against the PDF, and against the 2024-04-25 and 2025-06-03 kits as well — the
Agreement's notice is byte-identical across all three archived versions (SHA-256 of the extracted
sentence matches).

1. **`Ltd..` carries two full stops.** Not an extraction artefact.
2. **No space before any of its four ®.** The §2b line the band already carries *does* have one
   before its third ®, because CIG's §2b prose writes it that way. **The two notices differ in
   exactly this detail and both are correct.** Please do not harmonise them in the drawing — a
   band where both read the same is a band that satisfies neither document.
3. **Oxford comma** before "and Cloud Imperium®".

The app already pins the §2b line byte-for-byte in a test; the 2(g) notice will be pinned the same
way, including an assertion that the two spacings stay *different*.

## What I need drawn

**The band as three coupled elements** rather than two: logo, §2b line, 2(g) paragraph. The coupling
is the point of the component — none of the three may render, move or disappear alone — so it stays
one block, but its proportions are yours to set.

Please draw it in the two placements the spec already fixes, because they are the ones where the
CIG asset sits:

- **Login**, above the version footer. This is the hard case: it is the only surface visible
  without a session, so it is the one §2b actually depends on, and it has the least room.
- **Einstellungen**, where the band sits today.

Four things I would rather have your answer to than guess:

- **Type size.** The existing line is `bodyMedium` (14 sp) on `#D2D2D2`. Clause 2(g) has no size of
  its own, but the Guidelines' "legible font size and color" floor is ~10 pt. Does the longer
  paragraph drop a step to `bodySmall`, or does the band keep one size throughout?
- **Whether the login band scrolls.** At 411 dp and 14 sp, 370 characters is roughly six lines. On a
  compact phone at font scale 1.3× that is a lot of the screen. Does the login page gain a scroll,
  does the band get its own, or does the paragraph sit at a smaller size to fit?
- **Whether the paragraph is ever folded.** A disclosure („mehr") would save the height, but a
  notice behind a tap is arguably no longer "reasonably prominent". I would not make that call
  myself.
- **The Einstellungen variant.** That screen scrolls already, so it may simply take the full
  paragraph. Worth confirming rather than assuming.

## What is deliberately not in this request

- **The §2b line does not change.** Not its wording, not its spacing, not its placement, and it is
  not merged with the new one.
- **No further Fan Kit assets.** The surface stays the one `made-by-the-community` logo — no
  wallpapers, no audio, no manufacturer logos, no Fan Kit fonts.
- **No further screens.** Login and Einstellungen remain the only two placements for the band.

## Meanwhile

The web frontend is not bound by this chapter and has been brought into compliance already — its
band carries all three elements, and its test pins both notices and the difference between them.
The Android band waits for this drawing rather than deviating from the spec on its own
(owner decision, 27.08.2026).
