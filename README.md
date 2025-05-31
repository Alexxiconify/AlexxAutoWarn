AlexxAutoWarn

 ## Table of Contents

About

Features

Commands

Permissions

Configuration

Installation

Support & Contribution

License

About
AlexxAutoWarn is a Spigot/Paper plugin designed to help server administrators manage and monitor specific areas (zones) for the placement of designated "banned" materials. Unlike traditional protection plugins that simply deny actions, AlexxAutoWarn offers granular control, allowing you to configure zones to either deny placement, alert staff, or even allow certain materials based on per-zone and per-material settings. It's ideal for servers that want to keep an eye on specific activities without necessarily blocking them outright, or for setting up strict no-build zones for certain items.

Features
Zone-Based Monitoring: Define custom rectangular zones across any world.

Global Banned Materials List: Maintain a central list of materials to monitor.

Per-Zone Material Actions: Configure specific actions for materials within each zone:

DENY: Prevents the player from placing the material and alerts staff.

ALERT: Allows the player to place the material but sends an alert to staff.

ALLOW: Explicitly allows the material to be placed in this zone, even if it's on the global banned list (no alert).

Default Zone Actions: Set a default action (DENY, ALERT, or ALLOW) for materials not explicitly defined in a zone's material-actions.

Staff Alerts: Notifies online staff with a configurable message when a monitored action occurs.

Server Logging: Logs all monitored actions to the server console.

Bypass Permission: Designated staff can bypass all zone restrictions.

Configurable Messages: Customize all plugin messages to players and staff via the config.yml.

Easy Zone Selection: Use a special "wand" item to quickly select zone corners.

Commands
The main command is /autoinform, with /ainform as an alias.

Command Syntax

Description

Permission

/autoinform wand

Gives you the AutoInform Zone Selector Wand (Wooden Axe).

autoinform.admin.set

/autoinform <zone_name> pos1

Sets the first corner of a zone to your current location.

autoinform.admin.set

/autoinform <zone_name> pos2

Sets the second corner of a zone to your current location.

autoinform.admin.set

/autoinform <zone_name> define

Defines or updates the specified zone using your wand selections or pos1/pos2 manual selections.

autoinform.admin.set

`/autoinform <zone_name> defaultaction <DENY

ALERT

ALLOW>`

`/autoinform <zone_name> setaction  <DENY

ALERT

ALLOW>`

/autoinform remove <zone_name>

Removes a defined zone.

autoinform.admin.set

/autoinform info [zone_name]

Displays detailed information about a specific zone, or lists all defined zones if no name is provided.

autoinform.admin.set

/autoinform list

Lists the names of all currently defined zones.

autoinform.admin.set

/autoinform clearwand

Clears your current wand selections.

autoinform.admin.set

/autoinform reload

Reloads the plugin's configuration from config.yml. Can be run by console.

autoinform.admin.set

/autoinform banned add <material>

Adds a material to the global banned materials list.

autoinform.admin.set

/autoinform banned remove <material>

Removes a material from the global banned materials list.

autoinform.admin.set

/autoinform banned list

Lists all materials in the global banned materials list.

autoinform.admin.set

Permissions
Permission Node

Description

Default

autoinform.admin.set

Allows access to all /autoinform commands for managing zones and settings.

op

autoinform.alert.receive

Allows a player to receive in-game alerts when a monitored action occurs.

op

autoinform.bypass

Allows a player to bypass all AutoInform zone restrictions (no denials or alerts).

false

Configuration
The plugin's configuration is handled via config.yml.

# AutoInform Configuration

# This section defines multiple protected zones.
# Each zone can be configured with specific actions (DENY, ALERT, ALLOW)
# for different materials, and a default action for others.
zones:
  # Example Zone 1: Default action is ALERT, but LAVA_BUCKET is DENIED
  zone_alpha:
    world: "world"
    corner1:
      x: 0.0
      y: -64.0
      z: 0.0
    corner2:
      x: 100.0
      y: 100.0
      z: 100.0
    # Default action for materials in this zone if not specified in material-actions.
    # Options: DENY, ALERT, ALLOW
    default-material-action: ALERT

    # Specific actions for materials within this zone.
    # These override the default-material-action for the listed materials.
    material-actions:
      LAVA_BUCKET: DENY   # Deny placement of lava in this zone
      TNT: ALERT          # Only alert staff for TNT placement
      WATER_BUCKET: ALLOW # Allow water placement, even if it's in the global banned-materials list

  # Example Zone 2: Default action is DENY
  zone_beta:
    world: "world_nether"
    corner1:
      x: -50.0
      y: 30.0
      z: -50.0
    corner2:
      x: 50.0
      y: 80.0
      z: 50.0
    default-material-action: DENY # Deny all banned materials by default in this zone
    material-actions:
      FIRE: ALERT # But only alert for fire, don't deny

# This section defines a list of materials that are generally considered "banned".
# Zones will only apply actions to materials present in this global list,
# unless a specific 'ALLOW' action is defined within a zone's material-actions.
banned-materials:
  - LAVA_BUCKET
  - TNT
  - TNT_MINECART
  - FIRE
  - WATER_BUCKET # Example: Water is generally banned, but zone_alpha allows it.

