# KRT Design System → Kotlin / Jetpack Compose

Drop-in implementation of the DAS KARTELL / Bereich Profit design system for the Basetool
Android app. Written so the implementer has to decide **nothing** about style — every value is
already chosen, and where the spec has a gap it says so instead of leaving you to guess.

    Kotlin · Jetpack Compose · Material 3 · minSdk 31 / targetSdk 37 · dark only

## Files

| File | Contains |
| --- | --- |
| `KrtTokens.kt` | palette (fills **and** text tints), spacing, sizes, shapes (all `RectangleShape`), Lato typography, the Material 3 `darkColorScheme` mapping, `KrtTheme` |
| `KrtGlow.kt` | the only light effect in the app, with hard caps — radius ≤ 12 dp, alpha ≤ 0.10 |
| `KrtComponents.kt` | button ladder, icon button, card / flush card / HUD box, chip, squadron badge, section title, `KrtPanelHeader` (collapsible) |
| `KrtFieldsAndOverlays.kt` | text field, date+time pair, combobox, bottom sheet, modal, corner-bracket toast |
| `KrtPatterns.kt` | tab row, scope segment, **the permission gate**, status pill/badge, amounts, empty/loading states, Fan Kit band |

Package them as `core/designsystem` and depend on it from every feature module.

## Copy these in before the first build

1. **Fonts** — `lato_light.ttf`, `lato_regular.ttf`, `lato_bold.ttf`, `lato_black.ttf` into
   `core/designsystem/src/main/res/font/`. No other family is ever used.
2. **Icons** — the 63 in-house `ic_krt_*` vector drawables (24 × 24, stroke-only, `stroke-width 2`,
   round caps, `fill none`, `currentColor`). There is **no icon library**: if a glyph is missing,
   it does not exist yet — ask, do not substitute a Material icon.
3. **Fan Kit asset** — `made_by_the_community.png`, unmodified, plus the two notice strings
   byte-exact in `strings.xml` (they are NOT translated).

## The ten rules that produce this look

1. **Radius 0 everywhere.** The only rounded things are the pill squadron badge and the radio.
2. **One accent, one primary action.** Filled orange marks the single primary action per context;
   everything else steps down the ladder. Orange is action + identity, never plain data.
