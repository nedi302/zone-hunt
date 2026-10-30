# Zone Hunt

GPS-Hide-and-Seek für Android: echte Karte, schrumpfende Zone, Ping-System und Fangcode. Neben dem klassischen Modus gibt es **Zombie Apocalypse**, **Assassin** und **Eisfangen**. Die App heißt international **Zone Hunt**; Englisch ist die Standardsprache, Deutsch, Spanisch und Französisch sind ebenfalls enthalten.

## Sprachen

- Englisch (Standard und Fallback)
- Deutsch
- Spanisch
- Französisch

Die Sprache folgt der Systemeinstellung des Geräts. Über das Zahnrad auf dem Startbildschirm lässt sich die Sprache zusätzlich direkt in der App ändern: Systemsprache, Englisch, Deutsch, Spanisch oder Französisch. Die Auswahl wird gespeichert; Englisch ist die Basis- und Fallback-Sprache. Alle sichtbaren Texte, Dialoge, Benachrichtigungen und Datumsformate sind lokalisiert.

## Installieren

Die fertige APK liegt unter:

```
ZoneHunt-1.4.0.apk
```

1. APK auf das Android-Handy übertragen (Kabel, Cloud, Messenger …).
2. Datei antippen. Beim ersten Mal muss die Installation aus unbekannten Quellen für die verwendete App erlaubt werden.
3. Voraussetzungen: Android 8.0 oder neuer, GPS, mobile Daten oder WLAN.

Die APK ist release-signiert und kann direkt installiert werden. Beim Update über eine ältere Version derselben App muss dieselbe Signatur verwendet werden.

## So wird gespielt

1. **Host:** „Neue Jagd erstellen“, Namen eingeben, Regeln einstellen. Als Zone dient die eigene GPS-Position beim Start.
2. **Mitspieler:** „Jagd beitreten“ und den 4-stelligen Code eingeben, den der Host teilt.
3. In der Lobby sieht der Host alle Spieler als auswählbare Chips und kann einen oder mehrere Sucher antippen oder zufällig wählen lassen. Die Zufallswahl lässt sich beliebig oft neu würfeln, bis die Runde startet. Mindestens 2 Spieler, mindestens ein Verstecker bleibt übrig. Im Zombie- und Eis-Modus bestimmt der Host keinen Sucher, sondern die App zieht genau einen Start-Zombie bzw. einen Eisjäger. Im Assassin-Modus sind alle Spieler gleichberechtigte Attentäter. Über „Einstellungen ändern“ lassen sich Zone, Tempo, Pings, Versteckzeit, Team-Sicht, Spielmodus und der optionale Shop auch nach dem Erstellen der Lobby noch anpassen; der Beitrittscode bleibt gleich.
4. Der Host startet die Runde. Alle landen auf der Karte und erhalten eine Benachrichtigung.
5. **Verstecker** haben die vom Host eingestellte Versteckzeit (Standard 25 Sekunden) Zeit, sich zu verstecken. In dieser Phase schrumpft die Zone nicht, es gibt keine Pings und Fangen ist gesperrt. **Sucher** warten auf das Startsignal. Ein 3-Sekunden-Countdown zählt den Rundenstart über den ganzen Bildschirm ein.
6. Danach beginnt die Jagd: Bei jedem Ping werden die Positionen aller Spieler auf der Karte eingefroren und bleiben bis zum nächsten Ping an genau dieser Stelle sichtbar. Die Ping-Marker zeigen die ersten beiden Buchstaben des Spielernamens und pulsieren beim Eintreffen.
7. Die Zone schrumpft und wandert dabei. Auf der Karte ist immer die aktuelle Zone (grün, bei Gefahr rot) und die nächste Zone (orange gestrichelt) zu sehen. Jede neue Zone liegt vollständig in der vorherigen und damit auch in der Ausgangszone. Wer zu lange außerhalb der aktuellen Zone ist, scheidet automatisch aus. Wird die Zone kleiner, kommt eine Benachrichtigung.
8. Jeder Sucher tippt auf „Fangen“, wählt den Verstecker und gibt dessen 4-stelligen Fangcode ein. Der Code ist nur auf dem Handy des Versteckers sichtbar („Zeigen“).
9. Runde endet, wenn alle Verstecker raus sind, alle Sucher ausscheiden oder die Zeit abläuft – mit Benachrichtigung für alle.
10. Im Ergebnis kann ein **Replay** geöffnet werden: Karte, farbige Wege aller Spieler, Play/Pause, Zeitleiste und 1×/2×/4×-Tempo. Jede Runde wird automatisch lokal gespeichert. Die Ergebnisanzeige zeigt zusätzlich die in der Runde gelaufene Strecke und die geschätzten Kalorien; auf dem Startbildschirm stehen Gesamtstrecke und Gesamtkalorien aller Runden.

