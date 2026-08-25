# Prompt: fold the repo's corrections back into the design spec

The 2026-08-25 export was a clean upstream regeneration, and it silently dropped three corrections
the repository had made to the previous bundle. They were restored on import (see the handoff
[`README.md`](README.md) → *Reconciled on import*), but restoring them by hand is not a fix: the
next export will drop them again, and the drift is invisible to the build.

This prompt asks the design side to carry the corrections **in the source**, so the next export
already has them. It also carries the owner's decisions on four places where the spec drew a figure
the backend does not have — those are settled, and item 5 states each as an edit rather than a
question.

Paste the whole thing into Claude Design. It needs no other context.

---

## The prompt

> You are correcting the delivered UI specification for the **Profit Basetool Android companion
> app** (`design_handoff_basetool_android`, chapters 00–15). This is not a redesign: every change
> below is a factual correction or an annotation. Do not restyle, renumber or redraw anything that
> is not named here, and keep the bundle's structure, markup idiom and copy rules exactly as they
> are.
>
> Each item says what is wrong, what it must say instead, and why — the *why* matters, because the
> last export removed three of these and the reason it should not is what stops it happening again.
>
> ### 1 · minSdk is 30, not 29
>
> `README.md`, the "Target stack" line, currently reads:
>
> > **Kotlin + Jetpack Compose, Material 3, minSdk 29 / targetSdk 37**
>
> It must read **minSdk 30**.
>
> This is not a preference. ADR-0006 in the app repository raised the floor to 30 *and deleted the
> API-29 code path*, because on API 29 the only auth-bound Keystore key is time-bound: no
> `CryptoObject` accepts it, and `Cipher.init` throws until an authentication already exists — the
> opposite order from API 30+. On the whole of API 29 the app lock could therefore neither be armed
> nor opened, and a broad catch reported it as permanently unsatisfiable while every test stayed
> green. The decision is owner-approved and implemented. A spec that says 29 invites someone to
> reinstate a path that cannot work.
>
> ### 2 · `--color-gray-2-text` must stay in the design-system mirror
>
> `_ds/das-kartell-profit-basetool-design-syste-…/colors_and_type.css` defines
> `--color-gray-2: #646464` and, in the previous bundle, an accessible companion token that this
> export dropped. Put it back, immediately after `--color-gray-2`, with its comment:
>
> ```css
> /* Accessible muted-text tint — Grau 2 (#646464) reads at only ~3.5:1 on the
>    flat-black page and FAILS WCAG AA as small text. Use whenever muted grey IS
>    the text itself (secondary labels, placeholders, hints, field caps, dimmed
>    units); keep --color-gray-2 for hairline borders, scrollbar thumbs, disabled
>    fills and decorative glyphs. ≈ 6.1:1 on black, ≥ 4.9:1 on #141414 / #1C1C1C.
>    (Android mirror: KrtPalette.TextMuted.) */
> --color-gray-2-text: #8A8A8A;
> ```
>
> Two distinct tokens, deliberately. `#646464` is correct for a hairline and wrong for a word. The
> Android theme mirrors the second one as `KrtPalette.TextMuted`; without the token in the mirror
> that mirror has no source, and the next person to "reconcile" the two will move the app back to a
> colour that fails the contrast floor.
>
> ### 3 · Chapter 04 must carry the guest-mode decision again
>
> `04 Auth.dc.html` draws „Als Gast fortfahren" on the login screen. The previous bundle carried an
> annotation immediately above the frames saying that this entry is cancelled; the export removed
> it, so the chapter now shows a control that must not be built, with nothing to say so. Restore it
> verbatim, in the same place and the same style as the chapter's other lead-in note:
>
> > **Abweichung (Eigentümer-Entscheidung, 18.08.2026):** Der Gastmodus ist gestrichen — jeder
> > Nutzer meldet sich an. Der Eintrag „Als Gast fortfahren" auf dem Login-Screen entfällt
> > ersatzlos; die Frames unten zeigen ihn weiterhin, weil dieses Handoff der Stand der Übergabe
> > ist und nicht nachträglich umgeschrieben wird. Ebenfalls entfallen: der Link
> > „Nutzungsbedingungen" in der Fußzeile — die Zustimmung ist ohnehin erzwungen und versioniert,
> > und ohne Gastmodus passiert jeder Nutzer diese Schranke. Verbindlich ist Q8 in
> > ANDROID_APP_PLAN.md und REQ-APP-AUTH-007/008.
>
> Keeping the frames as drawn and annotating them is the right shape: the handoff is a record of
> what was delivered, and rewriting history would lose the fact that the decision came later.
>
> ### 4 · `assets/basetool-logo.svg` belongs in the bundle
>
> The export ships `basetool-appicon-512.png` and `basetool-favicon.svg` and no longer ships
> `basetool-logo.svg`. Two production vector drawables —
> `app/…/res/drawable/ic_launcher_foreground.xml` and
> `core/designsystem/…/res/drawable/krt_basetool_logo.xml` — name that SVG as the artwork they were
> traced from, in their own header comments. A raster and a favicon variant do not replace the
> source geometry for a trace. Ship the SVG again, alongside the other two.
>
> ### 5 · Four elements draw a figure the backend does not have — the owner has decided each
>
> These are not spec bugs; they are drawings of data the API does not expose. The owner settled all
> four on 2026-08-25, so each is a concrete edit rather than a question:
>
> **a · Chapter 13 — Beförderung: remove the `selbst` and `Führung` columns.** The matrix keeps
> three: Thema, the assigned level, and the goal (`≥ …`). `MemberEvaluationResponse` carries one
> `assignedLevel` per topic; there is no self-assessment and no separate lead assessment, and the
> decision is not to build one. Redraw the matrix with three columns and let the remaining ones
> breathe rather than leaving two empty gutters.
>
> **b · Chapter 12 — Bank: remove the „{n} Verwahrer" chip** from the account card.
> `BankAccountDto` carries no holder count and one will not be added; the card keeps name, balance,
> the 30-day delta and the sparkline.
>
> **c · Chapter 05 — Dashboard: keep „{n} angemeldet".** This one *is* being added to the API
> (`MissionListDto` gains a participant count), so the artboard stays as drawn. Add a handoff note
> saying the figure comes from the list endpoint's participant count, so nobody reads it as
> detail-only data again.
>
> **d · Chapter 10 — the note counter reads `0 / 500`, not `0 / 250`.**
> `AssigneeNoteRequest.note` is capped at 500 on the wire, and the client follows the contract; a
> 250 in the artboard would have the spec refusing text the server accepts. Update the counter and
> the „62 / 250" sample in artboard 7 accordingly.
>
> ### 6 · Give the corrections somewhere to live
>
> Add a short section to `README.md` — after "Fidelity" — titled **Corrections carried in this
> bundle**, listing items 1–4 as one line each with their reason. The point is not documentation for
> its own sake: this bundle is regenerated periodically, and every regeneration so far has dropped
> corrections that nobody noticed until an implementer read them. A section in the source is the
> only place a regeneration can preserve them.

---

## For the reviewer of the result

When the corrected bundle comes back, check these five things before importing — they are what the
last import had to fix by hand:

1. `README.md` says **minSdk 30**.
2. `colors_and_type.css` defines **both** `--color-gray-2` and `--color-gray-2-text`.
3. `04 Auth.dc.html` contains the string **„Der Gastmodus ist gestrichen"**.
4. `assets/basetool-logo.svg` exists.
5. `README.md` has the **Corrections carried in this bundle** section.
6. Chapter 13's matrix has **three** columns; chapter 12's account card has **no** Verwahrer chip;
   chapter 10's note counter reads **500**.

If any is missing, the import has to re-apply it and this prompt needs re-sending — the audit
records that under *The export dropped three of our reconciliations* in
[`../../DESIGN_PARITY_AUDIT.md`](../../DESIGN_PARITY_AUDIT.md).
