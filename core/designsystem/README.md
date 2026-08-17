# core:designsystem

KRT Compose theme and component library: DAS KARTELL tokens (dark-only, square-first, Lato,
orange #E77E23), buttons ladder, HUD box, cards, chips/status pills, tables/tree lists,
modals, toasts, picker equivalents.

**Binding sources (delivered design handoff, 2026-08-17):**

- Theme/tokens: [`docs/design/android/artifacts/Theme.kt`](../../docs/design/android/artifacts/Theme.kt)
  — drop-in `darkColorScheme`, `Typography`, `Shapes` (all 0 dp), `KrtExtendedColors`,
  spacing, motion constant. This module starts from that file, it does not reinvent it.
- Component sheet: `docs/design/android/02 Components.dc.html` (browser reference — every
  component in default/pressed/focus/disabled/error states, incl. the Fan Kit band §9).
- Icons: `docs/design/android/assets/krt-icons-mobile.js` exported as VectorDrawables per
  [`icon-export.md`](../../docs/design/android/artifacts/icon-export.md) (24 dp, stroke 2,
  round caps, currentColor; `ic_krt_<name>.xml`).
- Fonts: Lato TTF/WOFF2 mirrored in `docs/design/android/_ds/…/fonts/` — bundle
  Light/Regular/Bold/Black as app fonts with the OFL 1.1 text.

Screenshot-tested; KDoc on every public component.
