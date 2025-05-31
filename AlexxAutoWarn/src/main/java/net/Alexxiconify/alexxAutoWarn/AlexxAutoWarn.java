package net.Alexxiconify.alexxAutoWarn;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.BukkitCommandManager;
import co.aikar.commands.CommandHelp;
import co.aikar.commands.annotation.*;
import net.Alexxiconify.alexxAutoWarn.region.Region;
import net.Alexxiconify.alexxAutoWarn.region.RegionSelection;
import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

// Import the new RegionListener
import net.Alexxiconify.alexxAutoWarn.region.RegionListener;

public final class AlexxAutoWarn extends JavaPlugin implements Listener {

 private CoreProtectAPI coreProtectAPI;
 private File customConfigFile;
 private FileConfiguration customConfig;
 private BukkitCommandManager commandManager;

 // --- New fields for Region management ---
 private Map<String, Region> regions; // Stores all defined regions by name
 private Map<UUID, RegionSelection> playerSelections; // Stores ongoing region selections for players

 @Override
 public void onEnable() {
  getLogger().info("AlexxAutoWarn plugin starting...");

  // Register custom serializers for configuration.
  // IMPORTANT: Must be called before loading config.
  ConfigurationSerialization.registerClass(Region.class, "AlexxAutoWarnRegion");

  this.saveDefaultConfig(); // Creates config.yml if it doesn't exist
  loadCustomConfig(); // Load messages.yml
  setupCoreProtect();

  // --- Initialize region maps ---
  regions = new HashMap<>();
  playerSelections = new HashMap<>();
  loadRegions(); // Load regions from config

  commandManager = new BukkitCommandManager(this);
  commandManager.registerCommand(new AlexxAutoWarnCommand());

  // --- Register the new RegionListener ---
  getServer().getPluginManager().registerEvents(new RegionListener(this), this);

  getLogger().info("AlexxAutoWarn plugin enabled!");
 }

 @Override
 public void onDisable() {
  getLogger().info("AlexxAutoWarn plugin shutting down...");
  saveRegions(); // Save all regions to config
  getLogger().info("AlexxAutoWarn plugin disabled!");
 }

 private void setupCoreProtect() {
  Plugin plugin = getServer().getPluginManager().getPlugin("CoreProtect");
  if (plugin == null || !(plugin instanceof CoreProtect)) {
   getLogger().warning("CoreProtect not found or not enabled!");
   coreProtectAPI = null;
   return;
  }

  RegisteredServiceProvider<CoreProtectAPI> rsp = Bukkit.getServicesManager().getRegistration(CoreProtectAPI.class);
  if (rsp == null) {
   getLogger().warning("CoreProtectAPI service not found!");
   coreProtectAPI = null;
   return;
  }

  coreProtectAPI = rsp.getProvider();
  if (coreProtectAPI == null || !coreProtectAPI.isEnabled()) {
   getLogger().warning("CoreProtectAPI not enabled!");
   coreProtectAPI = null;
  } else {
   getLogger().info("CoreProtectAPI loaded successfully!");
  }
 }

 // --- New methods for region management ---

 public Map<String, Region> getRegions() {
  return regions;
 }

 public Map<UUID, RegionSelection> getPlayerSelections() {
  return playerSelections;
 }

 // Load regions from config.yml
 private void loadRegions() {
  if (getConfig().isConfigurationSection("regions")) {
   for (String key : getConfig().getConfigurationSection("regions").getKeys(false)) {
    try {
     // Get the map representation and deserialize
     Object obj = getConfig().get("regions." + key);
     if (obj instanceof Map) {
      Map<String, Object> serializedRegion = (Map<String, Object>) obj;
      // Directly use ConfigurationSerialization.deserializeObject
      Region region = (Region) ConfigurationSerialization.deserializeObject(serializedRegion, Region.class);
      regions.put(region.getName(), region);
      getLogger().info("Loaded region: " + region.getName());
     } else {
      getLogger().warning("Invalid region format for key: " + key);
     }
    } catch (Exception e) {
     getLogger().log(Level.SEVERE, "Failed to load region: " + key, e);
    }
   }
  }
 }


