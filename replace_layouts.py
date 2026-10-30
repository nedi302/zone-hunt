# -*- coding: utf-8 -*-
import os, re, html, sys
sys.stdout.reconfigure(encoding="utf-8")

layout_dir = r"E:\project\app\src\main\res\layout"

# Global mappings by exact (decoded) text, optionally per attribute.
GLOBAL = {
    # generic
    "Chat": "chat_title",
    "Menü": "game_menu_desc",
    "Zurück": "back",
    # create
    "NEUE JAGD": "create_kicker",
    "DU BESTIMMST\\nDIE REGELN.": "create_title",
    "Zone, Tempo und Pings stellst du vor dem Start ein. Die Rolle des Suchers legst du in der Lobby fest.": "create_sub",
    "SPIELZONE": "section_game_zone",
    "Startgröße": "create_start_size",
    "Radius der Spielzone beim Start. Der Host-Standort wird zum Mittelpunkt.": "create_radius_hint",
    "Schrumpf-Tempo": "create_shrink_speed",
    "So viele Meter verliert die Zone pro Minute. Bei 0 bleibt sie stehen.": "create_shrink_hint",
    "TEMPO & SIGNALE": "section_pace_signals",
    "Rundendauer": "create_round_duration",
    "Nach Ablauf gewinnen die Verstecker, die noch in der Zone sind.": "create_round_hint",
    "Ping-Takt": "create_ping_interval",
    "Bei jedem Ping werden alle Spieler-Positionen auf der Karte eingefroren und bleiben bis zum nächsten Ping sichtbar.": "create_ping_hint",
    "VERSTECKEN, TEAM & SHOP": "section_hide_team_shop",
    "Versteckdauer": "create_hide_duration",
    "So viel Vorsprung haben die Verstecker, bevor die Sucher losziehen.": "create_hide_hint",
    "Team live sehen": "create_team_live",
    "Verstecker sehen Verstecker live, Sucher sehen Sucher live – nur solange sie im Spiel sind.": "create_team_live_hint",
    "Team-Positionen live sehen": "create_team_live_desc",
    "Shop aktivieren": "create_shop_enable",
    "Fürs Überleben gibt es Münzen. Im Spiel kaufst du damit Köder, Ping-Tarnung oder Radar. Die Preise passen sich der Rundendauer an.": "create_shop_hint",
    "ZONEN-REGEL": "section_zone_rule",
    "Kulanz außerhalb": "create_grace",
    "So lange darfst du dich außerhalb der Zone aufhalten, bevor du automatisch ausscheidest.": "create_grace_hint",
    "Der Startpunkt wird beim Loslassen der Runde aus deinem GPS-Standort gesetzt. Die Zone kann sich nicht verschieben – nur schrumpfen.": "create_start_note",
    "Lobby erstellen": "create_lobby_button",
    "Du erhältst danach einen 4-stelligen Beitrittscode.": "create_footnote",
    "LOBBY WIRD GEÖFFNET": "overlay_opening_lobby",
    "Verbindung wird aufgebaut …": "connecting",
    # game
    "SUCHER": "game_role_seeker",
    "LIVE": "game_live",
    "ZONE": "game_zone_label",
    "NÄCHSTER PING": "game_next_ping_label",
    "bis zum nächsten Ping": "game_until_next_ping",
    "IM SPIEL": "game_in_game",
    "3 Verstecker": "game_hiders_placeholder",
    "PING! Positionen sind eingefroren.": "game_ping_banner",
    "VERSTECKPHASE": "game_hide_phase",
    "Die Sucher warten, bis alle versteckt sind.": "game_hide_phase_sub",
    "Du bist außerhalb der Zone!": "game_outside_zone",
    "Verbindung wird wiederhergestellt …": "game_reconnecting",
    "DU HAST JEMANDEN GEFUNDEN?": "game_found_someone",
    "Gib den Fangcode vom Handy des Versteckers ein.": "game_enter_catch",
    "Fangen": "game_catch",
    "DEIN FANGCODE": "game_your_catch_code",
    "Zeigen": "game_show",
    "Nur zeigen, wenn du gefunden wurdest.": "game_code_hint",
    "DU BIST RAUS": "game_out_title",
    "Außerhalb der Zone ausgeschieden.": "game_out_zone",
    "Zuschauen": "game_watch",
    "MÜNZEN": "game_coins",
    "Shop": "game_shop",
    "1 Münze pro 5 s Überleben · Preise passen zur Rundenzeit": "game_shop_status",
    "SPIELER": "section_players",
    "Übersicht": "game_overview",
    "Karte": "game_map",
    "© OpenStreetMap": "replay_attribution",
    "BEREIT?": "game_countdown_ready",
    "Die Jagd beginnt": "game_countdown_sub",
    "DEINE ROLLE": "game_intro_role",
    "DU BIST SUCHER": "game_intro_seeker_title",
    "Warte auf den ersten Ping. Danach bleiben die Positionen aller Spieler bis zum nächsten Ping eingefroren sichtbar.": "game_intro_seeker_text",
    "Verstanden, los!": "game_intro_got_it",
    "RUNDE BEENDET": "game_end_badge",
    "SUCHER GEWINNT": "game_end_seeker",
    "Alle Verstecker sind ausgeschieden.": "game_end_reason",
    "Dauer": "stat_duration",
    "übrig": "stat_left",
    "Pings": "stat_pings",
    "Strecke": "stat_distance",
    "Kalorien": "stat_calories",
    "Replay ansehen": "game_end_replay",
    "Zurück zum Start": "game_end_back",
    # join
    "EINER VON VIELEN": "join_kicker",
    "JAGD BEITRETEN": "join_title",
    "Gib den 4-stelligen Code der Lobby ein.": "join_sub",
    "BEITRITTSCODE": "join_code_label",
    "Dein Spielername": "player_name_hint",
    "Beitreten": "join_button",
    "Verbinde mit der Lobby …": "join_progress",
    "Jedes Handy spielt mit. Standort, Karte und Ping laufen direkt auf deinem Gerät.": "join_footnote",
    # lobby
    "LOBBY": "lobby_title",
    "Warte auf die Jagd.": "lobby_sub",
    "HOST": "host",
    "Kopieren": "copy",
    "Diesen Code an deine Gruppe teilen.": "lobby_share_code",
    "Warte auf Mitspieler …": "lobby_waiting_players",
    "DEINE REGIE": "section_your_call",
    "Wer sucht? · Mehrfachauswahl": "lobby_who_seeks",
    "Noch nicht gewürfelt": "lobby_not_rolled",
    "SUCHER AUSWÄHLEN · tippe auf Namen": "lobby_select_seeker",
    "Neu würfeln": "lobby_reroll",
    "Tipp: Mehrere Sucher möglich – mindestens ein Verstecker bleibt übrig. Die Versteckdauer legt der Host fest.": "lobby_seeker_tip",
    "ECHTZEIT-EINSTELLUNGEN": "section_realtime_settings",
    "Einstellungen ändern": "lobby_edit_settings",
    "Jagd starten": "lobby_start",
    "Mindestens 2 Spieler · GPS wird beim Start benötigt": "lobby_start_hint",
    "DER HOST STARTET BALD": "lobby_host_starts_soon",
    "Bleib in der Lobby. Sobald es losgeht, öffnet sich die Karte. Danach hast du Vorsprung zum Verstecken.": "lobby_guest_hint",
    "Der Sucher bekommt keinen Ping-Vorteil: Alle Positionen werden gleichzeitig sichtbar. Verstecker haben Vorsprung. Chat: Alle oder nur das eigene Team. Fangcodes bleiben auf dem Handy der Verstecker.": "lobby_info",
    # main
    "OUTDOOR": "main_kicker",
    "Die Jagd ist draußen. Deine Freunde, die Karte, eine Zone, die schrumpft.": "main_tagline",
    "Jagd erstellen": "main_create",
    "Mit Code beitreten": "main_join",
    "Kein Account. Keine Anmeldung. Nur draußen.": "main_footnote",
    "DEINE BILANZ": "section_stats",
    "Gesamtstrecke": "main_total_distance",
    "Kalorien verbrannt": "main_calories_burned",
    "Gezählt werden die Wege, die du während der Runden zu Fuß zurücklegst.": "main_stats_note",
    "GPS": "main_gps",
    "Echtzeit-Karte": "main_realtime_map",
    "Pings & Zone": "main_pings_zone",
    "Spieler": "main_players",
    "SO LÄUFT'S": "section_how_it_works",
    "OpenStreetMap-Karten · MQTT-Echtzeit · Für draußen gebaut": "main_footer",
    # replay
    "REPLAY": "replay_label",
    "Alle Wege einpassen": "replay_fit_desc",
    "Abspielen": "replay_play",
    "Tempo antippen · Regler ziehen zum Springen": "replay_hint",
    "WIEDERHOLUNGEN": "replays_title",
    "Deine Runden.": "replays_sub",
    "NOCH KEIN REPLAY": "replays_empty_title",
    "Beende eine Runde. Danach findest du hier die Wege aller Spieler – nur auf diesem Gerät.": "replays_empty_text",
    "Replays bleiben lokal gespeichert und können jederzeit gelöscht werden.": "replays_note",
    "Jagd vom 01.01.2026": "replay_title_placeholder",
    # dialogs
    "Der Verstecker zeigt dir seinen 4-stelligen Code.": "catch_prompt",
    "CHAT": "chat_title",
    "VERSTECKER": "game_role_hider",
    "Alle": "chat_all",
    "Team": "chat_team",
    "Team = alle Verstecker": "chat_team_hint_hiders",
    "Noch keine Nachrichten.\\nSchreib die erste.": "chat_empty",
    "Nachricht schreiben …": "chat_hint",
    "Senden": "chat_send_desc",
    "Team-Nachrichten sehen nur Spieler mit derselben Rolle.": "chat_team_note",
    "GEFUNDEN & AUSGESCHIEDEN": "overview_found_title",
    "Noch niemand ausgeschieden.": "overview_empty",
    "NOCH IM SPIEL": "overview_alive_title",
    "Fangcodes werden hier nie angezeigt.": "overview_note",
    "SHOP": "shop_title",
    "1 Münze pro 5 s Überleben": "shop_coins_per",
    "0 Münzen": "shop_coins_zero",
    "Preise passen sich automatisch der Rundenzeit an. Käufe werden an alle Spieler übertragen.": "shop_info",
    "Münzen gibt es nur, solange du im Spiel bist.": "shop_coins_note",
    # items
    "ALLE": "game_all_scope",
    "Bereit": "lobby_ready",
    "Replay löschen": "replay_delete_desc",
    "Falsche Markierung": "shop_item_decoy_name",
    "Platziere einen Köder mit deinem Namen.": "shop_item_decoy_description",
    "60 Münzen": "shop_price_placeholder",
    "Kaufen": "shop_buy",
    "Lobby aufmachen": "main_step1_title",
    "Der Host stellt Zone, Ping-Takt, Rundendauer und Versteckzeit ein und teilt den 4-stelligen Code.": "main_step1_text",
}

