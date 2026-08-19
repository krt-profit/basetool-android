# Changelog

## [Unreleased]

### Added

- **Die App hat ein Symbol.** Der Startbildschirm zeigt jetzt das Basetool-Zeichen (orange auf schwarz) statt des Android-Platzhalters — als anpassungsfähiges Symbol, das sich jeder Launcher-Form fügt, mit eigener einfarbiger Variante für die Design-Symbole von Android 13+. Auf dem Tablet trägt auch die Seitenleiste das Zeichen statt der Kartell-Marke.

### Changed

- **Sicherheitskonzept nach Code-Audit des Basetool-Backends nachgeschärft.** Die Konzeptdokumente übernehmen die verifizierten Ergebnisse einer Code-Analyse des Haupt-Repos: Die API-Exposition bekommt eine Default-Deny-Allowlist statt einer Blockliste, das Backend braucht vor der Freischaltung den Right-to-Left-`X-Forwarded-For`-Walk und einen eigenen Management-Port, Audience-Enforcement wird Release-Gate, und ein Keycloak-Härtungspaket (Event-Logging, S256-Client-Policy, Token-Endpoint-Budget) kommt in Phase 0 dazu. App-seitig neu: kein OkHttp-Disk-Cache, `setUnlockedDeviceRequired` für den Token-Schlüssel, server-synchronisierte DPoP-Uhrzeit, Mindestversions-Gate, CA-Pin als bevorzugte Pinning-Variante sowie Gradle Dependency Verification und Release-Provenance in der CI.

### Added

- **Die App baut: Gradle-Gerüst, KRT-Theme und Komponentenbibliothek stehen.** Zwei Module (`app`, `core:designsystem`) auf AGP 9.3 / Kotlin 2.4 / Compose Material 3, minSdk 29 und targetSdk 37. Enthalten sind das vollständige Design-Token-Set (Farben, Lato-Typografie, eckige Formen, Abstände), die Komponenten aus Kapitel 02 der Design-Spezifikation (Button-Leiter, HUD-Box und Karten, Chips und Status-Anzeigen, Listenzeilen, Formularfelder, Modal/Toast/Sheet, Lade-, Offline- und Leerzustände, KPI-Kacheln), 63 aus dem Design-Sprite erzeugte Vektor-Icons sowie die mitgelieferten Lato-Schriften samt OFL-Lizenztext. Eine Showcase-App zeigt alle Komponenten zum Abgleich mit den Design-Referenzen.

- **Navigationsgerüst steht (Kapitel 03).** Die App hat jetzt eine echte Navigation statt der Komponenten-Galerie: obere Leiste mit Bildschirmtitel, Org-Einheit-Chip und Glocke mit Zähler, auf dem Handy eine Leiste mit fünf Zielen (Übersicht, Einsätze, Aufträge, Lager, Mehr), auf dem Tablet eine Seitenleiste mit acht Zielen. „Mehr" führt zu den übrigen Bereichen. Jedes Ziel behält seinen eigenen Verlauf; ein erneuter Tipp auf das aktive Ziel springt an dessen Anfang, Zurück führt von jedem Ziel auf die Übersicht und von dort aus der App. Jeder Bereich ist zusätzlich per Deep Link (`basetool://…`) erreichbar. Die Bereichsinhalte selbst folgen mit den nächsten Kapiteln.

- **Komponentenbibliothek vervollständigt.** Ergänzt wurden die zunächst fehlenden Abschnitte aus Kapitel 02 der Design-Spezifikation: Datentabellen für Tablets samt Zusammenfall zur Datensatz-Karte auf dem Handy, die Auswahl-Bausteine (Type-to-Filter-Combobox mit Treffer-Hervorhebung und „x von y"-Hinweis, Auswahlfeld, eckige Checkbox, Radio, Chip-Auswahl), das Bottom Sheet als Container und der Hinweis-Tooltip für Fachregeln. Der orangene Leuchteffekt rendert jetzt weich statt in sichtbaren Stufen.

- **Fan-Kit-Konformität ist testgesichert.** Logo und Markenhinweis sind eine untrennbare Komponente; vier Tests prüfen den wortgleichen englischen Hinweis in der Standard-, deutschen und englischen Sprachumgebung sowie das Mitliefern des unveränderten Artworks.

- **Verbindliche Design-Spezifikation aufgenommen.** Das vollständige Claude-Design-Handoff (Kapitel 00–14 mit allen Screens für Handy hochkant und Tablet quer, `Theme.kt`-Token-Mapping, Icon-Export-Liste, Lato-Fonts) liegt unter `docs/design/android/` und ist ab jetzt die bindende UI-Referenz; der Design-Prompt ist damit historisch. Alle Konzeptdokumente wurden darauf ausgerichtet (u. a. Copy-Regeln „Einsätze"/„Bereich Profit"/„Administration", Fan-Kit-Platzierung fix auf Login + Einstellungen, FLAG_SECURE app-weit).

- **Projektgerüst angelegt.** Die fünf freigegebenen Konzeptdokumente (Masterplan mit den Entscheidungen Q1–Q7, Sicherheitskonzept, Datenschutz-Analyse, Entwicklungs-/CI-Konzept, Design-Prompt) unter `docs/`, die verbindlichen Projektregeln (`CLAUDE.md`), das geplante Modul-Skelett, `.gitignore` und die GPL-3.0-Lizenz.

- **Community- und Rechtsdokumente übernommen.** Code of Conduct (Contributor Covenant 3.0), eigenes Contributor License Agreement (CLA v1.0, wirksam 17.08.2026) mit öffentlichem Signatur-Roster unter `docs/cla-signatures.md`, ein Beitragsleitfaden (`CONTRIBUTING.md`) mit DCO-Sign-off-Pflicht für jeden Commit sowie eine Security-Policy (`.github/SECURITY.md`) mit privatem Meldeweg, App-spezifischem Scope und Safe-Harbor-Zusage.

- **Star-Citizen-Fan-Kit-Compliance verankert.** Das „Made By The Community"-Logo und der vorgeschriebene CIG-Markenhinweis sind als gekoppeltes Paar verbindlich geregelt (CLAUDE.md, Design-Prompt: Pflichtplatzierung auf dem Login-Screen); das unveränderte Logo-Artwork liegt unter `core/designsystem/fankit/`, der Markenhinweis steht zusätzlich im README.
