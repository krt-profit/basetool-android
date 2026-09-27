# ADR-0024 — „Mein Lager" is a mode of the Lager, and follows the server where the artboard does not

- **Status:** Accepted
- **Date:** 2026-09-27
- **Deciders:** @greluc
- **Related:** design ch. 19 (round 18, basetool-android #188), `REQ-APP-INV-025`–`031`,
  main repo REQ-INV-007, -036, -046, -052, krt-profit/basetool#2097, #2107

## Context

The app had no surface for the member's own stock, and three Lager writes waited for one: the
personal rebooking, the `PERSONALIZE` / `DEPERSONALIZE` modes of the bulk rebooking, and — new with
the external exchange — the org-unit change of a personal row. The owner reversed the old scope
decision on 2026-09-26 and had the screen drawn first (chapter 19). Building it against the running
server turned up five places where the drawing, the server and the web disagree, and two where the
drawing asks for something this change does not deliver.

## Decision

**„Mein Lager" is a mode of the existing Lager screen**, switched by the segment „Org-Lager | Mein
Lager" as artboard 1 draws it, on the same tree component, with one more grouped read
(`my-inventory/grouped`, both catalogues) and a move holder beside the view model. Where the
artboard and the server disagree, **the server decides** — the design handoff ranks below the
running contract for behaviour, and the web follows the server too:

1. **One pool for a selection moving into the shared Lager.** Artboard 5's footnote says every row
   keeps the unit it has. `InventoryCheckoutService.bulkRebookPersonalMarker` resolves **one**
   target unit for the whole selection, and the web's bulk dialog shows the pool picker for exactly
   that reason. The app shows the picker too.
2. **The tree keeps chapter 09's rails** rather than chapter 19's card per material: chapter 19's own
   decision is „dieselbe Baum-Komponente", and its cards are a sketch of the same three levels.
3. **The unit pill carries the unit's name only.** Artboard 1 writes „Staffel IRIDIUM", „Bereich
   Profit"; `InventoryStackDto.owningSquadron` has no kind, and inventing the prefix from the name
   would be wrong for every unit whose name already carries one.
4. **The info block uses the design system's `Info` token** (`#355DDC`), not artboard 6's
   `#3B82F6`, which exists in neither the design system nor its mirror.
5. **„Alle wählen" sits in the selection head** as a compact ghost button; artboard 2 names the
   function in its handoff and draws no place for it.

Deferred, each with its reason:

- **The segment is not remembered per device, and `?scope=my` is not a deep link.** Remembering it
  needs a stored preference, which makes it a new on-device store under § 25 TDDDG and a new
  `BasetoolApplication`-owned DataStore (ADR-0014). Within the process the scope survives
  navigation.
- **Artboard 7 sets „Nicht möglich:" in red and the count in white**; the app draws the sentence in
  one weight, because splitting a localised sentence at a colon would break in the next language.

## Consequences

- The bulk move sheet gained a mode field in „Mein Lager"; „Ort / Nutzer" hands over to the existing
  place move, so the Org-Lager's sheet is unchanged.
- The stack key now includes the owning unit, the personal flag and the „gestohlen" marker, so two
  stacks that differ only there never share their opened entries.
- A long-press on a collapsed group reads its stacks' entries before selecting them — chapter 09's
  rule that a branch press selects every entry beneath it now holds when nothing was opened yet.

## Alternatives considered

- **A second destination „Mein Lager".** Rejected by chapter 19's own decision: one navigation entry
  fewer, one tree, one filter row.
- **Following artboard 5 literally** (no pool picker for a selection). The server would stamp every
  row with one unit anyway — the first row's owner's resolution — so the member would be told
  something that is not what happens.
- **Persisting the segment now.** Rejected for this change only; it is the owner's call together with
  the storage analysis it needs.
