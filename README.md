 # alexxautowarn

 Lightweight zone-based monitoring for Minecraft servers. Define rectangular zones, set material-specific actions
 (DENY / ALERT / ALLOW), and notify staff when players place tracked items.

 Quick highlights
 - Public API (`AutoWarnAPI`) for other plugins to query zones and register listeners.
 - Configurable zones, messages, and a global banned-materials list.
 - Zone priorities allow overlapping areas to be resolved predictably.

 Core features
 - Define rectangular zones with corner coordinates.
 - Per-zone material actions: DENY, ALERT, or ALLOW.
 - Global banned-materials list with per-zone overrides.
 - Staff notifications and bypass permission.

 Commands (summary)
 - `/autoinform wand` — get the selection wand
 - `/autoinform <zone> pos1|pos2|define` — define a zone
 - `/autoinform <zone> setaction <material> <action>` — per-material action
 - `/autoinform banned add/remove <material>` — manage global banned list
 - `/autoinform reload` — reload config

 Permissions
 - `autoinform.admin.set` — admin actions (default: op)
 - `autoinform.alert.receive` — receive staff alerts (default: op)
 - `autoinform.bypass` — bypass rules (default: false)

 Configuration (example)
 ```yaml
 zones:
   example_zone:
     world: "world"
     corner1: {x: 0, y: 0, z: 0}
     corner2: {x: 100, y: 100, z: 100}
     default-material-action: ALERT
     material-actions:
       LAVA_BUCKET: DENY

 banned-materials:
   - LAVA_BUCKET
   - TNT

 messages:
   player-denied-placement: "&cYou are not allowed to place {material} here!"
 ```

 Installation
 1. Download `alexxautowarn.jar` from releases.
 2. Put the jar into your server's `plugins/` folder and start the server.
 3. Edit `plugins/alexxautowarn/config.yml` as needed and run `/autoinform reload`.

 Support and contributions
 - Report bugs or feature requests via GitHub Issues.
 - Pull requests welcome.

 License
 - MIT — see `LICENSE.md`.

 Minimal API example
 ```
 java
 AutoWarnAPI api = alexxautowarn.getAPI();
 api.registerZoneEnterListener(ev -> ev.getPlayer().sendMessage("Entered " + ev.getZone().getName()));
 ```