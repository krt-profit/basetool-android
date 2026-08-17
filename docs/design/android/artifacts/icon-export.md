# KRT icon export list — Android

Contract: 24dp viewBox, stroke-only 2dp, round caps/joins, `fill:none` (brand
glyphs GitHub/Discord are solid-fill), color always `currentColor` → tint via
theme. Export each `<symbol>` from `assets/krt-icons-mobile.js` as a
VectorDrawable named `ic_krt_<name>.xml`. No icon libraries, no emoji.

## Product sprite (verbatim from basetool `fragments/icons.html`)
close, chevron-down, chevron-up, chevron-left, chevron-right, warning, success,
info, plus, minus, search, filter, filter-off, edit, trash, bookout, rebook,
save, arrow-left, arrow-right, check, login, logout, user-plus, external-link,
clock, map-pin, eye, download, upload, clipboard-check, list, bank-in, bank-out,
swap, pdf, gear, reset, bell, headset, user, users, discord

## Mobile extensions (drawn for Android, same contract)
| id | use |
| :-- | :-- |
| dashboard | bottom bar / rail „Übersicht" |
| target | bottom bar / rail „Einsätze" |
| clipboard-list | bottom bar / rail „Aufträge" |
| crate | bottom bar / rail „Lager" |
| more-h | bottom bar „Mehr" |
| more-v | overflow menus |
| grip | drag handles (reorder) |
| ship | Hangar |
| refinery | Raffinerie |
| bank | Bank |
| rank | Beförderung |
| blueprint | Blueprints |
| wifi-off | offline banner |
| fingerprint | app-lock / biometrics |
| scan | extractor JSON import |
| calendar | date grouping / pickers |
| lock | app-lock, FLAG_SECURE hints |
| globe | language DE/EN |
| shield | terms / privacy |
| antenna | frequencies (Frequenzen tab) |

## Usage rules (from the DS button-icon guidelines)
- Icon + text for primary/rare/ambiguous actions; icon-only (`.btn-icon`,
  48dp target) ONLY for repeated universal row actions — always with
  contentDescription (TalkBack) + tooltip.
- Icons inherit the button's text color; never multi-color.
