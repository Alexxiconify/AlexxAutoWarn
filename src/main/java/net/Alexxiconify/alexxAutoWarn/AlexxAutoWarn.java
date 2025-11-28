package net.alexxiconify.alexxautowarn;

import com.google.common.base.Stopwatch;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
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
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.stream.Collectors;

public final class alexxautowarn extends JavaPlugin {
    private static AutoWarnAPI api;
    private Settings settings;
    private ZoneManager zoneManager;
    private Object coreProtectAPI;
    private AutoWarnCommand autoWarnCommand;

    public static AutoWarnAPI getAPI() { return api; }

    @Override
    public void onEnable() {
        final Stopwatch stopwatch = Stopwatch.createStarted();
        getLogger().info("Starting alexxautowarn...");

        this.settings = new Settings(this);
        this.zoneManager = new ZoneManager(this);

        saveDefaultConfig();
        reloadConfig();

        setupCoreProtect();

        this.autoWarnCommand = new AutoWarnCommand(this);

        // Register command
        Command command = new Command("autowarn") {
            @Override
            public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String @NotNull [] args) {
                return autoWarnCommand.onCommand(sender, this, commandLabel, args);
            }

            @Override
            public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String @NotNull [] args) throws IllegalArgumentException {
                return autoWarnCommand.onTabComplete(sender, this, alias, args);
            }
        };
        command.setDescription("Main command for the AutoWarn plugin.");
        command.setUsage("/<command> [subcommand] [args]");
        command.setAliases(Arrays.asList("aw"));
        
        getServer().getCommandMap().register("autowarn", command);
        getLogger().info("Command 'autowarn' registered successfully.");

        getServer().getPluginManager().registerEvents(new ZoneListener(this), this);

        api = new AutoWarnAPIImpl(this.zoneManager);

        long time = stopwatch.stop().elapsed(TimeUnit.MILLISECONDS);
        getLogger().log(Level.INFO, "alexxautowarn enabled successfully in {0}ms.", time);
    }

    @Override
    public void onDisable() {
        getLogger().info("Disabling alexxautowarn...");
        if (this.zoneManager != null) {
            this.zoneManager.saveZones(false);
        }
        getLogger().info("alexxautowarn has been disabled.");
    }

    @Override
    public void reloadConfig() {
        super.reloadConfig();
        if (this.settings != null) this.settings.reload();
        if (this.zoneManager != null) this.zoneManager.loadZones();
    }

    private void setupCoreProtect() {
        final Plugin coreProtectPlugin = getServer().getPluginManager().getPlugin("CoreProtect");
        if (coreProtectPlugin == null) {
            getLogger().warning("CoreProtect not found! Logging features will be disabled.");
            this.coreProtectAPI = null;
            return;
        }

        try {
            Class<?> coreProtectClass = Class.forName("net.coreprotect.CoreProtect");
            if (!coreProtectClass.isInstance(coreProtectPlugin)) {
                getLogger().warning("CoreProtect plugin found but is not the expected type. Logging disabled.");
                this.coreProtectAPI = null;
                return;
            }

            Object api = coreProtectClass.getMethod("getAPI").invoke(coreProtectPlugin);
            if (api == null) {
                getLogger().warning("CoreProtect found, but the API is not available. Logging disabled.");
                this.coreProtectAPI = null;
                return;
            }

            Boolean isEnabled = (Boolean) api.getClass().getMethod("isEnabled").invoke(api);
            if (!isEnabled) {
                getLogger().warning("CoreProtect found, but the API is not enabled. Logging disabled.");
                this.coreProtectAPI = null;
                return;
            }

            Integer apiVersion = (Integer) api.getClass().getMethod("APIVersion").invoke(api);
            if (apiVersion < 9) {
                getLogger().warning("Unsupported CoreProtect version found (API v" + apiVersion + "). Please update CoreProtect to at least API v9. Logging disabled.");
                this.coreProtectAPI = null;
                return;
            }

            this.coreProtectAPI = api;
            getLogger().info("Successfully hooked into CoreProtect API.");
        } catch (Exception e) {
            getLogger().warning("Failed to initialize CoreProtect API: " + e.getMessage() + ". Logging disabled.");
            this.coreProtectAPI = null;
        }
    }

    @NotNull public Settings getSettings() { return settings; }
    @NotNull public ZoneManager getZoneManager() { return zoneManager; }
    @Nullable public Object getCoreProtectAPI() { return coreProtectAPI; }
    @NotNull public AutoWarnCommand getAutoWarnCommand() { return autoWarnCommand; }

    // --- Inner Classes ---

    public static class Settings {
        private final alexxautowarn plugin;
        private final MiniMessage miniMessage;

        private boolean monitorChestAccess;
        private boolean debugLogAllowedActions;
        private Component pluginPrefix;
        private Set<Material> globallyBannedMaterials;

        public Settings(alexxautowarn plugin) {
            this.plugin = plugin;
            this.miniMessage = MiniMessage.miniMessage();
        }

        public void reload() {
            FileConfiguration config = plugin.getConfig();
            this.monitorChestAccess = config.getBoolean("settings.monitor-chest-access", false);
            this.debugLogAllowedActions = config.getBoolean("settings.debug-log-allowed-actions", false);
            this.pluginPrefix = miniMessage.deserialize(config.getString("messages.plugin-prefix", "<gray>[<gold>AutoWarn</gold>]</gray> "));

            this.globallyBannedMaterials = EnumSet.noneOf(Material.class);
            List<String> bannedMaterialsList = config.getStringList("settings.globally-banned-materials");
            for (String materialName : bannedMaterialsList) {
                try {
                    this.globallyBannedMaterials.add(Material.valueOf(materialName.toUpperCase()));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid globally banned material '" + materialName + "' found in config.yml. Skipping.");
                }
            }
            plugin.getLogger().log(Level.INFO, "Reloaded {0} globally banned materials.", globallyBannedMaterials.size());
        }

        public Component getMessage(@NotNull String key, TagResolver... resolvers) {
            String rawMessage = plugin.getConfig().getString("messages." + key, "<red>Message not found: " + key + "</red>");
            return pluginPrefix.append(miniMessage.deserialize(rawMessage, resolvers));
        }

        public void log(Level level, String message) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> 
                plugin.getLogger().log(level, MiniMessage.miniMessage().stripTags(message)));
        }

        public boolean isMonitorChestAccess() { return monitorChestAccess; }
        public boolean isDebugLogAllowedActions() { return debugLogAllowedActions; }
        @NotNull public Set<Material> getGloballyBannedMaterials() { return Collections.unmodifiableSet(globallyBannedMaterials); }

        public void addGloballyBannedMaterial(@NotNull Material material) {
            if (globallyBannedMaterials.add(material)) saveGloballyBannedMaterials();
        }

        public void removeGloballyBannedMaterial(@NotNull Material material) {
            if (globallyBannedMaterials.remove(material)) saveGloballyBannedMaterials();
        }

        private void saveGloballyBannedMaterials() {
            List<String> bannedNames = globallyBannedMaterials.stream().map(Material::name).collect(Collectors.toList());
            plugin.getConfig().set("settings.globally-banned-materials", bannedNames);
            plugin.saveConfig();
        }
    }

    public static class ZoneListener implements Listener {
        private final Settings settings;
        private final ZoneManager zoneManager;
        private final alexxautowarn plugin;
        private final NamespacedKey wandKey;
        private final Object coreProtectAPI;

        public ZoneListener(alexxautowarn plugin) {
            this.plugin = plugin;
            this.settings = plugin.getSettings();
            this.zoneManager = plugin.getZoneManager();
            this.coreProtectAPI = plugin.getCoreProtectAPI();
            this.wandKey = plugin.getAutoWarnCommand().getWandKey();
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

        @EventHandler(priority = EventPriority.NORMAL)
        public void onPlayerInteract(PlayerInteractEvent event) {
            Player player = event.getPlayer();
            ItemStack handItem = event.getItem();

            if (handItem != null && handItem.hasItemMeta()) {
                ItemMeta meta = handItem.getItemMeta();
                if (meta.getPersistentDataContainer().has(wandKey, PersistentDataType.STRING)) {
                    event.setCancelled(true);
                    Block clickedBlock = event.getClickedBlock();
                    if (clickedBlock == null) return;

                    Vector clickedBlockVector = clickedBlock.getLocation().toVector().toBlockVector();

                    if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
                        plugin.getAutoWarnCommand().setPos1(player.getUniqueId(), clickedBlockVector);
                        player.sendMessage(settings.getMessage("wand.pos1-set", Placeholder.unparsed("coords", formatLocation(clickedBlock.getLocation()))));
                    } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                        plugin.getAutoWarnCommand().setPos2(player.getUniqueId(), clickedBlockVector);
                        player.sendMessage(settings.getMessage("wand.pos2-set", Placeholder.unparsed("coords", formatLocation(clickedBlock.getLocation()))));
                    }
                    return;
                }
            }

            if (settings.isMonitorChestAccess() && event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                Block clickedBlock = event.getClickedBlock();
                if (clickedBlock != null && clickedBlock.getState() instanceof Container) {
                    handleAction(player, clickedBlock.getLocation(), clickedBlock.getType(), event);
                }
            }
        }

        private void handleAction(Player player, Location location, Material material, Cancellable event) {
            if (player.hasPermission("autowarn.bypass")) return;

            Zone zone = zoneManager.getHighestPriorityZoneAt(location);
            if (zone != null) {
                ZoneActionEvent actionEvent = new ZoneActionEvent(player, zone, material, "INTERACT");
                AutoWarnAPI api = alexxautowarn.getAPI();
                if (api != null) {
                    api.isActionAllowed(player, location, material, "INTERACT");
                    if (actionEvent.isCancelled()) {
                        event.setCancelled(true);
                        return;
                    }
                }
            }

            if (settings.getGloballyBannedMaterials().contains(material)) {
                processAction(Zone.Action.DENY, player, location, material, "Global", event);
                return;
            }

            if (zone != null) {
                processAction(zone.getActionFor(material), player, location, material, zone.getName(), event);
            }
        }

        private void processAction(Zone.Action action, Player player, Location loc, Material mat, String zoneName, Cancellable event) {
            var placeholders = new TagResolver[]{
                Placeholder.unparsed("player", player.getName()),
                Placeholder.unparsed("material", mat.name().toLowerCase().replace('_', ' ')),
                Placeholder.unparsed("zone", zoneName),
                Placeholder.unparsed("location", formatLocation(loc))
            };

            String logMessage = String.format("%s performed %s with %s in %s at %s", 
                player.getName(), action.name(), mat.name(), zoneName, formatLocation(loc));

            switch (action) {
                case DENY -> {
                    event.setCancelled(true);
                    player.sendMessage(settings.getMessage("action.denied", placeholders));
                    settings.log(Level.INFO, "[DENIED] " + logMessage);
                    logToCoreProtect(player.getName(), loc, mat);
                }
                case ALERT -> {
                    plugin.getServer().getOnlinePlayers().forEach(p -> {
                        if (p.hasPermission("autowarn.notify")) p.sendMessage(settings.getMessage("action.alert", placeholders));
                    });
                    settings.log(Level.INFO, "[ALERT] " + logMessage);
                    logToCoreProtect(player.getName(), loc, mat);
                }
                case ALLOW -> {
                    if (settings.isDebugLogAllowedActions()) {
                        settings.log(Level.INFO, "[ALLOWED] " + logMessage);
                        logToCoreProtect(player.getName(), loc, mat);
                    }
                }
            }
        }

        private void logToCoreProtect(String user, Location location, Material material) {
            if (coreProtectAPI != null) {
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    try {
                        coreProtectAPI.getClass().getMethod("logPlacement", String.class, Location.class, Material.class, Object.class)
                            .invoke(coreProtectAPI, user, location, material, null);
                    } catch (Exception e) {
                        plugin.getLogger().fine("Failed to log to CoreProtect: " + e.getMessage());
                    }
                });
            }
        }

        private String formatLocation(Location loc) {
            return String.format("%s: %d, %d, %d", loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        }
    }
}

