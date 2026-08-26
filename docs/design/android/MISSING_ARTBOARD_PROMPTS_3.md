# Prompt an die Design-Session — Runde 3

Nachtrag zu [`MISSING_ARTBOARD_PROMPTS_2.md`](MISSING_ARTBOARD_PROMPTS_2.md), das bereits
abgeschickt ist. Enthält eine **Korrektur** an dessen Rahmenbedingungen und **einen zusätzlichen
Auftrag**, der aus einer Entscheidung des Repository-Eigners hervorgeht
([ADR-0011](../adr/0011-the-app-knows-its-permissions-and-refuses-in-place.md),
`REQ-APP-AUTH-013`).

Alles unterhalb der Linie ist zum Einfügen in dieselbe Session gedacht.

---

Eine Korrektur zum vorigen Auftrag, bevor der eigentliche Punkt kommt: Dort steht als
Rahmenbedingung, die App kenne keine Rollen und rechteabhängige Bedienelemente seien deshalb
grundsätzlich „vorhanden und ablehnbar" zu zeichnen. **Das war falsch.** `/api/v1/users/me` — der
Aufruf, den die App ohnehin macht — liefert `roles` *und* `permissions`, und der Access-Token trägt
`realm_access.roles` mit. Die App hat diese Listen bisher nur weggeworfen. Bitte lass dich von dem
Satz also nicht leiten; was stattdessen gilt, steht im Auftrag unten. Alle anderen Punkte des
vorigen Auftrags bleiben unverändert gültig.

Und jetzt der Punkt, der jede Fläche berührt.

Ein einfaches KRT-Member hat `HANGAR_READ`, `HANGAR_WRITE` und `MISSION_READ` — sonst nichts. Auf
Org-Bestand zeigt die App ihm heute *Zuordnen*, *Buchen* und, nach langem Druck, *Umbuchen*, und der
Server beantwortet alle drei mit 403. Wer dreimal hintereinander abgewiesen wird, lernt, dass die
Oberfläche nicht meint, was sie anbietet — und das ist teurer als jede fehlende Funktion.

Entschieden ist: **Eine Aktion, die jemand nachweislich nicht ausführen darf, wird nicht
ausgeblendet.** Sie wird im Disabled-Stil der Button-Leiter gezeichnet, bleibt antippbar, und nennt
beim Antippen die fehlende Rolle — „Dafür brauchst du die Logistik-Rolle", nicht „403" und nicht
„Keine Berechtigung". Ausblenden wäre die sauberere Oberfläche, ist aber hier die schlechtere
Entscheidung: Diese Organisation vergibt Rollen von Hand, und eine Funktion, die niemand sieht, wird
auch nie angefragt. Technisch heißt das ausdrücklich *nicht* `enabled = false` — ein Compose-Element
mit diesem Flag nimmt keinen Tipp mehr entgegen, und was man nicht antippen kann, kann sich nicht
erklären. Gezeichnet wird der Disabled-Zustand, wirksam bleibt das Tippziel.

Bitte zeichne diesen Zustand aus, denn dabei sind mehrere Dinge festzulegen, die wir sonst wieder im
Code entscheiden:

**Wo erscheint der Grund?** Tooltip am Element, Toast am unteren Rand, oder eine Zeile, die unter
der Schaltfläche aufklappt. Die Button-Leiter kennt bereits einen Long-Press-Tooltip für
Icon-Schaltflächen, und Kapitel 02 §7 kennt den Toast mit Eckklammern — beides wäre denkbar, aber es
sollte eine Antwort geben und nicht zwei. Wie lange steht der Hinweis, und was passiert, wenn jemand
zweimal tippt?

**Ist die Sperre am Element selbst zu erkennen, bevor man tippt?** Reicht das Alpha der Leiter, oder
kommt eine Schloss-Glyphe dazu, oder ein eigener Ton? Ein Element, das nur blasser ist, ist von
einem Element in einem Ladezustand nicht zu unterscheiden.

**Verhält sich ein gesperrter CTA am Fuß eines Sheets anders als eine gesperrte Aktion in einer
Listenzeile?** In der Zeile ist kaum Platz, und dort stehen im Lager zwei Aktionen nebeneinander.

**Zwei Arten von Sperre, und ob sie gleich aussehen sollen.** Die eine hängt an der *Rolle* und ist
der App vorher bekannt — „Auftrag anlegen" ohne Logistik-Rolle. Die andere hängt an der *Zeile*: im
Lager gilt serverseitig „eigene Zeile oder Bearbeitungsrecht auf dieser Org-Einheit", das kann die
App für den Normalfall selbst beantworten, aber nicht immer. Wenn beide gleich aussehen sollen, sag
es; wenn nicht, zeichne beide.

Zu zeichnen sind mindestens: eine Lagerzeile mit gesperrter Zuordnen-Aktion, dieselbe Zeile
unmittelbar nach dem Antippen, ein Sheet mit gesperrtem CTA, und — falls die zeilenabhängige Sperre
anders aussieht — eine Zeile in diesem zweiten Fall. Nimm als Beispielrolle das einfache
KRT-Member auf Org-Bestand, das ist der Fall, der uns aufgefallen ist.

Ein Hinweis zur Copy: Die Begründung soll die **Rolle benennen**, nicht den Fehler. „Dafür brauchst
du die Logistik-Rolle" sagt jemandem, wen er fragen muss; „Keine Berechtigung" sagt ihm nur, dass er
verloren hat. Die Rollennamen kommen aus der Rollenmatrix der Organisation
(`ROLES_AND_PERMISSIONS.md` im Hauptrepository) — bitte verwende die dortigen Bezeichnungen, damit
der Text und die Rolle, die jemand anfragen soll, denselben Namen tragen.
