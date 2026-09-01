# Changelog

## [Unreleased]

### Added

- **Die Bank sagt jetzt, was die Gebühr kostet — vor dem Buchen.** Bei Auszahlung und
  halterwechselnder Umbuchung stehen Gebühr, „wird abgebucht" und „kommt an" im Blatt, dazu ein
  Schalter, ob dein Betrag der Bruttobetrag ist oder das, was ankommen soll. Bisher stand davon
  nichts da: wer 100 000 auszahlte, bei dem verließen mehr als 100 000 das Konto, und „Stand nach
  Buchung" rechnete die Gebühr auch nicht mit.
- **Ein Einsatz lässt sich jetzt beenden** — im Verwaltungs-Tab unter „Zeitplan". Das schließt
  zugleich die offenen Zeiten aller Teilnehmer, worauf die Auszahlung beruht. Bisher ging das
  nirgends in der App: der Start wird beim Aktivieren gesetzt, das Ende von nichts.
- **Eine Einsatz-Einheit trägt jetzt Schiff, Verantwortlichen, Frequenz und Notiz** — und behält
  sie. Bisher löschte schon das Umbenennen einer Einheit alle vier, weil die App sie nicht
  mitschickte und der Server die Einheit vollständig ersetzt.
- **Das Lager zeigt, welche Zeilen schon auf der Materialbörse liegen.**
- **Ein Einsatz lässt sich jetzt einer Operation zuordnen** — im Verwaltungs-Tab unter „Kern".
  Bisher ging das nirgends: das Operationen-Formular verwies auf den Einsatz, und der hatte kein
  solches Feld.

- **Beim Einbuchen lässt sich der Bestand direkt zuordnen.** Unter dem Ort stehen jetzt
  „Aufträge" und „Einsätze": 400 SCU einbuchen und dabei 250 für Auftrag #91 und 150 für #104
  vormerken — in einem Schritt, wie im Webtool. Bisher musstest du erst einbuchen und den Eintrag
  danach einzeln zuweisen; dazwischen lag der Bestand für alle anderen sichtbar als frei. Angeboten
  werden nur Aufträge, die das Material überhaupt brauchen, und wer mehr vormerkt als er einbucht,
  kommt nicht am Knopf vorbei.

- **Game-Items lassen sich jetzt ins Lager einbuchen.** Das Einbuchen-Blatt hat oben einen
  Umschalter „Material / Item"; die Item-Seite sucht im Item-Katalog, fragt keine Qualität (der
  Server nimmt dort keine) und zählt in ganzen Stück. Ein Wechsel des Umschalters verwirft die
  Wahl der anderen Seite — der Server nimmt genau eine von beiden.

### Fixed

- **Das Benachrichtigungs-Postfach öffnete auf der ältesten Nachricht.** Ohne
  Sortier-Parameter sortiert der Server aufsteigend, also lag die Nachricht von heute auf der
  letzten Seite. Betraf ebenso „Meine Raffinerieaufträge", wo alles noch Laufende hinten lag.
- **Die Einsatzliste zeigte mit „Vergangene" den ältesten Einsatz zuerst.** Die Richtung folgt
  jetzt dem Filter: kommende aufsteigend, vergangene mit dem jüngsten oben.
- **Abgelehnte Vorgänge meldeten „Jemand anderes hat geändert".** Ein Konto mit Restbestand,
  ein bereits entschiedener Antrag, eine nicht stornierbare Buchung — alle erschienen als
  Gleichzeitigkeits-Konflikt samt Rat „neu laden und erneut speichern", der nicht aufgehen konnte.
  Jetzt steht die Begründung des Servers da.
- **Textfelder liefen ins Serverlimit statt zu begrenzen.** Einsatz-Beschreibung, Treffpunkt,
  Einheiten-Notiz, Bank-Notiz und Lager-Notiz stoppen jetzt dort, wo der Server ablehnt.

### Changed
- **Eine Beteiligung zeigt, woraus sie besteht.** Neben dem Betrag stehen jetzt der Prozentsatz,
  die darin erstatteten Auslagen und die abgezogene Gebühr, und bei erledigten Auszahlungen wer
  sie wann gebucht hat. Bisher stand dort eine Summe, deren Teile unsichtbar waren.
- **Buchungen nennen Gebühr und Empfänger auch im Nachhinein.** Beides stand auf der Leitung und
  wurde verworfen, sodass eine vergangene Überweisung weder ihre Kosten noch den Empfänger zeigte.
- **Die Übergabe-Maske nennt die Einheit der Position.** Bei einem Stück-Material stand dort
  „Menge (SCU)" — auf dem einen Bildschirm, der einen Auftrag abschließt.
- **Direktbuchungen tragen jetzt Begründung und Empfänger mit**, wie im Webtool. Bisher gingen
  beide Angaben verloren.
- **Die Mengen-Beschriftung nennt die Einheit auf Deutsch.** Bei einem Stück-Material stand dort
  „Menge (PIECE)", das Wort vom Draht.
- **Picker sagen jetzt, wenn sie nicht alles zeigen.** Ort, Material und Mitglied im Lager, die
  Mitgliedersuche im Einsatz und die Grant-Suche in der Bank brachen die Liste bei 25 Treffern ab,
  ohne ein Wort — ein fehlender Eintrag sah aus, als gäbe es ihn nicht. Die Ortsliste holt jetzt
  außerdem den ganzen Katalog statt eines Bildschirms davon. Umgekehrt behauptet der Ort-Picker in
  „Mein Inventar" nicht mehr, es gäbe weitere Treffer, wenn er gerade alle zeigt.
- **Eine neue Materialzeile im Auftrag fordert wieder Qualität 650**, wie im Webtool. Bisher stand
  dort „Keine": derselbe Auftrag, im Browser und auf dem Telefon angelegt, bestellte
  unterschiedliches Material.
- **Auftragspositionen nennen die Einheit des Materials.** Bei einem Stück-Material stand „SCU"
  über der Menge — 500 Stück lasen sich als 500 SCU.
- **Der Hinweis auf Teilmengen steht nur noch bei SCU-Material.** Über einem Feld, das Stück
  zählt, bot er Bruchteile von etwas an, das keine hat.
- **Einbuchen verlangt jetzt eine Qualität, bevor der Knopf freigibt.** Ohne sie lehnte der Server
  die Buchung ab — eine Absage, die das Formular vorher kannte.
- **Der Raffinerieauftrag bietet nur noch raffinierbare Erze an.** Bisher stand der ganze
  Materialkatalog im Eingangs-Picker — und der Server lehnte ein nicht-raffinierbares Material ab,
  ohne einen Grund zu nennen. Das **Ausgangsmaterial** wählst du nicht mehr selbst: es ergibt sich
  aus dem Erz und steht als Text daneben, wie im Webtool.
- **Die Mengenfelder heißen jetzt Units, nicht SCU** — und darunter steht, was das in SCU ist. Das
  Feld war als SCU beschriftet, ging aber unverändert an den Server, der Units zählt: wer 442
  eintrug und SCU meinte, legte 4,42 SCU an.
- **Zeigt der Erz-Picker nicht alle Treffer, sagt er das.** Bisher endete die Liste bei 25 Zeilen,
  ohne Hinweis — ein fehlendes Erz sah aus, als gäbe es das nicht.

### Removed

- **Das Feld „Ertragsbonus" im Raffinerieauftrag ist raus.** Der Server berechnet den Bonus aus
  UEX-Daten und verwirft, was die App schickt — das Feld nahm einen Wert entgegen, der nie
  irgendwo ankam.

## [0.2.0] — 2026-08-31

### Added

- **Die Verwaltung eines Einsatzes zeigt jetzt, wer Einsatzleitung und wer Manager ist.** Vorher
  standen dort nur drei Knöpfe; die Manager waren nirgends zu sehen und liessen sich deshalb auch
  nicht entfernen. Jede Zeile nennt zuerst den aktuellen Stand, dann die Aktion — und das
  Entfernen eines Managers fragt nach und nennt die Person.

- **Freigabe-Limits je Konto.** In den Kontoeinstellungen steht jetzt, bis zu welchem Betrag welche
  Stufe — und welches Mitglied — ohne zusätzliche Freigabe buchen darf. Setzen und Entfernen; beim
  Entfernen sagt die Rückfrage, welches Limit danach gilt.

- **Direktbuchung in der Bank-Verwaltung.** Ein Blatt mit drei Modi — Einzahlung, Auszahlung,
  Umbuchung — samt Halter, Verwendungszweck und Vorschau „Stand nach Buchung". Ohne Antrag heisst
  ohne zweite Freigabe; das steht über dem Knopf. Ohne die Rolle Bank-Management ist der Einstieg
  gesperrt und nennt die Rolle.

- **Sammel-Ausbuchen im Lager.** Zweite Aktion in derselben Auswahlleiste wie das Umbuchen. Es
  werden immer ganze Zeilen ausgebucht — für Teilmengen bleibt das Einzel-Ausbuchen — und entweder
  geht alles oder nichts; eine abgelehnte Aktion lässt die Auswahl stehen.

- **Neue Ansicht „Game-Items" unter „Mehr".** Zeigt den Bestand nach Item statt nach Material: wie
  viele Stück, bei wie vielen Haltern und an welchen Orten. Suche und Kategorien filtern die
  vollständige Liste; eine Zeile klappt ihre Orte an Ort und Stelle auf.

- **Mehrere Einträge in „Mein Inventar" auf einmal löschen.** Langer Druck wählt, weitere Zeilen mit
  einem Tipp; unten stehen „Alles wählen", „Aufheben" und „Löschen". Gefragt wird nach, die Anzahl
  steht dabei, und danach sagt die Leiste, wie viele gelöscht und wie viele übersprungen wurden —
  abgelehnte Zeilen bleiben gewählt. Nicht rückholbar.