// --- API Interfaces & Classes ---

interface AutoWarnAPI {
    Zone getZoneAt(Location loc);
    Collection<Zone> getZonesAt(Location loc);
    Collection<Zone> getAllZones();
    void registerZoneActionListener(Consumer<ZoneActionEvent> listener);
    void registerCustomAction(String actionName, ZoneCustomAction action);
    boolean isActionAllowed(Player player, Location loc, Material mat, String action);
    Zone getHighestPriorityZoneAt(Location loc);
    void registerZoneEnterListener(Consumer<ZoneEnterEvent> listener);
    void registerZoneLeaveListener(Consumer<ZoneLeaveEvent> listener);
    void registerZonePriorityChangeListener(Consumer<ZonePriorityChangeEvent> listener);
}

interface ZoneEvent {
    Player getPlayer();
    Zone getZone();
}

class ZoneEnterEvent implements ZoneEvent {
    private final Player player;
    private final Zone zone;
    public ZoneEnterEvent(Player player, Zone zone) { this.player = player; this.zone = zone; }
    public Player getPlayer() { return player; }
    public Zone getZone() { return zone; }
}

class ZoneLeaveEvent implements ZoneEvent {
    private final Player player;
    private final Zone zone;
    public ZoneLeaveEvent(Player player, Zone zone) { this.player = player; this.zone = zone; }
    public Player getPlayer() { return player; }
    public Zone getZone() { return zone; }
}

