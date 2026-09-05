# Star Citizen Fan Kit compliance assets

`made-by-the-community.png` is the official "Made By The Community" logo from the Star
Citizen Fan Kit, taken over unmodified from the Profit Basetool web app (which binds its
usage as REQ-UI-018 there).

## Two CIG documents bind this band, and they apply cumulatively

| Document | Requires | String |
| --- | --- | --- |
| Fan Kit **Guidelines** §2 / §2b / §3 | the unmodified logo and the short trademark line | `krt_fankit_trademark_notice` |
| Fankit **Agreement** clause 2(g) | a separate, longer non-affiliation notice | `krt_fankit_agreement_notice` |

Neither notice substitutes for the other. Clause 2(g) places its notice "in a reasonably
prominent location … wherever materials, trademarks, or properties owned by CIG are located",
which in this app is exactly where the band already is.

## Checked kit version

Agreement clause 11 lets CIG change these documents at any time, so the version this app was
verified against is recorded here rather than assumed:

| | |
| --- | --- |
| Kit | `Fankit_2025_11_19` |
| Agreement | `06_Fankit_Agreement_2025_11_19.pdf` — clause 2(g) |
| Guidelines | `08_Fankit_Guidelines.pdf` — §2, §2a, §2b, §2c, §3 |
| Checked | 2026-08-27; re-checked 2026-09-04 against the same kit — why both notices stay is written into `REQ-APP-SET-007` |

The Agreement's 2(g) sentence is **byte-identical across the three archived kits**
(2024-04-25, 2025-06-03, 2025-11-19) — same SHA-256 of the extracted sentence. The Guidelines
differ between versions only in their asset list.

## The binding rules

- **Logo and BOTH notices are ONE coupled unit.** One composable renders all three
  (`KrtFanKitBand`); none of them may appear, move, or be removed alone. Section 2 requires the
  trademark notice wherever the logo appears; clause 2(g) requires its notice wherever CIG
  material sits.
- **Both notices are prescribed legal wording, byte-exact and never translated**
  (`translatable="false"`, verbatim English in every locale):

  > `Star Citizen®, Roberts Space Industries® and Cloud Imperium ® are registered trademarks of
  > Cloud Imperium Rights LLC`

  > `This site is not endorsed by or affiliated with the Cloud Imperium or Roberts Space
  > Industries group of companies. All game content and materials are copyright Cloud Imperium
  > Rights LLC and Cloud Imperium Rights Ltd.. Star Citizen®, Squadron 42®, Roberts Space
  > Industries®, and Cloud Imperium® are registered trademarks of Cloud Imperium Rights LLC.
  > All rights reserved.`

- **Three details look like mistakes and are not — never "harmonise" the two notices.**
  - `Ltd..` carries **two** full stops.
  - The §2b line has a space before its **third** ®, because CIG's §2b prose writes it that way.
    Clause 2(g) has **no** space before any of its four. Both are correct.
  - Clause 2(g) takes an Oxford comma before "and Cloud Imperium®".

  `KrtFanKitBandTest` asserts the **difference itself**, not only each value, because a band
  where both read alike looks tidier and satisfies neither document.

- **Placement (design spec ch. 02 §9): Login (above the version footer) and Einstellungen —
  nowhere else.** The login screen is the app's start surface and is visible without a session
  (the §2b home-page analog); Einstellungen is the second fixed placement. The band renders
  static, without KRT brackets or glows — this is third-party attribution, not brand chrome.
- **Never folded behind a disclosure** (design decision, 2026-08-27). A notice behind a tap is
  not "reasonably prominent" in the sense clause 2(g) asks for. When it does not fit, the login
  **page** scrolls.
- **One type size across the whole band**: both notices at 14 sp. The Guidelines' ~10 pt floor
  is ≈ 13.3 sp, which leaves no honest step down.
- **Artwork unmodified** (section 3): the white variant, no recolor, tint, flip, distortion,
  outline, shadow, pattern, or effect; rendered on the dark KRT surfaces.
- **Legibility** (section 2b): ≥ 10 pt equivalent in a high-contrast colour — `#D2D2D2` on the
  black surface, 14 sp.

## Tests

| Test | Pins |
| --- | --- |
| `KrtFanKitBandTest` | both notices byte-exact in the default, `de` and `en` locales; the spacing difference; the artwork's presence |
| `FanKitNoticeParityTest` | each notice declared once, `translatable="false"`, and absent from every localized bundle |
| web `FanKitComplianceMvcTest` | the mirror of the above in the Profit Basetool web app — **the two must stay in sync** |