 // Save regions to config.yml
 private void saveRegions() {
  getConfig().set("regions", null); // Clear existing regions section to prevent old data
  for (Region region : regions.values()) {
   getConfig().set("regions." + region.getName(), region); // Serialize Region object to config
  }
  saveConfig(); // Save the main config.yml
 }

 // --- Existing methods from previous versions ---

 public CoreProtectAPI getCoreProtectAPI() {
  return coreProtectAPI;
 }

 public String getMessage(String path) {
  String message = getCustomConfig().getString(path);
  if (message == null) {
   return ChatColor.RED + "Error: Message '" + path + "' not found in messages.yml";
  }
  return ChatColor.translateAlternateColorCodes('&', message);
 }

 private void loadCustomConfig() {
  customConfigFile = new File(getDataFolder(), "messages.yml");
  if (!customConfigFile.exists()) {
   saveResource("messages.yml", false);
  }
  customConfig = YamlConfiguration.loadConfiguration(customConfigFile);

  // Look for updates from the included messages.yml
  InputStream defaultStream = getResource("messages.yml");
  if (defaultStream != null) {
   YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
   customConfig.setDefaults(defaultConfig);
   customConfig.options().copyDefaults(true); // Copy missing defaults
   try {
    customConfig.save(customConfigFile); // Save updated config
   } catch (IOException e) {
    getLogger().log(Level.SEVERE, "Could not save messages.yml!", e);
   }
  }
 }

 public FileConfiguration getCustomConfig() {
  return customConfig;
 }

 private String getBlockPlacer(Block block) {
  // ... (This method should be present and correct as fixed in the previous turn)
  // Ensure you have only ONE instance of this method in your AlexxAutoWarn.java
  // As you indicated you fixed it, it should be fine here.
  if (coreProtectAPI == null) return null;

  FileConfiguration config = getConfig();
  int lookupTime = config.getInt("settings.coreprotect.lookup-time-seconds", 600);

  List<String[]> lookupResult = null;
  try {
   lookupResult = coreProtectAPI.blockLookup(block, (int) (System.currentTimeMillis() / 1000L) - lookupTime);
  } catch (Exception e) {
   getLogger().severe(getMessage("plugin-coreprotect-lookup-failed")
           .replace("{location}", formatLocation(block.getLocation()))
           .replace("{message}", e.getMessage()));
   return null;
  }

  if (lookupResult != null && !lookupResult.isEmpty()) {
   for (String[] result : lookupResult) {
    net.coreprotect.consumer.ParseResult parseResult = coreProtectAPI.parseResult(result);
    if (parseResult.getActionId() == 1) { // ActionId 1 is block placement
     return parseResult.getPlayer();
    }
   }
  }
  return null;
 }

 private String formatLocation(Location loc) {
  if (loc == null) return "N/A";
  return String.format("%s (%.1f, %.1f, %.1f)", loc.getWorld() != null ? loc.getWorld().getName() : "Unknown",
          loc.getX(), loc.getY(), loc.getZ());
 }


 @CommandAlias("autowarn|aw")
 @Description("Main command for AlexxAutoWarn plugin.")
 public class AlexxAutoWarnCommand extends BaseCommand {

  @Subcommand("reload")
  @Description("Reloads the plugin configuration.")
  @CommandPermission("alexxautowarn.admin.reload")
  public void onReload(CommandSender sender) {
   reloadConfig();
   loadCustomConfig();
   setupCoreProtect(); // Re-setup in case plugin was added/removed
   // --- Reload regions as well ---
   regions.clear(); // Clear existing regions
   loadRegions(); // Load fresh regions from config
   sender.sendMessage(getMessage("plugin-reloaded"));
  }