- **Neue Ansicht „Blueprint-Verfügbarkeit" unter „Mehr".** Zeigt org-weit, wer welchen Blueprint
  hat — Besitzer werden je Zeile nachgeladen, Mitglieder anderer Einheiten sind als solche
  gekennzeichnet. Ohne die Rolle Officer steht der Eintrag gesperrt da und nennt die Rolle. Der
  Filter „Nicht erfasst" gilt für die geladenen Zeilen und sagt das.

- **Mehrere Blueprints auf einmal übernehmen.** Dieselbe Suche, jetzt mit Kästchen; der Knopf
  nennt die Anzahl und das Blatt sagt danach, was passiert ist — „2 übernommen · 1 bereits
  vorhanden". Bereits vorhandene Blueprints tauchen in der Liste nicht mehr auf; eine Zeile im Blatt
  sagt warum. Eine Notiz geht nur bei einem einzelnen Blueprint mit.

- **Die Materialbörse kennt jetzt Items — und eigene Einträge lassen sich bearbeiten.** Angebot und
  Gesuch haben oben einen Umschalter „Material / Item"; die Item-Seite sucht das Produkt im Katalog
  und hängt an keiner Lagerzeile. Ein Tipp auf die eigene Zeile öffnet ein Blatt mit Menge,
  Mindestqualität, Anmerkung und dem Zurückziehen — das fragt nach, wenn Interessenten warten, und
  nennt sie.

- **Raffinerieaufträge lassen sich bearbeiten und löschen.** Beides im „⋮" der Auftragsansicht.
  Bearbeitet wird im gewohnten Anlegen-Formular, vorbefüllt; nach dem Einlagern sind Raffinerie,
  Methode und Waren festgeschrieben und stehen gesperrt mit dem Grund da — Geld und Einsatz bleiben
  änderbar. Gelöscht wird nach Rückfrage; ein bereits eingelagerter Auftrag lässt sich nicht
  löschen, dort korrigiert man die Lagerzeilen.

- **Operationen lassen sich in der App anlegen und bearbeiten.** Über der Filterleiste der
  Operationen-Liste und im „⋮" der Detailansicht; Name, Beschreibung und Status, ein Formular für
  beides. Beginn, Ende und die Zuordnung von Einsätzen fehlen bewusst — die Schnittstelle kennt
  keine solchen Felder, ein Einsatz wird über den Einsatz selbst zugeordnet; das Formular sagt das.

- **„Handel" bekommt zwei weitere Ansichten im Überlaufmenü.** Die **Preis-Übersicht** zeigt die
  Matrix Material × Terminal — die Materialspalte bleibt stehen, die Terminals scrollen darunter,
  der beste Preis je Zeile ist getont, umschaltbar zwischen Verkauf und Einkauf. Die
  **Profitberechnung** rechnet eine volle Ladung eines gewählten Schiffs je Material durch,
  optional auf einzelne Systeme eingeschränkt.

- **Neuer Bereich „Handel" unter „Mehr".** Die Material-Übersicht zeigt alle Materialien mit
  Einkaufs- und Verkaufspreis, filterbar nach Name, Kategorie und Preisgrenzen; ein Tipp öffnet
  „Preise und Terminals" mit dem besten Käufer und Verkäufer und der vollständigen Terminal-Tabelle.
  Fehlt ein Preis, steht ein Gedankenstrich statt einer Null.

- **Item-Aufträge zeigen ihre Unterbaugruppen und lassen sich bearbeiten.** Unter jeder Position
  steht, aus welchen Baugruppen sie besteht und was jede davon braucht — mit einem Chip, ob der
  Auftrag sie schon im Lager hat. Die Positionen selbst ändert ein Logistiker im gewohnten Formular,
  samt Blueprint-Variante, solange noch nichts übergeben wurde.

- **Neue Materialsammelübersicht im Auftrag.** Zeigt, welche Lagereinträge diesem Auftrag zugeordnet
  sind, mit Besitzer, Standort, Menge und Lieferstatus; der Lieferstatus lässt sich umstellen und
  eine Zuordnung lösen — nachgefragt wird nur, wenn tatsächlich eine Menge daran hängt. Besitzer
  und Standort ändert man weiterhin im Lager, weil das im Hintergrund eine Umbuchung ist.

- **Aufträge lassen sich jetzt in der App bearbeiten.** Logistiker ändern das ganze Formular,
  Antragsteller Mengen und Kommentar — die übrigen Felder stehen dort gesperrt mit dem Grund statt
  zu fehlen. Eine Position kann nicht unter die bereits übergebene Menge fallen; das Formular sagt,
  welche Zeile es ist. Item-Positionen werden weiterhin im Web bearbeitet.

- **Zusagen: neuer Tab im Auftrag.** Bei Aufträgen eines Spezialkommandos sieht man je Material,
  welche Staffel wie viel zugesagt hat und was noch offen ist, und kann für die eigene Staffel
  zusagen, die Zusage ändern oder sie zurückziehen. Eine Zusage ist eine Absicht, keine Buchung —
  geliefert wird weiterhin über die Übergabe.

- **Item-Aufträge lassen sich jetzt übergeben — und der Übergaben-Tab zeigt sie endlich an.** Je
  Position wird eingetragen, wie viele Stück an wen gegangen sind; die Obergrenze ist, was
  hergestellt und noch nicht übergeben wurde. Bisher blieb das Item-Übergabeprotokoll ungelesen, so
  dass ein vollständig gelieferter Item-Auftrag im Tab als „noch nichts übergeben" erschien.

- **Die Herstellung eines Item-Auftrags lässt sich jetzt in der App buchen.** Je Position wird
  eingetragen, wie viele Einheiten gebaut wurden, aus welchen verknüpften Lagereinträgen die
  Materialien entnommen werden und wo die fertigen Einheiten eingelagert werden. Der Bedarf muss je
  Material exakt gedeckt sein — „Bedarf decken" füllt ihn auf einen Tipp. Material, das außerhalb
  des Tools verbraucht wurde, kann je Zeile von der Ausbuchung ausgenommen werden. Ohne die Rolle
  Logistiker ist die Schaltfläche gesperrt sichtbar statt versteckt.

