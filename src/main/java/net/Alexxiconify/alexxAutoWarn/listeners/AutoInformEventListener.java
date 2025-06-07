package net.Alexxiconify.alexxAutoWarn.listeners;

import net.Alexxiconify.alexxAutoWarn.AlexxAutoWarn;
import net.Alexxiconify.alexxAutoWarn.managers.PlayerSelectionManager;
import net.Alexxiconify.alexxAutoWarn.managers.ZoneManager;
import net.Alexxiconify.alexxAutoWarn.objects.AutoInformZone;
import net.Alexxiconify.alexxAutoWarn.objects.ZoneAction;
import net.Alexxiconify.alexxAutoWarn.utils.MessageUtil;
import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
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
import org.bukkit.plugin.Plugin;

import java.util.logging.Level;


/**
 * Handles all event listeners for the AlexxAutoWarn plugin.
 * This includes monitoring block placements, bucket empties, and player interactions
 * to enforce zone rules and wand functionality.
 */
public class AutoInformEventListener implements Listener {

 private final AlexxAutoWarn plugin;
 private final ZoneManager zoneManager;
 private final MessageUtil messageUtil;
 private final PlayerSelectionManager playerSelectionManager;
 private final NamespacedKey wandKey;
 private final CoreProtectAPI coreProtectAPI;

 public AutoInformEventListener(AlexxAutoWarn plugin) {
  this.plugin = plugin;
  this.zoneManager = plugin.getZoneManager();
  this.messageUtil = plugin.getMessageUtil();
  this.playerSelectionManager = plugin.getPlayerSelectionManager();
  this.wandKey = plugin.getWandKey();
  this.coreProtectAPI = getCoreProtectAPI();
 }

 /**
  * Retrieves the CoreProtectAPI instance.
  *
  * @return CoreProtectAPI instance if CoreProtect is enabled, otherwise null.
  */
 private CoreProtectAPI getCoreProtectAPI() {
  Plugin coreProtectPlugin = Bukkit.getPluginManager().getPlugin("CoreProtect");
  if (coreProtectPlugin instanceof CoreProtect coreProtect) {
   CoreProtectAPI api = coreProtect.getAPI();
   if (api != null && api.isEnabled()) {
    plugin.getLogger().info("CoreProtectAPI enabled.");
    return api;
   } else {
    plugin.getLogger().warning("CoreProtectAPI not enabled. Logging features will be disabled.");
   }
  }
  plugin.getLogger().warning("CoreProtect plugin not found or not an instance of CoreProtect. Logging features will be disabled.");
  return null;
 }

 /**
  * Handles block placement events to check against zone rules.
  * Checks if the placed block is within a defined zone and applies the corresponding action.
  *
  * @param event The BlockPlaceEvent.
  */
 @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
 public void onBlockPlace(BlockPlaceEvent event) {
  Player player = event.getPlayer();
  // Bypass for players with alexxautowarn.bypass permission
  if (player.hasPermission("alexxautowarn.bypass")) {
   messageUtil.log(Level.FINE, "debug-bypass-active", "{player}", player.getName(), "{event_type}", "BlockPlaceEvent");
   return;
  }

  Location location = event.getBlock().getLocation();
  Material material = event.getBlockPlaced().getType();
  handleZoneAction(player, location, material, event, "place");
 }

 /**
  * Handles player bucket empty events to check against zone rules.
  * Checks if the bucket liquid placement is within a defined zone and applies the corresponding action.
  *
  * @param event The PlayerBucketEmptyEvent.
  */
 @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
 public void onPlayerBucketEmpty(PlayerBucketEmptyEvent event) {
  Player player = event.getPlayer();
  // Bypass for players with alexxautowarn.bypass permission
  if (player.hasPermission("alexxautowarn.bypass")) {
   messageUtil.log(Level.FINE, "debug-bypass-active", "{player}", player.getName(), "{event_type}", "PlayerBucketEmptyEvent");
   return;
  }

  Location location = event.getBlockClicked().getLocation();
  Material material = event.getBucket();
  handleZoneAction(player, location, material, event, "empty");
 }

