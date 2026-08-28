# Missing artboards, round 10 — the Einsatz's Verwaltung half

Chapter 06 draws the Einsatz detail as **seven tabs and one filled CTA**, and every one of those
tabs is a *reading* surface. The one management affordance it draws is the list's
„Einsatz erstellen" FAB — and not the form behind it.

Round 9's roster work is built and shipped: the per-row check-in, the manager payout toggle and the
„Funktion an Bord" select all came from artboard 2's own handoff notes, so they needed no new
drawing. **Everything else a manager does is undrawn**, and this is the request for it.

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

### 10a — One Verwaltung surface, or several?

The web puts all of this behind a `verw` tab. Chapter 06 gives the app **seven** tabs already, and
an eighth on a phone would push the row past what fits.

Two shapes seem plausible and we would rather be told than choose:

- **A sheet from the head.** The drawn head carries the title, the status badge and the fact row; a
  pencil affordance there opens one scrolling sheet with the Kern, Zeitplan and Flags sections. It
  keeps the tab row at seven and matches the booking sheet's shape (ch. 09, artboard 2).
- **Per-tab edit affordances.** Frequenzen gains a „+" and per-row delete, Einheiten gains a „+",
  the head gains the party lead — each where the thing being edited already lives, and no new
  surface at all.

The second reads better to us for 4–7 and worse for 1–3, which have no tab of their own. A mixed
answer is fine; we would like it stated.

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

### 10d — What a locked manager control looks like on a **head**, not a row

Chapter 09's artboard 14 settles the locked-but-tappable pattern for rows and buttons. The head is
different: it is dense, it is pinned, and it already carries three chips. If the party lead or a
pencil lives there, we need the lock's shape at that size.

### 10e — Two open questions we cannot answer from the code

1. **Removing a participant who has checked in.** The endpoint allows it. Does the app warn, and
   with what wording? The web does not.
2. **Adding a manager** resolves a member through the user search. Chapter 12's remote combobox is
   the obvious control — is it the right one here, or does a manager list want something else?

## What is already correct and needs nothing

Recorded so the next round does not re-litigate it: the seven-tab structure with its counts, the
head's fact row, the roster row (name · status chip · „Wunsch: …" · the three manager controls, all
shipped from artboard 2's own notes), the one-filled-CTA rule, and the locked-but-tappable pattern
as it applies to rows. None of that needs redrawing.
