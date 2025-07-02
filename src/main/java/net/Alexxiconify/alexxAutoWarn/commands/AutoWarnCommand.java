package net.Alexxiconify.alexxAutoWarn.commands;

import com.google.common.collect.ImmutableList;
import net.Alexxiconify.alexxAutoWarn.AlexxAutoWarn;
import net.Alexxiconify.alexxAutoWarn.managers.ZoneManager;
import net.Alexxiconify.alexxAutoWarn.objects.Zone;
import net.Alexxiconify.alexxAutoWarn.utils.Settings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Handles all commands for the AlexxAutoWarn plugin.
 * Implements CommandExecutor for command handling and TabCompleter for tab completion.
 */
public class AutoWarnCommand implements CommandExecutor, TabCompleter {

 // Regex for valid zone names
 private static final Pattern ZONE_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{3,32}$");
 private final Settings settings;
 private final ZoneManager zoneManager;
 // Key for the selection wand's persistent data
 private static final NamespacedKey WAND_KEY;

 static {
  // Initialize WAND_KEY in a static block.
  // It's important that "alexxautowarn" matches the plugin's name in plugin.yml.
  WAND_KEY = NamespacedKey.fromString ( "alexxautowarn:selection_wand" );
 }

 private final AlexxAutoWarn plugin;
 // Changed to store Vector directly for consistency with Zone constructor
 private final Map<UUID, Vector> pos1 = new ConcurrentHashMap<>();
 private final Map<UUID, Vector> pos2 = new ConcurrentHashMap<>();

 public AutoWarnCommand(AlexxAutoWarn plugin) {
  this.plugin = plugin;
  this.settings = plugin.getSettings(); // Get settings from the plugin instance
  this.zoneManager = plugin.getZoneManager(); // Get zone manager from the plugin instance
 }

 @Override
 public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
  if (args.length == 0) {
   sendHelp(sender);
   return true;
  }

