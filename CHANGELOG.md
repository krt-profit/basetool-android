# Changelog

## [Unreleased]

### Added

- **Für Einsätze kannst du dich jetzt in der App an- und abmelden.** Dazu Ein- und Auschecken, sobald der Einsatz läuft, und die Wahl zwischen Auszahlung und Spende. Dein eigener Eintrag ist in der Teilnehmerliste hervorgehoben. Die Einsatzplanung bleibt der Weboberfläche vorbehalten (REQ-APP-MIS-013…016).

- **Aufträge lassen sich jetzt aus der App übernehmen.** „Übernehmen" setzt dich auf den Auftrag, „Abmelden" nimmt dich wieder herunter, und zu deinem Eintrag gehört eine eigene Notiz — wann du arbeitest, welchen Teil du nimmst. Logistiker können zusätzlich den Status ändern. Übergaben und die übrige Bearbeitung bleiben der Weboberfläche vorbehalten (REQ-APP-ORDERS-009…012).

- **Das Lager lässt sich jetzt aus der App buchen.** Die Bestände einer Gruppe öffnen sich bis zum einzelnen Eintrag; von dort geht Einbuchen, Ausbuchen (verwerfen, übergeben, verkaufen) und Notiz ändern. Beim Verkaufen zeigt die App die Terminals des Materials mit ihrem Preis. Nach jeder Buchung bleibt der geöffnete Pfad offen und zeigt das Ergebnis. Persönliche Bestände, Mehrfachauswahl und Sammel-Umbuchung bleiben der Weboberfläche vorbehalten (REQ-APP-INV-007…012).

- **Der Hangar lässt sich jetzt aus der App pflegen.** Schiffe anlegen, ändern und löschen: Typ über eine Suche, Versicherung als LTI oder Monatszahl, Ort und Fitted-Zustand. Nur die eigenen Schiffe — die Org-Ansicht bleibt eine Übersicht. Der Import bleibt Phase 4 (REQ-APP-HANGAR-006…009).

- **Die Blueprints sind da — der zweite Reiter von „Mein Inventar".** Was dir gehört, mit einem Chip, der sagt, ob es baubar ist oder welche Materialien fehlen; ein Schalter rechnet die Raffinerie mit. Hinzufügen über die Produktsuche, Notiz ändern, entfernen. Was du schon hast, bietet die Suche nicht noch einmal an, und was der Server nicht freigibt, bekommt keinen Entfernen-Knopf (REQ-APP-PI-008…012).

- **„Mein Inventar" ist da — und die App kann zum ersten Mal schreiben.** Eigene Bestände anlegen, ändern und löschen, mit Ortssuche, Mengen-Stepper und Notiz. Nur für dich sichtbar; niemand sonst sieht diese Liste. Blueprints folgen im nächsten Schnitt, der Datei-Import in Phase 4 (REQ-APP-PI-001…006).

- **Ohne Netz sind Schreib-Aktionen gesperrt statt in einer Warteschlange.** Die Knöpfe bleiben sichtbar und ausgegraut, mit einer Zeile, die sagt warum — eine später abgeschickte Änderung würde der Server ohnehin ablehnen, weil ihr Stand veraltet ist. Dafür kommt die Berechtigung „Netzwerkstatus lesen" dazu; sie liest nur, ob überhaupt eine Verbindung besteht, und schickt nichts.

- **Der Eintrags-Dialog lässt sich scrollen.** Mit gewähltem Ort und offener Tastatur lagen „Abbrechen" und „Speichern" sonst unter dem Bildschirmrand — der Eintrag ließ sich nicht speichern.

- **Gleichzeitige Änderungen sagen es dir, ohne deine Eingabe wegzuwerfen.** Hat jemand anderes denselben Eintrag zwischenzeitlich geändert, bleibt alles Getippte stehen und der Dialog erklärt, was zu tun ist.

### Fixed

- **Aktionen in Bottom-Sheets lagen unter der Gestenleiste.** „Buchen" und „Speichern" ließen sich am unteren Bildschirmrand nicht antippen — der Tipp ging ans System statt an den Knopf. Betrifft alle Sheets der App.

- **Der leere Hangar verwies noch auf die Weboberfläche.** „Schiffe hinzufügen geht derzeit über die Weboberfläche" stimmte nicht mehr, sobald die App es selbst kann.

- **Die Sitzung hält jetzt länger als eine Stunde.** Bisher lief die Anmeldung nach Ablauf des Zugriffs-Tokens aus, und danach meldete jeder Bildschirm „Signal Lost" — nur ein Neustart der App half. Das Token wird jetzt vor Ablauf und nach einer Ablehnung durch den Server erneuert (REQ-APP-AUTH-012).

- **Ziehen zum Aktualisieren funktioniert jetzt auch auf leeren Listen.** Genau dort, wo man es braucht — leere Auftragsliste, noch kein Konto sichtbar — passierte beim Ziehen bisher nichts (REQ-APP-UI-001).

- **Ein laufender Einsatz verschwand aus der Liste.** „Vergangene aus" hat nach Startzeit gefiltert und damit auch jeden Einsatz ausgeblendet, der gerade läuft. Es filtert jetzt nach Status, wie die Weboberfläche (REQ-APP-MIS-002).

- **Der Posteingang ließ die App abstürzen.** Beim Zusammensetzen des Benachrichtigungstexts brach die App auf dem Gerät ab, sobald eine Benachrichtigung vorlag (REQ-APP-NOTIF-009).