class ZonePriorityChangeEvent implements ZoneEvent {
    private final Player player;
    private final Zone from;
    private final Zone to;
    public ZonePriorityChangeEvent(Player player, Zone from, Zone to) { this.player = player; this.from = from; this.to = to; }
    public Player getPlayer() { return player; }
    public Zone getZone() { return to; }
    public Zone getFromZone() { return from; }
    public Zone getToZone() { return to; }
}

interface ZoneCustomAction {
    void execute(Player player, Zone zone, Material mat, String context);
}

class ZoneActionEvent {
    private final Player player;
    private final Zone zone;
    private final Material material;
    private final String action;
    private boolean cancelled = false;

    public ZoneActionEvent(Player player, Zone zone, Material material, String action) {
        this.player = player;
        this.zone = zone;
        this.material = material;
        this.action = action;
    }

    public Player getPlayer() { return player; }
    public Zone getZone() { return zone; }
    public Material getMaterial() { return material; }
    public String getAction() { return action; }
    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}

class AutoWarnAPIImpl implements AutoWarnAPI {
    private final ZoneManager zoneManager;
    private final List<Consumer<ZoneActionEvent>> actionListeners = new CopyOnWriteArrayList<>();
    private final Map<String, ZoneCustomAction> customActions = new HashMap<>();
    private final List<Consumer<ZoneEnterEvent>> enterListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<ZoneLeaveEvent>> leaveListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<ZonePriorityChangeEvent>> priorityChangeListeners = new CopyOnWriteArrayList<>();