- **Übergaben lassen sich jetzt in der App erfassen** — damit ist ein Auftrag erstmals aus der App
  heraus abschließbar. Das Formular zeigt live, wo die Position danach steht („Nach dieser Übergabe
  300 / 400"), bucht die gewählte Lagerzeile aus und sagt vorab, dass eine erfasste Übergabe nicht
  zurücknehmbar ist.

- **Einsatz-Leitung kann die Teilnehmerliste jetzt in der App führen.** Wer einen Einsatz verwaltet,
  checkt Mitglieder direkt in deren Zeile ein und aus, stellt ihre Auszahlung um und weist die
  Funktion an Bord zu. Wer die Rolle nicht hat, sieht dieselben Bedienelemente gesperrt — antippbar,
  mit einem Hinweis, welche Rolle fehlt.

- **Einheiten, Crew und Frequenzen lassen sich jetzt in der App pflegen.** Eine Einheit anlegen und
  entfernen (mit HVU-Markierung), jemanden an Bord nehmen und wieder herunter, eine Frequenz
  hinzufügen und löschen. Wer die Rolle nicht hat, sieht dieselben Bedienelemente gesperrt.

- **Neuer Tab „Verwaltung" im Einsatz:** Titel, Beschreibung, Treffpunkt, Zeiten und Sichtbarkeit
  sind jetzt in der App änderbar. Jeder Abschnitt wird einzeln gespeichert, damit eine Änderung
  nicht mit einer gleichzeitigen Änderung an anderer Stelle kollidiert. „Einsatz läuft jetzt" setzt
  den tatsächlichen Start — davor weist der Server jeden Check-in ab, was bislang nur im Web
  aufhebbar war. Der Tab erscheint nur, wenn man den Einsatz auch verwalten darf.

- **Ablauf und Ziele lassen sich jetzt in der App pflegen.** Schritte anlegen, bearbeiten, abhaken,
  entfernen und verschieben; Ziele ebenso, mit Primär / Sekundär / Kein Ziel.

- **Einsatzleitung, Manager und zusätzliche Teilnehmer werden über eine Mitgliedersuche benannt.**
  Tippen, die Liste grenzt ein, darunter steht wie viele Treffer angezeigt werden.

- **Eine Einheit lässt sich umbenennen, jemand an Bord nehmen, und die Funktion an Bord wird pro
  Person gesetzt.**

- **Die Zeile zeigt jetzt auch den Funktionswunsch** („Wunsch: Pilot"), damit eine Zuweisung nicht
  geraten werden muss.

### Changed

- **Die Übersicht schliesst wieder mit „Ungelesen".** Die zwei neuesten ungelesenen
  Benachrichtigungen, darauf „Alle ansehen" in den Posteingang. Der Block ist eine Vorschau:
  antippen öffnet den Posteingang, und nichts darin setzt eine Benachrichtigung auf gelesen. Ist
  nichts ungelesen, steht er gar nicht da.

- **Der Verwaltungs-Tab im Einsatz ist jetzt sichtbar und gesperrt statt versteckt.** Wer den
  Einsatz nicht verwaltet, sieht ihn ausgegraut mit Schloss; ein Tipp nennt die fehlende Rolle.
  Eine Funktion, die niemand sieht, wird nie angefragt.

- **Der Verwaltungs-Tab ist in vier aufklappbare Abschnitte geteilt** — Kern, Zeitplan,
  Sichtbarkeit, Personen. Jeder Kopf zeigt seinen Stand, auch zugeklappt, und hat seinen eigenen
  Speichern-Knopf. Ein Konflikt nennt jetzt den betroffenen Abschnitt und sagt, dass die übrigen
  nicht betroffen sind.

- **Zeiten werden als Datum und Uhrzeit eingegeben statt als ISO-Text.** „Einsatz läuft jetzt"
  fragt vorher nach und nennt, wie viele Angemeldete sich danach einchecken können.

- **Ablauf- und Ziele-Zeilen tragen drei Symbolschaltflächen statt mehrerer Knopfreihen**;
  Anlegen und Bearbeiten öffnen ein Sheet. „Nur intern" und „HVU" sind Kästchen statt Radios,
  und „Person zuweisen" öffnet eine Auswahl statt einer Chip-Reihe über alle Angemeldeten.

- **Gedämpfter Text ist überall auf den lesbaren Grauton umgestellt** (23 Stellen) — der bisherige
  Wert ist die Hairline-Farbe und unterschreitet den Kontrast-Grenzwert als Text.

- **Der erste Tab im Einsatz heißt jetzt „Briefing" statt „Übersicht".** Direkt darunter steht in der
  unteren Leiste ebenfalls „Übersicht" — das Dashboard. Zwei gleich benannte Bedienelemente auf
  einem Handy-Bildschirm; der Tab-Name passt jetzt auch besser zu seinem Inhalt.

- **Auf dem Tablet steht neben dem Lagerbaum eine Materialtabelle.** Ein Material antippen öffnet es
  im Baum und zeigt rechts alle Einträge dazu — Nutzer, Ort, Qualität, Menge, seitenweise. Auf dem
  Telefon bleibt alles wie bisher.

- **Der Lagerbaum zeigt, wie viel ein Mitglied insgesamt hält.** Zwischen Material und Eintrag steht
  jetzt eine Zeile pro Halter mit dessen Summe; die Zeile darunter nennt nur noch den Ort. Wer ein
  Material an zwei Orten liegen hatte, sah bisher zwei zusammenhanglose Zeilen und keine Summe.

- **Die Materialbörse zeigt auf dem Tablet zwei Kartenspalten.** Bisher stand jede Karte über die
  volle Breite, mit dem gesamten Inhalt im linken Viertel.

- **Auf Tablets haben alle Listen links und rechts denselben Rand.** Lager, Posteingang, Mein
  Inventar, Blueprints, Operationen, Auftragsdetail, Bankkonto und die Übersicht klebten bisher an
  der Navigationsleiste und am Bildschirmrand. Auf dem Telefon bleibt alles wie gehabt — dort ist
  die Breite zu knapp, um sie zu verschenken. Das Listenende in „Mein Inventar" sieht jetzt aus wie
  überall sonst.

- **Logistiker können die Reihenfolge der Warteschlange in der App ändern.** Im Auftragsdetail:
  „An den Anfang", „Höher", „Niedriger". Ein Auftrag, der die Warteschlange verlassen hat, bekommt
  die Regler nicht — und einer, der schon vorn steht, lässt „Höher" gar nicht erst zu.

- **Aufträge lassen sich jetzt in der App anlegen.** Das „+" in der Auftragsliste öffnet das
  Formular: bearbeitende Einheit, Auftraggeber, Handle, beliebig viele Materialzeilen mit Menge und
  Mindestqualität sowie ein Kommentar. Auftraggeber kann jede aktive Einheit sein, bearbeiten darf
  nur eine profit-fähige — dieselbe Unterscheidung wie im Web.

- **Kontoauszug und 3-Monats-Bericht lassen sich aus der App heraus abrufen.** Beide landen im
  app-eigenen Speicher und gehen von dort an die App deiner Wahl weiter — es wird keine
  Speicherberechtigung verlangt und keine andere App kann die Datei lesen.

- **Raffinerieaufträge lassen sich jetzt in der App anlegen.** Raffinerie und Methode mit ihren drei
  Bewertungen, Waren mit Ein- und Ausgang, Timing mit berechnetem Ende und ein Geld-Block mit
  Gewinn-Vorschau. Einen Extractor-Import gibt es bewusst nicht — dessen Übergabe wird einmalig im
  Browser eingelöst und kann ein Telefon nicht erreichen.

- **Raffinerieaufträge lassen sich jetzt mit allen Angaben einlagern.** Pro Material kannst du die
  Menge korrigieren — die berechnete steht daneben —, eine Notiz setzen und die Position als
  persönlichen Eintrag buchen. Gebucht wird der ganze Auftrag in einem Zug.

- **Zahlenfelder akzeptieren jetzt das Komma.** Auf einer deutschen Tastatur liefert die
  Dezimaltaste ein Komma; die App hat das bisher nicht gelesen — im Lager und in der Bank wurde
  daraus stillschweigend eine 0, in der Raffinerie passierte gar nichts.

- **Bankmitarbeiter können eine Buchung stornieren.** Im Kontodetail hat jede Buchung eine
  Storno-Aktion; sie erzeugt eine negierte Gegenbuchung, die Originalbuchung bleibt unverändert im
  Ledger. Eine bereits stornierte Buchung ist als solche markiert und bietet die Aktion nicht mehr
  an. Konten öffnen sich für Bankmitarbeiter jetzt über die Verwaltungssicht — vorher meldete die
  App „Dieses Konto ist für dich nicht einsehbar", sobald man keine eigene Sichtfreigabe hatte.

- **Die Bankverwaltung zeigt jetzt das Halter-Detail.** Tippst du im Tab „Konten" auf einen Halter,
  siehst du, wie viel er verwahrt und welche Buchungen dahinterstehen, und kannst per
  „Halter-Umbuchung" Verwahrung an einen anderen Halter abgeben. Neu ist auch
  „+ Halter registrieren". Die Verwahrung wird auf Einheits-Ebene geführt, nicht je Konto — das
  steht an der Zahl. Für eine Umbuchung fällt eine Gebühr zulasten des KRT-Kontos an; wenn dieses
  Konto fehlt oder nicht gedeckt ist, sagt die App genau das statt „gleichzeitig geändert".

- **Die Bankverwaltung hat jetzt einen Tab „Grants".** Pro Konto siehst du, wer darauf einzahlen,
  auszahlen und umbuchen darf, und kannst es ändern. Wer im Konto steht, darf es auch sehen — auch
  ohne ein einziges Häkchen; das Sehen entziehst du, indem du den Eintrag entfernst, und dafür
  fragt die App vorher nach. Beim KRT-Konto entfällt das: das sieht ohnehin jedes Mitglied. Ohne
  die Rolle Bank-Management ist der Tab gesperrt statt unsichtbar und sagt dir, welche Rolle fehlt.
  Über „+ Grant hinzufügen" trägst du jemanden neu ein; hat das Mitglied die Rolle Bank-Mitarbeiter
  nicht oder steht es schon auf dem Konto, sagt die App genau das.

- **Die Bankverwaltung hat jetzt einen Tab „Konten".** Konten anlegen, umbenennen, schließen und
  wieder öffnen, dazu das Halter-Register der Einheit mit Deaktivieren und Reaktivieren. Ohne die
  Rolle Bank-Management siehst du die Aktionen mit einem Schloss statt gar nicht — ein Tipp sagt
  dir, welche Rolle du brauchst. Ein Konto mit Saldo lässt sich nicht schließen; das steht an der
  Aktion, bevor du sie drückst.

- **Bankmitarbeiter haben jetzt einen Umschalter „Mitglied | Verwaltung" in der Bank.** Unter
  „Verwaltung" siehst du alle Konten der Einheit — auch geschlossene und solche, für die du keine
  eigene Sichtfreigabe hast; letztere sind gekennzeichnet. Wer keine Bank-Rolle hat, sieht den
  Umschalter mit einem Schloss statt gar nicht.

- **Anträge lassen sich in der App bestätigen und ablehnen.** Beim Bestätigen erfasst du den Halter,
  der das Geld erhalten oder ausgezahlt hat, und bei einem freigabepflichtigen Antrag bestätigst du,
  dass die Freigabe des Kontoverantwortlichen vorliegt — die App zeigt dir daneben, ob sie schon
  erteilt wurde. Ablehnen verlangt einen Grund; den sieht der Antragsteller.

- **Beim Ausbuchen und Umbuchen lässt sich jetzt festlegen, von welchen Auftrags- und
  Einsatz-Marken die Menge abgezogen wird.** Was du nicht zuweist, kommt vom noch nicht
  zugewiesenen Rest — wie bisher. Neu ist, dass die App eine Zuordnung, die der Server ablehnen
  würde, schon vorher anzeigt und das Speichern sperrt, statt dich in einen Fehler laufen zu lassen.
 
 - **Die Bank hat jetzt einen Tab „Anträge":** du kannst eine Ein-, Aus- oder Umbuchung
  beantragen, deinen eigenen Antrag noch korrigieren oder zurückziehen, und — auf Konten, für die
  du verantwortlich bist — die Freigabe erteilen oder wieder zurücknehmen. Der Zähler am Tab zeigt,
  wie viele Anträge noch offen sind.

- **Der Antrag sagt vorher, ob er eine Freigabe braucht.** Unter dem Betrag steht das Limit deines
  Kontos, und sobald du darüber liegst, dass der Kontoverantwortliche zuerst freigeben muss. Bei
  einer Einzahlung steht dort nichts — die braucht nie eine Freigabe.

- **Der vom Fankit-Agreement geforderte Hinweis steht jetzt in der App** — der Satz zur
  Nicht-Verbundenheit mit Cloud Imperium, der Copyright-Hinweis und „All rights reserved". Er
  ergänzt die bisherige kurze Markenzeile, ersetzt sie nicht, und steht mit ihr und dem Logo an
  denselben zwei Stellen: Anmeldung und Einstellungen.

- **Benachrichtigungen lassen sich jetzt einzeln stummschalten:** fünf Kategorien in den
  Systemeinstellungen — Einsätze & Check-In, Aufträge & Zuweisungen, Materialbörse,
  Bank & Auszahlungen, System & Ankündigungen.
- **Der Hinweis im Benachrichtigungsschatten sagt jetzt, worum es geht** („Neuer Auftrag #9 für
  IRI") statt nur „Neue Benachrichtigung", und ein Tipp öffnet direkt den passenden Bildschirm.
  Auf dem **Sperrbildschirm** bleibt es weiterhin bei „Neue Benachrichtigung" — dort steht nie ein
  Inhalt.

- **Die Ankündigung auf der Übersicht zeigt jetzt, ob du sie schon gelesen hast**, und lässt sich
  mit „Als gelesen markieren" abhaken — wie im Webtool.

- **Einstellungen zeigt jetzt drei Konto-Zeilen mehr:** die aktive Org-Einheit (tippen öffnet
  denselben Umschalter wie der Chip oben), deine Auszahlungspräferenz und ob deine Blueprints mit
  der Org geteilt werden. Die letzten beiden liegen auf dem Server — du kannst sie auch im Browser
  ändern, und die App zeigt, was dort steht.

- **Beim Anmelden zu einem Einsatz fragt die App jetzt nach Auszahlung und Wunsch-Funktion.** Statt
  eines Knopfs öffnet sich ein Blatt: Anteil an dich oder an die Org-Kasse, und optional die
  Funktion an Bord, die du dir wünschst. Der Wunsch ist keine Zusage — die geplante Funktion setzt
  weiterhin die Einsatzleitung.

- **Umbuchen im Lager kann jetzt alles, was das Webtool kann:** neben Nutzer und Ort lässt sich
  auch die **Org-Einheit** wählen, in deren Bestand der Eintrag wandert — voreingestellt ist die
  aktuelle, damit ein reiner Ortswechsel den Bestand nicht unbemerkt in eine andere Einheit schiebt.
  Bei SCU-Material kommt die Option „Mit vorhandenem Bestand zusammenlegen" dazu.

- **Die Auftragsliste zeigt das Alter jetzt als Tageszahl.** „vor 94 Tagen" statt eines Datums —
  neben der Farbe, die schon sagt, ob ein Auftrag zu lange liegt.

- **Die App setzt jetzt Android 12 voraus** (vorher Android 11). Grund ist nicht die Reichweite,
  sondern die Prüfbarkeit: der Emulator für Android 11 kann sich nicht anmelden, und eine unterste
  Version, die niemand testen kann, ist ein Versprechen, das niemand geprüft hat. Direkt beim
  ersten Anmeldetest auf Android 12 kam ein Absturz zum Vorschein, der bis dahin ausgeliefert war.

- **Die Anmeldung stürzt nicht mehr ab, wenn das Gerät keine Bildschirmsperre hat** — sie sagt
  jetzt, dass eine eingerichtet werden muss. Vorher verschwand die App beim Tippen auf „Anmelden".
- **Die Fehlermeldung behauptet nicht mehr, die Administration sei informiert worden.** Das war
  nicht der Fall: die App meldet nichts nach außen. Sie nennt jetzt, was zu tun ist.

- **„Übergeben" heißt jetzt „Umbuchen"** — so wie im Webtool und im Designbild. Der Knopf unten
  nennt außerdem die Buchung, die er auslöst, statt immer „Ausbuchen".

- **Das Datum auf der Übersicht nennt jetzt beide Jahre:** „Mittwoch, 26.08.2026 (2956)" — das
  echte und das Star-Citizen-Jahr.

- **„Alle ansehen" und „Alle Einsätze" stehen jetzt in der Abschnittsüberschrift** statt als lose
  Zeile unter der Liste, und die Tablet-Leiste schreibt „Börse" statt „Materialbörse".

- **Wenn ein Speichern abgelehnt wird, weil jemand anderes den Eintrag inzwischen geändert hat,
  erscheint jetzt ein Hinweisfenster** statt einer leicht zu übersehenden Zeile unter dem Formular.
  „Neu laden" holt den aktuellen Stand; deine Eingabe bleibt bis dahin stehen.

- **Die Übersicht sieht aus wie entworfen:** Begrüßung in Orange und Versalien, Datum in Kurzform,
  die vier Schnellaktionen als 2×2-Raster mit ausgeschriebenen Beschriftungen („Einbuchen (Lager)"
  statt „Einbuchen"), eine ruhigere Statusmarkierung auf der Einsatzkarte und eine Zeitangabe unter
  jeder ungelesenen Meldung.

- **Ein Tipp auf das Symbol des Bereichs, in dem du schon bist, bringt die Liste wieder nach oben** —
  vorher blieb sie stehen, wo du warst.

- **Die Zahl der ungelesenen Benachrichtigungen steht jetzt oben in der Leiste** statt über der
  Liste — sie scrollt nicht mehr weg.
- **Zeitangaben lesen sich kürzer und über einen Tag hinaus verständlich:** „vor 4 Min.",
  „vor 2 Std.", „gestern, 21:14", „15.08., 09:30" — im Posteingang, in der Kartellbank und auf dem
  Dashboard gleich.

- **Bildschirme, die du aus „Mehr" öffnest, tragen oben keinen Org-Chip und keine Glocke mehr** —
  nur noch Zurück-Pfeil, Titel und was der Bildschirm selbst anbietet. Der Hangar-Titel wurde
  dadurch abgeschnitten, „Open-Source-Lizenzen" passt jetzt vollständig.

- **Der Org-Einheit-Reiter im Hangar ist jetzt eine Tabelle mit Kennzahlen darüber** — Schiffstyp,
  Anzahl und Fitted in Spalten statt als Fließtext unter dem Namen, mit „Schiffe" und „Fitted" als
  Summen obendrüber. Eine Zeile antippen zeigt die gefilterte Schiffsliste.

- **„Abmelden" in den Einstellungen fragt jetzt nach.** Ein Danger-Dialog nennt, was es kostet: Die
  Sitzung endet, der gespeicherte Anmelde-Schlüssel wird vom Gerät gelöscht, und die nächste
  Anmeldung läuft wieder über das Anmeldeformular im Browser. Auf den Sperr- und Freigabe-Bildschirmen
  bleibt „Abmelden" absichtlich ohne Rückfrage — dort ist es der einzige Weg weiter.

- **Zustands- und Sperrbildschirme sehen aus wie im Entwurf.** „GESPERRT", „FREIGABE AUSSTEHEND",
  „NUTZUNGSBEDINGUNGEN" und die Fehlermeldungen stehen jetzt in Versalien, wie jedes Kapitel des
  Entwurfs sie zeichnet; die Freigabe-Ansicht setzt deinen Kontonamen in ein hervorgehobenes Feld.
- **Der Offline-Hinweis ist ein Banner statt einer grauen Zeile** — gelbe Kante, Funk-aus-Symbol und
  der Grund darunter. Auf einem Bildschirm, dessen Knöpfe gerade grau geworden sind, war die
  Erklärung bisher das Unauffälligste.
- **In der Statusleiste steht jetzt das Basetool-Zeichen**, nicht mehr eine allgemeine Glocke.
- **Die Bank-Gesamtsumme hat ihren orangen Balken zurück** — er wurde bisher gar nicht gezeichnet —
  und „Einchecken" ist grün, weil Grün im Entwurf genau für diesen Schritt reserviert ist.
- **Jedes Suchfeld mit Vorschlägen sieht jetzt wie eines aus.** Schiffstyp, Ort, Material: die
  Treffer stehen in einer abgesetzten Liste mit orangem Rahmen, der eingetippte Teil ist fett, und
  am Feld sitzt ein Pfeil. Bisher waren es schlichte Textzeilen unter dem Feld. Die Liste öffnet
  sich beim Tippen und schließt sich bei der Auswahl, statt danach offen stehen zu bleiben.
- **Der Finanz-Eintrag eines Einsatzes zeigt die Richtung.** „+ Einnahme" ist grün, „− Ausgabe" rot,
  und der Betrag ist in derselben Farbe groß und rechtsbündig. Getippt wird auf einem Zahlenfeld;
  das Vorzeichen kommt aus der Auswahl darüber, nicht aus der Eingabe.
- **Die Operation-Ansicht führt mit deinem Anteil.** Name und Status stehen oben in der Leiste, dein
  Anteil darunter in einem hervorgehobenen Feld samt „offen" oder „ausgezahlt", und die Einsätze
  stehen mit vorzeichenbehaftetem Ergebnis vor der Summe, die sie bildet. Eine bestätigte
  Auszahlung zurückzunehmen fragt jetzt nach und nennt, was das bedeutet.
- **„Gesuch erstellen" auf der Materialbörse** hat die Beispiele und Beschriftungen aus dem Entwurf,
  Menge und Mindestqualität stehen nebeneinander, und es gibt einen Abbrechen-Knopf.
- **In „Mein Inventar" steht die Einheit neben der Menge**, der Name ist hervorgehoben, und
  Bearbeiten und Löschen sind zwei kleine Schaltflächen statt eines breiten „LÖSCHEN".

- **Einsatz- und Auftrag-Detail sehen aus wie vorgesehen.** Der Einsatz zeigt jetzt oben seinen
  Namen mit Status, eine Faktenzeile (TS · Join · Ort · Leiter), wie viele angemeldet und
  eingecheckt sind, und „Einsatz auf einen Blick" mit sechs Angaben über der Beschreibung; die
  Anmelde-Schaltfläche sitzt fest am unteren Rand. Der Auftrag hat Reiter statt einer langen Spalte.
- **Die Raffinerie-Karte nennt ihre Waren** samt Qualität und Menge und daneben den geschätzten
  Wert. Bisher stand da nur, dass es die Order gibt.
- **Die Bank zeigt eine Gesamtsumme** über der Kontenliste, und das Konto-Detail hebt den
  Kontostand hervor; die 30-Tage-Änderung ist grün oder rot statt grau.
- **Im Lager steht neben jeder Qualität ein kleiner Balken**, damit „Q 874" auf einen Blick als hoch
  lesbar ist.
- **Beim Einbuchen lässt sich die Menge schrittweise ändern**, die Qualität steht daneben statt
  darunter, und der Knopf heißt nach dem, was er tut — „Einbuchen" statt „Buchen".
- **Der Schiff-Editor fragt zuerst nach dem Typ, dann nach dem Namen.** Versicherung und Ort stehen
  nebeneinander, und bei „Fitted" steht jetzt dabei, was das zusagt.
- **Die Open-Source-Lizenzen sind eine richtige Seite geworden.** Oben steht, wie viele Artefakte und
  Lizenzen der Bericht umfasst und zu welchem Build er gehört; die Lizenz-Überschrift bleibt beim
  Scrollen stehen und lässt sich antippen, um ihre Softwareliste ein- und wieder auszuklappen. Am
  Ende steht, dass der Bericht zu Ende ist. Ohne Browser auf dem Gerät wird die Lizenz-Adresse
  kopiert statt ins Leere getippt.
- **Die Übersicht hat Schnellaktionen.** Vier Kacheln — Check-In, Einbuchen, Auftrag, Angebot — führen
  dorthin, wo die jeweilige Aktion zu Hause ist. Die Einsatz-Kachel nennt außerdem, wie lange es noch
  hin ist („In 1 Stunde · TS 22:33"), und rechnet das jede Minute nach.
- **Der Statuswechsel am Auftrag fragt jetzt nach, bevor er endgültig wird.** Du wählst den Status
  aus und bestätigst ihn — „Abgeschlossen" und „Abgelehnt" lassen sich in der App nicht zurücknehmen,
  und das steht jetzt dran, statt danach aufzufallen.
- **Im Hangar steht der Hersteller als Kürzel-Kachel vorne in der Zeile**, der Ort hinter einer
  Kartennadel, und die Versicherung nennt ihre Einheit statt nur einer Zahl. Bearbeiten und Löschen
  sind Symbolknöpfe, statt „LÖSCHEN" quer über jede Karte zu legen.
- **Ist die Freigabeprüfung nicht erreichbar, versucht die App es von selbst weiter** — nach 3, 6, 12
  und dann 30 Sekunden, mit sichtbarer Restzeit; ein eigener Versuch setzt den Rhythmus zurück. Ein
  laufender Versuch sagt das und wird nach 10 Sekunden abgebrochen, und ab dem dritten Fehlversuch
  steht eine Zeile da, die auf den Org-Discord verweist.
- **Die Versionszeile nennt zusätzlich die API-Version.**

- **Ist die Freigabeprüfung nicht erreichbar, versucht die App es von selbst weiter.** Der Bildschirm
  zeigt jetzt, als wer du angemeldet bist und in wie vielen Sekunden der nächste Versuch läuft
  (3, 6, 12, dann 30 Sekunden); ein eigener Versuch setzt den Rhythmus zurück. Vorher blieb nur der
  Knopf.
- **Listen sehen wieder aus wie im Entwurf: Kacheln statt Textzeilen.** Übersicht, Bank, Hangar,
  Lager, Einsätze, Operationen, Aufträge, Raffinerie, Materialbörse, Mein Inventar und Blueprints
  zeigen ihre Einträge jetzt als umrandete Kacheln mit Abstand. Der Posteingang und der Lager-Baum
  bleiben bewusst Zeilen — so sieht der Entwurf sie vor.
- **Eine Notiz am Auftrag geht bei gleichzeitiger Bearbeitung nicht mehr verloren.** Hat jemand
  anders zuerst gespeichert, zeigt die App jetzt den aktuellen Stand und daneben deine abgelehnte
  Fassung mit „Meine Fassung übernehmen" — statt nur einer Fehlermeldung. Dazu ein Zeichenzähler.
- **Im Lager steht jetzt bei jedem Eintrag, wo er liegt.** Bisher waren zwei Bestände desselben
  Materials an verschiedenen Orten nicht auseinanderzuhalten. Gruppen zeigen außerdem einen Pfeil,
  der anzeigt, dass sie sich öffnen lassen.
- **Eine Auftragsposition nennt ihre Zusagen.** Wie viele Mitglieder bereits zugesagt haben, stand
  nirgends.
- **Die Einsatz-Kachel auf der Übersicht zeigt jetzt, worum es geht.** Kurzbeschreibung, Treffzeit,
  Treffpunkt, die zuständige Einheit und ein „Öffnen" — vorher standen dort nur Name und Status.
- **Die Reiter im Einsatz-Detail zeigen ihre Anzahl.** Teilnehmer, Einheiten, Ablauf, Ziele und
  Frequenzen nennen die Zahl, ohne dass du den Reiter öffnen musst.
- **Die Kontokarte in der Bank zeigt den Saldo groß, darunter Veränderung und Verlauf.** Die
  30-Tage-Veränderung ist jetzt grün oder rot statt grau.
- **Die Auftragsübersicht zeigt wieder das, was sie zeigen soll.** Jeder Auftrag steht jetzt als
  Kachel da — mit Prio-Block, Art (Material/Item), Status, farbig markiertem Alter und beiden
  beteiligten Einheiten als Abzeichen. Vorher war das eine Textzeile ohne Art und ohne Alter.

- **Die App wurde Bild für Bild gegen die Design-Vorlagen gestellt.** Statusmarken schreien nicht
  mehr in Grossbuchstaben, ein Spezialkommando ist an seiner eigenen Farbe zu erkennen,
  Tabellenspalten stehen unter ihren Überschriften, und Zahlen tragen überall dieselbe Schrift.
- **Auf dem Tablet nimmt die Detailspalte zwei Drittel der Breite ein** statt gut der Hälfte — im
  Einsatz standen die acht Reiter sonst nicht nebeneinander.
- **„Operationen" steht bei den Einsätzen statt unter „Mehr"** und zeigt dort dieselbe Liste mit
  Detailspalte.
- **Ohne Verbindung sagt das die Anmeldeseite, bevor du tippst** — Banner oben, „Anmelden"
  ausgegraut samt Grund.

### Removed

- **Der Menüpunkt „Beförderung" ist vorerst raus.** Er führte auf „Dieser Bereich wird gerade
  gebaut." — ein Eintrag, hinter dem nur eine Entschuldigung steht, ist schlechter als kein Eintrag.
  Er kommt zurück, wenn die Ansicht wirklich fertig ist.

### Fixed

- **Eine abgelehnte Eingabe sagt jetzt, was falsch war.** Elf Schreibmasken zeigten „Konnte nicht
  gespeichert werden." und verschwiegen damit den Satz, den der Server längst mitschickt — er nennt
  das Feld und die Regel („Menge muss größer als 0 sein."). Aufgefallen beim Gerätedurchgang: eine
  Frequenz liess sich nicht anlegen, und die App nannte den Grund nicht. Fünf Masken behalten ihren
  eigenen Satz, weil er einen Ausweg nennt, den der Server nicht kennt.

- **Die Zielart hieß „SECONDARY" statt „Sekundär".** Die Leseseite zeigte den Rohwert des Servers,
  während die Auswahl daneben das deutsche Wort nannte.

- **„ABMELDEN" brach in eine Spalte aus einzelnen Buchstaben um.** In der Aktionsleiste standen drei
  Dinge nebeneinander; die Auszahlungspräferenz ist keine Aktion und steht jetzt über der Leiste,
  beschriftet. Anmelden und Einchecken teilen sich die Leiste je zur Hälfte.

- **Die Unterebenen im Lager standen auf Schwarz statt auf der Tabellenfläche.** Nutzer-, Stapel-
  und Eintragszeilen fielen auf den Seitenhintergrund durch; laut Design liegt der Baum auf einer
  eigenen dunkelgrauen Fläche, und nur die Eintragszeile wird darüber abgedunkelt.

- **Item-Aufträge lassen sich jetzt in der App anlegen.** Das Auftragsformular hat oben einen
  Umschalter „Material / Items"; eine Item-Zeile ist Item, Blueprint und Anzahl. Hat ein Item nur
  einen Blueprint, ist er sofort gesetzt. Was das Formular noch nicht kann, ist der
  Unterbaugruppen-Baum des Webs.

- **Item-Aufträge zeigen ihre Positionen.** Bisher stand bei einem Item-Auftrag „Positionen 0" und
  darunter nichts — auch bei Aufträgen, die im Web angelegt wurden. Jetzt erscheinen Item, gebaute
  und bestellte Anzahl, der Fortschrittsbalken, die übergebene Menge und der Hinweis „Rezept
  veraltet". Die Auftragskarte klappt sie als „Items (n)" auf.

- **Jede Buchungszeile zeigt ihre Richtung als Pfeil.** Eingang zeigt nach unten und ist grün,
  Ausgang nach oben und rot, alles andere bekommt den neutralen Tausch-Pfeil. Der Abschnitt heißt
  wieder „Buchungen" statt „Transaktionen".

- **Die Menge steht auf der Börsenkarte jetzt vorn.** Rechts neben dem Material, die Zahl betont,
  die Einheit leise — statt als erstes Drittel einer grauen Zeile, in der Menge, Qualität und
  Zusagen gleich aussahen.

- **Das Herstellerkürzel im Hangar stimmt wieder.** Es kommt jetzt aus dem Katalog (DRAK, MISC,
  RSI) statt aus den Anfangsbuchstaben des Firmennamens — MISC stand dort als „MIA". Außerdem steht
  die Versicherung vor dem Ausbau-Zustand und trägt einen neutralen Ton; nur eine benannte Police
  wie „LTI" wird hervorgehoben.

- **Die beiden Knöpfe über dem Posteingang tragen ihre Symbole** — Haken und Papierkorb, dieselben
  wie an jeder Zeile — und teilen sich die Breite gleichmäßig, statt dass der kürzere Text mitten im
  Wort umbricht.

- **Die Übersicht trifft ihr Artboard.** Die Begrüßung steht jetzt in einem Block mit orangem
  Rand statt als loser Text, und die vier Schnellaktionen tragen die Glyphe ihrer Aktion statt die
  ihres Ziels. Der Lager-Knopf „Einbuchen" bekommt dieselbe Pfeil-Glyphe zurück.

- **Auf dem Tablet nennt die Kopfzeile wieder den Bereich.** Wer einen Eintrag auswählte, bekam
  dessen Namen in die Leiste geschrieben, während die Seitenleiste weiter den Bereich hervorhob —
  beide sagten etwas anderes darüber, wo man ist. Der Eintrag steht jetzt über seinem eigenen
  Detailbereich.

- **Der offene Tab ist wieder als solcher zu erkennen.** Die orange Unterstreichung unter dem
  aktiven Tab wurde in der ganzen App nicht gezeichnet. Ebenfalls überall: die Umschalter
  („Mitglied / Verwaltung", „Meine Schiffe / Org-Einheit" …) stehen jetzt wie im Design in
  Großbuchstaben.

- **Bei großer Schrift (1,3×) wurden Beschriftungen abgeschnitten** — „CHECK-IN NÄCHSTER EI…" — und
  Filter brachen mitten im Wort („ABGEB ROCHE N"). Beides wächst jetzt sauber um.
- **Zeitangaben waren in Einsätzen, Aufträgen und der Materialbörse noch in der alten Langform**
  („Vor 20 Stunden"); sie folgen jetzt überall derselben Kurzform.

- **Der Benachrichtigungskanal „Einsätze & System" wurde entfernt** — er stand in den
  Systemeinstellungen, aber es kam nie etwas darauf an. Alle Hinweise laufen über „Allgemein".

- **Ein Link, den diese App-Version nicht kennt, landete stillschweigend auf der Übersicht** — nicht
  zu unterscheiden von einem Link, der funktioniert hat. Jetzt erscheint „Signal Lost" mit dem Weg
  zurück zur Basis.

- **Ein Tipp auf eine Benachrichtigung beendete die App**, statt den Posteingang zu öffnen — sie
  verschwand kommentarlos zum Startbildschirm. Der Weg vom Systemhinweis in die App funktioniert
  jetzt, und der Zurück-Schritt führt von dort auf die Übersicht.

- **Mehrere Lager-Einträge auf einmal umbuchen.** Langes Drücken wählt eine Zeile aus, weitere
  Zeilen kommen mit einem Tipp dazu, und unten erscheint eine Leiste mit „Umbuchen" — ein Ort für
  alle Ausgewählten. Langes Drücken auf eine Gruppe oder einen Stapel nimmt alles darunter auf
  einmal, jede Zeile trägt ein Kästchen, und die Gruppe zeigt „1/3 gewählt". Einklappen ändert nur
  die Ansicht, nicht die Auswahl. Danach sagt das Blatt, was passiert ist — umgebucht und
  übersprungen als zwei Zahlen, mit einem Satz dazu, dass Einträge am Zielort kein Fehler sind.
  Lehnt der Server ab, bleibt die Auswahl stehen.

- **Lagerbestand lässt sich Aufträgen und Einsätzen zuordnen.** Über das Ziel-Symbol an einem
  Eintrag: Aufträge und Einsätze werden getrennt gegen die Menge abgeglichen, der Rest steht als
  Zahl daneben und wird rot, sobald mehr zugesagt ist als vorhanden. Persönliche Einträge tragen
  keine Zuordnung.
- **Der Hangar kann eine Fleetview-Datei importieren.** Hinter dem neuen `⋮` oben rechts: „Fleetview-Import"
  nimmt eine JSON-Datei oder eingefügten Text und legt die Schiffe an; danach steht da, wie viele
  importiert wurden, wie viele schon im Hangar waren und welche der Server nicht erkannt hat.
  Erkannt werden CCU-Game-Fleetview, HangarXPLOR-Shiplist und Fleetyards-JSON.
- **Zwei weitere Hangar-Aktionen im selben Menü:** „Home-Location setzen" ändert den Ort für die
  ganze Flotte auf einmal, „Hangar leeren" löscht alle Schiffe. Beide sagen jetzt vorher, wie viele
  Schiffe betroffen sind, und was nicht angetastet wird; nach dem Leeren steht da, wie viele
  gelöscht wurden. Einträge, die gerade nicht gehen, bleiben im Menü stehen und nennen den Grund.
- **„Alle Org-Einheiten" im Umschalter.** Wer mehreren Staffeln oder einem Spezialkommando angehört,
  kann jetzt alle auf einmal sehen statt nur eine — die Wahl bleibt über einen Neustart erhalten.
  Der Umschalter benennt außerdem, um was für eine Einheit es geht („IRI — IRIDIUM", „SK Vanguard"),
  damit gleichnamige Staffeln und Spezialkommandos unterscheidbar sind.
- **Aktionen, die dir fehlende Rechte verwehren, sagen das jetzt.** Sie bleiben sichtbar, tragen ein
  Schloss und antworten beim Antippen mit der Rolle, die dir fehlt — „Dafür brauchst du die Rolle
  Logistiker." statt einer stummen grauen Schaltfläche oder einer 403 nach dem Absenden. Fremde
  Lager-Einträge nennen stattdessen die Regel, nach der nur die eigene Zeile dir gehört.

- **„Mehr" führt wieder ins Menü.** Warst du auf Bank, Hangar oder den Lizenzen, passierte beim
  Tippen auf „Mehr" nichts — ausgerechnet der Knopf, der dich da wieder herausbringen soll.

## [0.1.3] — 2026-08-25

### Fixed

- **Die App-Sperre wirft dich nicht mehr aus der Anmeldung.** Mit eingeschalteter Sperre führte
  jedes Beenden der App dazu, dass nach dem Entsperren die Anmeldeseite kam — obwohl die Sitzung
  noch gültig war und auf dem Gerät lag. Ursache war eine Abfrage vor dem Entsperren, die „noch
  gesperrt" nicht von „nicht angemeldet" unterscheiden konnte.
- **Eine kurz nicht erreichbare Verbindung fragt nicht mehr nach dem Passwort.** Konnte die Sitzung
  beim Start nicht bestätigt werden, zeigte die App die Anmeldeseite ohne jeden Hinweis. Jetzt steht
  dort, dass du weiterhin angemeldet bist, mit „Erneut versuchen".

## [0.1.2] — 2026-08-25

### Fixed

- **Live-Sync gibt nicht mehr für die ganze App auf.** Lehnt der Server die Verbindung als
  fehlerhaft ab, fragte die App bis zum Beenden im Sekundentakt weiter — und blieb dabei auf jedem
  Bildschirm stumm, ohne dass etwas darauf hingewiesen hätte. Jetzt hört sie nach zwei Versuchen auf
  und die betroffenen Bildschirme laden wieder selbst nach.

## [0.1.1] — 2026-08-25

### Changed

- **Screenshots lassen sich jetzt in den Einstellungen freigeben.** Unter „App" steht neben der
  App-Sperre der Schalter „Screenshots erlauben" — aus, solange ihn niemand einschaltet. Das war
  bisher gar nicht möglich und hat Tester daran gehindert, einem Fehlerbericht ein Bild beizulegen.
  Wer ihn einschaltet, zeigt damit auch in der App-Übersicht des Systems seine Daten (#81).

## [0.1.0] — 2026-08-25

Erste Veröffentlichung der Android-App.

### Added

- **Das Tablet nutzt die Breite jetzt so, wie es der Entwurf vorsieht.** Einsätze, Aufträge, Raffinerie und Bank zeigen die Liste neben dem Detail; die Übersicht wird zweispaltig, der Posteingang bekommt eine schmalere Lesespalte, die Nutzungsbedingungen stehen neben ihrer Zustimmungsleiste, und Blueprints zeigen rechts das Rezept mit der geforderten Qualität je Zutat. Auf dem Telefon ändert sich nichts.
- **Der Hangar zeigt auf dem Tablet die volle Tabelle statt Karten.** Dieselben Spalten wie im Browser — Typ, Name, Versicherung, Ort, Ausgebaut — und ein Klick auf die Zeile öffnet den Editor wie auf dem Telefon.
- **Die Haupt-Aktion einer Liste sitzt jetzt als quadratische Schaltfläche unten rechts.** Hangar, Lager, Mein Inventar und Materialbörse hatten sie in einer Kopfzeile, die auf dem Telefon Suche und Filter nach unten schob, bevor ein einziges Ergebnis zu sehen war.
- **Der Posteingang lässt sich jetzt bedienen.** Einzelne Benachrichtigungen als gelesen markieren oder löschen — per Wischgeste oder über die Knöpfe in der Zeile — dazu „Alle als gelesen markieren" und „Gelesene löschen". Ein Löschen lässt sich fünf Sekunden lang rückgängig machen (REQ-APP-NOTIF-011, REQ-APP-NOTIF-012).
- **Die App erfährt jetzt, wenn ihre Version nicht mehr bedient wird.** Ist der Build zu alt, erscheint „Update erforderlich" mit dem Weg zur Download-Seite statt eines unverständlichen Fehlers. Ohne Antwort vom Server läuft die App normal weiter, und gespeicherte Daten bleiben in jedem Fall erhalten (REQ-APP-UI-004).

- **Die Materialbörse ist in der App.** Angebote und Gesuche org-weit, „Ich kann liefern" als Umschalter mit jederzeitiger Rücknahme, eigene Einträge mit „Zurückziehen", und beide Erstellen-Dialoge — das Angebot mit Vorschlägen aus dem eigenen Lagerbestand. Übergabe und Ort bleiben wie im Web außerhalb des Tools.

- **Die Raffinerie ist in der App.** „Meine Orders" mit den Filtern In Arbeit, Abholbereit und Eingelagert, die Restzeit läuft auf dem Gerät minütlich weiter, und im Detail legt „In Lager buchen" pro Material einen Lager-Eintrag mit Qualität an. Der Extractor-Import aus Design-Kapitel 11 kommt später mit den übrigen Datei-Importen.

- **Ein ausgelasteter Server bekommt auf jedem Bildschirm einen Countdown statt einer Fehlermeldung.** Die App versucht es automatisch erneut — 3, 6, 12, dann alle 30 Sekunden — und zeigt die verbleibende Zeit; bei einer Drosselung hält sie sich an die Wartezeit, die der Server nennt. „Jetzt erneut versuchen" setzt den Rhythmus zurück. Bereits geladene Inhalte bleiben stehen (REQ-APP-UI-003).

- **Benachrichtigungen erscheinen jetzt auch in der Statusleiste** — solange die App läuft. Auf dem Sperrbildschirm steht dabei nie mehr als „Neue Benachrichtigung". Ohne Push-Dienst erreicht dich das nicht bei geschlossener App (REQ-APP-NOTIF-010).

- **Die App aktualisiert sich jetzt live, und andere sehen deine Änderungen sofort.** Ändert jemand im Browser einen Einsatz, einen Auftrag oder das Lager, zieht die App den betroffenen Bereich nach — ohne Neuladen und ohne dass die Anzeige leer blinkt. Umgekehrt aktualisiert eine Buchung aus der App jede offene Browser-Ansicht. Lager, Einsatz-Detail, Einsatz- und Auftragsliste, Auftrags-Detail, Operationen und die Bank sind angebunden; Materialbörse, Raffinerie und Beförderung folgen mit ihren Screens (REQ-APP-SYNC-001…005).

- **Konten der Kartellbank lassen sich jetzt in der App einstellen.** Zielsaldo setzen und regeln, wer das Konto sieht — sichtbar nur für die Person, die für das Konto verantwortlich ist. Ein- und Auszahlungen bleiben der Weboberfläche vorbehalten (REQ-APP-BANK-006, REQ-APP-BANK-007).

- **Einnahmen und Ausgaben eines Einsatzes lassen sich jetzt in der App buchen.** Betrag, Richtung und ein Wofür — änderbar und löschbar, solange es deine eigene Buchung ist. Buchen setzt voraus, dass du für den Einsatz angemeldet bist. Einsatzleiter können in der Operation außerdem Auszahlungen bestätigen (REQ-APP-MIS-017, REQ-APP-OPS-013).

- **Für Einsätze kannst du dich jetzt in der App an- und abmelden.** Dazu Ein- und Auschecken, sobald der Einsatz läuft, und die Wahl zwischen Auszahlung und Spende. Dein eigener Eintrag ist in der Teilnehmerliste hervorgehoben. Die Einsatzplanung bleibt der Weboberfläche vorbehalten (REQ-APP-MIS-013…016).

- **Aufträge lassen sich jetzt aus der App übernehmen.** „Übernehmen" setzt dich auf den Auftrag, „Abmelden" nimmt dich wieder herunter, und zu deinem Eintrag gehört eine eigene Notiz — wann du arbeitest, welchen Teil du nimmst. Logistiker können zusätzlich den Status ändern. Übergaben und die übrige Bearbeitung bleiben der Weboberfläche vorbehalten (REQ-APP-ORDERS-009…012).

- **Das Lager lässt sich jetzt aus der App buchen.** Die Bestände einer Gruppe öffnen sich bis zum einzelnen Eintrag; von dort geht Einbuchen, Ausbuchen (verwerfen, übergeben, verkaufen) und Notiz ändern. Beim Verkaufen zeigt die App die Terminals des Materials mit ihrem Preis. Nach jeder Buchung bleibt der geöffnete Pfad offen und zeigt das Ergebnis. Persönliche Bestände, Mehrfachauswahl und Sammel-Umbuchung bleiben der Weboberfläche vorbehalten (REQ-APP-INV-007…012).

- **Der Hangar lässt sich jetzt aus der App pflegen.** Schiffe anlegen, ändern und löschen: Typ über eine Suche, Versicherung als LTI oder Monatszahl, Ort und Fitted-Zustand. Nur die eigenen Schiffe — die Org-Ansicht bleibt eine Übersicht. Der Import bleibt Phase 4 (REQ-APP-HANGAR-006…009).

- **Die Blueprints sind da — der zweite Reiter von „Mein Inventar".** Was dir gehört, mit einem Chip, der sagt, ob es baubar ist oder welche Materialien fehlen; ein Schalter rechnet die Raffinerie mit. Hinzufügen über die Produktsuche, Notiz ändern, entfernen. Was du schon hast, bietet die Suche nicht noch einmal an, und was der Server nicht freigibt, bekommt keinen Entfernen-Knopf (REQ-APP-PI-008…012).

- **„Mein Inventar" ist da — und die App kann zum ersten Mal schreiben.** Eigene Bestände anlegen, ändern und löschen, mit Ortssuche, Mengen-Stepper und Notiz. Nur für dich sichtbar; niemand sonst sieht diese Liste. Blueprints folgen im nächsten Schnitt, der Datei-Import in Phase 4 (REQ-APP-PI-001…006).

- **Ohne Netz sind Schreib-Aktionen gesperrt statt in einer Warteschlange.** Die Knöpfe bleiben sichtbar und ausgegraut, mit einer Zeile, die sagt warum — eine später abgeschickte Änderung würde der Server ohnehin ablehnen, weil ihr Stand veraltet ist. Dafür kommt die Berechtigung „Netzwerkstatus lesen" dazu; sie liest nur, ob überhaupt eine Verbindung besteht, und schickt nichts.

- **Der Eintrags-Dialog lässt sich scrollen.** Mit gewähltem Ort und offener Tastatur lagen „Abbrechen" und „Speichern" sonst unter dem Bildschirmrand — der Eintrag ließ sich nicht speichern.

- **Gleichzeitige Änderungen sagen es dir, ohne deine Eingabe wegzuwerfen.** Hat jemand anderes denselben Eintrag zwischenzeitlich geändert, bleibt alles Getippte stehen und der Dialog erklärt, was zu tun ist.

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

- **Die App-Sperre schützt jetzt auch die gespeicherte Anmeldung, nicht nur den Bildschirm.** Bisher lag das gespeicherte Sitzungs-Token hinter dem Sperrbildschirm, war aber technisch auch ohne Entsperren lesbar. Es liegt nun zusätzlich unter einem Schlüssel, den das Gerät erst nach Fingerabdruck, Gesicht oder Gerätesperre herausgibt. Wer die Sperre aus- oder einschaltet, bleibt angemeldet; ist die Sperre aktiv und noch nicht geöffnet, bleibt die gespeicherte Anmeldung unangetastet liegen statt gelöscht zu werden (REQ-APP-AUTH-010).

- **Entwickler-Builds erreichen jetzt das Backend des lokalen Teststacks — ohne Einrichtung.** Dessen Zertifikat scheiterte bisher an der TLS-Prüfung, und zwar ohne erkennbare Ursache: Die App meldete schlicht „keine Verbindung", während der Server auf demselben Rechner lief. Der Teststack bringt jetzt ein gemeinsames Zertifikat mit, das der Entwickler-Build kennt; das früher nötige Einrichten eines eigenen Zertifikats im Emulator entfällt — es ließ sich auf den gebräuchlichen Emulator-Abbildern gar nicht automatisieren. Release-Builds sind davon unberührt: Die Ausnahme steht in einem Bereich, den Android nur für Debug-Builds beachtet, und das Zertifikat liegt ausschließlich im Entwickler-Flavour (REQ-APP-AUTH-011).

- **Die Nutzungsbedingungen erscheinen jetzt in der App und lassen sich dort annehmen.** Wer noch nicht zugestimmt hat, bekommt nach der Anmeldung den vollständigen Text zu lesen, mit Kästchen zum Bestätigen und einem Weg zum Ablehnen — der meldet ab und sagt das vorher deutlich. Der Text kommt dabei vom Server und steckt nicht in der App: So kann die App nie eine andere Fassung anzeigen als die, für die die Zustimmung gespeichert wird. Lässt sich der Text nicht laden, erscheint eine Fehlermeldung statt einer leeren Seite — einer leeren Seite zuzustimmen wäre keine Zustimmung (REQ-APP-AUTH-009).
- **Die App lässt sich jetzt sperren, und Bildschirmfotos sind app-weit unterbunden.** Wer will, schaltet in den Einstellungen eine Sperre ein: Beim Start und nach fünf Minuten im Hintergrund fragt das System per Fingerabdruck, Gesicht oder Gerätesperre nach, bevor die App wieder etwas zeigt. Kurzes Wechseln zu einer anderen App sperrt nicht — sonst wäre die Funktion im Alltag unbrauchbar. Der Sperrbildschirm zeigt bewusst keinerlei Daten, auch keine Zähler. Unabhängig davon verhindert die App ab sofort überall Bildschirmfotos und Bildschirmaufnahmen und hält damit auch die Vorschau in der App-Übersicht leer. Ohne eingerichtete Gerätesperre bleibt die Einstellung sichtbar, aber abgeschaltet.

- **Wer noch nicht freigegeben ist, sieht das jetzt — statt einer Wand aus Fehlern.** Nach der Anmeldung prüft die App, ob das Konto von der Administration freigeschaltet ist, und zeigt andernfalls einen eigenen Wartebildschirm mit dem Kontonamen, einer Schaltfläche zum Aktualisieren und dem Weg zum Abmelden. Der Status wird alle 60 Sekunden automatisch neu geprüft; sobald die Freigabe kommt, geht es ohne Zutun weiter. Abgelehnte Konten bekommen denselben Bildschirm mit eigener Formulierung. Ist der Status gar nicht abfragbar, sagt die App genau das — und behauptet nicht, das Konto warte auf Freigabe.

- **Die App hat ein Symbol.** Der Startbildschirm zeigt jetzt das Basetool-Zeichen (orange auf schwarz) statt des Android-Platzhalters — als anpassungsfähiges Symbol, das sich jeder Launcher-Form fügt, mit eigener einfarbiger Variante für die Design-Symbole von Android 13+. Auf dem Tablet trägt auch die Seitenleiste das Zeichen statt der Kartell-Marke.

- **Die App baut: Gradle-Gerüst, KRT-Theme und Komponentenbibliothek stehen.** Zwei Module (`app`, `core:designsystem`) auf AGP 9.3 / Kotlin 2.4 / Compose Material 3, minSdk 29 und targetSdk 37. Enthalten sind das vollständige Design-Token-Set (Farben, Lato-Typografie, eckige Formen, Abstände), die Komponenten aus Kapitel 02 der Design-Spezifikation (Button-Leiter, HUD-Box und Karten, Chips und Status-Anzeigen, Listenzeilen, Formularfelder, Modal/Toast/Sheet, Lade-, Offline- und Leerzustände, KPI-Kacheln), 63 aus dem Design-Sprite erzeugte Vektor-Icons sowie die mitgelieferten Lato-Schriften samt OFL-Lizenztext. Eine Showcase-App zeigt alle Komponenten zum Abgleich mit den Design-Referenzen.

- **Navigationsgerüst steht (Kapitel 03).** Die App hat jetzt eine echte Navigation statt der Komponenten-Galerie: obere Leiste mit Bildschirmtitel, Org-Einheit-Chip und Glocke mit Zähler, auf dem Handy eine Leiste mit fünf Zielen (Übersicht, Einsätze, Aufträge, Lager, Mehr), auf dem Tablet eine Seitenleiste mit acht Zielen. „Mehr" führt zu den übrigen Bereichen. Jedes Ziel behält seinen eigenen Verlauf; ein erneuter Tipp auf das aktive Ziel springt an dessen Anfang, Zurück führt von jedem Ziel auf die Übersicht und von dort aus der App. Jeder Bereich ist zusätzlich per Deep Link (`basetool://…`) erreichbar. Die Bereichsinhalte selbst folgen mit den nächsten Kapiteln.

- **Komponentenbibliothek vervollständigt.** Ergänzt wurden die zunächst fehlenden Abschnitte aus Kapitel 02 der Design-Spezifikation: Datentabellen für Tablets samt Zusammenfall zur Datensatz-Karte auf dem Handy, die Auswahl-Bausteine (Type-to-Filter-Combobox mit Treffer-Hervorhebung und „x von y"-Hinweis, Auswahlfeld, eckige Checkbox, Radio, Chip-Auswahl), das Bottom Sheet als Container und der Hinweis-Tooltip für Fachregeln. Der orangene Leuchteffekt rendert jetzt weich statt in sichtbaren Stufen.

- **Fan-Kit-Konformität ist testgesichert.** Logo und Markenhinweis sind eine untrennbare Komponente; vier Tests prüfen den wortgleichen englischen Hinweis in der Standard-, deutschen und englischen Sprachumgebung sowie das Mitliefern des unveränderten Artworks.

- **Verbindliche Design-Spezifikation aufgenommen.** Das vollständige Claude-Design-Handoff (Kapitel 00–14 mit allen Screens für Handy hochkant und Tablet quer, `Theme.kt`-Token-Mapping, Icon-Export-Liste, Lato-Fonts) liegt unter `docs/design/android/` und ist ab jetzt die bindende UI-Referenz; der Design-Prompt ist damit historisch. Alle Konzeptdokumente wurden darauf ausgerichtet (u. a. Copy-Regeln „Einsätze"/„Bereich Profit"/„Administration", Fan-Kit-Platzierung fix auf Login + Einstellungen, FLAG_SECURE app-weit).

- **Projektgerüst angelegt.** Die fünf freigegebenen Konzeptdokumente (Masterplan mit den Entscheidungen Q1–Q7, Sicherheitskonzept, Datenschutz-Analyse, Entwicklungs-/CI-Konzept, Design-Prompt) unter `docs/`, die verbindlichen Projektregeln (`CLAUDE.md`), das geplante Modul-Skelett, `.gitignore` und die GPL-3.0-Lizenz.

- **Community- und Rechtsdokumente übernommen.** Code of Conduct (Contributor Covenant 3.0), eigenes Contributor License Agreement (CLA v1.0, wirksam 17.08.2026) mit öffentlichem Signatur-Roster unter `docs/cla-signatures.md`, ein Beitragsleitfaden (`CONTRIBUTING.md`) mit DCO-Sign-off-Pflicht für jeden Commit sowie eine Security-Policy (`.github/SECURITY.md`) mit privatem Meldeweg, App-spezifischem Scope und Safe-Harbor-Zusage.

- **Star-Citizen-Fan-Kit-Compliance verankert.** Das „Made By The Community"-Logo und der vorgeschriebene CIG-Markenhinweis sind als gekoppeltes Paar verbindlich geregelt (CLAUDE.md, Design-Prompt: Pflichtplatzierung auf dem Login-Screen); das unveränderte Logo-Artwork liegt unter `core/designsystem/fankit/`, der Markenhinweis steht zusätzlich im README.

### Changed

- **Die Navigation ist jetzt übersetzbar.** Die Bezeichnungen der Bereiche standen fest im Code
  und wären auf Englisch deutsch geblieben — also ausgerechnet auf der größten Fläche der App.
  Fachbegriffe bleiben auch im Englischen deutsch (Einsätze, Aufträge, Lager, Raffinerie,
  Materialbörse, Beförderung) — sie sind die Sprache der Staffel, keine zu übersetzenden Wörter
  (REQ-APP-SET-003).

- **Die App setzt jetzt Android 11 voraus (vorher Android 10).** Android 10 bot für die App-Sperre nur einen schwächeren Schlüsseltyp an, der einen zweiten, kaum benutzten Sonderweg im Code nötig machte — und dieser Sonderweg funktionierte nicht: Auf Android 10 ließ sich die Sperre weder einschalten noch öffnen, und die App meldete sie fälschlich als dauerhaft unbrauchbar. Statt ihn zu reparieren ist er entfallen. Auf Android 11 und neuer bestätigt jede Abfrage jetzt genau den Vorgang, den sie freigibt. Geräte mit Android 10 können die App nicht mehr installieren und nutzen weiterhin die Web-Anwendung.

- **Sicherheitskonzept nach Code-Audit des Basetool-Backends nachgeschärft.** Die Konzeptdokumente übernehmen die verifizierten Ergebnisse einer Code-Analyse des Haupt-Repos: Die API-Exposition bekommt eine Default-Deny-Allowlist statt einer Blockliste, das Backend braucht vor der Freischaltung den Right-to-Left-`X-Forwarded-For`-Walk und einen eigenen Management-Port, Audience-Enforcement wird Release-Gate, und ein Keycloak-Härtungspaket (Event-Logging, S256-Client-Policy, Token-Endpoint-Budget) kommt in Phase 0 dazu. App-seitig neu: kein OkHttp-Disk-Cache, `setUnlockedDeviceRequired` für den Token-Schlüssel, server-synchronisierte DPoP-Uhrzeit, Mindestversions-Gate, CA-Pin als bevorzugte Pinning-Variante sowie Gradle Dependency Verification und Release-Provenance in der CI.

### Fixed

- **Benachrichtigungen in der Statusleiste kamen bei niemandem an.** Die App hatte die Android-Berechtigung dafür nie angefragt, sondern nur geprüft — und damit still nichts angezeigt. Sie fragt jetzt einmal, nachdem du drin bist.

- **Sicherheits-Durchsicht vor dem ersten Release.** Serverseitige Fehlertexte landen nicht mehr im Geräte-Log, die Download-Adresse der Update-Seite wird geprüft, ein fremdes Programm kann eine laufende Anmeldung nicht mehr abbrechen, und die Android-Datensicherung ist ganz abgeschaltet — sie hätte ohnehin nichts gesichert.

- **Die Raffinerie rechnete die Erntemenge falsch um.** Der Server führt Mengen in Units (100 Units = 1 SCU); die App zeigte sie als SCU an und hätte beim Einlagern das Hundertfache gebucht. Zeitstempel in Raffinerie und Materialbörse erscheinen jetzt in deiner Zeitzone statt als Rohwert, und die App stürzt nach einer Buchung nicht mehr ab.

- **Negative Beträge sahen unterschiedlich aus.** In den Einsatz-Finanzen stand „−2.500" bei den Ausgaben und „-2.500" eine Zeile darunter beim Netto — zwei Zeichen für dieselbe Sache.

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

- **Die App stürzte ab, sobald der Bildschirm neu aufgebaut wurde** — etwa beim Drehen eines
  Tablets. Sie verschwand dabei kommentarlos zum Startbildschirm. Ursache war, dass die
  Anmelde-Verwaltung an den Bildschirm statt an die App gebunden war und der gespeicherte
  Anmelde-Speicher sich nicht zweimal öffnen lässt. Sie gehört jetzt der App: Beim Neuaufbau
  bleibt man angemeldet, eine offene App-Sperre bleibt offen, und eine laufende Anmeldung läuft
  weiter. Gefunden beim Umschalten der Sprache, die denselben Neuaufbau auslöst
  (REQ-APP-SET-008).
