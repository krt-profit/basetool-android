# Missing artboards, round 10 — the Einsatz's Verwaltung half

Chapter 06 draws the Einsatz detail as **seven tabs and one filled CTA**, and every one of those
tabs is a *reading* surface. The one management affordance it draws is the list's
„Einsatz erstellen" FAB — and not the form behind it.

Round 9's roster work is built and shipped: the per-row check-in, the manager payout toggle and the
„Funktion an Bord" select all came from artboard 2's own handoff notes, so they needed no new
drawing. **Everything else a manager does is undrawn**, and this is the request for it.

> [!important] § 10a is answered and § 10d is retired — 2026-08-29
> The repository owner settled the shape while this round was open: the Verwaltung is an **eighth
> tab**, drawn only for a manager (ADR-0018). Everything in the table below is now **built** against
> that shape and device-verified; what is still wanted is the **drawing** of that tab, not a
> decision about where it lives. § 10f is new, and comes out of the same review.

> [!important] The data layer is ready and the UI is not being invented
> `MissionAdminSource` exists, is tested, and carries the three section edits. What is missing is a
> drawing, not an endpoint. Following the Beförderung precedent — *a derived layout is not a
> followed one* — nothing below has been built against a guess.

## What a manager can do on the web and nowhere in the app

Eight surfaces, in the order they cost the organisation most:

| # | Surface | Endpoint | Why it hurts |
| --- | --- | --- | --- |
| 1 | Set the **actual start time** | `PATCH /{id}/schedule` | The server refuses **every** check-in until it is set. Until a manager can set it from the app, the roster's check-in — which *is* drawn — can never be used on a phone. |
| 2 | Edit the Einsatz: title, briefing, meeting point | `PATCH /{id}/core` | The briefing is the six-field block artboard 2 draws. It can be read and not corrected. |
| 3 | Internal ↔ open | `PATCH /{id}/flags` | Decides whether guests and anonymous visitors can find it at all. |
| 4 | **Frequenzen**: add, remove | `POST/DELETE /{id}/frequencies` | The tab is drawn, tap-to-copy and all. It is read-only. |
| 5 | **Einheiten**: add, edit, delete | `POST/PUT/DELETE /{id}/units` | Artboard 2 draws „+ Person zuweisen — antippen oder halten & ziehen" and „Einheiten sind offen (keine Slot-Grenze)". The crew *assignment* is drawn; creating the Einheit that holds it is not. |
| 6 | **Crew**: assign, change roles, remove | `POST/PUT/DELETE /{id}/units/{unitId}/crew` | See 5 — the drawn interaction has no drawn container. |
| 7 | **Party lead** | `PUT /{id}/party-lead` | „Leiter Rhea" is in the drawn fact row and cannot be changed. |
| 8 | **Manager** add / remove, **participant** add / remove | `POST/DELETE /{id}/managers/{userId}`, `POST/DELETE /{id}/participants` | Who may run the Einsatz, and who is on it at all. |

## What we would like drawn

### 10a — One Verwaltung surface, or several? — **ANSWERED, 2026-08-29**

> **The repository owner decided: an eighth tab, „Verwaltung", drawn only for a manager.**
> Recorded as ADR-0018 and `REQ-APP-MIS-022`; built and device-verified. It lands the app on the
> web's own naming — `mission.tab.admin=Verwaltung`.
>
> The tab row is horizontally scrollable, so an eighth entry costs nothing; it is **absent** for a
> caller the server says may not manage, rather than locked, so most members still see seven.
>
> **What we would still like drawn** is that tab: the section rhythm, the save affordances, and what
> a section looks like mid-save. It is currently composed from drawn parts (`KrtTextField` under
> `KrtSectionTitle`, one full-width `KrtGhostButton` per section, a filled `KrtCtaButton` for
> „Einsatz läuft jetzt") and its composition is unratified.

The question as originally asked, for the record:

The web puts all of this behind a `verw` tab. Chapter 06 gives the app **seven** tabs already, and
an eighth on a phone would push the row past what fits.

- **A sheet from the head.** The drawn head carries the title, the status badge and the fact row; a
  pencil affordance there opens one scrolling sheet with the Kern, Zeitplan and Flags sections.
- **Per-tab edit affordances.** Frequenzen gains a „+" and per-row delete, Einheiten gains a „+",
  the head gains the party lead — each where the thing being edited already lives.

### 10b — The three sections have three independent locks

This is a mechanism the drawing has to survive rather than a look. Kern, Zeitplan and Flags each
carry their **own** version counter on the server, deliberately, so a manager fixing the briefing
does not collide with a colleague moving the start time.

If the sheet in 10a saves all three at once, that property is thrown away and the first concurrent
edit produces a 409 the member cannot make sense of. **Please draw the save as per-section** — three
save affordances, or one that only submits what changed — and say which.

### 10c — Setting the actual start time is not a date field, it is a verb

„Der Einsatz läuft jetzt" is what a manager means. A date-time picker prefilled with *now* is
technically the same write and reads as paperwork.

Chapter 06 draws the status badge (`Geplant` / `Aktiv`) in the head. Should starting the Einsatz be
an action **on that badge** rather than a field in a form? If so, what does it look like, and what
happens to the badge while the write is in flight?

### 10d — What a locked manager control looks like on a **head**, not a row — **RETIRED, 2026-08-29**

> Nothing manager-owned lives in the head any more: 10a's answer puts it all in the Verwaltung tab,
> and the tab itself is absent rather than locked for a non-manager. The question only existed
> because a pencil might have had to sit among the head's three chips. Chapter 09's artboard 14
> continues to cover rows and buttons, which is where the locking now happens — inside the tab, for
> a manager who is offline or mid-write.

The question as originally asked, for the record:

Chapter 09's artboard 14 settles the locked-but-tappable pattern for rows and buttons. The head is
different: it is dense, it is pinned, and it already carries three chips.

### 10e — Two open questions we cannot answer from the code

1. **Removing a participant who has checked in.** The endpoint allows it. Does the app warn, and
   with what wording? The web does not.
2. **Adding a manager** resolves a member through the user search. Chapter 12's remote combobox is
   the obvious control — is it the right one here, or does a manager list want something else?

### 10f — Nothing in a page may reuse a navigation label — **new, 2026-08-29**

Not a request so much as a constraint we would like the next chapter to respect.

The Einsatz's first tab was „Übersicht" (ch. 06) and the shell's Home destination is „Übersicht"
(ch. 03). On the web those never meet — one is in a sidebar, the other inside the page. On a phone
both are on screen at once, about 200 dp apart, and the owner tapped the tab expecting the
dashboard. The tab is **„Briefing"** now (`REQ-APP-MIS-024`).

**Please check any new page-tab, chip or filter label against the five navigation labels** —
„Übersicht", „Einsätze", „Aufträge", „Lager", „Mehr" — before it ships in a chapter.

## What is already correct and needs nothing

Recorded so the next round does not re-litigate it: the seven-tab structure with its counts, the
head's fact row, the roster row (name · status chip · „Wunsch: …" · the three manager controls, all
shipped from artboard 2's own notes), the one-filled-CTA rule, and the locked-but-tappable pattern
as it applies to rows. None of that needs redrawing.
