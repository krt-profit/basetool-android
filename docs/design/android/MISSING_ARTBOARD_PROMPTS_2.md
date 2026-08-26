# Prompt an die Design-Session — Runde 2

Runde 1 ist [`MISSING_ARTBOARD_PROMPTS.md`](MISSING_ARTBOARD_PROMPTS.md) und geschlossen.

Alles unterhalb der Linie ist **ein** Auftrag, gedacht zum Einfügen in die bestehende Claude-Design-
Session, die das Bundle bereits im Kontext hat. Woher jeder Punkt kommt, steht in
[`docs/DESIGN_PARITY_AUDIT.md`](../../DESIGN_PARITY_AUDIT.md) — hier nicht, damit der Prompt
zusammenhängend bleibt.

---

Wir haben das gesamte Bundle Artboard für Artboard gegen die laufende App geprüft: jedes Kapitel
gerendert, jede erreichbare Ansicht auf Emulatoren dagegen gehalten. Die App entspricht jetzt der
Spezifikation — bis auf elf Stellen, an denen die Spezifikation selbst eine Lücke hat. An jeder
davon musste die Implementierung eine Gestaltungsentscheidung allein treffen, und eine Entscheidung,
die nur im Kotlin-Code steht, hat niemand gestaltet. Bitte ergänze das Bundle um die folgenden neun
Artboards und korrigiere zwei bestehende. Konventionen, Raster, Kapitelaufbau und Handoff-Notizen
wie gehabt.

Drei Rahmenbedingungen, die für diese Runde besonders zählen: **Die App hat keinen Push-Kanal**
(beschlossene Entscheidung Q2) — zeichne nie Copy, die eine Benachrichtigung verspricht. **Die App
kennt keine Rollen** — sie kann nicht wissen, ob jemand etwas darf; der Server antwortet mit 403 und
die App sagt es. Zeichne rechteabhängige Bedienelemente also vorhanden und ablehnbar, nicht
versteckt. Und **alles, was du zeichnest, muss ein Feld haben, das es in `openapi.json` gibt** —
wenn du etwas brauchst, das fehlt, schreib es als Anforderung an das Backend in die Handoff-Notiz,
statt es zu zeichnen, als gäbe es das Feld schon.

**1 · Kapitel 08, das offene Overflow-Menü.** Artboard 08.1 zeigt ein `⋮` in der Kopfleiste, und die
Handoff-Notiz nennt seine drei Einträge — Home-Location setzen (Bulk), Hangar leeren, Import —, aber
kein Artboard zeigt das geöffnete Menü. Zeichne den Hangar mit offenem Menü, einmal mit allen drei
Einträgen aktiv und einmal mit einem leeren Hangar, in dem zwei Einträge nicht wählbar sind. Lege
dabei fest: Bleibt ein nicht wählbarer Eintrag stehen oder verschwindet er, ist der destruktive
Eintrag schon im Menü rot oder erst im Modal dahinter, tragen die Einträge führende Glyphen, und wo
verankert das Menü auf dem Tablet.

**2 · Kapitel 08, „Home-Location setzen" als Bottom Sheet.** Ein Ort für die ganze Flotte, gegen
`POST /api/v1/hangar/ships/home-location` mit `{locationId}`. Zeichne: nichts gewählt, Ort gewählt,
Speichern läuft, abgelehnt. Entscheide, ob das Sheet nennt, wie viele Schiffe es bewegt — die Zahl
ist da — und ob ein Massen-Schreibvorgang ohne Datenverlust überhaupt eine Rückfrage braucht.

**3 · Kapitel 08, „Hangar leeren" als Danger-Modal.** Hier widersprechen sich zwei bestehende
Notizen: 08.1 nennt es ein *type-safe* Danger-Modal, 08.3 zitiert die Copy „Alle 3 Schiffe
löschen?", die nur die Anzahl nennt. Zeichne, welches von beiden gilt, und schreib die Begründung in
die Notiz. Kapitel 02 §7 behält die Tipp-Hürde irreversiblen Admin-Aktionen vor; einen persönlichen
Hangar zu leeren ist nur durch erneuten Import rückgängig zu machen, das ist also eine echte Frage.