# Customizable messages for the plugin.
messages:
  player-denied-placement: "&cYou are not allowed to place {material} here!"
  player-wand-pos1-set: "&aPosition 1 set: {location}"
  player-wand-pos2-set: "&aPosition 2 set: {location}"
  player-wand-click-block: "&eYou must click on a block to set Position {pos_num}."
  player-wand-received: "&aYou have received the AutoInform Zone Selector Wand!"
  player-positions-same-world: "&cBoth positions must be in the same world."
  player-define-zone-success: "&aAutoInform zone '{zone_name}' defined and saved for world '{world_name}'."
  player-define-zone-corners: "&7Corner 1: {corner1_loc}\n&7Corner 2: {corner2_loc}"
  player-define-zone-deny-setting: "&7Default Action: {default_action}"
  player-zone-removed: "&aZone '{zone_name}' has been removed."
  player-zone-not-found: "&eZone '{zone_name}' not found."
  player-no-zones-defined: "&eNo AutoInform zones are currently defined."
  player-all-zones-header: "&b--- All AutoInform Zones ---"
  player-zone-info-header: "&b--- AutoInform Zone: {zone_name} ---"
  player-zone-info-world: "&6World: &f{world_name}"
  player-zone-info-corner1: "&6Corner 1: &f{corner1_loc}"
  player-zone-info-corner2: "&6Corner 2: &f{corner2_loc}"
  player-zone-info-default-action: "&6Default Action: &f{default_action}"
  player-zone-info-material-actions: "&6Material Actions:"
  player-zone-info-material-action-entry: "&f  - {material}: {action}"
  player-defined-zones-header: "&b--- Defined AutoInform Zones ---"
  player-wand-selections-cleared: "&aYour wand selections have been cleared."
  player-material-added-banned: "&aMaterial '{material}' added to banned list."
  player-material-already-banned: "&eMaterial '{material}' is already in the banned list."
  player-invalid-material-name: "&cInvalid material name: '{material}'. Please use a valid Minecraft material name (e.g., LAVA_BUCKET, TNT)."
  player-material-removed-banned: "&aMaterial '{material}' removed from banned list."
  player-material-not-banned: "&eMaterial '{material}' is not in the banned list."
  player-no-materials-banned: "&eNo materials are currently banned."
  player-banned-materials-header: "&b--- Banned Materials ---"
  player-deny-setting-updated: "&aDefault action for zone '{zone_name}' set to {action}."
  player-material-action-updated: "&aAction for {material} in zone '{zone_name}' set to {action}."
  player-invalid-action-type: "&cInvalid action type. Please use DENY, ALERT, or ALLOW."
  player-no-permission: "&cYou do not have permission to use this command."
  player-command-usage: "&cUsage: {usage}"
  player-console-only-reload: "&cThis command can only be run by a player (except for /{command_name} reload)."
  staff-alert-message: "&c[AutoInform] &e{player} placed {material} in zone '{zone_name}' at {x},{y},{z}. Action: {action_color}{action_status}."
  plugin-config-reloaded: "&aAutoInform configuration reloaded."
  plugin-warning-no-zones: "&eWarning: No zones defined in config or some worlds are invalid."
  plugin-success-zones-loaded: "&bSuccessfully loaded {count} zone(s)."
  plugin-current-banned-materials: "&bCurrently banned materials: {materials}"
  plugin-enabled: "AutoInform enabled!"
  plugin-disabled: "AutoInform disabled!"
  plugin-coreprotect-not-found: "CoreProtect API not found! Related functionality might be affected."
  plugin-coreprotect-hooked: "CoreProtect API hooked successfully."
  plugin-coreprotect-api-disabled: "CoreProtect API is not enabled."
  plugin-coreprotect-api-outdated: "CoreProtect API version is outdated (found: {version}). Please update CoreProtect."
  plugin-invalid-zone-config: "Invalid configuration for zone '{zone_name}'. Skipping."
  plugin-world-not-found: "World '{world_name}' for zone '{zone_name}' not found! This zone will not be active."
  plugin-error-loading-zone-coords: "Error loading coordinates for zone '{zone_name}': {message}. Skipping."
  plugin-invalid-banned-material-config: "Invalid material name '{name}' found in config.yml banned-materials list. Skipping."
  plugin-no-zones-in-config: "No 'zones' section found in config.yml. No zones loaded."

Installation
Download the latest AlexxAutoWarn.jar from the releases page.

Place the AlexxAutoWarn.jar file into your server's plugins/ folder.

(Optional but Recommended) Install CoreProtect if you wish to utilize its logging capabilities for more detailed block history.

Start or Restart your Minecraft server.

Edit the generated config.yml file in plugins/AlexxAutoWarn/ to customize zones, banned materials, and messages.

Reload the plugin using /autoinform reload or restart your server again for changes to take effect.

Support & Contribution
If you encounter any bugs, have feature requests, or wish to contribute, please feel free to:

Open an issue on the GitHub Issues page.

Submit a pull request (for code contributions).

License
This project is licensed under the MIT License - see the LICENSE file for details.