### Spielmodi

Der Modus wird beim Erstellen der Lobby unter **Spielmodus** gewählt und gilt für die gesamte Runde. Fangcodes, Ping-System, Zone, Chat und Replays funktionieren in allen Modi; Rollen, Siegbedingungen und ein Teil der Einstellungen unterscheiden sich.

#### Classic

Ein oder mehrere Sucher jagen die Verstecker. Der Sucher gibt den 4-stelligen Fangcode des Versteckers ein, um ihn auszuschalten. Die Verstecker gewinnen, wenn mindestens einer die Zeit überlebt.

#### Zombie Apocalypse

- Die Sucher sind von Beginn an Zombies. Genau ein Spieler wird zufällig als Start-Zombie bestimmt; alle anderen starten als Überlebende.
- Ein Zombie fängt einen Überlebenden weiterhin über dessen 4-stelligen Fangcode. Der Gefangene wird **infiziert** und gehört ab diesem Moment zur Horde. Es gibt keinen stillen Tod: Wer infiziert ist, jagt anschließend selbst die verbliebenen Überlebenden.
- **Infektionsschutz:** Ein frisch verwandelter Zombie kann für die eingestellte Dauer noch niemanden fangen. Das verhindert eine sofortige Kettenreaktion am Fangort.
- **Horde-Sinn:** Zombies erhalten in festen Abständen für 4 Sekunden die Live-Positionen aller noch aktiven Überlebenden. Der Sinn ist ein lokaler Effekt und wird nicht als Dauer-Reveal übertragen.
- Die Runde endet, wenn alle Überlebenden infiziert sind (**ZOMBIES** gewinnen) oder die Zeit abläuft und mindestens ein Überlebender übrig ist (**SURVIVORS** gewinnen).

#### Assassin

- Jeder Spieler ist Attentäter und hat genau ein geheimes Ziel. Die Ziele werden als geschlossener Ring aus der Spielerliste und der Spiel-ID gebildet. Jeder ist gleichzeitig Jäger und Gejagter.
- Im Ziel-Panel steht der Name des eigenen Ziels. Die Entfernung stammt aus dem letzten Ping bzw. aus einem aktiven Zielradar, nicht aus der Live-Position.
- **Ausschalten:** Man muss sich dem Ziel näher als den eingestellten Kill-Radius nähern und „Ausschalten“ bestätigen. Ein Fangcode wird nicht benötigt; das Opfer erhält die Meldung, dass es ausgeschaltet wurde.
- Nach einem erfolgreichen Ausschalten erbt der Attentäter das Ziel des Opfers. Der Ring bleibt dadurch geschlossen, bis nur noch eine Person übrig ist.
- Der Host ist für die Zielverteilung autoritativ. Verlässt ein Spieler die Runde, scheidet er durch die Zone aus oder wird ausgeschaltet, repariert der Host den Ring und verteilt die neuen Ziele an alle Geräte.
- **Ziel-Pings:** Standardmäßig zeigt ein Ping nur das eigene aktuelle Ziel. Die Option lässt sich beim Erstellen der Lobby abschalten, dann sind wieder alle Positionen sichtbar.
- Die Runde endet, sobald nur noch ein Attentäter übrig ist (**ASSASSIN:<Spieler-ID>** gewinnt), oder bei Zeitablauf. Bei Zeitablauf gewinnt der Attentäter mit den meisten Ausschaltungen; bei Gleichstand entscheidet die verbleibende Spielerliste.