**4 · Kapitel 09, das Lager im Auswahlmodus.** Kapitel 02 §4 kanonisiert die Interaktion — langer
Druck wählt, die Zeile bekommt den 3 dp orangen Einsatzbalken und einen Haken, unten erscheint eine
Aktionsleiste — und die Handoff-Notiz von Kapitel 09 nennt das Massen-Umbuchen als Anwendung. Kein
Artboard zeigt den Lagerbaum in diesem Zustand, und der Baum hat drei Ebenen, wo die Kanon-Zeile
flach ist. Zeichne 09.1 mit zwei ausgewählten Einträgen samt Aktionsleiste (Anzahl · Aufheben ·
Umbuchen) und lege fest: Lässt sich eine Gruppe oder ein Stapel als Ganzes wählen — und was heißt
das bei einer Gruppe, deren Zeilen in drei verschiedenen Hangars liegen? Was passiert mit den
Aktionen auf der Zeile (Buchen, Zuordnen), solange der Modus läuft? Zeigt die Gruppenzeile, wie
viele ihrer Zeilen gewählt sind? Und überlebt die Auswahl das Einklappen einer Gruppe?

**5 · Kapitel 09, „Umbuchen" als Bulk-Sheet.** Das Sheet hinter der Aktionsleiste, gegen
`POST /api/v1/inventory/bulk-rebook` im Modus `LOCATION`. Wichtig ist der Ausgang: Der Endpoint
meldet zwei Zahlen — **rebooked** und **skipped**, wobei eine Zeile, die schon am Ziel steht,
übersprungen und nicht als Fehler gezählt wird. Zeichne: Ort noch nicht gewählt, gewählt, Speichern
läuft, Ergebnis mit übersprungenen Zeilen, abgelehnt. Entscheide, wie das Ergebnis erzählt wird —
Toast, Zeile im Sheet oder eigener Ergebnisschritt. Wer zwölf Stapel verschiebt und elf bewegt
sieht, braucht den Satz, der den zwölften erklärt.

**6 · Kapitel 06, „Funktion an Bord".** Kapitel 02 §6 zeichnet `KrtChipSelect` mit einem
PILOT-Chip unter genau dieser Beschriftung, und die API führt das Feld auch:
`MissionParticipantDto.desiredMissionJobType` und `plannedMissionJobType`, schreibbar über
`UpdateParticipantRequest`. Kein Screen-Artboard zeigt es, deshalb zeigt die App es nirgends — bisher
kann niemand sagen, was er fliegen will, und keine Einsatzleitung es zuweisen. Zeichne das Feld
zweimal: im Teilnehmer-Reiter von 06.2 auf der Teilnehmerzeile und im Anmelden-Sheet 06.3, wo die
Person ihre eigene Funktion wählt. Lege fest, wer die *geplante* Funktion gegenüber der *gewünschten*
ändern darf und wie die Zeile aussieht, wenn beide auseinanderfallen.

**7 · Ein Detailscreen mit `KrtPanelHeader`.** Die Komponente beschreibt sich selbst als das Mittel,
lange Detailscreens in einklappbare Abschnitte zu falten (Finanzen, Teilnehmer, …), und Kapitel 02
§2 zeichnet sie mit „FINANZEN 4". Kein einziges Detail-Artboard benutzt sie: 06.2 hat sieben Reiter,
06.5 flache Abschnittstitel. Zeichne einen Detailscreen mit gefalteten Abschnitten und entscheide
damit das Verhältnis zu den Reitern — Alternative, eine Ebene darunter, oder die Tablet-Antwort auf
die Reiter des Telefons. Sag außerdem, welche Abschnitte zugeklappt starten.

