# Star Citizen Fan Kit compliance assets

`made-by-the-community.png` is the official "Made By The Community" logo from the Star
Citizen Fan Kit, taken over unmodified from the Profit Basetool web app (which binds its
usage as REQ-UI-018 there). The binding rules for this app (root `CLAUDE.md`, section
"Star Citizen Fan Kit compliance"):

- **Logo and trademark notice are ONE coupled unit** (Fan Kit Guidelines section 2): one
  composable renders both; neither element may appear, move, or be removed alone.
- **The notice is prescribed legal wording, byte-exact and never translated**
  (`translatable="false"` in every locale):
  `Star Citizen®, Roberts Space Industries® and Cloud Imperium ® are registered trademarks
  of Cloud Imperium Rights LLC` — including the space before the third ®.
- **Placement** (section 2b, applied to the app analogously to a website home page): the
  **login/entry screen** carries the band — it is the app's start surface and visible
  without a login. The settings "About" screen may repeat it as a welcome addition, never
  as a substitute.
- **Artwork unmodified** (section 3): the white variant, no recolor, tint, flip,
  distortion, outline, shadow, pattern, or effect; rendered on the dark KRT surfaces.
- **Legibility** (section 2b): notice at ≥ 10 pt equivalent (≥ 14 sp) in a high-contrast
  color (`#D2D2D2` on black/dark surfaces).
- A UI test pins logo + notice on the login screen and the byte-exact string in every
  locale (mirror of the web app's `FanKitComplianceMvcTest`); it ships with the Phase-1
  login screen.

Phase-1 note: this file parks the asset until the Gradle scaffold exists; it then moves to
`core/designsystem/src/main/res/drawable-nodpi/` and this README's rules move into the
`REQ-APP-UI-*` spec.