#### Eisfangen

- Genau ein Spieler wird zufällig zum **Eisjäger**; alle anderen sind Verstecker.
- Der Eisjäger friert einen Verstecker über dessen 4-stelligen Fangcode ein. Der Eingefrorene scheidet nicht sofort aus, sondern bleibt als **eingefrorener** Marker auf der Karte stehen.
- **Auftauen:** Ein aktives, nicht eingefrorenes Teammitglied muss sich innerhalb des eingestellten Radius aufhalten und die Haltezeit lang in der Nähe bleiben. Danach ist der Eingefrorene wieder aktiv. Der Auftauende sieht einen Fortschritt; der Eingefrorene erhält eine Benachrichtigung.
- **Erfrieren:** Wird niemand rechtzeitig aktiv, scheidet der Eingefrorene nach der eingestellten Melt-Zeit aus.
- Die Runde endet, wenn alle Verstecker eingefroren/ausgeschieden sind (**ICE** gewinnt) oder die Zeit abläuft und mindestens ein Verstecker aktiv ist (**HIDERS** gewinnen). Scheidet der Eisjäger aus, gewinnen die Verstecker sofort.

### Chat, Übersicht & Benachrichtigungen

- **Chat:** Über das Sprechblasen-Symbol in der Spielansicht. Nachrichten gehen entweder an **Alle** oder nur an das **eigene Team** (alle Verstecker bzw. alle Sucher). Team-Nachrichten werden clientseitig anhand der Rolle gefiltert. Ungelesene Nachrichten erscheinen als roter Zähler am Chat-Symbol.
- **Spielerübersicht:** Über „Übersicht“ in der unteren Leiste oder das Menü. Sie zeigt getrennt alle ausgeschiedenen/gefundenen Spieler (mit Grund) und alle noch im Spiel befindlichen Spieler (mit Rolle und letztem Ping). Fangcodes werden dort nie angezeigt.
- **Benachrichtigungen:** Lokale Android-Benachrichtigungen beim Rundenstart, beim Beginn der Jagd nach der Versteckphase, beim Schrumpfen der Zone und beim Rundenende. Dafür ist auf Android 13+ die Benachrichtigungsberechtigung nötig; die App fragt sie beim ersten Start ab. Es werden keine Push-Server oder Firebase benötigt.

### Replays

- Aufzeichnungen liegen ausschließlich im app-privaten Speicher des Geräts (`files/replays`).
- Über „Wiederholungen“ auf dem Startbildschirm lassen sich alle Runden öffnen und mit dem Papierkorb endgültig löschen.
- Pro Gerät werden maximal 50 Replays aufbewahrt; danach wird automatisch die älteste Aufzeichnung entfernt.
- Ein Replay enthält die Wegpunkte aller Spieler, Rollen, Rundendauer, Zone samt wanderndem Verlauf und Ergebnis – aber keine Fangcodes.
- Die Wege werden beim Abspielen von GPS-Ausreißern befreit und leicht geglättet, damit keine Zickzack-Linien über die Karte laufen.

### Shop (optional)

Der Shop wird in den Rundeneinstellungen aktiviert. Jeder Spieler sammelt **1 Münze pro 5 Sekunden Überleben**; nach dem Ausscheiden gibt es keine Münzen mehr. Die Preise werden automatisch mit der eingestellten Rundendauer skaliert (Basis: 30 Minuten), damit Käufe in kurzen wie langen Runden möglich bleiben. Jeder Modus hat seine eigenen drei Artikel; gekaufte Effekte gelten nur für die eigene Rolle und werden pro Spieler gespeichert.

**Classic**

