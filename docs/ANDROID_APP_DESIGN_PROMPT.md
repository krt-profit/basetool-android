# Android App — Claude Design Prompt

Doc type: **historical plan** — superseded 2026-08-17. This brief (including the Fan Kit
amendment below) was executed in Claude Design; the resulting handoff is in-repo at
[`docs/design/android/`](design/android/README.md) and is the **binding UI reference** now.
Do not design or implement against this prompt anymore — it is kept for provenance only.
Where prompt and delivered spec differ, the spec wins (notably: fixed copy rules
„Einsätze"/„Bereich Profit"/„Administration", Fan Kit placements fixed to Login +
Einstellungen, FLAG_SECURE app-wide).

The block below was the complete, self-contained prompt handed to Claude Design. It embeds the
DAS KARTELL design-system facts extracted from the populated submodule
(`.claude/skills/das-kartell-design/`) and the binding spec `docs/specs/ui-design-system.md`,
so the design tool needed no repo access.

---

```text
You are designing the complete UI for "Profit Basetool", the native Android companion app of a
Star Citizen squadron-management web tool operated by the org DAS KARTELL / IRIDIUM. The app is
Kotlin + Jetpack Compose (Material 3), Android 10+ (minSdk 29, targetSdk 37), phones used in
PORTRAIT, tablets used in LANDSCAPE. Produce a design specification with mockups that developers
can implement 1:1. The DAS KARTELL design system below is BINDING — do not invent colors, fonts,
radii, or shadows outside it.

## Deliverables

1. High-fidelity mockups for every screen listed below, each in TWO layouts:
   phone portrait (~412 x 915 dp, compact width class) and tablet landscape
   (~1280 x 800 dp, expanded width class, list-detail where applicable).
2. A component sheet: every reusable component in default/hover-pressed/focus/disabled/error
   states (buttons ladder, cards, HUD box, chips/status pills, list rows, data tables,
   key-value lists, form fields, combobox/picker, modal, toast, tabs, empty state, loading,
   offline banner, pagination/infinite-list affordance).
3. A navigation map: phone (bottom navigation bar, max 5 destinations + "Mehr" overflow) and
   tablet (navigation rail + list-detail panes). Include back behavior and deep-link targets.
4. Design tokens mapped to Material 3 slots (colorScheme, typography, shapes) exactly as
   specified below, ready for a Compose theme file.
5. App icon: adaptive icon (foreground/background/monochrome layers) based on the KRT logo
   mark (wedge-through-ring: planet ring + four-point star + triangle). HARD RULE: the logo
   appears ONLY in orange #E77E23, white, or black — never any other color. Provide the
   monochrome layer so Android 13+ themed icons render deliberately.
6. State designs: loading (KRT spinner, orange ring, uppercase label), empty (dashed border,
   short reason, exactly ONE next action), error (in-fiction copy, e.g. 403 "Access Denied —
   Insufficient security clearance."), offline (cached-data banner with timestamp), and the
   three auth states: login screen, "approval pending" screen, terms-acceptance screen.

## Brand character (binding)

Dark sci-fi technical HUD — "operator at a console on a capital ship". Efficient, geometric,
slightly classified, never playful or cute. NO emoji anywhere. Restrained motion (0.2 s color
transitions; no bounces/parallax; honor reduced-motion). Voice: functional, military-terse,
imperative German-first UI copy ("Speichern", "Schiff hinzufügen"); error states lean into the
fiction; destructive actions always confirm and name the consequence. Bilingual DE (primary) /
EN; domain terms stay German even in EN (Staffel, Spezialkommando/SK, Auftrag, Lager,
Raffinerie). Design for long German compound words — no fixed label widths.

## Color tokens (dark-ONLY; there is no light theme; disable Material You dynamic color)

Core: page background #000000 (flat, no texture) · surface #141414 (cards, header, tables) ·
input/table-head surface #1C1C1C · hairline borders & hover-row #282828 · muted decorative
#646464 · muted TEXT #8A8A8A (use for text, #646464 fails contrast as text) · body text
#D2D2D2 · bright data values #FFFFFF · house orange (hero accent, headings, THE one CTA per
context) #E77E23 · orange hover #EEB64B · admin/elevated chrome #C45C00.
Semantic fills: danger #A3000A, success #239E33, warning #FFD23F, info #355DDC,
cross-org highlight #FFD23F. Semantic TEXT tints (mandatory when the hue is small text on
black): danger-text #F2564B, success-text #2EBC3D, info-text #6C93EF.
Department colors (frozen, semantic only, never decorative, never for the logo):
Raumueberlegenheit #37BBC0, Forschung #355DDC, Sub-Radar #A3000A, Marinekorps #7A5E96,
Profit #239E33, Search-and-Rescue #FFD23F.
Rules: orange = action + identity, never plain data values or every label; data values are
bright white on dark chips; labels neutral #D2D2D2 bold, never orange; buy prices red with
minus, sell prices green with plus (use the *-text tints); selection = orange bg + black text.

## Typography

Single family: Lato (bundled, OFL 1.1). Body Light 300, emphasis/labels/buttons/headlines
Bold 700, Black 900 for hero numbers. Headlines/nav/labels/table headers UPPERCASE with
letter-spacing 0.05em (overline chips 0.15em); headings orange; section titles gray with a
hairline rule. Scale (sp, from the web rem tokens): h1 32, h2 24, h3 ~19, body 16, sm 14.4,
xs 12.8, 2xs 11.2 — round pragmatically for Android (14/13/11); body line-height 1.5; numeric
data uses tabular figures.

## Shape, depth, spacing

Square-first: corner radius 0 on cards, buttons, inputs, modals, tables. The ONLY rounded
elements: pill badges (999 dp) and the circular radio, spinner, and presence dot — status
dots are SQUARE (8 dp). NO soft drop
shadows — depth = hairlines (1 dp #282828) + orange corner brackets + orange "bloom" glows
(focus 0 0 5 rgba(231,126,35,.3); modal/CTA 0 0 20 rgba(231,126,35,.2)). Signature container
"HUD box": hairline border, translucent #141414 fill, two 10 dp diagonal orange corner
brackets (top-left + bottom-right). Modals: #141414, 3 dp orange top edge + 13 dp corner
brackets + glow; danger variant swaps orange to red; exactly ONE filled CTA (right), cancel
as ghost. Spacing scale (dp): 4/8/12/16/24/32. Touch targets >= 48 dp (web system uses 44 px
minimum; on Android use 48 dp). Content max ~1200 dp on tablets; prose measure <= 80 ch.

## Component canon (adapt these web patterns to Compose/Material 3, keeping the look)

Buttons ladder (strongest to quietest): filled-orange CTA (text BLACK, max ONE per screen
context, glow on press) > filled-green success (state changes like Check-In) > orange outline
> ghost (hairline, turns orange) > quiet-danger (gray, turns red). Uppercase Lato Bold 13 sp,
letter-spacing 0.03 em, min height 48 dp. Icon buttons for repeated row actions.
Tables/lists: dense; header on #1C1C1C, uppercase, with 2 dp orange bottom rule; hairline row
separators; subtle zebra; full-row press state #282828; numerics right-aligned tabular; on
phones collapse tables to cards/rows with key-value pairs — never horizontal page scroll.
Status: squadron badge = orange-ring pill (the one pill); chips = squared uppercase 11 sp
labels with tone variants (primary/success/danger/warning/info/muted); status-pill = 8 dp
square dot + uppercase tint text (planned=info, active=success, briefing=warning,
completed=gray, cancelled=danger).
Forms: #1C1C1C fill, hairline border, radius 0, height 48 dp, focus = orange border + bloom;
custom orange chevron selects; square orange checkboxes (black check), circular radio; inline
errors in #F2564B. Comboboxes: type-to-filter, active option filled orange with black text.
Icons: the in-house 31-symbol set (24 dp viewBox, stroke-only 2 dp, round caps, currentColor)
— close, chevrons, warning, success, info, plus, minus, search, filter, filter-off, edit,
trash, bookout, save, arrows, check, login, logout, user-plus, external-link, clock, map-pin,
eye, download, upload, clipboard-check, list. Extend in the same style where mobile needs
more (nav icons). No icon libraries, no emoji.
KPI tiles: value white bold tabular on dark chip, orange 4 dp left bar for totals, deltas in
success/danger text tints. Toasts: near-black fill, orange border + corner brackets + glow,
uppercase orange title; error variant red. Loading: orange ring spinner + uppercase label.
Presence/live: pulsing orange dot pill (product copy: "Wird gerade bearbeitet von …") —
never blocks input; an "updates available" pill when live data changed under an open editor.

## Navigation & screens

Phone bottom bar (5): Übersicht (dashboard), Missionen, Aufträge, Lager, Mehr.
Top app bar: screen title, notification bell with unread badge, active-org-unit chip
(squadron badge pill, tap = org switcher sheet). Tablet: navigation rail with all
destinations + list-detail.

Screens to design (each phone + tablet):
 1. Login (KRT logo, orange radial top bloom allowed here, one CTA "Anmelden" -> opens
    Keycloak in a Custom Tab; guest-browse entry if enabled) · approval-pending state ·
    terms-acceptance screen (scrollable terms, accept CTA). MANDATORY on the login screen:
    the Star Citizen Fan Kit compliance band — the "Made By The Community" logo (white
    artwork, unmodified, ~36 dp) paired with the verbatim notice "Star Citizen®, Roberts
    Space Industries® and Cloud Imperium ® are registered trademarks of Cloud Imperium
    Rights LLC" at >= 14 sp in #D2D2D2, as one quiet unit at the bottom; the two elements
    are legally coupled and never appear separately, and the notice text is never
    translated.
 2. Dashboard: next missions (cards with countdown), unread notifications, org context,
    announcement banner, quick actions.
 3. Missionen: list (search/filter, status pills, date grouping) · detail with section tabs
    (Übersicht, Teilnehmer, Einheiten/Crew, Ablauf, Ziele, Frequenzen, Finanzen) · signup /
    check-in flows (participant row actions, payout preference) · finance entry form.
 3b. Operationen: list with status; detail with mission-finance rollup, finance summary and the
    member's payout status ("ausgezahlt?" as a prominent status chip); paid-out toggle for
    mission managers.
 4. Benachrichtigungen: inbox (unread emphasis, type icon, relative time, swipe to
    read/delete), load-more, unread badge states in the top bar.
 5. Hangar: my ships (cards: manufacturer mark monochrome, name, loadout flags), add/edit
    ship, squadron overview (counter tiles + per-ship breakdown), import flow (paste JSON).
 6. Lager/Inventar: aggregated stock (tree/grouped list with depth rails, quantities
    right-aligned), my inventory, book-in/out/rebook forms, allocation sheet.
 6b. Mein Inventar & Blueprints: personal inventory list + editor; blueprint list with
    craftability indicator (can-craft chip + missing-materials breakdown), recipe detail,
    batch import flow (file pick -> match preview -> apply).
 7. Aufträge: queue (priority + status + responsible-unit badges), detail with tabs
    (Positionen, Materialbedarf, Übergaben, Verlauf), assignee/note actions, status changes.
 8. Materialbörse: offers/requests feed with interest toggle; create offer/request sheets.
 9. Raffinerie: my orders list with yield summaries, order detail, "in Lager buchen" flow.
10. Bank (member surface): org-unit balances, account detail (transactions list,
    balance-series sparkline as drawn line, statement download), booking-request form,
    approvals tab.
11. Beförderung: my evaluations matrix, eligibility progress.
12. Einstellungen: language DE/EN, active org unit, app-lock (biometric toggle), payout
    preference, blueprint sharing, "Lokale Daten löschen", Datenschutzerklärung, Impressum,
    OSS-Lizenzen, logout. Version/server-status footer. The About/Impressum area repeats
    the Fan Kit band from screen 1 (same coupled unit, welcome addition to the mandatory
    login placement).
13. System states: offline banner + cached timestamp, 409-conflict dialog ("Neu laden und
    erneut versuchen"), 429/503 retry state, forced-update screen ("Server erwartet eine
    neuere App-Version").

## Platform rules

Edge-to-edge (insets respected, dark system bars), predictive-back friendly transitions,
one-handed reachability on phones (primary actions bottom-anchored), pull-to-refresh on all
lists, FLAG_SECURE screens may not need special design but avoid layouts that invite
screenshots for sharing secrets. Accessibility: WCAG AA contrast (the *-text tints exist for
exactly this), TalkBack labels for icon buttons, min 48 dp targets, dynamic font scaling up
to 1.3x without truncation (German compounds!), reduced-motion variants.

Deliver the spec as: token sheet -> component sheet -> navigation map -> per-screen mockups
(phone + tablet side by side) -> state gallery -> app icon. Annotate spacing and type sizes
on one exemplar screen per pattern so developers can infer the rest.
```