  // --- Handle subcommands ---
  switch (args[0].toLowerCase()) {
   case "wand":
    if (sender instanceof Player player) {
     if (!player.hasPermission("autowarn.wand")) {
      player.sendMessage(settings.getMessage("error.no-permission"));
      return true;
     }
     giveSelectionWand(player);
    } else {
     sender.sendMessage(settings.getMessage("error.player-only"));
    }
    return true;

   case "pos1":
   case "pos2":
    if (sender instanceof Player player) {
     if (!player.hasPermission("autowarn.pos")) {
      player.sendMessage(settings.getMessage("error.no-permission"));
      return true;
     }
     // Store the block location as a Vector directly
     Vector blockLoc = player.getLocation().toVector().toBlockVector();
     if (args[0].equalsIgnoreCase("pos1")) {
      pos1.put(player.getUniqueId(), blockLoc);
      player.sendMessage(settings.getMessage("command.pos-set",
              Placeholder.unparsed("pos", "1"),
              Placeholder.unparsed("coords", formatVector(blockLoc))));
     } else {
      pos2.put(player.getUniqueId(), blockLoc);
      player.sendMessage(settings.getMessage("command.pos-set",
              Placeholder.unparsed("pos", "2"),
              Placeholder.unparsed("coords", formatVector(blockLoc))));
     }
    } else {
     sender.sendMessage(settings.getMessage("error.player-only"));
    }
    return true;

   case "define":
    if (sender instanceof Player player) {
     if (!player.hasPermission("autowarn.define")) {
      player.sendMessage(settings.getMessage("error.no-permission"));
      return true;
     }
     if (args.length != 2) {
      player.sendMessage(settings.getMessage("error.usage.define"));
      return true;
     }

     String zoneName = args[1].toLowerCase(); // Store zone names in lowercase for consistent lookup
     if (!ZONE_NAME_PATTERN.matcher(zoneName).matches()) {
      player.sendMessage(settings.getMessage("error.invalid-zone-name"));
      return true;
     }

     // Retrieve Vectors directly
     Vector p1Vector = pos1.get(player.getUniqueId());
     Vector p2Vector = pos2.get(player.getUniqueId());

     if (p1Vector == null || p2Vector == null) {
      player.sendMessage(settings.getMessage("error.define-no-selection"));
      return true;
     }

     // To get the world for the zone, we use the player's current world
     // For more advanced usage, you might want to store world with the selection points
     World playerWorld = player.getWorld();

     // Check if both vectors logically refer to the same world (if you were storing world with selection)
     // For simplicity, defining a zone uses the player's current world.
     // If selection was made in another world, the player would need to be in that world to define.

     // Create the zone and add it
     Zone newZone = new Zone(zoneName, playerWorld, p1Vector, p2Vector,
             Zone.Action.ALERT, new EnumMap<>(Material.class)); // Default to ALERT, empty material actions
     zoneManager.addOrUpdateZone(newZone);

     player.sendMessage(settings.getMessage("command.define-success",
             Placeholder.unparsed("zone", zoneName)));
     pos1.remove(player.getUniqueId()); // Clear selection after defining
     pos2.remove(player.getUniqueId());
    } else {
     sender.sendMessage(settings.getMessage("error.player-only"));
    }
    return true;

   case "remove":
    if (!sender.hasPermission("autowarn.remove")) {
     sender.sendMessage(settings.getMessage("error.no-permission"));
     return true;
    }
    if (args.length != 2) {
     sender.sendMessage(settings.getMessage("error.usage.remove"));
     return true;
    }
    String zoneNameToRemove = args[1].toLowerCase();
    if (zoneManager.removeZone(zoneNameToRemove)) {
     sender.sendMessage(settings.getMessage("command.remove-success",
             Placeholder.unparsed("zone", zoneNameToRemove)));
    } else {
     sender.sendMessage(settings.getMessage("error.zone-not-found",
             Placeholder.unparsed("zone", zoneNameToRemove)));
    }
    return true;

   case "list":
    if (!sender.hasPermission("autowarn.list")) {
     sender.sendMessage(settings.getMessage("error.no-permission"));
     return true;
    }
    Collection<Zone> zones = zoneManager.getAllZones();
    if (zones.isEmpty()) {
     sender.sendMessage(settings.getMessage("command.list-empty"));
    } else {
     sender.sendMessage(settings.getMessage("command.list-header",
             Placeholder.unparsed("count", String.valueOf(zones.size()))));
     zones.forEach(zone ->
             sender.sendMessage(Component.text(" - " + zone.getName()).color(NamedTextColor.GRAY)));
    }
    return true;

   case "info":
    if (!sender.hasPermission("autowarn.info")) {
     sender.sendMessage(settings.getMessage("error.no-permission"));
     return true;
    }
    if (args.length != 2) {
     sender.sendMessage(settings.getMessage("error.usage.info"));
     return true;
    }
    String infoZoneName = args[1].toLowerCase();
    Zone infoZone = zoneManager.getZone(infoZoneName);
    if (infoZone == null) {
     sender.sendMessage(settings.getMessage("error.zone-not-found",
             Placeholder.unparsed("zone", infoZoneName)));
     return true;
    }
    sendZoneInfo(sender, infoZone);
    return true;

   case "defaultaction":
    if (!sender.hasPermission("autowarn.defaultaction")) {
     sender.sendMessage(settings.getMessage("error.no-permission"));
     return true;
    }
    if (args.length != 3) {
     sender.sendMessage(settings.getMessage("error.usage.defaultaction"));
     return true;
    }
    String daZoneName = args[1].toLowerCase();
    String daActionName = args[2];

    Zone daZone = zoneManager.getZone(daZoneName);
    if (daZone == null) {
     sender.sendMessage(settings.getMessage("error.zone-not-found",
             Placeholder.unparsed("zone", daZoneName)));
     return true;
    }

    Zone.Action newDefaultAction;
    try {
     // Trim whitespace and convert to uppercase for robust parsing
     newDefaultAction = Zone.Action.valueOf(daActionName.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
     sender.sendMessage(settings.getMessage("error.invalid-action"));
     return true;
    }

    // Get the World object using Bukkit.getWorld(worldName)
    World daWorld = Bukkit.getWorld(daZone.getWorldName());
    if (daWorld == null) {
     sender.sendMessage(Component.text("Error: World '" + daZone.getWorldName() + "' for zone '" + daZoneName + "' not found.").color(NamedTextColor.RED));
     return true;
    }

    Zone updatedDaZone = new Zone(daZone.getName(), daWorld, daZone.getMin(), daZone.getMax(),
            newDefaultAction, daZone.getMaterialActions());
    zoneManager.addOrUpdateZone(updatedDaZone); // This will save the updated zone

    sender.sendMessage(settings.getMessage("command.defaultaction-success",
            Placeholder.unparsed("zone", daZoneName),
            Placeholder.unparsed("action", newDefaultAction.name())));
    return true;

   case "setaction":
    if (!sender.hasPermission("autowarn.setaction")) {
     sender.sendMessage(settings.getMessage("error.no-permission"));
     return true;
    }
    if (args.length != 4) {
     sender.sendMessage(settings.getMessage("error.usage.setaction"));
     return true;
    }
    String saZoneName = args[1].toLowerCase();
    String saMaterialName = args[2];
    String saActionName = args[3];

    Zone saZone = zoneManager.getZone(saZoneName);
    if (saZone == null) {
     sender.sendMessage(settings.getMessage("error.zone-not-found",
             Placeholder.unparsed("zone", saZoneName)));
     return true;
    }

    Material saMaterial;
    try {
     saMaterial = Material.valueOf ( saMaterialName.toUpperCase ( ) );
    } catch ( IllegalArgumentException e ) {
     sender.sendMessage ( settings.getMessage ( "error.invalid-material" ) );
     return true;
    }
    if ( !saMaterial.isBlock ( ) ) { // Ensure it's a block-like material
     sender.sendMessage(settings.getMessage("error.invalid-material"));
     return true;
    }

    Zone.Action saAction;
    try {
     // Trim whitespace and convert to uppercase for robust parsing
     saAction = Zone.Action.valueOf(saActionName.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
     sender.sendMessage(settings.getMessage("error.invalid-action"));
     return true;
    }

    // Create a new map to avoid modifying the original zone's map directly
    // FIX: Initialize EnumMap explicitly with Material.class
    Map<Material, Zone.Action> updatedMaterialActions = new EnumMap<>(Material.class);
    updatedMaterialActions.putAll(saZone.getMaterialActions()); // Copy existing actions
    updatedMaterialActions.put(saMaterial, saAction); // Add/overwrite the specific action

    // Get the World object using Bukkit.getWorld(worldName)
    World saWorld = Bukkit.getWorld(saZone.getWorldName());
    if (saWorld == null) {
     sender.sendMessage(Component.text("Error: World '" + saZone.getWorldName() + "' for zone '" + saZoneName + "' not found.").color(NamedTextColor.RED));
     return true;
    }

    Zone updatedSaZone = new Zone(saZone.getName(), saWorld, saZone.getMin(), saZone.getMax(),
            saZone.getDefaultAction(), updatedMaterialActions);
    zoneManager.addOrUpdateZone(updatedSaZone); // This will save the updated zone

    sender.sendMessage(settings.getMessage("command.setaction-success",
            Placeholder.unparsed("material", saMaterial.name()),
            Placeholder.unparsed("zone", saZoneName),
            Placeholder.unparsed("action", saAction.name())));
    return true;

   case "removeaction":
    if (!sender.hasPermission("autowarn.removeaction")) {
     sender.sendMessage(settings.getMessage("error.no-permission"));
     return true;
    }
    if (args.length != 3) {
     sender.sendMessage(settings.getMessage("error.usage.removeaction"));
     return true;
    }
    String raZoneName = args[1].toLowerCase();
    String raMaterialName = args[2];

    Zone raZone = zoneManager.getZone(raZoneName);
    if (raZone == null) {
     sender.sendMessage(settings.getMessage("error.zone-not-found",
             Placeholder.unparsed("zone", raZoneName)));
     return true;
    }

    Material raMaterial;
    try {
     raMaterial = Material.valueOf ( raMaterialName.toUpperCase ( ) );
    } catch ( IllegalArgumentException e ) {
     sender.sendMessage ( settings.getMessage ( "error.invalid-material" ) );
     return true;
    }
    if ( !raMaterial.isBlock ( ) ) { // Ensure it's a block-like material
     sender.sendMessage(settings.getMessage("error.invalid-material"));
     return true;
    }

    // Create a new map to avoid modifying the original zone's map directly
    // FIX: Initialize EnumMap explicitly with Material.class
    Map<Material, Zone.Action> currentMaterialActions = new EnumMap<>(Material.class);
    currentMaterialActions.putAll(raZone.getMaterialActions()); // Copy existing actions

    if (!currentMaterialActions.containsKey(raMaterial)) {
     sender.sendMessage(settings.getMessage("error.no-material-action"));
     return true;
    }
    currentMaterialActions.remove(raMaterial);

    // Get the World object using Bukkit.getWorld(worldName)
    World raWorld = Bukkit.getWorld(raZone.getWorldName());
    if (raWorld == null) {
     sender.sendMessage(Component.text("Error: World '" + raZone.getWorldName() + "' for zone '" + raZoneName + "' not found.").color(NamedTextColor.RED));
     return true;
    }

    Zone updatedRaZone = new Zone(raZone.getName(), raWorld, raZone.getMin(), raZone.getMax(),
            raZone.getDefaultAction(), currentMaterialActions);
    zoneManager.addOrUpdateZone(updatedRaZone); // This will save the updated zone

    sender.sendMessage(settings.getMessage("command.removeaction-success",
            Placeholder.unparsed("material", raMaterial.name()),
            Placeholder.unparsed("zone", raZoneName)));
    return true;

   case "banned":
    if (!sender.hasPermission("autowarn.banned")) {
     sender.sendMessage(settings.getMessage("error.no-permission"));
     return true;
    }
    if (args.length < 2 || args.length > 3) {
     sender.sendMessage(settings.getMessage("error.usage.banned"));
     return true;
    }

    String bannedSubcommand = args[1].toLowerCase();
    switch (bannedSubcommand) {
     case "add":
      if (args.length != 3) {
       sender.sendMessage(settings.getMessage("error.usage.banned-add"));
       return true;
      }
      Material materialToAdd;
      try {
       materialToAdd = Material.valueOf ( args[ 2 ].toUpperCase ( ) );
      } catch ( IllegalArgumentException e ) {
       sender.sendMessage ( settings.getMessage ( "error.invalid-material" ) );
       return true;
      }
      if ( !materialToAdd.isItem ( ) ) { // Can be any item or block
       sender.sendMessage(settings.getMessage("error.invalid-material"));
       return true;
      }
      if (settings.getGloballyBannedMaterials().contains(materialToAdd)) {
       sender.sendMessage(settings.getMessage("error.material-already-banned"));
       return true;
      }
      settings.addGloballyBannedMaterial(materialToAdd); // Add to settings, saves automatically
      sender.sendMessage(settings.getMessage("command.banned-add-success",
              Placeholder.unparsed("material", materialToAdd.name())));
      return true;

     case "remove":
      if (args.length != 3) {
       sender.sendMessage(settings.getMessage("error.usage.banned-remove"));
       return true;
      }
      Material materialToRemove;
      try {
       materialToRemove = Material.valueOf ( args[ 2 ].toUpperCase ( ) );
      } catch ( IllegalArgumentException e ) {
       sender.sendMessage(settings.getMessage("error.invalid-material"));
       return true;
      }
      if (!settings.getGloballyBannedMaterials().contains(materialToRemove)) {
       sender.sendMessage(settings.getMessage("error.material-not-banned"));
       return true;
      }
      settings.removeGloballyBannedMaterial(materialToRemove); // Remove from settings, saves automatically
      sender.sendMessage(settings.getMessage("command.banned-remove-success",
              Placeholder.unparsed("material", materialToRemove.name())));
      return true;

     case "list":
      if (args.length != 2) {
       sender.sendMessage(settings.getMessage("error.usage.banned"));
       return true;
      }
      Set<Material> bannedMaterials = settings.getGloballyBannedMaterials();
      if (bannedMaterials.isEmpty()) {
       sender.sendMessage(settings.getMessage("command.banned-list-empty"));
      } else {
       sender.sendMessage(settings.getMessage("command.banned-list-header",
               Placeholder.unparsed("count", String.valueOf(bannedMaterials.size()))));
       bannedMaterials.forEach(material ->
               sender.sendMessage(Component.text(" - " + material.name()).color(NamedTextColor.GRAY)));
      }
      return true;

     default:
      sender.sendMessage(settings.getMessage("error.usage.banned"));
      return true;
    }

   case "reload":
    if (!sender.hasPermission("autowarn.reload")) {
     sender.sendMessage(settings.getMessage("error.no-permission"));
     return true;
    }
    plugin.reloadConfig(); // Call the main plugin's reload method
    sender.sendMessage(settings.getMessage("command.reload-success"));
    return true;

   default:
    sendHelp(sender);
    return true;
  }
 }

 private void giveSelectionWand(Player player) {
  ItemStack wand = new ItemStack(Material.BLAZE_ROD); // Or any other suitable item
  ItemMeta meta = wand.getItemMeta();
  if (meta != null) {
   meta.displayName ( settings.getMessage ( "wand.name" ) );
   meta.lore ( Arrays.asList (
     settings.getMessage ( "wand.lore1" ) ,
     settings.getMessage ( "wand.lore2" )
   ));
   // Add persistent data to identify it as the selection wand
   meta.getPersistentDataContainer().set(WAND_KEY, PersistentDataType.STRING, "autowarn_wand");
   wand.setItemMeta(meta);
  }
  player.getInventory().addItem(wand);
  player.sendMessage(settings.getMessage("command.wand-given"));
 }

 private String formatLocation(Location loc) {
  return String.format("%s, %s, %s", loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
 }

 private void sendZoneInfo(CommandSender sender, Zone zone) {
  sender.sendMessage(settings.getMessage("command.info-header", Placeholder.unparsed("zone", zone.getName()))); // Assuming you add this message
  sender.sendMessage(Component.text("  World: ").append(Component.text(zone.getWorldName()).color(NamedTextColor.GRAY)));
  sender.sendMessage(Component.text("  Min: ").append(Component.text(formatVector(zone.getMin())).color(NamedTextColor.GRAY)));
  sender.sendMessage(Component.text("  Max: ").append(Component.text(formatVector(zone.getMax())).color(NamedTextColor.GRAY)));
  sender.sendMessage(Component.text("  Default Action: ").append(Component.text(zone.getDefaultAction().name()).color(NamedTextColor.GRAY)));

  Map<Material, Zone.Action> materialActions = zone.getMaterialActions();
  if (!materialActions.isEmpty()) {
   sender.sendMessage(Component.text("  Material Actions:").color(NamedTextColor.GOLD));
   materialActions.forEach((material, action) ->
           sender.sendMessage(Component.text("    - " + material.name() + ": " + action.name()).color(NamedTextColor.GRAY)));
  } else {
   sender.sendMessage(Component.text("  No specific material actions defined.").color(NamedTextColor.GRAY));
  }
 }

 private String formatVector(Vector vec) {
  return String.format("%d, %d, %d", vec.getBlockX(), vec.getBlockY(), vec.getBlockZ());
 }

 private void sendHelp(CommandSender sender) {
  sender.sendMessage(settings.getMessage("command.help-header"));
  sender.sendMessage(settings.getMessage("command.help.wand"));
  sender.sendMessage(settings.getMessage("command.help.pos"));
  sender.sendMessage(settings.getMessage("command.help.define"));
  sender.sendMessage(settings.getMessage("command.help.remove"));
  sender.sendMessage(settings.getMessage("command.help.list"));
  sender.sendMessage(settings.getMessage("command.help.info"));
  sender.sendMessage(settings.getMessage("command.help.setaction"));
  sender.sendMessage(settings.getMessage("command.help.removeaction"));
  sender.sendMessage(settings.getMessage("command.help.defaultaction"));
  sender.sendMessage(settings.getMessage("command.help.banned"));
  sender.sendMessage(settings.getMessage("command.help.reload"));
 }

 // --- Public methods for ZoneListener to interact with ---

 /**
  * Gets the NamespacedKey used to identify the AutoWarn wand.
  * @return The NamespacedKey for the wand.
  */
 public NamespacedKey getWandKey() {
  return WAND_KEY;
 }

 /**
  * Sets the first selection point for a player.
  * @param uuid The UUID of the player.
  * @param pos The Vector representing the position.
  */
 public void setPos1(UUID uuid, Vector pos) {
  pos1.put(uuid, pos);
 }

 /**
  * Sets the second selection point for a player.
  * @param uuid The UUID of the player.
  * @param pos The Vector representing the position.
  */
 public void setPos2(UUID uuid, Vector pos) {
  pos2.put(uuid, pos);
 }


 @Override
 public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
  List<String> completions = new ArrayList<>();
  List<String> commands = ImmutableList.of("wand", "pos1", "pos2", "define", "remove", "list", "info", "defaultaction", "setaction", "removeaction", "banned", "reload");

  if (args.length == 1) {
   String input = args[ 0 ].toLowerCase ( );
   completions.addAll ( commands.stream ( )
                          .filter ( cmd -> cmd.toLowerCase ( ).startsWith ( input ) )
                          .collect ( Collectors.toList ( ) ) );
  } else if (args.length == 2) {
   String input = args[ 1 ].toLowerCase ( );
   switch (args[0].toLowerCase()) {
    case "remove" , "info" , "defaultaction" , "setaction" , "removeaction" -> {
     List < String > zoneNames =
       zoneManager.getAllZones ( ).stream ( ).map ( Zone :: getName ).collect ( Collectors.toList ( ) );
     completions.addAll ( zoneNames.stream ( )
                            .filter ( name -> name.toLowerCase ( ).startsWith ( input ) )
                            .collect ( Collectors.toList ( ) ) );
    }
    case "banned" -> {
     List < String > bannedCommands = ImmutableList.of ( "add" , "remove" , "list" );
     completions.addAll ( bannedCommands.stream ( )
                            .filter ( cmd -> cmd.toLowerCase ( ).startsWith ( input ) )
                            .collect ( Collectors.toList ( ) ) );
    }
   }
  } else if (args.length == 3) {
   String input = args[ 2 ].toLowerCase ( );
   switch (args[0].toLowerCase()) {
    case "defaultaction" -> {
     List < String > actions =
       Stream.of ( Zone.Action.values ( ) ).map ( Enum :: name ).collect ( Collectors.toList ( ) );
     completions.addAll ( actions.stream ( )
                            .filter ( action -> action.toLowerCase ( ).startsWith ( input ) )
                            .collect ( Collectors.toList ( ) ) );
    }
    case "setaction" , "removeaction" -> {
     List < String > materials =
       Arrays.stream ( Material.values ( ) ).filter ( Material :: isBlock ).map ( Enum :: name ).collect ( Collectors.toList ( ) );
     completions.addAll ( materials.stream ( )
                            .filter ( material -> material.toLowerCase ( ).startsWith ( input ) )
                            .collect ( Collectors.toList ( ) ) );
    }
    case "banned" -> {
     if ("add".equalsIgnoreCase(args[1])) {
      List < String > materials =
        Arrays.stream ( Material.values ( ) ).filter ( Material :: isItem ).map ( Enum :: name ).collect ( Collectors.toList ( ) );
      completions.addAll ( materials.stream ( )
                             .filter ( material -> material.toLowerCase ( ).startsWith ( input ) )
                             .collect ( Collectors.toList ( ) ) );
     } else if ("remove".equalsIgnoreCase(args[1])) {
      List < String > bannedMaterials =
        settings.getGloballyBannedMaterials ( ).stream ( ).map ( Enum :: name ).collect ( Collectors.toList ( ) );
      completions.addAll ( bannedMaterials.stream ( )
                             .filter ( material -> material.toLowerCase ( ).startsWith ( input ) )
                             .collect ( Collectors.toList ( ) ) );
     }
    }
   }
  } else if (args.length == 4) {
   if ("setaction".equalsIgnoreCase(args[0])) {
    String input = args[ 3 ].toLowerCase ( );
    List < String > actions =
      Stream.of ( Zone.Action.values ( ) ).map ( Enum :: name ).collect ( Collectors.toList ( ) );
    completions.addAll ( actions.stream ( )
                           .filter ( action -> action.toLowerCase ( ).startsWith ( input ) )
                           .collect ( Collectors.toList ( ) ) );
   }
  }
  Collections.sort(completions);
  return completions;
 }
}