| Artikel | Rolle | Preis bei 30 min | Wirkung |
| --- | --- | --- | --- |
| Falsche Markierung | Verstecker | 90 Münzen | Platziert einen Köder mit dem eigenen Namen an einer frei gewählten Kartenstelle. Alle Sucher sehen ihn 45 s lang wie einen normalen Ping-Marker – dort ist niemand. Der Käufer sieht seinen eigenen Köder ebenfalls auf der Karte, farblich abgesetzt. |
| Ping-Tarnung | Verstecker | 180 Münzen | Der eigene Marker fehlt beim nächsten Ping und erscheint erst beim übernächsten wieder. |
| Sucher-Radar | Sucher | 150 Münzen | Zeigt 10 s lang die Live-Positionen aller Verstecker, auch zwischen den Pings. |

**Zombie Apocalypse**

| Artikel | Rolle | Preis bei 30 min | Wirkung |
| --- | --- | --- | --- |
| Überlebenden-Köder | Überlebender | 90 Münzen | Legt eine falsche Spur mit dem eigenen Namen, um die Horde wegzulocken. |
| Horden-Tarnung | Überlebender | 180 Münzen | Der eigene Marker fehlt beim nächsten Zombie-Ping. |
| Horde-Sinn | Zombie | 150 Münzen | Zeigt 10 s lang alle Überlebenden live. |

**Assassin**

| Artikel | Rolle | Preis bei 30 min | Wirkung |
| --- | --- | --- | --- |
| Falsche Spur | Attentäter | 90 Münzen | Platziert einen falschen Namen auf der Karte, um Verfolger zu täuschen. |
| Schatten | Attentäter | 180 Münzen | Der eigene Marker fehlt beim nächsten Ping. |
| Zielradar | Attentäter | 150 Münzen | Zeigt 10 s lang das eigene geheime Ziel live. |

**Eisfangen**

| Artikel | Rolle | Preis bei 30 min | Wirkung |
| --- | --- | --- | --- |
| Warmer Köder | Verstecker | 90 Münzen | Legt eine falsche Spur mit dem eigenen Namen, um den Eisjäger abzulenken. |
| Heißer Tee | Verstecker | 150 Münzen | Taucht den eingefrorenen Käufer sofort wieder auf. |
| Wärmesicht | Eisjäger | 150 Münzen | Zeigt 10 s lang alle Verstecker live. |

Die Preise in den Tabellen gelten für 30 Minuten und werden proportional zur Rundendauer angepasst (bei 5 Minuten z. B. 15/30/25 Münzen). Köder werden über einen Tipp auf die Karte platziert. Käufe und Effekte werden als MQTT-Event `SHOP` an alle Spieler übertragen. Münzen, Preise und aktive Effekte werden im Spiel unter der Spielerleiste angezeigt. Der Käufer sieht seinen eigenen Köder immer; fremde Köder sehen nur die Rollen, die sie auch sehen sollen. Radar-, Tarnungs- und Sinneffekte gelten ausschließlich für den Käufer.

### Host-Einstellungen

| Einstellung | Bedeutung |
| --- | --- |
| Spielmodus | Classic, Zombie Apocalypse, Assassin oder Eisfangen |
| Zonengröße | Startradius in Metern |
| Schrumpfung | Meter pro Minute |
| Ping-Intervall | Abstand zwischen Positions-Pings |
| Rundendauer | Maximale Spieldauer der Jagdphase |
| Kulanzzeit | Wie lange man außerhalb der Zone sein darf |
| Versteckphase | Vorsprung für alle Verstecker, 5–120 Sekunden einstellbar |
| Team live | Verstecker sehen Verstecker live, Sucher sehen Sucher live; gefundene Spieler verschwinden sofort |
| Shop | Aktiviert Münzen und die drei Shop-Artikel des gewählten Modus |
| Infektionsschutz (Zombie) | 5–60 s, in denen ein frisch verwandelter Zombie noch nicht fangen kann |
| Horde-Sinn-Intervall (Zombie) | 15–120 s zwischen den kurzen Live-Sichtfenstern der Zombies |
| Kill-Radius (Assassin) | 10–60 m, innerhalb derer das geheime Ziel ausgeschaltet werden kann |
| Ziel-Pings (Assassin) | Ping zeigt nur das eigene Ziel; abschaltbar für alle Positionen |
| Tauradius (Eis) | 5–40 m um einen eingefrorenen Spieler |
| Tauzeit (Eis) | 3–15 s, die ein aktives Teammitglied in Reichweite bleiben muss |
| Schmelzzeit (Eis) | 20–120 s bis zum Erfrieren ohne Hilfe |

