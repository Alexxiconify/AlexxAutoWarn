# AlexxAutoWarn: Smart Zone Monitoring for Minecraft

**AlexxAutoWarn** is a Spigot/Paper plugin for server admins to monitor and control specific material placements within custom zones. It offers flexible rules to deny, alert staff, or allow items, providing granular oversight.

---

## 📋 Table of Contents

- [Features](#features)  
- [Commands](#commands)  
- [Permissions](#permissions)  
- [Configuration](#configuration)  
- [Installation](#installation)  
- [Support](#support)  
- [License](#license)

---

## Features

- **Custom Zones**: Define rectangular monitoring areas.  
- **Global Banned List**: A central list of materials to track.  
- **Flexible Zone Actions**:
  - `DENY`: Block placement + alert staff.
  - `ALERT`: Allow placement + alert staff.
  - `ALLOW`: Permit placement (even if globally banned).
- **Default Zone Rules**: Set fallback actions for unlisted materials.
- **Staff Notifications**: Configurable in-game alerts.
- **Bypass Permission**: Allow specific staff to ignore all rules.
- **Custom Messages**: Personalize all plugin messages via `config.yml`.
- **Easy Setup**: Use a special wand to define zones.

---

## Commands

> Main command: `/autoinform` (alias: `/ainform`)  
> All admin commands require `autoinform.admin.set`.

| Command | Description |
|--------|-------------|
| `/autoinform wand` | Get zone selection wand. |
| `/autoinform <zone> pos1/pos2` | Set zone corners. |
| `/autoinform <zone> define` | Define/update zones using selections. |
| `/autoinform <zone> defaultaction <action>` | Set zone's default action. |
| `/autoinform <zone> setaction <material> <action>` | Set specific material action for a zone. |
| `/autoinform remove <zone>` | Delete a zone. |
| `/autoinform info [zone]` | Show zone info, or list all zones. |
| `/autoinform list` | List all defined zones. |
| `/autoinform clearwand` | Clear wand selections. |
| `/autoinform reload` | Reload plugin config. |
| `/autoinform banned add/remove <material>` | Manage global banned list. |
| `/autoinform banned list` | List banned materials. |

---

## Permissions

| Permission Node | Description | Default |
|------------------|-------------|---------|
| `autoinform.admin.set` | Access to all admin commands. | op |
| `autoinform.alert.receive` | Receive in-game staff alerts. | op |
| `autoinform.bypass` | Bypass all zone restrictions. | false |

---

## Configuration

Customize plugin behavior in `plugins/AlexxAutoWarn/config.yml`.

### Example Configuration

```yaml
zones:
  my_first_zone:
    world: "world"
    corner1: {x: 0.0, y: -64.0, z: 0.0}
    corner2: {x: 100.0, y: 100.0, z: 100.0}
    default-material-action: ALERT # DENY, ALERT, or ALLOW
    material-actions:
      LAVA_BUCKET: DENY
      TNT: ALERT

banned-materials:
  - LAVA_BUCKET
  - TNT
  - FIRE
```
messages:
  player-denied-placement: "&cYou are not allowed to place {material} here!"
  staff-alert-message: "&c[AutoInform] &e{player} placed {material} in zone '{zone_name}'."
## Installation
Download AlexxAutoWarn.jar from the releases page.

Place the .jar in your server's plugins/ folder.

(Optional): Install CoreProtect for detailed logging.

Start or restart your server.

Edit config.yml in plugins/AlexxAutoWarn/.

Reload the plugin with /autoinform reload or restart the server.

## Support
For bugs, feature requests, or contributions:

Open an issue on the GitHub Issues page.

Submit a pull request.

## License
This project is licensed under the MIT License.
See the [LICENSE]([AlexxAutoWarn/blob/main/LICENSE](https://github.com/Alexxiconify/AlexxAutoWarn/blob/main/LICENSE.md)) file for details.
