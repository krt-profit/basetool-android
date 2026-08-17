# Changelog

## [Unreleased]

### Added

- **Verbindliche Design-Spezifikation aufgenommen.** Das vollständige Claude-Design-Handoff (Kapitel 00–14 mit allen Screens für Handy hochkant und Tablet quer, `Theme.kt`-Token-Mapping, Icon-Export-Liste, Lato-Fonts) liegt unter `docs/design/android/` und ist ab jetzt die bindende UI-Referenz; der Design-Prompt ist damit historisch. Alle Konzeptdokumente wurden darauf ausgerichtet (u. a. Copy-Regeln „Einsätze"/„Bereich Profit"/„Administration", Fan-Kit-Platzierung fix auf Login + Einstellungen, FLAG_SECURE app-weit).

- **Projektgerüst angelegt.** Die fünf freigegebenen Konzeptdokumente (Masterplan mit den Entscheidungen Q1–Q7, Sicherheitskonzept, Datenschutz-Analyse, Entwicklungs-/CI-Konzept, Design-Prompt) unter `docs/`, die verbindlichen Projektregeln (`CLAUDE.md`), das geplante Modul-Skelett, `.gitignore` und die GPL-3.0-Lizenz.

- **Community- und Rechtsdokumente übernommen.** Code of Conduct (Contributor Covenant 3.0), eigenes Contributor License Agreement (CLA v1.0, wirksam 17.08.2026) mit öffentlichem Signatur-Roster unter `docs/cla-signatures.md`, ein Beitragsleitfaden (`CONTRIBUTING.md`) mit DCO-Sign-off-Pflicht für jeden Commit sowie eine Security-Policy (`.github/SECURITY.md`) mit privatem Meldeweg, App-spezifischem Scope und Safe-Harbor-Zusage.

- **Star-Citizen-Fan-Kit-Compliance verankert.** Das „Made By The Community"-Logo und der vorgeschriebene CIG-Markenhinweis sind als gekoppeltes Paar verbindlich geregelt (CLAUDE.md, Design-Prompt: Pflichtplatzierung auf dem Login-Screen); das unveränderte Logo-Artwork liegt unter `core/designsystem/fankit/`, der Markenhinweis steht zusätzlich im README.