### Zonenverlauf

- Die Zone wird in festen 5-Minuten-Stufen kleiner. Der Mittelpunkt kann sich bei jeder Stufe bewegen und dabei auch die Richtung wechseln.
- Die Bewegung einer Stufe ist höchstens so groß wie der Radiusverlust dieser Stufe. Dadurch liegt die nächste Zone immer vollständig in der aktuellen Zone; alle Zonen bleiben in der Ausgangszone.
- Der Verlauf wird aus der Spiel-ID berechnet. Alle Geräte und das Replay zeigen deshalb dieselbe aktuelle und nächste Zone, ohne laufende Zonen-Updates zu übertragen.
- Im HUD steht bei „JETZT“ die aktuelle Zone und bei „NÄCHSTE“ der nächste Radius samt Countdown. Ist die letzte Stufe erreicht, wird daraus „ENDZONE“.
- Bei einer statischen Zone (Schrumpfung 0 m/min) bleibt der Mittelpunkt fest.

## Technik

- Native Android-App (Java), Paket `com.zonehunt.app`, Anzeigename **Zone Hunt**
- Sprachen: Englisch (Basis), Deutsch, Spanisch, Französisch
- Karte: osmdroid / OpenStreetMap
- Multiplayer ohne eigenen Server über MQTT/TLS: `ssl://broker.emqx.io:8883`
- Topic-Root: `zonenjagd/v1/`
- Die Zone wird auf jedem Gerät aus Startzeit, Mittelpunkt, Host-Einstellungen und Spiel-ID berechnet. Der wandernde Verlauf ist deterministisch; es werden keine laufenden Zonen-Updates übertragen.
- Fangcode-Schutz: Verstecker senden nur `SHA-256(code|playerId)` per Heartbeat. Der Sucher prüft den eingegebenen Code lokal gegen diesen Hash.
- Replay: Jeder Heartbeat mit Koordinaten wird lokal als Wegpunkt gespeichert. Beim Rundenende wird die Aufzeichnung als JSON-Datei gesichert und in der Replay-Ansicht abgespielt.
- Benachrichtigungen: lokale Android-Notifications über `NotificationHelper`; kein FCM, kein zusätzlicher Server. Auf Android 13+ wird `POST_NOTIFICATIONS` benötigt.
- Chat: MQTT-Event `CHAT` auf dem Spiel-Topic. Team-Nachrichten werden clientseitig nach Rolle gefiltert; der Broker könnte technisch mitlesen.
- Shop: MQTT-Event `SHOP` mit Artikel, Effekt-ID und Hunting-Zeitstempel. Jeder Client wendet den Effekt lokal an; Münzen und Käufe werden lokal geführt.
- Mehrere Sucher: Die ausgewählten Sucher stehen kommagetrennt im Feld `seekerId` der Lobby. Mindestens ein Verstecker bleibt immer übrig.
- Modi: `mode` in den Lobby-Einstellungen ist `CLASSIC`, `ZOMBIE`, `ASSASSIN` oder `ICE`. Zombie-Infektionen laufen über `CATCH`/`OUT` mit Zustandsfeldern im Heartbeat; Assassin nutzt `KILL` und host-autoritative `TARGETS`; Eis nutzt `CATCH`, `THAW` und die Melt-Prüfung über Heartbeat-Zustände. Replays speichern den Modus und den Gewinner.

### Wichtiger Hinweis zum Broker

`broker.emqx.io` ist ein öffentlicher Test-Broker. Für eine dauerhafte oder kommerzielle Nutzung sollte er durch einen eigenen, authentifizierten MQTT-Endpunkt ersetzt werden. Der Broker kann ausfallen, gedrosselt werden oder von Dritten mitgelesen werden. Die Lobby-Daten sind nicht verschlüsselt (nur der Transport via TLS).

## Selbst bauen

Voraussetzungen:

- JDK 17
- Android SDK mit Platform 35 und Build-Tools 35
- Gradle 8.9 (oder Android Studio)