 /**
  * Handles player interaction events, primarily for the selection wand and container access.
  * This method is crucial for players to define zones with the wand.
  *
  * @param event The PlayerInteractEvent.
  */
 @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
 public void onPlayerInteract(PlayerInteractEvent event) {
  Player player = event.getPlayer();
  ItemStack itemInHand = player.getInventory().getItemInMainHand();

  // Check if the item in hand is the custom wand
  if (itemInHand.hasItemMeta() && itemInHand.getItemMeta().getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE)) {
   Block clickedBlock = event.getClickedBlock();
   if (clickedBlock == null) {
    return;
   }

   // Cancel the event to prevent block interaction with the wand
   event.setCancelled(true);

   if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
    playerSelectionManager.setSelection(player, "pos1", clickedBlock.getLocation());
    messageUtil.sendMessage(player, "wand-pos1-set", new Object[]{
            "{location}", String.format("[%d, %d, %d]",
            clickedBlock.getX(), clickedBlock.getY(), clickedBlock.getZ()),
            "{world}", clickedBlock.getWorld().getName()
    });
    messageUtil.log(Level.FINE, "debug-wand-pos-set", "{player}", player.getName(), "{position}", "pos1",
            "{location_string}", String.format("%s [%d, %d, %d]", clickedBlock.getWorld().getName(), clickedBlock.getX(), clickedBlock.getY(), clickedBlock.getZ()));
   } else if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
    playerSelectionManager.setSelection(player, "pos2", clickedBlock.getLocation());
    messageUtil.sendMessage(player, "wand-pos2-set", new Object[]{
            "{location}", String.format("[%d, %d, %d]",
            clickedBlock.getX(), clickedBlock.getY(), clickedBlock.getZ()),
            "{world}", clickedBlock.getWorld().getName()
    });
    messageUtil.log(Level.FINE, "debug-wand-pos-set", "{player}", player.getName(), "{position}", "pos2",
            "{location_string}", String.format("%s [%d, %d, %d]", clickedBlock.getWorld().getName(), clickedBlock.getX(), clickedBlock.getY(), clickedBlock.getZ()));
   }
   return;
  }

  // Check for container access for logging/alerts
  if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
   Block clickedBlock = event.getClickedBlock();
   if (clickedBlock != null && clickedBlock.getState() instanceof Container) {
    // Bypass for players with alexxautowarn.bypass permission
    if (player.hasPermission("alexxautowarn.bypass")) {
     messageUtil.log(Level.FINE, "debug-bypass-active", "{player}", player.getName(), "{event_type}", "ContainerAccessEvent");
     return;
    }
    Location location = clickedBlock.getLocation();
    Material material = clickedBlock.getType();
    handleContainerAccess(player, location, material);
   }
  }
 }

 /**
  * Centralized method to handle zone-related actions (DENY, ALERT, ALLOW) for block changes.
  *
  * @param player The player performing the action.
  * @param location The location where the action occurred.
  * @param material The material involved in the action.
  * @param event The cancellable event.
  * @param actionType Description of the action (e.g., "place", "empty").
  */
 private void handleZoneAction(Player player, Location location, Material material, Cancellable event, String actionType) {
  AutoInformZone zone = zoneManager.getZoneAtLocation(location);

  // Global ban check takes precedence if no zone applies or overrides
  if (zoneManager.isGloballyBanned(material)) {
   event.setCancelled(true);
   messageUtil.sendMessage(player, "action-denied-globally", new Object[]{
           "{material}", material.name(),
           "{world}", location.getWorld().getName(),
           "{x}", String.valueOf(location.getBlockX()),
           "{y}", String.valueOf(location.getBlockY()),
           "{z}", String.valueOf(location.getBlockZ())
   });
   messageUtil.log(Level.INFO, "action-denied-globally-console",
           "{player}", player.getName(),
           "{material}", material.name(),
           "{world}", location.getWorld().getName(),
           "{x}", String.valueOf(location.getBlockX()),
           "{y}", String.valueOf(location.getBlockY()),
           "{z}", String.valueOf(location.getBlockZ()));
   return;
  }

  if (zone != null) {
   ZoneAction effectiveAction = zone.getEffectiveAction(material);
   String zoneName = zone.getName();

   switch (effectiveAction) {
    case DENY:
     event.setCancelled(true);
     messageUtil.sendMessage(player, "action-denied", new Object[]{
             "{material}", material.name(),
             "{zone_name}", zoneName,
             "{world}", location.getWorld().getName(),
             "{x}", String.valueOf(location.getBlockX()),
             "{y}", String.valueOf(location.getBlockY()),
             "{z}", String.valueOf(location.getBlockZ())
     });
     messageUtil.log(Level.INFO, "action-denied-console",
             "{player}", player.getName(),
             "{material}", material.name(),
             "{zone_name}", zoneName,
             "{world}", location.getWorld().getName(),
             "{x}", String.valueOf(location.getBlockX()),
             "{y}", String.valueOf(location.getBlockY()),
             "{z}", String.valueOf(location.getBlockZ()));
     break;
    case ALERT:
     messageUtil.sendAlert(player, "action-alert", new Object[]{
             "{player}", player.getName(),
             "{material}", material.name(),
             "{zone_name}", zoneName,
             "{world}", location.getWorld().getName(),
             "{x}", String.valueOf(location.getBlockX()),
             "{y}", String.valueOf(location.getBlockY()),
             "{z}", String.valueOf(location.getBlockZ())
     });
     if (coreProtectAPI != null) {
      coreProtectAPI.logPlacement(player.getName(), location.getBlock(), material, Material.AIR);
     }
     messageUtil.log(Level.INFO, "action-alert-console",
             "{player}", player.getName(),
             "{material}", material.name(),
             "{zone_name}", zoneName,
             "{world}", location.getWorld().getName(),
             "{x}", String.valueOf(location.getBlockX()),
             "{y}", String.valueOf(location.getBlockY()),
             "{z}", String.valueOf(location.getBlockZ()));
     break;
    case ALLOW:
     messageUtil.log(Level.FINE, "action-allowed-console",
             "{player}", player.getName(),
             "{material}", material.name(),
             "{zone_name}", zoneName,
             "{world}", location.getWorld().getName(),
             "{x}", String.valueOf(location.getBlockX()),
             "{y}", String.valueOf(location.getBlockY()),
             "{z}", String.valueOf(location.getBlockZ()));
     if (coreProtectAPI != null) {
      coreProtectAPI.logPlacement(player.getName(), location.getBlock(), material, Material.AIR);
     }
     break;
    default:
     plugin.getLogger().log(Level.SEVERE, "Unexpected effective action for material " + material.name() + " in zone " + zoneName + ": " + effectiveAction);
     throw new IllegalStateException("Unexpected value: " + effectiveAction);
   }
  } else {
   messageUtil.log(Level.FINE, "debug-no-zone-or-global-ban-action",
           "{player}", player.getName(),
           "{action_type}", actionType,
           "{material}", material.name(),
           "{location}", String.format("%s [%d, %d, %d]", location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ()));
  }
 }

 /**
  * Handles container access for logging/alerts.
  *
  * @param player The player accessing the container.
  * @param location The location of the container.
  * @param material The material of the container.
  */
 private void handleContainerAccess(Player player, Location location, Material material) {
  AutoInformZone zone = zoneManager.getZoneAtLocation(location);

  if (zone != null) {
   ZoneAction effectiveAction = zone.getEffectiveAction(material);

   if (effectiveAction == ZoneAction.ALERT) {
    messageUtil.sendAlert(player, "alert-container-access", new Object[]{
            "{player}", player.getName(),
            "{material}", material.name(),
            "{zone_name}", zone.getName(),
            "{world}", location.getWorld().getName(),
            "{x}", String.valueOf(location.getBlockX()),
            "{y}", String.valueOf(location.getBlockY()),
            "{z}", String.valueOf(location.getBlockZ())
    });
    messageUtil.log(Level.INFO, "action-alert-container-access-console",
            "{player}", player.getName(),
            "{material}", material.name(),
            "{zone_name}", zone.getName(),
            "{world}", location.getWorld().getName(),
            "{x}", String.valueOf(location.getBlockX()),
            "{y}", String.valueOf(location.getBlockY()),
            "{z}", String.valueOf(location.getBlockZ()));
    if (coreProtectAPI != null) {
     BlockState containerState = location.getBlock().getState();
     if (containerState instanceof Container) {
      coreProtectAPI.logContainerAccess(player.getName(), containerState);
     } else {
      plugin.getLogger().warning("Attempted to log container access at " + location + " but block is not a container state.");
     }
    }
   } else if (effectiveAction == ZoneAction.DENY) {
    messageUtil.sendMessage(player, "action-denied-container-access", new Object[]{
            "{material}", material.name(),
            "{zone_name}", zone.getName(),
            "{world}", location.getWorld().getName(),
            "{x}", String.valueOf(location.getBlockX()),
            "{y}", String.valueOf(location.getBlockY()),
            "{z}", String.valueOf(location.getBlockZ())
    });
    messageUtil.log(Level.INFO, "action-denied-container-access-console",
            "{player}", player.getName(),
            "{material}", material.name(),
            "{zone_name}", zone.getName(),
            "{world}", location.getWorld().getName(),
            "{x}", String.valueOf(location.getBlockX()),
            "{y}", String.valueOf(location.getBlockY()),
            "{z}", String.valueOf(location.getBlockZ()));
    if (coreProtectAPI != null) {
     BlockState containerState = location.getBlock().getState();
     if (containerState instanceof Container) {
      coreProtectAPI.logContainerAccess(player.getName(), containerState);
     }
    }
   }
  }
 }
}