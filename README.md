AlexxAutoWarn: Smart Zone Monitoring for Minecraft
AlexxAutoWarn is a Spigot/Paper plugin that helps server admins monitor and control specific material placements within custom zones. It offers flexible rules to deny, alert staff, or allow items, providing granular oversight for your server.

Table of Contents
Features

Commands

Permissions

Configuration

Installation

Support

License

Features
Custom Zones: Define rectangular areas for monitoring.

Global Banned List: A central list of materials to track.

Flexible Zone Actions:

DENY: Block placement + alert staff.

ALERT: Allow placement + alert staff.

ALLOW: Permit placement (even if globally banned).

Default Zone Rules: Set fallback actions for unlisted materials in a zone.

Staff Notifications: Configurable in-game alerts.

Bypass Permission: Allow specific staff to ignore all rules.

Custom Messages: Personalize all plugin messages via config.yml.

Easy Setup: Use a special wand to define zones.

Commands
Main command: /autoinform (alias: /ainform). All admin commands require autoinform.admin.set.

Command Syntax

Description

/autoinform wand

Get the zone selection wand.

/autoinform <zone> pos1/pos2

Set zone corners manually.

/autoinform <zone> define

Save/update a zone using selections.

/autoinform <zone> defaultaction <action>

Set zone's default action (DENY, ALERT, ALLOW).

/autoinform <zone> setaction <material> <action>

Set specific material action (DENY, ALERT, ALLOW) for a zone.

/autoinform remove <zone>

Delete a zone.

/autoinform info [zone]

Show info for a zone, or list all zones.

/autoinform list

List all defined zones.

/autoinform clearwand

Clear your wand selections.

/autoinform reload

Reload plugin config.

/autoinform banned add/remove <material>

Add/remove material from global banned list.

/autoinform banned list

List all globally banned materials.

Permissions
Permission Node

Description

Default

autoinform.admin.set

Access to all admin commands.

op

autoinform.alert.receive

Receive in-game staff alerts.

op

autoinform.bypass

Bypass all zone restrictions.

false

Configuration
Customize plugin behavior in plugins/AlexxAutoWarn/config.yml.

# AlexxAutoWarn Configuration Example

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

messages:
  player-denied-placement: "&cYou are not allowed to place {material} here!"
  staff-alert-message: "&c[AutoInform] &e{player} placed {material} in zone '{zone_name}'."
  # ... other messages ...

Installation
Download AlexxAutoWarn.jar from the releases page.

Place .jar in your server's plugins/ folder.

(Optional): Install CoreProtect for detailed logging.

Start/Restart your server.

Edit config.yml in plugins/AlexxAutoWarn/.

Reload plugin with /autoinform reload or restart server.

Support
For bugs, feature requests, or contributions:

Open an issue on the GitHub Issues page.

Submit a pull request.

License
This project is licensed under the MIT License. See the LICENSE file for details.
