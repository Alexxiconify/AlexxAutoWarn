package net.Alexxiconify.alexxAutoWarn;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class ZoneManager {
    private final AlexxAutoWarn plugin;
    private final Map<String, Zone> zones = new ConcurrentHashMap<>();

    public ZoneManager(AlexxAutoWarn plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<Void> loadZones() {
        return CompletableFuture.runAsync(() -> {
            zones.clear();
            FileConfiguration config = plugin.getConfig();
            ConfigurationSection zonesSection = config.getConfigurationSection("zones");
            if (zonesSection == null) {
                plugin.getSettings().log(Level.INFO, "No zones section found in config.yml. Loaded 0 zones.");
                return;
            }

            for (String zoneName : zonesSection.getKeys(false)) {
                ConfigurationSection zoneConfig = zonesSection.getConfigurationSection(zoneName);
                if (zoneConfig == null) {
                    plugin.getSettings().log(Level.WARNING, "Skipping malformed zone configuration for '" + zoneName + "'.");
                    continue;
                }

                try {
                    String worldName = zoneConfig.getString("world");
                    World world = worldName != null ? plugin.getServer().getWorld(worldName) : null;
                    if (world == null) {
                        plugin.getSettings().log(Level.WARNING, "World '" + worldName + "' for zone '" + zoneName + "' not found. Skipping.");
                        continue;
                    }

                    Vector corner1 = parseVector(zoneConfig.getConfigurationSection("corner1"));
                    Vector corner2 = parseVector(zoneConfig.getConfigurationSection("corner2"));
                    if (corner1 == null || corner2 == null) {
                        plugin.getSettings().log(Level.SEVERE, "Zone '" + zoneName + "' missing corner coordinates.");
                        continue;
                    }

                    Zone.Action defaultAction = parseAction(zoneConfig.getString("default-action"), Zone.Action.ALERT);
                    Map<Material, Zone.Action> materialActions = parseMaterialActions(zoneConfig.getConfigurationSection("material-actions"), zoneName);
                    int priority = zoneConfig.getInt("priority", 0);

                    Zone zone = new Zone(zoneName, world, corner1, corner2, defaultAction, materialActions, priority);
                    zones.put(zone.getName(), zone);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "Error loading zone '" + zoneName + "': " + e.getMessage(), e);
                }
            }
            plugin.getSettings().log(Level.INFO, "Loaded " + zones.size() + " zones.");
        });
    }

    private @Nullable Vector parseVector(ConfigurationSection section) {
        if (section == null) return null;
        return new Vector(section.getDouble("x"), section.getDouble("y"), section.getDouble("z"));
    }

    private @NotNull Zone.Action parseAction(String name, Zone.Action def) {
        if (name == null) return def;
        try {
            return Zone.Action.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return def;
        }
    }

    private @NotNull Map<Material, Zone.Action> parseMaterialActions(ConfigurationSection section, String zoneName) {
        Map<Material, Zone.Action> materialActions = new EnumMap<>(Material.class);
        if (section == null) return materialActions;

        for (String materialKey : section.getKeys(false)) {
            try {
                Material material = Material.valueOf(materialKey.toUpperCase());
                String actionString = section.getString(materialKey);
                if (actionString != null) {
                    Zone.Action action = Zone.Action.valueOf(actionString.toUpperCase());
                    materialActions.put(material, action);
                }
            } catch (IllegalArgumentException e) {
                plugin.getSettings().log(Level.WARNING, "Invalid material or action '" + materialKey + "' in zone '" + zoneName + "'. Skipping.");
            }
        }
        return materialActions;
    }

    public void saveZones(boolean async) {
        Runnable saveTask = () -> {
            FileConfiguration config = plugin.getConfig();
            config.set("zones", null);

            for (Zone zone : zones.values()) {
                String path = "zones." + zone.getName();
                config.set(path + ".world", zone.getWorldName());
                saveVector(config, path + ".corner1", zone.getMin());
                saveVector(config, path + ".corner2", zone.getMax());
                config.set(path + ".default-action", zone.getDefaultAction().name());
                config.set(path + ".priority", zone.getPriority());

                if (!zone.getMaterialActions().isEmpty()) {
                    zone.getMaterialActions().forEach((m, a) -> config.set(path + ".material-actions." + m.name(), a.name()));
                }
            }
            plugin.saveConfig();
            plugin.getSettings().log(Level.INFO, "Successfully saved " + zones.size() + " zones.");
        };

        if (async) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, saveTask);
        } else {
            saveTask.run();
        }
    }

    private void saveVector(FileConfiguration config, String path, Vector vec) {
        config.set(path + ".x", vec.getX());
        config.set(path + ".y", vec.getY());
        config.set(path + ".z", vec.getZ());
    }

    public void addOrUpdateZone(@NotNull Zone zone) {
        zones.put(zone.getName(), zone);
        saveZones(true);
    }

    public boolean removeZone(@NotNull String zoneName) {
        if (zones.remove(zoneName.toLowerCase()) != null) {
            saveZones(true);
            return true;
        }
        return false;
    }

    public @Nullable Zone getZone(@NotNull String zoneName) {
        return zones.get(zoneName.toLowerCase());
    }

    public @Nullable Zone getZoneAt(@NotNull Location location) {
        return getHighestPriorityZoneAt(location);
    }

    public @NotNull Collection<Zone> getZonesAt(@NotNull Location location) {
        ArrayList<Zone> result = new ArrayList<>();
        for (Zone zone : zones.values()) {
            if (zone.contains(location)) {
                result.add(zone);
            }
        }
        result.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
        return result;
    }

    public @Nullable Zone getHighestPriorityZoneAt(@NotNull Location location) {
        Zone highest = null;
        for (Zone zone : zones.values()) {
            if (zone.contains(location)) {
                if (highest == null || zone.getPriority() > highest.getPriority()) {
                    highest = zone;
                }
            }
        }
        return highest;
    }

    public @NotNull Collection<Zone> getAllZones() {
        return zones.values();
    }
}