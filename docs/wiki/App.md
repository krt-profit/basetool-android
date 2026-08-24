# Basetool-App für Android

Die App ist der Begleiter zum Basetool im Browser. Sie zeigt dieselben Daten, mit denselben Rechten
— was du in der App siehst, siehst du auch im Browser, und umgekehrt. Sie ersetzt das Basetool
nicht: die Verwaltung bleibt bewusst im Browser.

---

## Installieren

Die App gibt es **nicht im Play Store**. Sie wird über GitHub veröffentlicht, und der bequemste Weg
ist **Obtainium** — eine kleine App, die Updates von GitHub holt, so wie ein Store es täte.

### Mit Obtainium (empfohlen)

1. Obtainium installieren: <https://github.com/ImranR98/Obtainium/releases> (die Datei
   `app-release.apk`).
2. In Obtainium **„App hinzufügen"** und diese Adresse eintragen:

   ```
   https://github.com/krt-profit/basetool-android
   ```

3. Installieren. Obtainium meldet sich künftig von selbst, wenn eine neue Version da ist.

### Ohne Obtainium

Auf <https://github.com/krt-profit/basetool-android/releases> die neueste `basetool-*.apk`
herunterladen und öffnen. Android fragt einmal, ob es Apps aus dieser Quelle installieren darf.
Updates musst du dann selbst holen.

### Was Android dich fragen wird

„Installation aus unbekannten Quellen" — das ist normal und heißt nur, dass die Datei nicht aus dem
Play Store kommt. Ob sie wirklich von uns ist, kannst du prüfen; siehe **Ist das die echte App?**
weiter unten.

---

## Anmelden

Mit deinem gewohnten Basetool-Zugang. Die App öffnet dafür kurz den Browser — das ist Absicht: dein
Passwort wird nie in der App eingegeben und die App bekommt es nie zu sehen.

Wenn dein Zugang noch nicht freigeschaltet ist, sagt die App das und prüft von selbst weiter. Die
Nutzungsbedingungen bestätigst du beim ersten Mal direkt in der App.

---

## Was die App kann

**Ansehen**

- **Übersicht** — was in den nächsten sieben Tagen ansteht und was ungelesen ist
- **Einsätze** mit Detail, Teilnehmerliste und Finanzen
- **Operationen** und dein Anteil daran
- **Aufträge** samt Materialbedarf
- **Lager** — Bestände bis auf die einzelne Buchung
- **Hangar** — deine Schiffe und die Flotte der Staffel
- **Bank** — die Konten, die du sehen darfst
- **Mein Inventar** und deine **Blueprints**
- **Materialbörse** — Angebote und Gesuche
- **Raffinerie** — deine eigenen Orders
- **Benachrichtigungen**

**Ändern**

- an Einsätzen an- und abmelden, ein- und auschecken, Auszahlung wählen
- Einsatz-Finanzen buchen
- Schiffe im Hangar anlegen, ändern, löschen
- ein- und ausbuchen im Lager
- dich auf Aufträge setzen, Notizen schreiben, als Logistiker den Status ändern
- eigenes Inventar und eigene Blueprints pflegen
- Kontoeinstellungen, wenn du für ein Konto verantwortlich bist
- auf der Materialbörse anbieten, suchen und „Ich kann liefern" melden
- Raffinerie-Erträge ins Lager buchen

**Was die App nicht kann, und auch nicht können soll**

Die **Administration** bleibt im Browser — Rollen, Mitglieder, Kataloge, Einsatzplanung. Ebenso die
**Bankangestellten-Ansicht** (Ein- und Auszahlungen, Überweisungen) und die **Beförderungs-Matrix**.
Datei-Importe (Fleetview, Blueprints, Raffinerie-Screenshots) kommen später.

---

## Gut zu wissen

**Die App aktualisiert sich live.** Ändert jemand im Browser einen Auftrag oder das Lager, zieht die
App den betroffenen Bereich nach — ohne dass du etwas tust. Umgekehrt genauso.

**Benachrichtigungen erreichen dich nur, solange die App läuft.** Es gibt bewusst keinen
Push-Dienst: das würde bedeuten, deine Benachrichtigungen über Google laufen zu lassen. Auf dem
Sperrbildschirm steht ohnehin nie mehr als „Neue Benachrichtigung".

**Ohne Netz** kannst du weiterlesen, was schon geladen war. Ändern geht nicht — die Knöpfe sind dann
abgeschaltet, statt dass die App etwas sammelt und später mit veralteten Daten losschickt.

**Ist der Server ausgelastet**, versucht die App es selbst noch einmal und zeigt dir, in wie vielen
Sekunden.

**App-Sperre.** In den Einstellungen kannst du die App hinter Fingerabdruck oder Gesichtsentsperrung
legen. Dein Anmelde-Token liegt dann verschlüsselt und ist ohne Entsperrung nicht lesbar.

**Screenshots sind aus.** Die App verbietet Bildschirmfotos und Aufnahmen — überall, nicht nur auf
einzelnen Seiten.

---

## Ist das die echte App?

Weil es keinen Store gibt, der das für dich prüft, veröffentlichen wir bei jedem Release zwei
Prüfsummen. Beide stehen in den Release-Notizen.

**Der wichtigere Wert ist der Fingerabdruck des Signaturzertifikats.** Der ändert sich **nie** ohne
eine angekündigte Schlüsselumstellung. Steht in einem Release ein anderer, stammt die Datei nicht
von uns — Android würde ein Update damit ohnehin verweigern.

Der zweite Wert ist die Prüfsumme der APK selbst; damit prüfst du, ob die heruntergeladene Datei
unterwegs verändert wurde.

Beide findest du bei jedem Release unter „Prüfsummen".

---

## Was die App über dich speichert

Auf dem Gerät bleibt nur, was die App zum Arbeiten braucht: dein Anmelde-Token (verschlüsselt) und
die zuletzt geladenen Inhalte. Nichts davon wird in eine Cloud gesichert oder auf ein neues Handy
übertragen.

Es gibt **keine Analyse, keine Werbung, kein Tracking** und keinen automatischen Absturzbericht. Ein
Absturz wird auf dem Gerät notiert; ob du ihn schickst, entscheidest du.

Abmelden löscht alles Lokale. Die vollständige Datenschutzerklärung erreichst du in der App unter
**Einstellungen → Datenschutz** und im Browser unter <https://profit-base.online/privacy>.

---

## Wenn etwas nicht geht

**„Freigabe ausstehend"** — dein Zugang ist noch nicht freigeschaltet. Die App prüft jede Minute
selbst nach.

**„Update erforderlich"** — deine Version wird vom Server nicht mehr bedient. Über die Schaltfläche
kommst du zur aktuellen.

**„Signal Lost"** — der Bereich ließ sich nicht laden. Meist ist das die Verbindung; bleibt es, sag
Bescheid.

**Die App startet nach einem Update nicht mehr** oder Android verweigert die Installation: dann
wurde die neue Datei mit einem anderen Schlüssel signiert. Nicht deinstallieren, sondern fragen —
Deinstallieren löscht deine App-Sperre und deine Anmeldung mit.

Fehler und Wünsche gehören nach
<https://github.com/krt-profit/basetool-android/issues> oder in den üblichen Discord-Kanal.
