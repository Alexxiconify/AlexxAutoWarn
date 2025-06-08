package net.alexxiconify.autowarn.listeners;

import net.alexxiconify.autowarn.AutoWarnPlugin;
import net.alexxiconify.autowarn.commands.AutoWarnCommand;
import net.alexxiconify.autowarn.managers.ZoneManager;
import net.alexxiconify.autowarn.objects.Zone;
import net.alexxiconify.autowarn.utils.Settings;
import net.coreprotect.CoreProtectAPI;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.logging.Level;

/**
 * Handles all event listeners for the AutoWarn plugin.
 * This includes monitoring block placements and player interactions to enforce zone rules and wand functionality.
 */
public class ZoneListener implements Listener {

 private final AutoWarnPlugin plugin;
 private final Settings settings;
 private final ZoneManager zoneManager;
 private final AutoWarnCommand command;
 private final NamespacedKey wandKey;
 private final CoreProtectAPI coreProtectAPI;

 public ZoneListener(AutoWarnPlugin plugin) {
  this.plugin = plugin;
  this.settings = plugin.getSettings();
  this.zoneManager = plugin.getZoneManager();
  this.coreProtectAPI = plugin.getCoreProtectAPI();
  // A bit of a workaround to access command state, ideally use a separate selection manager
  this.command = (AutoWarnCommand) plugin.getCommand("autowarn").getExecutor();
  this.wandKey = command.getWandKey();
 }

 @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
 public void onBlockPlace(BlockPlaceEvent event) {
  handleAction(event.getPlayer(), event.getBlock().getLocation(), event.getBlock().getType(), event);
 }

 @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
 public void onPlayerBucketEmpty(PlayerBucketEmptyEvent event) {
  Material placedMaterial = event.getBucket() == Material.LAVA_BUCKET ? Material.LAVA : Material.WATER;
  handleAction(event.getPlayer(), event.getBlockClicked().getLocation(), placedMaterial, event);
 }

 @EventHandler(priority = EventPriority.NORMAL) // Not ignoring cancelled to handle wand clicks
 public void onPlayerInteract(PlayerInteractEvent event) {
  Player player = event.getPlayer();
  ItemStack handItem = event.getItem();

  // --- Wand Functionality ---
  if (handItem != null && handItem.hasItemMeta() && handItem.getItemMeta().getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE)) {
   event.setCancelled(true); // Always cancel event when using the wand
   Block clickedBlock = event.getClickedBlock();
   if (clickedBlock == null) return;

   if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
    command.setPos1(player.getUniqueId(), clickedBlock.getLocation().toVector());
    player.sendActionBar(settings.getMessage("wand.pos1-set", Placeholder.unparsed("coords", formatLocation(clickedBlock.getLocation()))));
   } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
    command.setPos2(player.getUniqueId(), clickedBlock.getLocation().toVector());
    player.sendActionBar(settings.getMessage("wand.pos2-set", Placeholder.unparsed("coords", formatLocation(clickedBlock.getLocation()))));
   }
   return;
  }

  // --- Container Access Monitoring ---
  if (settings.isMonitorChestAccess() && event.getAction() == Action.RIGHT_CLICK_BLOCK) {
   Block clickedBlock = event.getClickedBlock();
   if (clickedBlock != null && clickedBlock.getState() instanceof Container) {
    handleAction(player, clickedBlock.getLocation(), clickedBlock.getType(), event);
   }
  }
 }

 /**
  * Centralized method to handle a player action at a specific location.
  */
 private void handleAction(Player player, Location location, Material material, Cancellable event) {
  if (player.hasPermission("autowarn.bypass")) {
   return;
  }

  // Check globally banned materials first
  if (settings.getGloballyBannedMaterials().contains(material)) {
   processAction(Zone.Action.DENY, player, location, material, "Global", event);
   return;
  }

  // Check for zone-specific rules
  Zone zone = zoneManager.getZoneAt(location);
  if (zone != null) {
   Zone.Action action = zone.getActionFor(material);
   processAction(action, player, location, material, zone.getName(), event);
  }
 }

 /**
  * Processes the determined action (DENY, ALERT, ALLOW), sends messages, and logs.
  */
 private void processAction(Zone.Action action, Player player, Location loc, Material mat, String zoneName, Cancellable event) {
  var placeholders = new TagResolver[]{
          Placeholder.unparsed("player", player.getName()),
          Placeholder.unparsed("material", mat.name().toLowerCase().replace('_', ' ')),
          Placeholder.unparsed("zone", zoneName),
          Placeholder.unparsed("location", formatLocation(loc))
  };

  String logMessage = String.format("%s performed %s with %s in %s at %s", player.getName(), action.name(), mat.name(), zoneName, formatLocation(loc));

  switch (action) {
   case DENY:
    event.setCancelled(true);
    player.sendMessage(settings.getMessage("action.denied", placeholders));
    settings.log(Level.INFO, "[DENIED] " + logMessage);
    logToCoreProtect(player.getName(), loc, mat);
    break;
   case ALERT:
    // Alert staff with permission
    Bukkit.getOnlinePlayers().forEach(p -> {
     if (p.hasPermission("autowarn.notify")) {
      p.sendMessage(settings.getMessage("action.alert", placeholders));
     }
    });
    settings.log(Level.INFO, "[ALERT] " + logMessage);
    logToCoreProtect(player.getName(), loc, mat);
    break;
   case ALLOW:
    if (settings.isDebugLogAllowedActions()) {
     settings.log(Level.INFO, "[ALLOWED] " + logMessage);
     logToCoreProtect(player.getName(), loc, mat);
    }
    break;
  }
 }

 private void logToCoreProtect(String user, Location location, Material material) {
  if (coreProtectAPI != null) {
   coreProtectAPI.logPlacement(user, location, material, null);
  }
 }

 private String formatLocation(Location loc) {
  return String.format("%s: %d, %d, %d", loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
 }
}