# Per-file overrides for ambiguous words.
OVERRIDES = {
    "item_lobby_player.xml": {"Spieler": "player", "HOST": "host"},
    "item_chat_message.xml": {"Spieler": "player", "ALLE": "game_all_scope"},
    "activity_main.xml": {"Wiederholungen": "main_replays", "LIVE": "main_live"},
    "activity_replays.xml": {"WIEDERHOLUNGEN": "replays_title"},
    "activity_game.xml": {"LIVE": "game_live", "SPIELER": "section_players"},
    "dialog_shop.xml": {"SHOP": "shop_title"},
    "activity_lobby.xml": {"HOST": "host", "SPIELER": "section_players"},
}

ATTR_RE = re.compile(r'android:(text|hint|contentDescription)="([^"]*)"')

def esc_xml(v):
    return v.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace('"', "&quot;")

def esc(v):
    return esc_xml(v)

for name in sorted(os.listdir(layout_dir)):
    if not name.endswith(".xml"):
        continue
    path = os.path.join(layout_dir, name)
    with open(path, encoding="utf-8") as f:
        content = f.read()
    overrides = OVERRIDES.get(name, {})
    changed = [0]
    unresolved = []

    def repl(m):
        attr, raw = m.group(1), m.group(2)
        # decode XML entities so keys match data values
        val = html.unescape(raw)
        key = overrides.get(val)
        if key is None and attr == "contentDescription" and val == "Shop aktivieren":
            key = "create_shop_desc"
        if key is None:
            key = GLOBAL.get(val)
        if key is None:
            if raw and not raw.startswith("@") and not re.fullmatch(r"[\d\s:.,/×+–—-]*", val):
                unresolved.append((attr, val))
            return m.group(0)
        changed[0] += 1
        return f'android:{attr}="@string/{key}"'

    content2 = ATTR_RE.sub(repl, content)
    # special case: the same value is used for switch text and its contentDescription
    if name == "activity_create_game.xml":
        content2 = content2.replace('android:text="@string/create_shop_enable"', 'android:text="@string/create_shop_enable"', 1)
    if content2 != content:
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            f.write(content2)
    print(f"{name}: replaced {changed[0]}")
    for attr, val in unresolved:
        print(f"   text-left: {attr}={val!r}")
