package net.alexxiconify.alexxautowarn;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.logging.Level;

final class ZoneListener implements Listener {
    private final AlexxAutoWarn plugin;
    private final Settings settings;
    private final ZoneManager zoneManager;
    private final NamespacedKey wandKey;

    ZoneListener(AlexxAutoWarn plugin) {
        this.plugin = plugin;
        this.settings = plugin.getSettings();
        this.zoneManager = plugin.getZoneManager();
        this.wandKey = plugin.getWandKey();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        handleAction(event.getPlayer(), event.getBlock().getLocation(), event.getBlockPlaced().getType(), event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerBucketEmpty(PlayerBucketEmptyEvent event) {
        Block target = event.getBlockClicked();
        if (target == null) {
            return;
        }
        Material placedMaterial = event.getBucket() == Material.LAVA_BUCKET ? Material.LAVA : Material.WATER;
        Location location = target.getRelative(event.getBlockFace()).getLocation();
        handleAction(event.getPlayer(), location, placedMaterial, event);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (isWand(item)) {
            Block clickedBlock = event.getClickedBlock();
            if (clickedBlock == null) {
                return;
            }

            event.setCancelled(true);
            Vector clicked = clickedBlock.getLocation().toVector().toBlockVector();
            if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
                plugin.getAutoWarnCommand().setPos1(player.getUniqueId(), clicked);
                player.sendMessage(settings.getMessage("wand.pos1-set", Placeholder.unparsed("coords", formatLocation(clickedBlock.getLocation()))));
            } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                plugin.getAutoWarnCommand().setPos2(player.getUniqueId(), clicked);
                player.sendMessage(settings.getMessage("wand.pos2-set", Placeholder.unparsed("coords", formatLocation(clickedBlock.getLocation()))));
            }
            return;
        }

        if (!settings.isMonitorChestAccess() || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock != null && clickedBlock.getState() instanceof Container) {
            handleAction(player, clickedBlock.getLocation(), clickedBlock.getType(), event);
        }
    }

    private boolean isWand(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(wandKey, PersistentDataType.STRING);
    }

    private void handleAction(Player player, Location location, Material material, org.bukkit.event.Cancellable event) {
        if (player.hasPermission("autowarn.bypass") || player.hasPermission("autowarn.bypass.all")) {
            return;
        }

        if (settings.getGloballyBannedMaterials().contains(material)) {
            deny(player, location, material, "Global", event);
            return;
        }

        Zone zone = zoneManager.getHighestPriorityZoneAt(location);
        if (zone != null) {
            denyOrAlert(player, location, material, zone, event);
        }
    }

    private void denyOrAlert(Player player, Location location, Material material, Zone zone, org.bukkit.event.Cancellable event) {
        Zone.Action action = zone.getActionFor(material);
        String zoneName = zone.getName();
        String logMessage = String.format("%s performed %s with %s in %s at %s", player.getName(), action.name(), material.name(), zoneName, formatLocation(location));
        var placeholders = new net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[] {
                Placeholder.unparsed("player", player.getName()),
                Placeholder.unparsed("material", material.name().toLowerCase().replace('_', ' ')),
                Placeholder.unparsed("zone", zoneName),
                Placeholder.unparsed("location", formatLocation(location))
        };

        AutoWarnAPI api = AlexxAutoWarn.getAPI();
        if (api instanceof AutoWarnAPIImpl impl) {
            ZoneActionEvent zoneActionEvent = new ZoneActionEvent(player, zone, material, action.name());
            impl.fireZoneAction(zoneActionEvent);
            if (zoneActionEvent.isCancelled()) {
                event.setCancelled(true);
                return;
            }
        }

        switch (action) {
            case DENY -> deny(player, location, material, zoneName, event, placeholders, logMessage);
            case ALERT -> {
                plugin.getServer().getOnlinePlayers().stream()
                        .filter(staff -> staff.hasPermission("autowarn.notify"))
                        .forEach(staff -> staff.sendMessage(settings.getMessage("action.alert", placeholders)));
                settings.log(Level.INFO, "[ALERT] " + logMessage);
            }
            case ALLOW -> {
                if (settings.isDebugLogAllowedActions()) {
                    settings.log(Level.INFO, "[ALLOWED] " + logMessage);
                }
            }
        }
    }

    private void deny(Player player, Location location, Material material, String zoneName, org.bukkit.event.Cancellable event) {
        var placeholders = new net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[] {
                Placeholder.unparsed("player", player.getName()),
                Placeholder.unparsed("material", material.name().toLowerCase().replace('_', ' ')),
                Placeholder.unparsed("zone", zoneName),
                Placeholder.unparsed("location", formatLocation(location))
        };
        String logMessage = String.format("%s performed DENY with %s in %s at %s", player.getName(), material.name(), zoneName, formatLocation(location));
        event.setCancelled(true);
        player.sendMessage(settings.getMessage("action.denied", placeholders));
        settings.log(Level.INFO, "[DENIED] " + logMessage);
    }

    private void deny(Player player, Location location, Material material, String zoneName, org.bukkit.event.Cancellable event, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[] placeholders, String logMessage) {
        event.setCancelled(true);
        player.sendMessage(settings.getMessage("action.denied", placeholders));
        settings.log(Level.INFO, "[DENIED] " + logMessage);
    }

    private String formatLocation(Location location) {
        return String.format("%s: %d, %d, %d", location.getWorld() == null ? "unknown" : location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }
}