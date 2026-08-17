# GitHub source

repo: krt-profit/basetool
branch: main

## Last sync
date: 2026-08-17T09:55:12Z

### Updated in this project
- Fan Kit compliance band (Made By The Community logo + verbatim trademark notice) added to component sheet, login and Einstellungen; asset copied from static/images
- Full Android design spec (ch. 00–14): foundations/tokens, component sheet, navigation, auth, all feature screens, system states + adaptive icon
- Copy conventions per user edits: „Bereich Profit" statt IRIDIUM, „Einsätze" statt Missionen, „Administration" statt Führung
- Artifacts: Theme.kt, icon-export.md; icon sprite verbatim from fragments/icons.html + mobile extensions

## Screen map
| Project screen | Repo files |
| :-- | :-- |
| 01 Foundations.dc.html | static/css/styles.css (via DS colors_and_type.css) |
| 02 Components.dc.html | DS krt-components.css (ported from styles.css) |
| 03 Navigation.dc.html | templates/fragments/sidebar.html |
| 04 Auth.dc.html | keycloak-theme/krt-theme, fragments/terms-body.html, README (approval, guest) |
| 05 Dashboard.dc.html | templates/index.html (greeting, announcement, upcoming 7d) |
| 06 Missionen.dc.html | templates/missions.html, operations-index.html, DS einsatz-uebersicht-final (V. B) |
| 07 Benachrichtigungen.dc.html | templates/notifications.html (actions, 50-cap, load more) |
| 08 Hangar.dc.html | templates/hangar.html (columns, ship modal, Fleetview import, home location) |
| 09 Lager.dc.html | DS tree-table + entry-assign (Variante C, Modell G), README Lager |
| 10 Auftraege.dc.html | templates/orders-index.html (prio/status/materials/progress, age 30/90), README Materialbörse |
| 11 Raffinerie.dc.html | README refinery + extractor ingest (§4 terms) |
| 12 Bank.dc.html | DS bank patterns (kpi/sparkline/holder rows), README Kartellbank |
| 13 Einstellungen.dc.html | sidebar promotion links, README (payout, blueprint sharing) |
| 14 System States.dc.html | templates/error/*, README error copy |
| assets/krt-icons-mobile.js | templates/fragments/icons.html (verbatim) + extensions |
| assets/made-by-the-community.png | static/images/made-by-the-community.png (unmodified) |
