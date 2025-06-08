package net.alexxiconify.alexxAutoWarn.objects;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a defined protection zone.
 * This class is immutable by design. Use the ZoneManager to create or modify zones.
 */
public final class Zone {

 private final String name;
 private final String worldName;
 private final Vector min;
 private final Vector max;
 private final Action defaultAction;
 private final Map<Material, Action> materialActions;
 /**
  * Constructs a new protection zone.
  *
  * @param name            The unique name of the zone (case-insensitive).
  * @param world           The world the zone resides in.
  * @param corner1         The first corner of the zone's bounding box.
  * @param corner2         The second corner of the zone's bounding box.
  * @param defaultAction   The default action for materials not specifically defined.
  * @param materialActions A map of materials to their specific actions.
  */
 public Zone(@NotNull String name, @NotNull World world, @NotNull Vector corner1, @NotNull Vector corner2,
             @NotNull Action defaultAction, @NotNull Map<Material, Action> materialActions) {
  this.name = name.toLowerCase();
  this.worldName = world.getName();
  this.min = Vector.getMinimum(corner1, corner2);
  this.max = Vector.getMaximum(corner1, corner2);
  this.defaultAction = defaultAction;
  // Use EnumMap for performance with Material keys
  this.materialActions = new EnumMap<>(materialActions);
 }

 /**
  * Checks if a given location is within the bounds of this zone.
  *
  * @param loc The location to check.
  * @return true if the location is inside the zone, false otherwise.
  */
 public boolean contains(@NotNull Location loc) {
  return loc.getWorld().getName().equals(this.worldName) && loc.toVector().isInAABB(min, max);
 }

 /**
  * Gets the specific action for a given material.
  * If no specific action is defined, the zone's default action is returned.
  *
  * @param material The material to check.
  * @return The Action for the material.
  */
 @NotNull
 public Action getActionFor(@NotNull Material material) {
  return materialActions.getOrDefault(material, defaultAction);
 }

 @NotNull
 public String getName() {
  return name;
 }

 // --- Getters ---

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
  return Collections.unmodifiableMap(materialActions);
 }

 @Override
 public boolean equals(Object o) {
  if (this == o) return true;
  if (o == null || getClass() != o.getClass()) return false;
  Zone zone = (Zone) o;
  return name.equals(zone.name);
 }

 @Override
 public int hashCode() {
  return Objects.hash(name);
 }

 /**
  * Defines the possible actions for materials within a zone.
  */
 public enum Action {
  DENY,   // Deny the action (e.g., block placement)
  ALERT,  // Allow the action, but alert staff/console
  ALLOW   // Allow the action without any special alerts
 }
}