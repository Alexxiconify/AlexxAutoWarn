package net.Alexxiconify.alexxAutoWarn.objects;

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

 private Map<Material, Action> currentMaterialActions;
 private Map<Material, Action> updatedMaterialActions;
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
 public Zone(@NotNull String name, World world, @NotNull Vector corner1, @NotNull Vector corner2,
             @NotNull Action defaultAction, @NotNull Map<Material, Action> materialActions) {
  this.name = name.toLowerCase(); // Store zone name in lowercase for consistent lookups
  this.worldName = world.getName();
  // Calculate min/max vectors from corners to define the true bounding box,
  // ensuring min <= max for all axes regardless of corner input order.
  this.min = Vector.getMinimum(corner1, corner2);
  this.max = Vector.getMaximum(corner1, corner2);
  this.defaultAction = defaultAction;
  // Use EnumMap for performance with Material keys, and create an unmodifiable copy
  // to maintain immutability from the outside.
  this.materialActions = Collections.unmodifiableMap(new EnumMap<>(materialActions));
 }


 /**
  * Checks if a given location is within the bounds of this zone.
  *
  * @param loc The location to check.
  * @return true if the location is inside the zone, false otherwise.
  */
 public boolean contains(@NotNull Location loc) {
  // Check if the world matches and the location's vector is within the zone's AABB (Axis-Aligned Bounding Box)
  return loc.getWorld ( ).getName ( ).equals ( this.worldName ) &&
    loc.getX ( ) >= min.getX ( ) && loc.getX ( ) <= max.getX ( ) &&
    loc.getY ( ) >= min.getY ( ) && loc.getY ( ) <= max.getY ( ) &&
    loc.getZ ( ) >= min.getZ ( ) && loc.getZ ( ) <= max.getZ ( );
 }

 /**
  * Gets the specific action for a given material within this zone.
  * If no specific action is defined for the material, the zone's default action is returned.
  *
  * @param material The material to check.
  * @return The Action for the material.
  */
 @NotNull
 public Action getActionFor(@NotNull Material material) {
  return materialActions.getOrDefault(material, defaultAction);
 }

 /**
  * Gets the unique name of the zone.
  *
  * @return The zone's name.
  */
 @NotNull
 public String getName() {
  return name;
 }

 // --- Getters for immutable fields ---

 /**
  * Gets the name of the world this zone resides in.
  *
  * @return The world name.
  */
 @NotNull
 public String getWorldName() {
  return worldName;
 }

 /**
  * Gets the minimum corner vector of the zone's bounding box.
  * @return The minimum Vector.
  */
 @NotNull
 public Vector getMin() {
  return min;
 }

 /**
  * Gets the maximum corner vector of the zone's bounding box.
  * @return The maximum Vector.
  */
 @NotNull
 public Vector getMax() {
  return max;
 }

 /**
  * Gets the default action for materials not specifically defined in this zone.
  * @return The default Action.
  */
 @NotNull
 public Action getDefaultAction() {
  return defaultAction;
 }

 /**
  * Gets an unmodifiable map of material-specific actions.
  * @return An unmodifiable map of Material to Action.
  */
 @NotNull
 public Map<Material, Action> getMaterialActions() {
  return materialActions;
 }

 @Override
 public boolean equals(Object o) {
  if (this == o) return true;
  if (o == null || getClass() != o.getClass()) return false;
  Zone zone = (Zone) o;
  return name.equals(zone.name); // Equality based on unique zone name
 }

 @Override
 public int hashCode() {
  return Objects.hash(name);
 }

 /**
  * Defines the possible actions for materials within a zone.
  */
 public enum Action {
  DENY,   // Deny the action (e.g., block placement, container access)
  ALERT,  // Allow the action, but alert staff/console
  ALLOW   // Allow the action without any special alerts
 }
}