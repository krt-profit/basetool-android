# Design gaps, round 14 — what walking the app on a device turned up

**Date:** 2026-08-31 · **Previous:** `MISSING_ARTBOARD_PROMPTS_13.md`.

Round 13's list came out of *building* chapter 18. This one comes out of *running* the app: every
screen and every state opened on three emulators and held against its **rendered artboard**, not
against the chapter's prose. That is the only way several of these show up at all — a caption can
be correct while the picture beside it is not, and a field can be drawn in a frame that no endpoint
can fill.

Each item says where it is, what the app does instead, and what would settle it. Where the app was
wrong it has been corrected already and the item is not here; what is here is what the
specification cannot answer as it stands.

---

## S1 · Artboard 06-13's drawing contradicts its own corrected caption

**Where:** `06 Missionen.dc.html`, artboard 13 („Ablauf & Ziele — Zeilenaktionen als Icon-Buttons").

The caption was corrected on 30.08.2026 to *„vier sichtbare Ziele: Haken · ↑ · ↓ · ⋮ (Bearbeiten ·
Duplizieren · Löschen)"* and states that ↑/↓ are 40×44 dp and dimmed at the ends. **The picture was
not redrawn:** it still shows three icon buttons per row (Haken · ✎ · 🗑), a caption line
„Reihenfolge über ↑ / ↓ je Zeile …" *below* the list, and a „⣿ SORTIEREN" button — the very
mechanism the correction struck.

**What is missing:** the artboard redrawn to match its caption — four targets on the row, no
SORTIEREN button, no explanatory line under the list.

**The app follows the caption.**

## S2 · The Ziele row's tick box has no field on the wire

**Where:** `06 Missionen.dc.html`, artboard 2, Ziele tab; and artboard 13's *„Ziele identisch:
dieselben drei Icons"*.

The row draws a `.matrix-flag` (18 dp, orange-filled with a black ✓ when set) as the first element,
and two of the five drawn goals are ticked, with their text dimmed to `#8A8A8A`. `MissionObjectiveDto`
carries `id`, `title`, `kind`, `orderIndex` — **no done/achieved field**, and there is no
`POST /missions/{id}/objectives/{oid}/toggle` counterpart to the step's toggle.

**What is missing:** either a decision that Ziele are not tickable (then the flag and the dimmed
text come out of the artboard, and „dieselben drei Icons" becomes two), or a backend ask for the
field and its endpoint.

**The app builds the row without the flag** and gives a Ziel no tick action.

## S3 · Chapter 02 §1 and the token artifact still disagree on the icon button

**Where:** ch. 02 §1 vs `artifacts/Theme.kt` (`KrtDimens.iconButton`).

The chapter says an icon-only button is a **48 × 48 dp** target; the token artifact says **44**.
Carried over from round 13 and still open. The app follows the chapter (48), except the Ablauf's
two move buttons, which the ch. 18 §3 correction explicitly ratified at 40 × 44.

## S4 · Artboard 2 draws the status badge in the head; artboard 06-a draws it in the band

**Where:** `06 Missionen.dc.html`, artboard 2 vs artboard 06-a.

Artboard 06-a (F2) makes the lifecycle band *„die EINE Fläche für den Lebenszyklus"*; artboard 2
still draws „Geplant" under the title in the top bar. Drawing both puts the same word on screen
twice, a finger apart.

**The app follows 06-a** and leaves only the org badge in the head.

## S5 · The drawn frequency values cannot be stored

**Where:** `06 Missionen.dc.html`, artboard 2, Frequenzen tab.

The four drawn values — 148.500, 148.510, 148.520, 121.500 — all carry **three** decimals. The API
declares `AddCustomFrequencyRequest.value` as `number, maximum 999.99` and the backend validates
`@Digits(integer = 3, fraction = 2)`; the server refuses every one of them with
„numerischer Wert außerhalb des gültigen Bereichs (<3 digits>.<2 digits> erwartet)".

**What is missing:** either the artboard's values corrected to two decimals, or a backend ask to
widen the constraint (SC radio frequencies are conventionally written with three).

## S6 · „Anteil je Teilnehmer" ignores who donates

**Where:** `06 Missionen.dc.html`, artboard 2, Finanzen tab.

The card closes on „ANTEIL JE TEILNEHMER (14) 5.335 aUEC" = 74.700 ÷ 14. The wire carries no
per-head figure, so the app computes it the same way — but a participant whose payout preference is
`DONATE` is still counted in the divisor, which makes the figure wrong for everybody else in the
direction that matters (too low) and wrong for the donor (they get nothing).

**What is missing:** whether the share divides by *all* registered participants or only by those
taking a payout. The app follows the artboard and names the divisor in the label.

## S7 · A crew member wearing an HVU chip has no field

**Where:** artboards 06-2 (Einheiten tab) and 06-14 — „Dorn **HVU**" beside a person's name.

HVU is a property of the **Einheit** (`MissionUnitDto.highValueUnit`); no participant or crew DTO
carries one.

**What is missing:** either the chip moved to the unit's header in the drawing, or a backend ask.

## S8 · Switching another member's payout is not placed

**Where:** `06 Missionen.dc.html`, artboard 2, Teilnehmer tab.

The row draws the payout as a read chip and stops. The write exists (`UpdateParticipantRequest`)
and nothing in the register strikes it, so the app keeps it as a ghost button under the row.

**What is missing:** where a manager changes it — a row overflow, the member's own sheet, or a
ruling that it is web-only.

## S9 · The status filter chips carry the status's own tone, and the unselected ones are dashed

**Where:** `10 Auftraege.dc.html`, artboard 1 (and the same shape on other queues).

„OFFEN" is drawn with a **blue** outline and „IN BEARBEITUNG" with a **yellow** one — each selected
chip in its own status tone rather than in the brand orange — while „ABGELEHNT" and
„ABGESCHLOSSEN" are **dashed**, which is how the artboard says „not selected".

`KrtFilterChip` has one look: hairline when off, orange fill when on. Giving it a per-status tone
and a dashed off-state is a **design-system change**, so it is not invented here.

**What is missing:** a ruling on whether the filter chip takes the tone of what it filters, and
whether an unselected filter chip is dashed everywhere or only on this queue.

## S10 · The local test stack cannot show fields added since the last release

**Not a design gap — a verification limit worth writing down.** `docker-compose.yml` runs the
backend from `ghcr.io/krt-profit/basetool-backend:stable`, and the image in place is ten days old.
`MissionListDto.registeredCount` is in the committed `openapi.json` and in the generated DTO, but
the running server does not send it — so the dashboard card's „{n} angemeldet" cannot be seen on
the device until the stack pulls a newer backend.

## S11 · The Lager's „MATERIAL: ALLE" and „ORT: ALLE" chips have no field on the tree's endpoint

**Where:** `09 Lager.dc.html`, artboard 1 — three filter chips above the tree.

The tree's group rows come from `GET /api/v1/inventory/aggregated`, whose only parameters are
`catalog`, `page`, `size`, `sort`. There is no `materialIds` and no location parameter there —
`materialIds` exists on `/inventory/all/grouped` (the *inside* of one group) and a `locationId`
only on the per-stack `entries` read.

So neither chip can narrow what the tree draws. The app builds the third one („Nur mit Bestand")
and leaves these two out rather than filtering client-side over a paged read, which would narrow
the page rather than the warehouse.

**What is missing:** `materialIds` (and a location filter) on `/inventory/aggregated` — or a
ruling that the two chips come out of the artboard.

## S12 · The `.kpi-card` figure has no token at its drawn size

**Where:** `12 Bank.dc.html`, artboard 1 — the account cards.

The artboard draws the balance at `font: 900 20px` (`.kpi-card .kpi-value` is `1.3rem`). The
Compose scale jumps from `headlineMedium` (Bold 24) to `displaySmall` (Black 32) — there is no
Black-20 rung. `KrtKpiCard` uses `displaySmall`, so every account balance renders about half again
as large as drawn.

**What is missing:** either the rung, or a ruling that the KPI figure takes `headlineMedium`.
The card's **title** was a separate, unambiguous miss and is fixed: `.kpi-title` is bold white and
the app had it muted.

## S13 · The Bank's own header drops the org context

**Where:** `12 Bank.dc.html`, artboard 1 — „← BANK  (PROFIT)" and „GESAMT BEREICH PROFIT".

The app's Bank top bar carries no org badge, and the total tile reads „GESAMT" without the unit
it totals. The string `bank_total_for` („GESAMT %1$s") exists in both bundles and is **used
nowhere** — the plumbing was never done.

Whether a pushed sub-screen keeps the org chip is a **navigation-canon** question (ch. 03), not a
Bank one, which is why it is asked rather than answered: the Einsatz detail's badge names the
*record's* org, and this one would name the *active* org.

## S14 · The Hangar row draws one action; the app has two

**Where:** `08 Hangar.dc.html`, artboard 1 — each ship row ends in a single ✎.

The app also puts a 🗑 there. Per-ship deletion is drawn nowhere else either: artboard 08-4's ⋮
overflow carries „Hangar leeren" (all of them) and 08-6 is that danger modal, so the single delete
has no drawn home.

With both buttons the row has about 190 dp for its chips and location, which squeezed „Everus
Harbor" out of existence; the chips wrap now, so nothing is lost.

**What is missing:** where a single ship is deleted — the row, the editor sheet the ✎ opens, or a
row overflow. The app keeps it on the row until that is ruled.

## S15 · A Raffinerieauftrag has no human-readable number

**Where:** `11 Raffinerie.dc.html`, artboard 1 — each card is titled „Raffinerieauftrag #7841".

`RefineryOrderListDto` and `RefineryOrderDto` carry `id` as a UUID and nothing else identifying.
There is no `displayId` of the kind `JobOrderDto` has.

The app titles the card with the station instead, which on a squadron that refines at one station
makes several cards read alike — exactly what the artboard's number is there to prevent.

**What is missing:** a `displayId` on the refinery order, or a ruling on what the card leads with.

## S16 · „UEX-Stand heute, 06:12" has no field

**Where:** `16 Materialien.dc.html`, artboard 1 — the count line reads
„148 MATERIALIEN · UEX-STAND HEUTE, 06:12".

`MaterialPriceOverviewDto` carries `id`, `name`, `category`, `isIllegal`, `isVolatileQt`,
`isVolatileTime`, `minPriceBuy`, `maxPriceSell`; the page wrapper carries only paging. Nothing
says when the prices were last pulled from UEX.

Without it the app cannot say how stale a price is — which is the whole reason the artboard puts
the stamp there.

**What is missing:** a freshness timestamp on the prices-overview read.

## S17 · The Einstellungen account row loses the member's rank and unit

**Where:** `13 Einstellungen.dc.html`, artboard 2 — the first row reads
„GrafRotz / Specialist · Staffel 1" with a „PROFIT" org badge on the right.

The app draws the name alone: no sub-line, no badge. `/users/me` carries the roles (as **display
names**, per the app's own contract note) and the org units, so the sub-line is buildable — but
which of several roles is „the rank", and whether the badge names the *active* org or the member's
*home* unit, is not something the artboard settles.

**What is missing:** which role the sub-line shows when a member holds several, and which org the
badge names.

## S18 · „Mit Discord anmelden" needs a capability answer

**Where:** `04 Auth.dc.html`, artboard 1 — „Genau zwei Einstiege: Keycloak (Custom Tab) und
Discord", the Discord one „only when the IdP is configured (fail-closed guild gate)".

The app has no endpoint that says whether the realm has the Discord IdP configured, so the second
button is not drawn: one that fails after the tap is worse than one that is absent. Documented in
`LoginScreen`'s KDoc, and it is a **backend ask** rather than a design question.

**What is missing:** an unauthenticated capability read naming the configured identity providers.

## S19 · Chapter 03's „Mehr" lists two destinations the app does not have

**Where:** `03 Navigation.dc.html` — the phone's „Mehr" list names Hangar, Materialbörse,
Raffinerie, Mein Inventar & Blueprints, Bank, **Beförderung**, **Organigramm**, Einstellungen.

- **Beförderung** is settled and needs no answer: the screen was built, then removed by owner
  decision (2026-08-25, reaffirmed 2026-08-26) and the reasoning is written down in
  `docs/specs/promotion.md`. Chapter 03 still lists it.
- **Organigramm** is named nowhere else — no chapter draws it, no spec mentions it, no endpoint is
  obvious. It may be a leftover of the web app's own menu.

**What is missing:** chapter 03's Mehr list reconciled with what the app is meant to have — or a
statement that the two entries are the web's and not the app's.

## S20 · Artboard 10-2 draws the position chips mixed-case; chapter 02 says chips are uppercase

**Where:** `10 Auftraege.dc.html` artboard 2 — the position card's two data chips read
„Gebucht: **748 SCU**" and „Zugesagt: **300 SCU**", label muted and value bold white, in mixed
case. `02 Components.dc.html` §3 states the rule outright: „Chips — squared, **11 sp uppercase**",
and every chip it draws is uppercase.

**Built as:** uppercase, following chapter 02, because a component rule outranks one frame's
rendering.

**What is missing:** either the artboard corrected to uppercase, or a named exception for
label-plus-value data chips — including whether the value keeps its bolder weight, which chapter 02
shows on „IRI: **1.200 SCU**" but does not state as a rule.

## S21 · „fällig 21.08." on the Auftrag head has no field

**Where:** `10 Auftraege.dc.html` artboard 2 and the tablet frame — the detail head reads
„Prio 1 · fällig 21.08.", and the tablet frame adds „angelegt 12.07. · fällig 21.08.".

`JobOrderDto` carries `createdAt` and nothing else dated: there is no due date on a job order, in
the contract or in the backend model. Chapter 18 strikes several drawn-but-unbuildable fields; this
one is in neither the struck list nor the seven backend asks.

**What is missing:** a decision — strike the due date, or add it as a backend ask beside G1–G7
(field on `JobOrderDto`, and who may set it).

## S22 · The Materialbedarf figures are tinted by an unstated rule

**Where:** `10 Auftraege.dc.html` artboard 2, Materialbedarf tab — three rows, three tints:
Laranite 452 SCU in warning yellow, Bexalit 500 SCU in danger red, Titanium 28 SCU in plain white.
No chapter says what decides the tint. Against the frame's own numbers the ratio of outstanding to
required is 38 %, 100 % and 29 % — so a threshold pair somewhere between 30 % and 100 % fits, and
so does „nothing booked at all = red".

**What is missing:** the thresholds, and what they are measured against. Until they are named a
figure that goes red is a guess about urgency, which the chapters otherwise forbid.

## S23 · The Auftrag detail's priority band is drawn nowhere

**Where:** the app puts „AN DEN ANFANG / ↑ / ↓" under the detail head. Artboard 10-2 draws no such
band, and artboard 10-1's handoff puts priority where the chapter wants it: „Prio-Reorder
(Logistiker): long-press card → drag; fallback in overflow ‚Priorität ändern'" — on the queue.

Not removed here, because the band is currently the app's only way to change a priority: the
queue's long-press drag is not built, so striking the band would drop the function rather than move
it.

**What is missing:** approval either way — move it into the detail's overflow as
„Priorität ändern" and build the queue's drag, or ratify the band and draw it.

## S24 · The page-level status badge is drawn with a border, built with a fill

**Where:** `02 Components.dc.html` §3 — „PAGE-LEVEL STATUS BADGE" is a hairline-bordered box with a
square dot and tinted uppercase text. `KrtStatusBadge` draws a tinted fill with a 3 dp leading edge
and no border.

Low stakes — the badge is the loud sibling and the detail heads use the pill — but the two shapes
are different enough that a member sees one thing in the chapter and another in the app.

**What is missing:** which of the two is the badge.

## S25 · Chapter 13 still draws the Beförderung screen the owner removed

**Where:** `13 Einstellungen.dc.html` is titled „Beförderung & Einstellungen" and its **artboard 1**
is a full Beförderungs screen — current rank, next rank, an „Aufstiegs-Voraussetzungen 3 / 5"
progress block and a three-column evaluation matrix, with two handoff notes dated 25.08.2026 that
*ratify* the matrix as final.

The screen was built and then **removed by owner decision** (2026-08-25, reaffirmed 2026-08-26);
the reasoning is `docs/specs/promotion.md`. This is the same decision [[S19]] already records for
chapter 03's „Mehr" list — but chapter 13 does not merely name it, it draws it and ratifies it,
which reads as an instruction to build it.

**What is missing:** chapter 13 reduced to Einstellungen, or a note on artboard 1 saying the
surface is struck and why.

## S26 · The Einstellungen account row draws a rank the app has no field for

**Where:** `13 Einstellungen.dc.html` artboard 2 — the KONTO row reads
„GrafRotz / **Specialist · Staffel 1**" with a PROFIT badge beside it. The app draws the handle
alone.

Rank is not on any endpoint the app consumes: `/users/me` returns roles as **display names** and
carries no rank field, and `Identity` has none. The unit is known (the active org unit is right
below it), the rank is not.

**What is missing:** either the rank struck from the row, or a backend ask beside G1–G7 naming the
field. Same class as [[S21]]: drawn, wanted, and undrawable today.

## S27 · The version footer's server-status dot has no source

**Where:** `13 Einstellungen.dc.html` artboard 2, handoff —
„Version-Footer: Server-Status-Dot (grün/gelb/rot) + App-/API-Version, tabular". The app prints
the version line without a dot.

Three colours imply three states, and nothing says what they are or where they come from: the app
has no health endpoint in its contract, and „is the server up" is answerable only by a request
that has just succeeded or failed. Read literally it would be a permanent green dot.

Chapter 04's login frame draws the same footer with the dot green and the words
„Server bereit · v1.4.2 (37)", which gives green a meaning on **that** screen — the gate check has
just answered. Inside the app, where every screen has its own last request, it is not clear what
the dot would be reading.

**What is missing:** what the three colours mean and what they are read from — or the dot kept on
the login screen, where the gate check answers it, and struck in the Einstellungen footer in favour
of the offline banner chapter 14 already defines.

## S28 · The org pill is tinted by unit kind in chapter 02 and by row role in chapter 10

**Where:** `02 Components.dc.html` §3 draws the four pills side by side — „Bereich Profit" orange,
„SK VANGUARD" grey, „TITAN" cross-org yellow, „Alle Einheiten" grey — and calls the tone the
badge's *relationship to the current context*. Under that rule a Spezialkommando is grey wherever
it appears.

`10 Auftraege.dc.html` artboard 1 draws the queue rows the other way: **FÜR is orange and DURCH is
grey in every row**, including the row where FÜR is „SK VG" (orange) and the row where DURCH is
„SK VG" (grey). The same unit takes both tints depending on which side of the row it stands on.

The two rules disagree, and each page reads as authoritative on its own.

**Built as:** chapter 02's rule — the component chapter defines the component, and „which unit is
this" is a property of the unit rather than of the column it is printed in. The app had drawn a
Spezialkommando in the same orange as the member's own unit, which matched neither reading; that is
fixed.

**What is missing:** which of the two the pill follows. If it is the role, chapter 02 §3 needs the
exception written into it; if it is the kind, chapter 10 artboard 1 needs its second row redrawn.

## S29 · „Angebote links, Gesuche rechts" — two columns of what?

**Where:** `18 Abweichungs-Register.dc.html` §E9 ratifies the Materialbörse's tablet layout in one
sentence: „Materialbörse: zwei Kartenspalten, nicht drei. **Angebote links, Gesuche rechts**, je
480 dp, 24 dp Rinne; eine dritte Spalte macht die Karten schmaler als die Telefon-Fassung. Ab
1600 dp wachsen die Spalten, nicht ihre Zahl." Chapter 17 has no tablet frame, so this sentence is
the whole specification.

It carries two readings and the app has picked one:

- **Two columns of cards.** The question the paragraph answers is *how many columns* — its own
  justification is about card width, and „zwei, nicht drei" is the ruling. The segment stays, and
  whichever side is selected flows into two columns. **This is what the app does**, and its code
  cites the same paragraph for it.
- **Two columns of content.** Read literally, the left column holds the offers and the right the
  requests, and the tablet has no segment at all.

**What is missing:** which one. The second is a different screen, not a different layout — it needs
both sides loaded, paged and refreshed at once, so it is not a change to make on a coin-flip
reading of one sentence.

## S30 · The Operationen list has no detail pane on a tablet, its sibling segment does

**Where:** „Einsätze" and „Operationen" are the two halves of one segment. Opened from the rail,
Einsätze is a list-detail with the pane chapter 06's tablet frame draws. Opened from „Mehr",
Operationen is a full-width list — same segment, same screen head, different layout, and tapping a
row pushes a full-screen detail instead of filling the pane beside it.

Chapter 06's tablet frame only draws the Einsätze half, so nothing says which of the two the
Operationen half should follow.

**What is missing:** whether Operationen is a pane of the same surface (and therefore reached from
the rail rather than from „Mehr"), or a screen of its own that happens to share a segment with one.

## S31 · The Bank's account rows are phone cards on a tablet

**Where:** `12 Bank.dc.html`'s tablet frame lists the accounts as compact one-line rows — name
left, balance and delta right — in a ~360 dp column, with the sparkline and the booking table in
the **detail** pane. The app puts the phone's three-line card (name / balance / delta + sparkline)
in that column, so three accounts fill it.

This is chapter 02 §5's rule („tablet keeps tables, phone collapses to rows") applied to a list
rather than a table, and the chapter states it for tables only.

**What is missing:** whether §5's rule extends to list rows — i.e. whether a card that is right on
a phone must become a row in a tablet's list column, and which surfaces that covers.

---

# Round 15 — what building the round-14 answers turned up

The handoff answered all 31 items and eleven chapters changed; the app follows them. Two answers
could not be built as written, and one component note disagrees with the spec's own scale.

## R1 · The login screen's server-status dot has nothing to read

**Where:** `13 Einstellungen.dc.html`, S27 — the dot is struck in the Einstellungen footer („die App
hat keinen Health-Endpunkt") and **kept** on the login screen, „wo die Gate-Prüfung ihn
beantwortet". Artboard 04-1 draws it green beside „Server bereit · v1.4.2 (37)".

On the login screen the app has not yet asked the server anything. The gate check
(`AccountGateViewModel`) runs **after** a successful sign-in, on its own screen; before that the
only signals are the device's connectivity and, once a sign-in has been tried and failed,
`login_error_unreachable`. A dot drawn before the first attempt would be permanently green — which
is the same objection S27 raised against keeping it in the Einstellungen.

**Built as:** no dot, the version line alone — the same as the Einstellungen footer.

**What is missing:** what the dot reads *on the login screen, before a request has been made*. If
the answer is „the device's network", it is an offline marker and should say so; if it is „the last
attempt", it has nothing to show on a cold start.

## R2 · S12's sizes contradict the spec's own type scale

**Where:** `12 Bank.dc.html`, S12 — „Die Kontostand-Zahl der Konto-Karte ist **headlineMedium
(Black 20)** … Das KPI-Gesamt oben bleibt **headlineLarge (Black 28)**".

`artifacts/Theme.kt`, which the app's scale mirrors line for line, defines `headlineMedium` as
**Bold 24/30** and `headlineLarge` as **Black 32/38**. Neither bracketed size matches the entry it
names, and there is no Black 20 or Black 28 step in the scale at all.

**Built as:** the **names**, not the numbers — the card's figure is `headlineMedium`, one step below
the total. That is the distinction the note is about („die kleinere Schwester, nicht dieselbe
Stufe") and it is the half the app can act on.

**What is missing:** either the bracketed sizes corrected, or two new steps in the scale.

## R3 · The KPI total tile has no named scale entry

**Where:** the same note says the total „stays headlineLarge". In the scale `headlineLarge` carries
`letterSpacing 1.6` — it is the uppercase h1 — and applying it to a figure tracks the digits apart.
The app draws the tile with `displaySmall` (Black 32, no tracking), which is the same size and
weight without the tracking.

**What is missing:** confirmation that a KPI figure uses `displaySmall` rather than the h1 step, or
a tracking-free headline entry to point at.

