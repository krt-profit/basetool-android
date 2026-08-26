# Round 4 — what the bundle draws that the app cannot build

> Written 2026-08-26, after implementing chapters 02, 04, 06, 08 and 09 of the delivered bundle
> and checking every one of them on a device against the running test stack.
> Rounds 1–3: [`MISSING_ARTBOARD_PROMPTS.md`](MISSING_ARTBOARD_PROMPTS.md),
> [`_2`](MISSING_ARTBOARD_PROMPTS_2.md), [`_3`](MISSING_ARTBOARD_PROMPTS_3.md).

---

The bundle you sent is built. Chapter 09's artboards 5–14, chapter 08's 4–11, chapter 06's
sign-up sheet and the corrections in 02 and 04 are all in the app and verified on a phone, not just
compiled. Four defects only the device found — an over-strong bloom, a toast passing under the
floating action button, a kind marker that doubled on a unit already named „SK Nebelkraehe", and a
plural reading „ausgewählt" where every artboard writes „gewählt" — and those are fixed.

This round is not a request for more screens. It is six places where the drawing and the machine
disagree, and where I need your decision rather than my guess.

## 1. Four elements are drawn with no data behind them

Each of these is in the bundle and has no field or no reachable endpoint. I have shipped none of
them as a dash or a greyed control, because both say „this exists and is merely missing today",
which is a promise the app cannot keep. They are recorded as open items in the specs instead.

- **The LTI tile**, chapter 08 artboard 1, over the Hangar's org-unit aggregate.
  `SquadronShipOverviewDto` carries `count` and `fittedCount`; `SquadronShipDetailDto` carries
  owner, location and `fitted`. There is no insurance field anywhere on that endpoint. Two tiles
  ship, the third does not.
- **„Eigenes Schiff einbringen (optional)"**, chapter 06 artboard 3, the sign-up sheet's third
  section. `AddParticipantPublicRequest` has no ship field, and adding a unit to a mission is
  `POST /missions/{id}/units` behind `canManageMission` — a participant cannot reach it. The sheet
  ships with two sections where you drew three.
- **„EINGEREICHT · vor 2 Std. · via Discord"**, chapter 04 artboard 3, on the approval card.
  `RegistrationStatusDto` has exactly one property: `approvalStatus`. Neither the submission time
  nor the identity provider is on the wire.
- **The Bereich tag** is already handled — your own chapter-02 note (26.08.) says no Android DTO
  carries one and the unit context takes the squadron badge. That is what the app does.

**What I need:** for each of the first three, either drop it from the artboard, or say it is a
backend requirement so it can be raised in the main repo as one. Right now the picture and the
build disagree with nothing written down about which is authoritative.

## 2. Chapter 04, artboard 3 contradicts its own annotation

The card's body reads „Dein Zugang wartet auf Freigabe durch die Administration. **Du wirst
benachrichtigt**, sobald dein Konto freigeschaltet ist." The annotation immediately beside it reads
„Polls every 60 s — the ONLY channel: the app has no push (decision Q2); approval arrives via the
poll or not at all. **Never promise a notification here**."

The shipped copy follows the annotation and has done since before this bundle. I swept both string
bundles for every phrasing of that promise and found no other instance. **Please correct the
artboard's body**, so the next person to read it does not build what it says.

## 3. The chapter-02 web-only note groups two different things

The note says „kein Android-Screen zeichnet Presence/Live-Sync", with the reason „openapi.json führt
keine Presence-Daten".

The reason is exactly right about **presence** — there is no presence data, and no production screen
draws the indicator or the update pill. `KrtPresenceIndicator` lives in the design system and is
called only from the dev showcase.

It is not right about **live-sync**, which ships and works: eight ViewModels observe `/ws/sync`
rooms and refresh silently when a peer changes a shared surface. It draws nothing — no pill, no
names — so it is invisible to a screenshot, which is presumably why it looked absent. The rooms
exist server-side; that is the thing presence lacks.

**What I need:** split the sentence. Presence stays web-only; live-sync is Android canon and is
already spec'd (`docs/specs/sync.md`) and tested.

## 4. The lock screen draws a system control as an app control

Chapter 04 artboard 5 draws two actions: „MIT BIOMETRIE ENTSPERREN" and, under it,
„GERÄTESPERRE VERWENDEN". Your annotation says both go through the same prompt.

That prompt is the platform's `BiometricPrompt`, configured `BIOMETRIC_STRONG or DEVICE_CREDENTIAL`.
Android draws the device-credential fallback **inside** it, as a control this app neither owns nor
styles. A second button in the app would open the sheet the first one opens, and its only effect
would be to suggest that the first one had not.

The app therefore ships one button, and it keeps the label „Entsperren": „Mit Biometrie entsperren"
describes a button standing *beside* an alternative, and on a button that opens both paths it names
one and hides the other. Taking your label while dropping your second button would follow the letter
and lose the sense.

This is recorded as [ADR-0013](../../adr/0013-the-lock-screen-has-one-button-because-the-prompt-has-two.md).
**If you disagree, say so and I will build it as drawn** — the decision is contingent on the
authenticator set, not on taste, and if `DEVICE_CREDENTIAL` ever comes off, both your button and
your label become right again.

## 5. Two copy values I had to choose between

- **The org badge in the all-units scope.** Chapter 02 §3 lists „Alle Einheiten" among the badge
  values, beside „Bereich Profit" and „SK VANGUARD"; the switcher's row in artboard 7 reads „Alle
  Org-Einheiten". The badge now uses the shorter one and the sheet row the longer, which is what the
  two places show. Confirm that is deliberate rather than a drift between two drawings.
- **The sheet subtitle in chapter 09, artboards 6–9** reads „12 Einträge · Modus LOCATION · POST
  inventory/bulk-rebook". I shipped only „12 Einträge" — the mode name and the HTTP verb read as
  handoff annotation rather than as copy for a member. Say if the line was meant to render.

## 6. Two more things worth drawing, both found by building

Not corrections — states the bundle has no picture for, which I have implemented from the rules and
would like drawn so the next pass has something to compare against:

- **The bulk sheet's third step on a real failure that is not a 403** — a network drop or a 5xx
  mid-batch. Artboard 10 covers the refusal; chapter 14's error picture covers a screen. What a
  half-finished batch's *sheet* shows is currently my reading of „lassen das Sheet ebenfalls offen".
- **The sign-up sheet after a successful sign-up.** Artboard 3 ends at the CTA. The app closes the
  sheet and the detail screen's own bar flips to „Abmelden", which seems right and is undrawn.

---

Everything in sections 1–4 is already recorded on the app side, so nothing is blocked on this. What
would help is a decision on each, so the artboards and the build stop disagreeing quietly.
