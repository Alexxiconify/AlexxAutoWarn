package net.alexxiconify.alexxautowarn;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class ZoneManager {
    private final alexxautowarn plugin;
    private final Map<String, Zone> zones = new ConcurrentHashMap<>();

    public ZoneManager(alexxautowarn plugin) {
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
                if (zoneConfig == null) continue;

                try {
                    String worldName = zoneConfig.getString("world");
                    World world = worldName != null ? plugin.getServer().getWorld(worldName) : null;
                    
                    if (world == null) {
                        plugin.getSettings().log(Level.WARNING, "World '" + worldName + "' for zone '" + zoneName + "' not found. Skipping.");
                        continue;
                    }

                    Vector corner1 = getVector(zoneConfig.getConfigurationSection("corner1"));
                    Vector corner2 = getVector(zoneConfig.getConfigurationSection("corner2"));

                    if (corner1 == null || corner2 == null) {
                        plugin.getSettings().log(Level.SEVERE, "Zone '" + zoneName + "' missing corner coordinates.");
                        continue;
                    }

                    Zone.Action defaultAction = getAction(zoneConfig.getString("default-action"), Zone.Action.ALERT);
                    Map<Material, Zone.Action> materialActions = new EnumMap<>(Material.class);
                    
                    ConfigurationSection actionsSection = zoneConfig.getConfigurationSection("material-actions");
                    if (actionsSection != null) {
                        for (String key : actionsSection.getKeys(false)) {
                            try {
                                Material mat = Material.valueOf(key.toUpperCase());
                                Zone.Action action = getAction(actionsSection.getString(key), null);
                                if (action != null) materialActions.put(mat, action);
                            } catch (IllegalArgumentException ignored) {}
                        }
                    }

                    int priority = zoneConfig.getInt("priority", 0);
                    zones.put(zoneName, new Zone(zoneName, world, corner1, corner2, defaultAction, materialActions, priority));

                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "Error loading zone '" + zoneName + "': " + e.getMessage(), e);
                }
            }
            plugin.getSettings().log(Level.INFO, "Loaded " + zones.size() + " zones.");
        });
    }

    private Vector getVector(ConfigurationSection section) {
        if (section == null) return null;
        return new Vector(section.getDouble("x"), section.getDouble("y"), section.getDouble("z"));
    }

    private Zone.Action getAction(String name, Zone.Action def) {
        if (name == null) return def;
        try { return Zone.Action.valueOf(name.toUpperCase()); } 
        catch (IllegalArgumentException e) { return def; }
    }

    public void saveZones(boolean async) {
        Runnable saveTask = () -> {
            FileConfiguration config = plugin.getConfig();
            config.set("zones", null);

            for (Zone zone : zones.values()) {
                String path = "zones." + zone.getName();
                config.set(path + ".world", zone.getWorldName());
                setVector(config, path + ".corner1", zone.getMin());
                setVector(config, path + ".corner2", zone.getMax());
                config.set(path + ".default-action", zone.getDefaultAction().name());
                config.set(path + ".priority", zone.getPriority());

                if (!zone.getMaterialActions().isEmpty()) {
                    zone.getMaterialActions().forEach((m, a) -> config.set(path + ".material-actions." + m.name(), a.name()));
                }
            }
            plugin.saveConfig();
            plugin.getSettings().log(Level.INFO, "Saved " + zones.size() + " zones.");
        };

        if (async) plugin.getServer().getScheduler().runTaskAsynchronously(plugin, saveTask);
        else saveTask.run();
    }

    private void setVector(FileConfiguration config, String path, Vector vec) {
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

    @Nullable public Zone getZone(@NotNull String zoneName) { return zones.get(zoneName.toLowerCase()); }

    @Nullable public Zone getZoneAt(@NotNull Location location) {
        return zones.values().stream().filter(z -> z.contains(location)).findFirst().orElse(null);
    }

    @NotNull public Collection<Zone> getZonesAt(@NotNull Location location) {
        return zones.values().stream()
                .filter(z -> z.contains(location))
                .sorted((a, b) -> Integer.compare(b.getPriority(), a.getPriority()))
                .toList();
    }

    @Nullable public Zone getHighestPriorityZoneAt(@NotNull Location location) {
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

    @NotNull public Collection<Zone> getAllZones() { return zones.values(); }
}