  @Subcommand("check")
  @Description("Checks if a block was placed by a player within the last 10 minutes.")
  @CommandPermission("alexxautowarn.check")
  public void onCheck(Player player) {
   Block block = player.getTargetBlock(null, 10); // Check block up to 10 blocks away
   if (block == null || block.getType() == Material.AIR) {
    player.sendMessage(getMessage("check-no-block"));
    return;
   }

   String placer = getBlockPlacer(block);

   if (placer != null) {
    player.sendMessage(getMessage("check-placed-by")
            .replace("{player}", placer)
            .replace("{location}", formatLocation(block.getLocation())));
   } else {
    player.sendMessage(getMessage("check-not-placed-by-player")
            .replace("{location}", formatLocation(block.getLocation())));
   }
  }

  // --- New Commands for Region Management ---

  @Subcommand("region")
  @CommandAlias("region")
  @Description("Manage and configure protected regions.")
  public class RegionSubCommand extends BaseCommand {

   @Subcommand("select")
   @Description("Starts or continues a region selection process.")
   @CommandPermission("alexxautowarn.region.admin")
   @Syntax("<name>")
   public void onRegionSelect(Player player, String regionName) {
    if (regions.containsKey(regionName)) {
     player.sendMessage(getMessage("region-name-exists").replace("{name}", regionName));
     return;
    }
    RegionSelection selection = playerSelections.getOrDefault(player.getUniqueId(), new RegionSelection(regionName, player.getWorld()));
    selection.setRegionName(regionName); // Update name in case of ongoing selection
    selection.setWorld(player.getWorld()); // Ensure correct world
    playerSelections.put(player.getUniqueId(), selection);
    player.sendMessage(getMessage("region-selection-started").replace("{name}", regionName));
    player.sendMessage(getMessage("region-selection-prompt-pos1"));
   }

   @Subcommand("pos1")
   @Description("Sets the first position for region selection.")
   @CommandPermission("alexxautowarn.region.admin")
   public void onRegionPos1(Player player) {
    RegionSelection selection = playerSelections.get(player.getUniqueId());
    if (selection == null || !selection.getWorld().equals(player.getWorld())) {
     player.sendMessage(getMessage("region-no-active-selection"));
     return;
    }
    selection.setPos1(player.getLocation().getBlock().getLocation());
    player.sendMessage(getMessage("region-pos1-set").replace("{location}", formatLocation(selection.getPos1())));
    if (selection.isComplete()) {
     createRegionFromSelection(player, selection);
    } else {
     player.sendMessage(getMessage("region-selection-prompt-pos2"));
    }
   }

   @Subcommand("pos2")
   @Description("Sets the second position for region selection.")
   @CommandPermission("alexxautowarn.region.admin")
   public void onRegionPos2(Player player) {
    RegionSelection selection = playerSelections.get(player.getUniqueId());
    if (selection == null || !selection.getWorld().equals(player.getWorld())) {
     player.sendMessage(getMessage("region-no-active-selection"));
     return;
    }
    selection.setPos2(player.getLocation().getBlock().getLocation());
    player.sendMessage(getMessage("region-pos2-set").replace("{location}", formatLocation(selection.getPos2())));
    if (selection.isComplete()) {
     createRegionFromSelection(player, selection);
    } else {
     player.sendMessage(getMessage("region-selection-prompt-pos1"));
    }
   }

   @Subcommand("create")
   @Description("Creates a region from your current selection.")
   @CommandPermission("alexxautowarn.region.admin")
   @Syntax("<name>")
   // This command is technically redundant if pos1/pos2 complete selection,
   // but can be used as a final confirmation or if a player forgets the name.
   public void onRegionCreate(Player player, @Optional String regionName) {
    RegionSelection selection = playerSelections.get(player.getUniqueId());
    if (selection == null || !selection.isComplete()) {
     player.sendMessage(getMessage("region-selection-incomplete"));
     return;
    }
    if (regionName == null || regionName.isEmpty()) {
     // Use the name from the selection if not provided
     regionName = selection.getRegionName();
    } else {
     // Update the selection name if a new one is provided
     selection.setRegionName(regionName);
    }

    if (regions.containsKey(regionName)) {
     player.sendMessage(getMessage("region-name-exists").replace("{name}", regionName));
     return;
    }
    createRegionFromSelection(player, selection);
   }