- **„Anteil je Teilnehmer" zeigte 0 aUEC.** Im Finanz-Rollup einer Operation wurde der Anteil eines Verzichtenden gelesen, der serverseitig null ist. Genannt wird jetzt der tatsächlich verdiente Anteil, bei ungleicher Aufteilung als Spanne (REQ-APP-OPS-012).

- **Fehlende Mengen im Auftrag lasen sich wie ein Anzeigefehler.** Eine Materialzeile ohne Bestandsangabe zeigte „ / 500"; jetzt steht dort ein Gedankenstrich, und ohne Bestandsangabe gibt es keinen Fortschrittsbalken statt eines leeren (REQ-APP-ORDERS-008).

- **Die Übersicht behauptete „Nichts Ungelesenes", bevor der Posteingang geantwortet hatte** — auch dann, wenn die Glocke daneben eine 2 zeigte (REQ-APP-DASH-008).

- **Der Reiter „Einsätze" trug eine erfundene Zahl.** Am Symbol klebte fest eine 2, unabhängig davon, was es zu sehen gab.

### Added

- **Das Lager ist da.** Der Bestand als Baum: je Material eine Zeile mit Menge, Einheit und Qualität, aufklappbar zu den einzelnen Beständen mit Verwahrer, Ort und Anzahl der Einträge. Geladen wird eine Gruppe erst beim Aufklappen. Schlägt das fehl, bleibt die Gruppe offen und sagt es — statt sich wortlos wieder zu schließen. Ein- und Ausbuchen bleibt der Weboberfläche vorbehalten (REQ-APP-INV-001…006).

- **Die Aufträge sind da — Warteschlange und Detail.** Nach Status filtern, je Zeile die Materialliste aufklappen und den Fortschritt sehen, im Detail Anmerkung, Material, Zuständige und Übergaben. Ist ein Auftrag für dich nur teilweise sichtbar, sagt der Bildschirm das jetzt ausdrücklich, statt den Rest als vollständig erscheinen zu lassen. Eine Benachrichtigung zu einem Auftrag öffnet ihn direkt. Anlegen, Priorisieren und Übergaben erfassen bleibt der Weboberfläche vorbehalten (REQ-APP-ORDERS-001…007).

- **Die Bank ist da — Konten und Kontodetail.** Jede für dich sichtbare Kasse mit Kontostand, 30-Tage-Bewegung und gezeichneter Verlaufslinie; im Detail der vollständige Buchungsverlauf zum Nachladen. Einzahlungen stehen grün mit +, Auszahlungen rot mit −, und eine Buchungsart, die diese App nicht kennt, bekommt kein erfundenes Vorzeichen. Buchungen beantragen und freigeben bleibt der Weboberfläche vorbehalten (REQ-APP-BANK-001…005).

- **Der Hangar ist da.** Die eigenen Schiffe als Karten mit Typ, Hersteller, Versicherung, Ort und Fitted-Zustand, dazu die Aggregation über die aktive Org-Einheit — beide nach Schiffstyp filterbar. Was fehlt, sagt die Karte auch: „Keine Versicherung" statt eines leeren Feldes. Schiffe anlegen, ändern und importieren bleibt vorerst der Weboberfläche vorbehalten (REQ-APP-HANGAR-001…005).

- **Die Übersicht zeigt jetzt echte Daten statt eines Platzhalters.** Begrüßung mit Name, Org-Einheit und Datum, die Ankündigung der Organisation (antippen klappt sie auf), die Einsätze der nächsten sieben Tage und eine Vorschau des Ungelesenen. Ankündigung und Einsatzband laden unabhängig: fällt eines aus, bleibt das andere stehen. Ist nichts angekündigt, erscheint kein leeres Band, sondern gar keines (REQ-APP-DASH-001…007).

- **Benachrichtigungen sind da — Posteingang, Zähler an der Glocke und Live-Push.** Der Reiter zeigt die neuesten 50 mit „Mehr laden", ungelesene deutlich abgesetzt, jede Zeile mit dem Symbol ihres Bereichs. Die Zahl an der Glocke stimmt jetzt wirklich: sie kommt vom Server, wird per Push sofort und zusätzlich jede Minute aktualisiert, und bleibt bei einem Fehler stehen, statt fälschlich null zu zeigen. Beides läuft nur, solange die App im Vordergrund ist. Als gelesen markieren und Löschen folgen später (REQ-APP-NOTIF-001…008).

- **Operationen sind da — Liste und Detail.** Über der Einsatzliste steht jetzt der Umschalter „Einsätze / Operationen"; die Operationen-Liste trennt laufende von abgeschlossenen und lässt sich suchen und nach Status filtern. Das Detail zeigt das Finanz-Rollup, das Ergebnis je Einsatz, alle Auszahlungszeilen mit ihrem Stand und den eigenen Anteil. Vorläufige Beträge und eine vom Server gekürzte Einsatzliste werden als solche benannt. Auszahlungen markieren bleibt der Weboberfläche vorbehalten (REQ-APP-OPS-001…011).

- **Ein Einsatz lässt sich jetzt öffnen.** Das Antippen einer Zeile zeigt den Einsatz mit sieben Reitern: Übersicht, Teilnehmer, Einheiten, Ablauf, Ziele, Frequenzen und Finanzen. Die Finanzen werden erst geladen, wenn man den Reiter öffnet — sie brauchen eine eigene Berechtigung, und wer sie nicht hat, sieht den Einsatz trotzdem vollständig. Frequenzen kopiert ein Antippen in die Zwischenablage; Beträge stehen gruppiert und mit Vorzeichen (+86.400 / −11.700). Anmelden, Check-In und Finanz-Einträge folgen später (REQ-APP-MIS-008…012).

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