3. **Fills and text tints are different colours.** `Success` fills a meter, `SuccessText` writes a
   word. Never use `Gray2` (#646464) for text — that is the hairline colour.
4. **Depth is hairlines and corner brackets, not shadow.** Every container is elevation `0.dp`.
5. **Glow is capped.** radius ≤ 12 dp, alpha ≤ 0.10, three sizes only (focus / emphasis /
   overlay), never two blooms on one screen. `KrtGlow.krtGlow` refuses anything larger.
6. **44 dp minimum tap target**, including icon-only row actions. Icon-only buttons always carry
   a label (content description **and** tooltip).
7. **No native dialogs.** No `AlertDialog` defaults, no `Snackbar`, no system toast. Use
   `KrtBottomSheet`, `KrtModal`, `KrtToast`.
8. **Permissions are drawn, never hidden.** See below — this is the rule most likely to be got
   wrong, and it has already been got wrong once in this app.
9. **Numbers are tabular and signed by kind.** `KrtAmount` handles the sign, the colour and the
   em-dash-for-missing rule. A German keyboard sends a comma: parse with `krtToDoubleOrNull()`.
10. **Every surface has four states.** Loading (skeletons in the list shape, spinner only after
    300 ms), empty (one sentence), filtered-empty (different sentence + reset), error (Kap. 14).
    Offline disables writes with a reason line — never a queue.

## The permission gate — read this twice

`/api/v1/users/me` returns `roles` and `permissions`; the access token carries
`realm_access.roles`. The app reads them. An action the caller demonstrably may not perform is:

* **not hidden** — this org grants roles by hand, and a function nobody sees is never requested;
* **drawn disabled** — alpha `.45` **plus a lock glyph**, because alpha alone is
  indistinguishable from a loading state;
* **still tappable** — never `enabled = false`: what cannot be tapped cannot explain itself;
* **answered in words** — a 4 s singleton corner-bracket toast naming the **missing role**:
  „Dafür brauchst du die Rolle Logistiker." Never „403", never „Keine Berechtigung".

```kotlin
val denials = rememberKrtDenialState()
val gate = KrtGate(
    allowed = state.canManage,
    reason = stringResource(R.string.gate_role_mission_manager),   // names the ROLE
    detail = stringResource(R.string.gate_role_mission_manager_detail),
)
val (dim, click) = rememberKrtGated(gate, onAllowed = { vm.assign(row) }, denials = denials)

KrtGhostButton("Zuordnen", onClick = click, modifier = dim.fillMaxWidth(), iconRes = if (gate.allowed) null else R.drawable.ic_krt_lock)

denials.current?.let { KrtToast(it.reason, it.detail, onTimeout = denials::clear) }
```

Role names come from `ROLES_AND_PERMISSIONS.md` so the copy and the role somebody has to request
carry the same name. Two kinds of lock — role (known up front) and row (own row / edit right on
this org unit) — look **identical**; only the sentence differs.

## Web class → Compose composable

| Web (`krt-components.css`) | Compose | Spec |
| --- | --- | --- |
| `.btn.btn--cta` | `KrtCtaButton` | 02 §1 |
| `.btn-success` | `KrtSuccessButton` | 02 §1 |
| `.btn-outline` | `KrtOutlineButton` | 02 §1 |
| `.btn-ghost` | `KrtGhostButton` | 02 §1 |
| `.btn-quiet-danger` | `KrtQuietDangerButton` | 02 §1 |
| `.btn-icon` | `KrtIconButton` (label mandatory) | 02 §1 |
| `.card`, `.card--flush` | `KrtCard`, `KrtFlushCard` | 02 §2 |
| `.hud-box` | `KrtHudBox` (two diagonal brackets) | 02 §2 |
| `.chip`, `.chip--*` | `KrtChip` + `KrtChipTone` | 02 §3 |
| `.squadron-badge` | `KrtSquadronBadge` | 02 §3 |
| `.section-title` | `KrtSectionTitle` | 02 §4 |
| `.panel-header` | `KrtPanelHeader` | 02 §2, §10 |
| form fields | `KrtTextField` | 02 §5 |
| `.datetime-split-inputs` | `KrtDateTimeField` | 06 · 11 |
| `.krt-combobox*` | `KrtCombobox` | 12 · 06 |
| bottom sheet | `KrtBottomSheet` | 02 §7 |
| `.krt-modal`, `--danger` | `KrtModal(danger = …)` | 02 §7 |
| `.notification-toast`, `showKrtConfirm` | `KrtToast`, `KrtModal` | 02 §7 |
| `.tab-nav`, `.tab`, `.tab-count` | `KrtTabRow` | 06 · 10 · 12 |
| scope switch | `KrtSegment` | 08 · 12 |
| `.status-badge`, `.status-pill` | `KrtStatusBadge`, `KrtStatusPill` | 06 · 10 |
| `.krt-fankit-band` | `KrtFanKitBand` | 02 §9 |
| `.krt-spinner` | `CircularProgressIndicator` 14 dp / 2 dp | 02 |

## Known design-system defects — do NOT mirror them

1. `krt-components.css` gives every `.chip--<tone>` the **canonical fill** as its `color`:
   `muted` #646464 (3.55:1), `info` #355DDC (2.92:1), `success` #239E33 (4.20:1). All fail WCAG AA
   as label text. `KrtChip` uses the text tints instead. Report upstream; the web should swap the
   `color` token in all four rules (fills and borders keep the canonical values).
2. `--color-gray-2-text` (#8A8A8A) exists precisely so #646464 is never text. The web still uses
   `--color-gray-2` in places; the Compose mirror must not.

## What is deliberately NOT here

* No light theme, no `dynamicDarkColorScheme()` — the orange is identity, not a preference.
* No push channel (resolved decision Q2): nothing in this system announces a notification.
* No emoji. Warnings use the in-house `ic_krt_warning` glyph; status uses small square dots.
* No drop shadows, no gradients (one exception: the greeting banner's single dark→transparent
  wash), no parallax, no bounce. Motion is 200 ms colour transitions; the drawer is 400 ms.
