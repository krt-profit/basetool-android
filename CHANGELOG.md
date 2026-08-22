# Changelog

## [Unreleased]

### Added

- **Ein Einsatz lässt sich jetzt öffnen.** Das Antippen einer Zeile zeigt den Einsatz mit sieben Reitern: Übersicht, Teilnehmer, Einheiten, Ablauf, Ziele, Frequenzen und Finanzen. Die Finanzen werden erst geladen, wenn man den Reiter öffnet — sie brauchen eine eigene Berechtigung, und wer sie nicht hat, sieht den Einsatz trotzdem vollständig. Frequenzen kopiert ein Antippen in die Zwischenablage. Anmelden, Check-In und Finanz-Einträge folgen später (REQ-APP-MIS-008…012).

- **Eingabefelder sind für Screenreader jetzt beschriftet.** Sie meldeten dem Bedienungshilfen-Baum bisher gar keinen Namen, und der Platzhaltertext tauchte dort überhaupt nicht auf — sichtbar war er, vorlesbar nicht. Felder tragen jetzt einen Namen (Beschriftung, sonst Platzhalter), der auch beim Tippen erhalten bleibt, und Fehlermeldungen hängen am Feld statt nur darunter zu stehen.

- **Die Einsatzliste ist da.** Statt des Platzhalters zeigt der Reiter „Einsätze" jetzt die echten Einsätze der aktiven Org-Einheit, nach Tag gruppiert („Heute", „Morgen", sonst Wochentag und Datum), mit Status, Treffzeit und Einheit. Suchen, nach Status filtern, vergangene Einsätze ein- und ausblenden, nachladen und per Ziehen aktualisieren — alles serverseitig gefiltert, damit die angezeigte Anzahl stimmt. Das Antippen eines Eintrags öffnet noch nichts; die Detailansicht folgt (REQ-APP-MIS-001…007).

- **Die App zeigt jetzt die eigene Org-Einheit an und lässt sie umschalten.** Die Kopfzeile trug
  bisher einen festen Platzhalter; sie nennt jetzt die Einheit, in der man tatsächlich arbeitet,
  und jede Anfrage an den Server ist auf sie bezogen. Wer mehreren Einheiten angehört, wählt über
  die Kopfzeile; die Wahl bleibt über Neustarts erhalten und endet mit dem Abmelden. Wer nur einer
  Einheit angehört, bekommt keine Auswahl angeboten — es gibt nichts zu wählen (REQ-APP-API-006).

- **Release-Builds lassen sich jetzt signieren, und der Weg dorthin wird bei jeder Änderung
  geprobt.** Das Signieren passiert sonst genau einmal pro Veröffentlichung — mit dem einen
  Schlüssel, der sich nicht neu erzeugen lässt. Ein eigener CI-Lauf baut daher bei jedem Pull
  Request eine signierte Fassung mit einem Wegwerf-Schlüssel und prüft sie nach; für spätere
  Downloads heißt das, dass sich die Signatur verlässlich überprüfen lässt.

- **Die App hat einen Einstellungen-Bildschirm.** Er versammelt, was die App selbst entscheidet:
  Sprache, App-Sperre, die Rechtstexte, die Open-Source-Lizenzen, die Version und das Abmelden.
  Die App-Sperre ist von „Mehr“ dorthin umgezogen. Rang, aktive Org-Einheit,
  Auszahlungspräferenz und Blueprint-Freigabe zeigt der Bildschirm noch nicht — diese Werte
  kommen vom Server, und ein Rang, den niemand gesetzt hat, wäre eine falsche Aussage statt
  einer fehlenden (REQ-APP-SET-001).

- **Die App lässt sich auf Deutsch oder Englisch stellen, unabhängig vom Gerät.** Die Wahl
  merkt sich das System; ab Android 13 taucht sie auch in dessen eigener Sprachliste auf, sodass
  beide Stellen denselben Wert zeigen. Bis zur ersten Wahl steht die Umschaltung auf der Sprache,
  in der die App gerade erscheint (ADR-0007, REQ-APP-SET-002).

- **Datenschutzerklärung, Impressum und Nutzungsbedingungen sind jetzt erreichbar** — aus den
  Einstellungen und vom Anmeldebildschirm aus. Die beiden Schaltflächen dort waren bisher ohne
  Funktion, was gerade beim Datenschutzhinweis nicht bleiben durfte: Er muss vor der ersten
  Verarbeitung lesbar sein, und die beginnt mit dem Tippen auf „Anmelden“. Geöffnet wird die
  Seite der Web-Anwendung, damit App und Web nie zwei auseinanderlaufende Fassungen zeigen
  (REQ-APP-SET-005).

- **Open-Source-Lizenzen als eigene Seite.** Sie listet jede mitgelieferte Fremdsoftware mit
  genauer Version und Lizenz. Die Liste wird beim Bauen aus den tatsächlichen Abhängigkeiten
  erzeugt, nicht gepflegt — und der Build bricht ab, wenn eine Abhängigkeit unter einer Lizenz
  hereinkommt, die nicht ausdrücklich erlaubt ist (REQ-APP-SET-006).

### Fixed