    public AutoWarnAPIImpl(ZoneManager zoneManager) { this.zoneManager = zoneManager; }

    @Override public Zone getZoneAt(Location loc) { return zoneManager.getZoneAt(loc); }
    @Override public Collection<Zone> getZonesAt(Location loc) { return zoneManager.getZonesAt(loc); }
    @Override public Collection<Zone> getAllZones() { return zoneManager.getAllZones(); }
    @Override public void registerZoneActionListener(Consumer<ZoneActionEvent> listener) { actionListeners.add(listener); }
    @Override public void registerCustomAction(String actionName, ZoneCustomAction action) { customActions.put(actionName.toUpperCase(Locale.ROOT), action); }
    @Override public Zone getHighestPriorityZoneAt(Location loc) { return zoneManager.getHighestPriorityZoneAt(loc); }
    @Override public void registerZoneEnterListener(Consumer<ZoneEnterEvent> listener) { enterListeners.add(listener); }
    @Override public void registerZoneLeaveListener(Consumer<ZoneLeaveEvent> listener) { leaveListeners.add(listener); }
    @Override public void registerZonePriorityChangeListener(Consumer<ZonePriorityChangeEvent> listener) { priorityChangeListeners.add(listener); }

    @Override
    public boolean isActionAllowed(Player player, Location loc, Material mat, String action) {
        Zone zone = getZoneAt(loc);
        if (zone == null) return true;

        ZoneActionEvent event = new ZoneActionEvent(player, zone, mat, action);
        for (Consumer<ZoneActionEvent> listener : actionListeners) listener.accept(event);

        ZoneCustomAction custom = customActions.get(action.toUpperCase(Locale.ROOT));
        if (custom != null) custom.execute(player, zone, mat, action);

        return !event.isCancelled();
    }

    public void fireZoneEnter(Player player, Zone zone) {
        ZoneEnterEvent event = new ZoneEnterEvent(player, zone);
        for (var l : enterListeners) l.accept(event);
    }

    public void fireZoneLeave(Player player, Zone zone) {
        ZoneLeaveEvent event = new ZoneLeaveEvent(player, zone);
        for (var l : leaveListeners) l.accept(event);
    }

    public void fireZonePriorityChange(Player player, Zone from, Zone to) {
        ZonePriorityChangeEvent event = new ZonePriorityChangeEvent(player, from, to);
        for (var l : priorityChangeListeners) l.accept(event);
    }
}