   private void createRegionFromSelection(Player player, RegionSelection selection) {
    Region newRegion = new Region(selection.getRegionName(), selection.getWorld(), selection.getPos1().toVector(), selection.getPos2().toVector());
    regions.put(newRegion.getName(), newRegion);
    saveRegions();
    playerSelections.remove(player.getUniqueId()); // Clear selection
    player.sendMessage(getMessage("region-created")
            .replace("{name}", newRegion.getName())
            .replace("{world}", newRegion.getWorldName())
            .replace("{min}", formatLocation(newRegion.getMin().toLocation(newRegion.getWorld())))
            .replace("{max}", formatLocation(newRegion.getMax().toLocation(newRegion.getWorld()))));
   }

   @Subcommand("delete")
   @Description("Deletes an existing region.")
   @CommandPermission("alexxautowarn.region.admin")
   @Syntax("<name>")
   public void onRegionDelete(Player player, String regionName) {
    if (!regions.containsKey(regionName)) {
     player.sendMessage(getMessage("region-not-found").replace("{name}", regionName));
     return;
    }
    regions.remove(regionName);
    saveRegions();
    player.sendMessage(getMessage("region-deleted").replace("{name}", regionName));
   }

   @Subcommand("list")
   @Description("Lists all defined regions.")
   @CommandPermission("alexxautowarn.region.admin")
   public void onRegionList(CommandSender sender) {
    if (regions.isEmpty()) {
     sender.sendMessage(getMessage("region-list-empty"));
     return;
    }
    sender.sendMessage(getMessage("region-list-header"));
    for (Region region : regions.values()) {
     sender.sendMessage(getMessage("region-list-entry")
             .replace("{name}", region.getName())
             .replace("{world}", region.getWorldName())
             .replace("{min}", String.format("%.0f,%.0f,%.0f", region.getMin().getX(), region.getMin().getY(), region.getMin().getZ()))
             .replace("{max}", String.format("%.0f,%.0f,%.0f", region.getMax().getX(), region.getMax().getY(), region.getMax().getZ())));
    }
   }

   @Subcommand("info")
   @Description("Displays detailed information about a region.")
   @CommandPermission("alexxautowarn.region.admin")
   @Syntax("<name>")
   public void onRegionInfo(CommandSender sender, String regionName) {
    Region region = regions.get(regionName);
    if (region == null) {
     sender.sendMessage(getMessage("region-not-found").replace("{name}", regionName));
     return;
    }

    sender.sendMessage(getMessage("region-info-header").replace("{name}", region.getName()));
    sender.sendMessage(getMessage("region-info-world").replace("{world}", region.getWorldName()));
    sender.sendMessage(getMessage("region-info-coords")
            .replace("{min}", String.format("%.0f,%.0f,%.0f", region.getMin().getX(), region.getMin().getY(), region.getMin().getZ()))
            .replace("{max}", String.format("%.0f,%.0f,%.0f", region.getMax().getX(), region.getMax().getY(), region.getMax().getZ())));

    sender.sendMessage(getMessage("region-info-banned-blocks")
            .replace("{list}", region.getBannedBlockPlacement().isEmpty() ? "None" :
                    region.getBannedBlockPlacement().stream().map(Enum::name).collect(Collectors.joining(", "))));
    sender.sendMessage(getMessage("region-info-ban-chest-interaction")
            .replace("{status}", region.isBanChestInteraction() ? "Yes" : "No"));
    sender.sendMessage(getMessage("region-info-banned-items")
            .replace("{list}", region.getBannedItemUsage().isEmpty() ? "None" :
                    region.getBannedItemUsage().stream().map(Enum::name).collect(Collectors.joining(", "))));
   }

   // --- GUI Command will go here later ---
   // @Subcommand("gui")
   // @Description("Opens the configuration GUI for a region.")
   // @CommandPermission("alexxautowarn.region.admin")
   // @Syntax("<name>")
   // public void onRegionGui(Player player, String regionName) {
   //    // Logic to open GUI will be here
   // }

   @HelpCommand
   @CatchUnknown
   public void doHelp(CommandSender sender, CommandHelp help) {
    help.showHelp();
   }
  }
 }
}