---

Implementation note: the resulting spec feeds `core:designsystem` (Compose theme + component
library). Two repo facts the designer cannot know: (1) no clean vector logo exists in-repo (the
old SVG exports are broken rasters) — the icon work includes a vector redraw of the mark;
(2) the Lato font files ship in the APK from `design/fonts/` with the OFL 1.1 license text added
(currently missing next to the fonts).

---

## Amendment prompt: Fan Kit compliance band

If the design spec was (or is being) produced from an earlier version of the prompt above that
did not yet contain the Fan Kit requirements, paste the following amendment into the same Claude
Design session instead of re-running the full brief. It is self-contained.

```text
AMENDMENT to the Basetool Android design spec you produced from my earlier brief. One addition
is legally required; update only the deliverables listed at the end and keep everything else
exactly as designed.

## New mandatory component: Star Citizen Fan Kit compliance band

The app is a Star Citizen fan project and must display two legally COUPLED elements as one
inseparable unit (Fan Kit Guidelines sections 2, 2b, 3):

1. The "Made By The Community" logo — the official white artwork, used UNMODIFIED: no
   recolor, tint, flip, distortion, outline, drop shadow, pattern, or effect. Rendered at
   about 36 dp on the dark ground. (Asset: made-by-the-community.png, square, white on
   transparent.)
2. The trademark notice, byte-exact, including the space before the third registered sign:
   "Star Citizen®, Roberts Space Industries® and Cloud Imperium ® are registered trademarks
   of Cloud Imperium Rights LLC"

Binding rules:
- The two elements form ONE component ("Fan Kit band") and never appear separately.
- The notice text is prescribed legal wording: it stays verbatim ENGLISH in every locale,
  including the German UI. Never rephrase, retranslate, or typographically "improve" it.
- Legibility: notice at >= 14 sp in #D2D2D2 (or brighter) on the dark surfaces — muted is
  fine, illegible is not. WCAG AA contrast applies.
- The band is static, quiet, and non-interactive. It must not compete with the screen's
  single orange CTA and must not look like a button or badge of the KRT brand — it is a
  third-party attribution. Keep KRT corner brackets/glows OFF this component; a plain
  hairline-topped row on the page background is the right register.
- Layout: logo left, notice right of it, vertically centered; on narrow phones the notice
  may wrap to two lines. On tablet landscape keep the band to the content max-width,
  centered. Suggested placement: pinned to the bottom of the login screen's content, above
  the version footer.

## Deliverables to update (and nothing else)

1. Component sheet: add the "Fan Kit band" with its spacing and type annotations (one
   exemplar is enough; include the two-line phone wrap variant).
2. Screen 1 (Login): both mockups (phone portrait + tablet landscape) now include the band
   as described. This placement is MANDATORY — the login screen is the app's start surface
   and must carry the band.
3. Screen 12 (Einstellungen): the About/Impressum area repeats the same band as a welcome
   addition (identical component, no variation).
4. Do not add the band to any other screen, and do not move any existing element to make
   room beyond minor spacing adjustments on the two affected screens.
```
