# What each caller may see — measured, not assumed

Doc type: *historical record.* The binding rules are the main repo's
[`docs/specs/org-unit-tenancy.md`](https://github.com/krt-profit/basetool/blob/main/docs/specs/org-unit-tenancy.md)
and [`docs/specs/security-and-access.md`](https://github.com/krt-profit/basetool/blob/main/docs/specs/security-and-access.md).
This page records one run against the isolated test stack on **2026-08-26** and what it proved.

The question behind it: the app renders whatever the API returns, so if a member of one Staffel can
read another's Lager rows, the app is the surface that leaks them. It is worth knowing which half
of that sentence is load-bearing.

## The fixture

Counts prove nothing on their own — a caller can be handed three rows that belong to somebody else
— so the stack was given **two of every org-unit kind**, each carrying its own data, and every
response was searched for *whose* rows came back rather than how many.

| Kind | Units |
| --- | --- |
| Staffel | IRIDIUM, VANGUARD |
| Spezialkommando | SK NEBELKRAEHE, SK ROTFUCHS |
| Bereich | BEREICH PROFIT, BEREICH SICHERHEIT |
| Organisationsleitung | ORGANISATIONSLEITUNG |

Seeded per unit: Lager rows, Einsätze, Raffinerie-Aufträge and a Materialbörse offer; Aufträge in
the four profit-eligible units. Members: `test-staffel-a` and `test-member` in IRIDIUM,
`test-staffel-b` in VANGUARD, `test-sk-1` in SK NEBELKRAEHE, `test-sk-2` in SK ROTFUCHS — all plain
`KRT Member`, no Logistiker, no admin.

## What came back

Each cell is *rows : the units those rows belong to*.

| Caller | Home | Lager | Einsätze | Aufträge | Raffinerie | Börse |
| --- | --- | --- | --- | --- | --- | --- |
| `test-admin` | admin | 31 : all 7 | 10 : all 7 | 8 : all 4 | 22 : all 7 | 7 |
| `test-member` | IRI | 7 : IRI | 4 : IRI | 7 : IRI, SKN, SKR | 4 : IRI | 7 |
| `test-staffel-a` | IRI | 7 : IRI | 4 : IRI | 7 : IRI, SKN, SKR | 4 : IRI | 7 |
| `test-staffel-b` | VGD | 4 : VGD | 3 : IRI, VGD | 3 : VGD, SKN, SKR | 3 : VGD | 7 |
| `test-sk-1` | SKN | 4 : SKN | 3 : IRI, SKN | 2 : SKN, SKR | 3 : SKN | 7 |
| `test-sk-2` | SKR | 4 : SKR | 3 : IRI, SKR | 2 : SKN, SKR | 3 : SKR | 7 |

**Lager and Raffinerie are strictly the caller's own unit.** No member saw a foreign row in either.

Three columns look like bleed and are not. Each was checked against the spec **and** re-measured
per row rather than accepted from the shape of the table:

- **Einsätze.** Every foreign row a member saw was `isInternal = false`. That is the documented
  public escape — `owning_org_unit.id IN (:memberOrgUnitIds) OR is_internal = false`. Asserting
  it directly (*is any foreign row internal?*) returned **none** for all three cross-unit callers.
  The seeded fixture missions were created internal and correctly did **not** cross.
- **Aufträge.** Every foreign row was owned by a **Spezialkommando** — the shared SK queue. Asserting
  *is any foreign row owned by a Staffel?* returned **none**. VANGUARD's member never saw IRIDIUM's
  orders and IRIDIUM's never saw VANGUARD's.
- **Materialbörse.** Org-wide by design: „The board read applies no OrgUnit scope filter"
  (`docs/specs/materialboerse.md`). The unit shown against an offer is the *anbieter's* membership
  badge, not the stock's owning unit, which is why every row reads IRI here — one account seeded
  them all.

Bank returned nothing to any plain member; balances need an explicit visibility grant.

## Switching between two units

Single-membership members prove that scoping filters. They cannot prove that the **pin** works, so
two more were added: `test-multi-staffel` (IRIDIUM + VANGUARD) and `test-multi-mixed`
(IRIDIUM + SK NEBELKRAEHE).

| Active unit | Lager | Einsätze | Aufträge | Raffinerie |
| --- | --- | --- | --- | --- |
| *(no pin)* | 11 : IRI, VGD | 5 : IRI, VGD | 8 : IRI, SKN, SKR, VGD | 7 : IRI, VGD |
| IRIDIUM | 7 : IRI | 4 : IRI | 7 : IRI, SKN, SKR | 4 : IRI |
| VANGUARD | 4 : VGD | 3 : IRI, VGD | 3 : VGD, SKN, SKR | 3 : VGD |
| SK ROTFUCHS *(not theirs)* | 11 : IRI, VGD | 5 : IRI, VGD | 8 : IRI, SKN, SKR, VGD | 7 : IRI, VGD |

Three answers, all of them the right one: **no pin** gives the union of the member's own units, a
pin on one of their own narrows to exactly it, and a pin on a unit they do **not** belong to falls
back to their own — it neither errors nor grants. `test-multi-mixed` behaves identically with its
Spezialkommando in place of the second Staffel.

Verified on the device as well, signed in as each of the two: the badge is tappable, the sheet lists
both units, and switching narrows the Lager visibly — under IRIDIUM four material groups, under
VANGUARD one. Choosing „Alle Org-Einheiten" shows Agricium (Ore) at 200 SCU where each single unit
shows 100, which is the union rather than one unit's list.

## The header is not trusted

The app sends `X-Active-Org-Unit-Id` on every call, so the obvious question is whether pinning
somebody else's unit widens what comes back. It does not: `test-staffel-b` (VANGUARD) asking for
IRIDIUM's id got **4 rows, all VGD** from `/inventory/all` and **3 rows, all VGD** from
`/refinery-orders/all`. The header selects among the caller's *own* memberships; it does not grant
one.

## Two things worth knowing for the app

- **`/api/v1/users/search` ignores its `query` parameter** and returns the full page regardless.
  Harmless for the app, which does not use it, but it cost an hour here: a fixture script asking
  for `q=test-staffel-b` with `size=5` got the first five users alphabetically and concluded the
  user did not exist. The parameter is named `query`, not `q`.
- **Bereiche and the Organisationsleitung cannot own Aufträge.** There is no profit-eligible toggle
  for them, and the endpoint refuses with *„The selected responsible org unit is not profit-eligible
  and cannot process orders"*. That is the intended shape, not a gap.

## What this does and does not license

The server scopes; the app must keep treating its own permission list as a **hint, never a gate**
(`REQ-APP-AUTH-013`). Nothing here says a screen may skip a check because the numbers looked right
on one afternoon against one fixture.