- **Die App stürzte ab, sobald der Bildschirm neu aufgebaut wurde** — etwa beim Drehen eines
  Tablets. Sie verschwand dabei kommentarlos zum Startbildschirm. Ursache war, dass die
  Anmelde-Verwaltung an den Bildschirm statt an die App gebunden war und der gespeicherte
  Anmelde-Speicher sich nicht zweimal öffnen lässt. Sie gehört jetzt der App: Beim Neuaufbau
  bleibt man angemeldet, eine offene App-Sperre bleibt offen, und eine laufende Anmeldung läuft
  weiter. Gefunden beim Umschalten der Sprache, die denselben Neuaufbau auslöst
  (REQ-APP-SET-008).

### Changed

- **Die Navigation ist jetzt übersetzbar.** Die Bezeichnungen der Bereiche standen fest im Code
  und wären auf Englisch deutsch geblieben — also ausgerechnet auf der größten Fläche der App.
  Fachbegriffe bleiben auch im Englischen deutsch (Einsätze, Aufträge, Lager, Raffinerie,
  Materialbörse, Beförderung) — sie sind die Sprache der Staffel, keine zu übersetzenden Wörter
  (REQ-APP-SET-003).

### Changed

- **Die App setzt jetzt Android 11 voraus (vorher Android 10).** Android 10 bot für die App-Sperre nur einen schwächeren Schlüsseltyp an, der einen zweiten, kaum benutzten Sonderweg im Code nötig machte — und dieser Sonderweg funktionierte nicht: Auf Android 10 ließ sich die Sperre weder einschalten noch öffnen, und die App meldete sie fälschlich als dauerhaft unbrauchbar. Statt ihn zu reparieren ist er entfallen. Auf Android 11 und neuer bestätigt jede Abfrage jetzt genau den Vorgang, den sie freigibt. Geräte mit Android 10 können die App nicht mehr installieren und nutzen weiterhin die Web-Anwendung.

### Added

- **Die App-Sperre schützt jetzt auch die gespeicherte Anmeldung, nicht nur den Bildschirm.** Bisher lag das gespeicherte Sitzungs-Token hinter dem Sperrbildschirm, war aber technisch auch ohne Entsperren lesbar. Es liegt nun zusätzlich unter einem Schlüssel, den das Gerät erst nach Fingerabdruck, Gesicht oder Gerätesperre herausgibt. Wer die Sperre aus- oder einschaltet, bleibt angemeldet; ist die Sperre aktiv und noch nicht geöffnet, bleibt die gespeicherte Anmeldung unangetastet liegen statt gelöscht zu werden (REQ-APP-AUTH-010).

- **Entwickler-Builds erreichen jetzt das Backend des lokalen Teststacks — ohne Einrichtung.** Dessen Zertifikat scheiterte bisher an der TLS-Prüfung, und zwar ohne erkennbare Ursache: Die App meldete schlicht „keine Verbindung", während der Server auf demselben Rechner lief. Der Teststack bringt jetzt ein gemeinsames Zertifikat mit, das der Entwickler-Build kennt; das früher nötige Einrichten eines eigenen Zertifikats im Emulator entfällt — es ließ sich auf den gebräuchlichen Emulator-Abbildern gar nicht automatisieren. Release-Builds sind davon unberührt: Die Ausnahme steht in einem Bereich, den Android nur für Debug-Builds beachtet, und das Zertifikat liegt ausschließlich im Entwickler-Flavour (REQ-APP-AUTH-011).

- **Die Nutzungsbedingungen erscheinen jetzt in der App und lassen sich dort annehmen.** Wer noch nicht zugestimmt hat, bekommt nach der Anmeldung den vollständigen Text zu lesen, mit Kästchen zum Bestätigen und einem Weg zum Ablehnen — der meldet ab und sagt das vorher deutlich. Der Text kommt dabei vom Server und steckt nicht in der App: So kann die App nie eine andere Fassung anzeigen als die, für die die Zustimmung gespeichert wird. Lässt sich der Text nicht laden, erscheint eine Fehlermeldung statt einer leeren Seite — einer leeren Seite zuzustimmen wäre keine Zustimmung (REQ-APP-AUTH-009).
- **Die App lässt sich jetzt sperren, und Bildschirmfotos sind app-weit unterbunden.** Wer will, schaltet in den Einstellungen eine Sperre ein: Beim Start und nach fünf Minuten im Hintergrund fragt das System per Fingerabdruck, Gesicht oder Gerätesperre nach, bevor die App wieder etwas zeigt. Kurzes Wechseln zu einer anderen App sperrt nicht — sonst wäre die Funktion im Alltag unbrauchbar. Der Sperrbildschirm zeigt bewusst keinerlei Daten, auch keine Zähler. Unabhängig davon verhindert die App ab sofort überall Bildschirmfotos und Bildschirmaufnahmen und hält damit auch die Vorschau in der App-Übersicht leer. Ohne eingerichtete Gerätesperre bleibt die Einstellung sichtbar, aber abgeschaltet.

- **Wer noch nicht freigegeben ist, sieht das jetzt — statt einer Wand aus Fehlern.** Nach der Anmeldung prüft die App, ob das Konto von der Administration freigeschaltet ist, und zeigt andernfalls einen eigenen Wartebildschirm mit dem Kontonamen, einer Schaltfläche zum Aktualisieren und dem Weg zum Abmelden. Der Status wird alle 60 Sekunden automatisch neu geprüft; sobald die Freigabe kommt, geht es ohne Zutun weiter. Abgelehnte Konten bekommen denselben Bildschirm mit eigener Formulierung. Ist der Status gar nicht abfragbar, sagt die App genau das — und behauptet nicht, das Konto warte auf Freigabe.

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
