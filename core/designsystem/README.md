# core:designsystem

The KRT Compose design system: theme tokens, the component library, the icon set and the
bundled Lato fonts. Everything here is derived from the binding design handoff — this module
implements the spec, it does not invent design.

## What is in here

| Area | Contents |
|---|---|
| `theme/` | `Color.kt` (palette, M3 scheme, extended brand colours), `Type.kt` (Lato + type scale), `Shape.kt` (all 0 dp + the one pill), `KrtSpacing.kt` (spacing, metrics, motion), `Theme.kt` (`KrtTheme` + accessors) |
| `component/` | Button ladder, containers (HUD box, cards, panel header, key-value rows), status (org badge, chips, department tags, status pill/badge, presence, update pill), list rows and pagination, form fields and stepper, overlays (modal, toast, sheet option), feedback (spinner, loading, offline banner, empty state, KPI tiles, sparkline), `KrtIcon`, text helpers, **`KrtFanKitBand`** |
| `modifier/` | `krtCornerBrackets`, `krtBloom`, `krtHairline` — the three depth devices of the system (the app has no drop shadows) |
| `res/drawable/` | 63 `ic_krt_*` VectorDrawables generated from the design sprite (24 dp, stroke 2, round caps, tinted at use site) |
| `res/font/` | Lato Light/Regular/Bold/Black; the OFL 1.1 text sits in `licenses/LATO_OFL.txt` |
| `res/drawable-nodpi/` | The unmodified "Made By The Community" artwork |

## Binding sources

- Tokens: [`docs/design/android/artifacts/Theme.kt`](../../docs/design/android/artifacts/Theme.kt)
- Component sheet: `docs/design/android/02 Components.dc.html` (open in a browser)
- Foundations: `docs/design/android/01 Foundations.dc.html`
- Icon contract: [`icon-export.md`](../../docs/design/android/artifacts/icon-export.md)

## Rules that are tested, not just documented

`KrtFanKitBandTest` pins the Fan Kit trademark notice byte for byte in the default, German and
English locales and asserts the artwork ships — the legal coupling of logo and notice cannot be
broken by an innocent-looking edit.

## Conventions

Every public composable carries KDoc that explains *why* the component looks and behaves the way
it does, not just what it renders. Previews are `private` and wrapped in `KrtPreviewSurface` so
they render on the real page canvas. Colours come from the theme, never as literals at the call
site.
