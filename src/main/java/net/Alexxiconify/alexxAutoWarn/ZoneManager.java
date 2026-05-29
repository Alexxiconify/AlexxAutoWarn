package net.alexxiconify.alexxautowarn;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class ZoneManager {
    private final AlexxAutoWarn plugin;
    private final Map<String, Zone> zones = new ConcurrentHashMap<>();

    public ZoneManager(AlexxAutoWarn plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }


    public AlexxAutoWarn getPlugin() {
        return plugin;
    }

    public synchronized void loadZones() {
        zones.clear();
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("zones");
        if (root != null) {
            for (String zoneName : root.getKeys(false)) {
                Zone zone = readZone(zoneName, root.getConfigurationSection(zoneName));
                if (zone != null) {
                    zones.put(zone.getName(), zone);
                }
            }
        }
        CompletableFuture.completedFuture ( null );
    }

    public synchronized void saveZones(boolean saveConfig) {
        FileConfiguration config = plugin.getConfig();
        config.set("zones", null);
        zones.values().stream()
                .sorted(Comparator.comparing(Zone::getName))
                .forEach(zone -> writeZone(config, zone));
        if (saveConfig) {
            plugin.saveConfig();
        }
    }

    public void addOrUpdateZone(Zone zone) {
        zones.put(zone.getName(), zone);
        saveZones(true);
    }

    public boolean removeZone(String name) {
        String key = normalize(name);
        Zone removed = zones.remove(key);
        if (removed != null) {
            plugin.getConfig().set("zones." + removed.getName(), null);
            plugin.saveConfig();
            return true;
        }
        return false;
    }

    public Zone getZone(String name) {
        if (name == null) {
            return null;
        }
        return zones.get(normalize(name));
    }

    public Zone getZoneAt(Location location) {
        return getHighestPriorityZoneAt(location);
    }

    public Collection<Zone> getZonesAt(Location location) {
        if (location == null) {
            return List.of();
        }
        return zones.values().stream()
                .filter(zone -> zone.contains(location))
                .sorted(Comparator.comparingInt(Zone::getPriority).reversed().thenComparing(Zone::getName))
                .toList();
    }

    public Zone getHighestPriorityZoneAt(Location location) {
        return getZonesAt(location).stream().findFirst().orElse(null);
    }

    public Collection<Zone> getAllZones() {
        return zones.values().stream()
                .sorted(Comparator.comparing(Zone::getName))
                .toList();
    }

    private Zone readZone(String zoneName, ConfigurationSection section) {
        if (section == null) {
            return null;
        }

        String worldName = section.getString("world");
        Vector corner1 = readVector(section.getConfigurationSection("corner1"));
        Vector corner2 = readVector(section.getConfigurationSection("corner2"));
        if (worldName == null || corner1 == null || corner2 == null) {
            plugin.getLogger().warning(String.format("Skipping zone '%s' because it is missing world or corner data.", zoneName));
            return null;
        }

        Zone.Action defaultAction = readAction(section.getString("default-action", section.getString("default-material-action", "ALERT")), Zone.Action.ALERT);
        int priority = section.getInt("priority", 0);
        Map<Material, Zone.Action> actions = readMaterialActions(section.getConfigurationSection("material-actions"));
        return new Zone(zoneName, worldName, corner1, corner2, defaultAction, actions, priority);
    }

    private Map<Material, Zone.Action> readMaterialActions(ConfigurationSection section) {
        if (section == null) {
            return Map.of();
        }

        Map<Material, Zone.Action> actions = new EnumMap<>(Material.class);
        for (String materialName : section.getKeys(false)) {
            Material material = Material.matchMaterial(materialName);
            Zone.Action action = readAction(section.getString(materialName), null);
            if (material != null && action != null) {
                actions.put(material, action);
            }
        }
        return actions;
    }

    private Zone.Action readAction(String value, Zone.Action fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Zone.Action.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    private Vector readVector(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        double x = section.getDouble("x", Double.NaN);
        double y = section.getDouble("y", Double.NaN);
        double z = section.getDouble("z", Double.NaN);
        if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z)) {
            return null;
        }
        return new Vector(x, y, z);
    }

    private void writeZone(FileConfiguration config, Zone zone) {
        String base = "zones." + zone.getName();
        config.set(base + ".world", zone.getWorldName());
        config.set(base + ".corner1.x", zone.getMin().getX());
        config.set(base + ".corner1.y", zone.getMin().getY());
        config.set(base + ".corner1.z", zone.getMin().getZ());
        config.set(base + ".corner2.x", zone.getMax().getX());
        config.set(base + ".corner2.y", zone.getMax().getY());
        config.set(base + ".corner2.z", zone.getMax().getZ());
        config.set(base + ".default-action", zone.getDefaultAction().name());
        config.set(base + ".priority", zone.getPriority());

        zone.getMaterialActions().forEach((material, action) ->
                config.set(base + ".material-actions." + material.name(), action.name()));
    }

    private String normalize(String name) {
        return Objects.requireNonNull(name, "name").trim().toLowerCase(Locale.ROOT);
    }
}