**8 · Der Telefon-Zusammenfall einer echten Tabelle.** Kapitel 02 §5 stellt die Regel auf — das
Tablet behält Tabellen, das Telefon fällt zu Key-Value-Karten zusammen — und illustriert sie mit
Lager-Daten. Das Tablet-Artboard von Kapitel 09 zeichnet aber das Blueprint-Master-Detail statt der
Bestandstabelle, und das von Kapitel 08 zeigt eine Schiffstabelle, ohne zu zeigen, was aus ihr auf
dem Telefon wird. Zeichne diesen Zusammenfall für mindestens eine echte Tabelle, damit die Regel ein
Beispiel in einem Screen hat und nicht nur im Komponentenblatt.

**9 · Zwei Komponenten, die erst eine Entscheidung brauchen.** `KrtDepartmentTag` hält feste
Bereichsfarben und sagt, er werde nur dort verwendet, wo der Bereich tatsächlich zutrifft — kein
Artboard zeigt ihn. `KrtPresenceIndicator` zeichnet Kapitel 02 §3 als pulsenden Punkt mit den Namen
der gerade Bearbeitenden, aber kein Screen zeigt ihn, und die Android-App bekommt überhaupt keine
Präsenzdaten: den Presence-Socket der Weboberfläche konsumiert sie nicht. Entscheide für beide, ob
sie auf einer Android-Fläche vorkommen. Wenn ja, zeichne welche — bei der Präsenz zusätzlich, was
passiert, wenn die Liste der Bearbeitenden lang wird, und schreib den nötigen Backend-Anschluss als
Anforderung in die Notiz. Wenn nein, kennzeichne sie im Komponentenblatt als web-only, statt sie
ungenutzt im Android-Kanon stehen zu lassen.

**10 · Korrektur an 08.3.** Das Beispiel im Einfügefeld des Fleetview-Imports zeigt
`{"ships": [{"name": "Meridian", "type": "Carrack"}]}`. Genau das beantwortet
`POST /api/v1/hangar/import/fleetview` mit **400 — „The uploaded file must contain a JSON array at
the root"**. Der Endpoint nimmt ein Array an der Wurzel und kennt drei Formate, die er in seinen
eigenen Fehlermeldungen benennt: CCU Game Fleetview, HangarXPLOR Shiplist und Fleetyards JSON.
Gegen den laufenden Stack geprüft, mit exakt dem Multipart, das die App schickt: Objektform 400,
Array-Form 200 mit der Auswertung. Bitte zeichne den Hinweis als Array und nenne die drei Formate im
Artboard so, wie der Server sie nennt.

**11 · Korrektur an 04.3.** Die Fußzeile des Freigabe-Bildschirms lautet „Automatische Prüfung alle
60 s — Push bei Freigabe." Die App hat keinen Push-Kanal; eine Freigabe kommt über die Abfrage oder
gar nicht. Die Implementierung zeigt die erste Hälfte und lässt die zweite weg, weil eine
versprochene Benachrichtigung, die nie eintreffen kann, jemanden auf seinen Sperrbildschirm warten
lässt. Bitte streich die zweite Hälfte — oder, falls Push doch wieder zur Debatte steht, bring das
als Entscheidung ein statt als Copy.

Nicht anfragen, damit es nicht doppelt bearbeitet wird: 15.3 („Kein Browser erkannt") ist gezeichnet
und gebaut und nur deshalb ungeprüft, weil jeder Emulator hier einen Browser hat — eine Testlücke,
keine Designlücke. `KrtDataValue` ist ein Baustein anderer Komponenten und braucht kein
Screen-Artboard. `KrtUpdateAvailablePill` bleibt bewusst ungenutzt: Die App lädt bei einem
Live-Signal an Ort und Stelle nach und lässt eine offene Eingabe stehen, es gibt also keinen
entrissenen Zustand, vor dem zu warnen wäre — die Pille zu zeichnen hieße, die Änderung einer
anderen Person zurückzuhalten, und das ist eine Verhaltensentscheidung, keine Gestaltung. Bring sie
als solche ein, falls sie gewollt ist.