`local.properties` mit dem SDK-Pfad anlegen:

```properties
sdk.dir=C\:\\Pfad\\zum\\Android-SDK
```

Dann im Projektordner:

```powershell
gradle assembleRelease
```

Die APK entsteht unter `app/build/outputs/apk/release/`: mit `key.properties` als `app-release.apk`, ohne als `app-release-unsigned.apk`.

Für einen signierten Release-Build eine lokale `key.properties` im Projektordner anlegen:

```properties
storeFile=../signing/zonenjagd-release.jks
storePassword=<Passwort>
keyAlias=<Alias>
keyPassword=<Passwort>
```

`key.properties`, der Ordner `signing/` und Keystore-Dateien sind in `.gitignore` eingetragen und dürfen nicht veröffentlicht werden. Fehlt `key.properties`, erzeugt Gradle eine unsignierte Release-APK; das ist der passende Fall für F-Droid, das die APK mit einem eigenen Schlüssel signiert.

## Projektstruktur

```
app/src/main/java/com/zonehunt/app/
├── MainActivity.java          Start / Name / Einstellungen
├── SettingsActivity.java      Sprache & App-Einstellungen
├── CreateGameActivity.java    Regeln & Lobby erstellen
├── JoinGameActivity.java      Code eingeben
├── LobbyActivity.java         Spieler, Sucherwahl, Start
├── GameActivity.java          Karte, Zone, Ping, Fangdialog, Chat, Übersicht, Ergebnis
├── ReplayListActivity.java    Gespeicherte Replays ansehen/löschen
├── ReplayActivity.java        Karte, Wege und Wiedergabe
├── model/                     Spieler, Einstellungen, Lobby, Laufzeit, Zonenverlauf, Shop, Replay
├── net/GameClient.java        MQTT, Lobby, Heartbeat, Ping, Fanglogik, Shop, Aufzeichnung
├── util/                      Geo, Hash, Replay-Speicher, UI-Helfer
└── view/                      Zonen-Overlay, Pins, Radar, Replay-Marker
```

## Bekannte Grenzen

- Ohne Internet keine Verbindung; der öffentliche Broker ist ein Single Point of Failure.
- Standortverfolgung läuft nur, solange die App im Vordergrund ist.
- Benachrichtigungen erscheinen nur, wenn die App die Berechtigung hat und Benachrichtigungen systemweit erlaubt sind.
- 4-stellige Lobby-Codes sind nicht global eindeutig; bei Kollision einfach neu erstellen.
- Es gibt keine Konten oder Moderationsfunktionen; der MQTT-Broker sollte für ernsthafte Runden privat betrieben werden.
- Ein Replay wird beim regulären Rundenende (oder Verlassen der Runde) gespeichert. Wird die App während der Runde hart beendet, geht die Aufzeichnung verloren.

## Lizenz

Dieses Projekt steht unter der **GNU General Public License Version 3 (GPL-3.0)**.

Copyright (C) 2026 Michael/nedi

Der vollständige Lizenztext liegt in der Datei [`LICENSE`](LICENSE) und ist zusätzlich unter <https://www.gnu.org/licenses/gpl-3.0.txt> verfügbar. Die GPL-3.0 erlaubt Nutzung, Veränderung und Weitergabe der Software, verlangt bei Weitergabe aber, dass abgeleitete Werke ebenfalls unter der GPL-3.0 stehen und der Quellcode verfügbar bleibt. Es gibt keine Gewährleistung.

### Abhängigkeiten und Assets

- AndroidX AppCompat, Activity und Core, Material Components sowie osmdroid: Apache-2.0, kompatibel mit GPL-3.0.
- Eclipse Paho MQTT Client: Eclipse Distribution License 1.0 (BSD-3-Clause), kompatibel mit GPL-3.0.
- Die Vektor-Icons in `app/src/main/res/drawable` basieren auf Material-Design-Pfaddaten (Apache-2.0).
- Kartendaten und Kacheln von OpenStreetMap stehen unter der ODbL; die erforderliche Attribution ist in der App enthalten.
