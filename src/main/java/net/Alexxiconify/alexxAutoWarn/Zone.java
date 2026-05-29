package net.alexxiconify.alexxautowarn;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class Zone {
    private final String name;
    private final String worldName;
    private final Vector min;
    private final Vector max;
    private final Action defaultAction;
    private final Map<Material, Action> materialActions;
    private final int priority;

    public Zone(@NotNull String name, @NotNull String worldName, @NotNull Vector corner1, @NotNull Vector corner2,
                @NotNull Action defaultAction, @NotNull Map<Material, Action> materialActions, int priority) {
        this.name = normalize(name);
        this.worldName = Objects.requireNonNull(worldName, "worldName");
        this.min = Vector.getMinimum(corner1, corner2);
        this.max = Vector.getMaximum(corner1, corner2);
        this.defaultAction = Objects.requireNonNull(defaultAction, "defaultAction");

        EnumMap<Material, Action> copy = new EnumMap<>(Material.class);
        copy.putAll(materialActions);
        this.materialActions = Collections.unmodifiableMap(copy);
        this.priority = priority;
    }

    public boolean contains(@NotNull Location location) {
        // Use block coordinates for containment checks to make zone behavior
        // consistent when other parts of the code convert locations to block vectors.
        return location.getWorld() != null
                && worldName.equalsIgnoreCase(location.getWorld().getName())
                && location.getBlockX() >= min.getBlockX() && location.getBlockX() <= max.getBlockX()
                && location.getBlockY() >= min.getBlockY() && location.getBlockY() <= max.getBlockY()
                && location.getBlockZ() >= min.getBlockZ() && location.getBlockZ() <= max.getBlockZ();
    }

    @NotNull
    public Action getActionFor(@NotNull Material material) {
        return materialActions.getOrDefault(material, defaultAction);
    }

    @NotNull
    public String getName() {
        return name;
    }

    @NotNull
    public String getWorldName() {
        return worldName;
    }

    @NotNull
    public Vector getMin() {
        return min;
    }

    @NotNull
    public Vector getMax() {
        return max;
    }

    @NotNull
    public Action getDefaultAction() {
        return defaultAction;
    }

    @NotNull
    public Map<Material, Action> getMaterialActions() {
        return materialActions;
    }

    public int getPriority() {
        return priority;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Zone zone)) return false;
        return name.equals(zone.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    private String normalize(String value) {
        return Objects.requireNonNull(value, "name").trim().toLowerCase(Locale.ROOT);
    }

    public enum Action {
        DENY, ALERT, ALLOW
    }
}