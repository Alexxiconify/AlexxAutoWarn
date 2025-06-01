package net.Alexxiconify.alexxAutoWarn.objects;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a protected zone in the game world with defined boundaries,
 * default actions, and material-specific actions.
 */
public class AutoInformZone {
 private final String name;
 private final World world;
 private final Location corner1;
 private final Location corner2;
 private final Map<Material, ZoneAction> materialSpecificActions; // Using Material enum directly
 private ZoneAction defaultAction;

 /**
  * Constructor for a new AutoInformZone.
  *
  * @param name                    The unique name of the zone.
  * @param world                   The world this zone is in.
  * @param corner1                 The first corner of the bounding box.
  * @param corner2                 The second corner of the bounding box.
  * @param defaultAction           The default action for materials not specifically listed.
  * @param materialSpecificActions A map of materials to their specific actions within this zone.
  */
 public AutoInformZone(@NotNull String name, @NotNull World world, @NotNull Location corner1, @NotNull Location corner2,
                       @NotNull ZoneAction defaultAction, @NotNull Map<Material, ZoneAction> materialSpecificActions) {
  this.name = Objects.requireNonNull(name, "Zone name cannot be null");
  this.world = Objects.requireNonNull(world, "Zone world cannot be null");
  // Ensure corners are valid and in the same world
  if (!corner1.getWorld().equals(world) || !corner2.getWorld().equals(world)) {
   throw new IllegalArgumentException("Corners must be in the same world as the zone definition.");
  }
  this.corner1 = corner1.clone(); // Clone to prevent external modification
  this.corner2 = corner2.clone(); // Clone to prevent external modification
  this.defaultAction = Objects.requireNonNull(defaultAction, "Default action cannot be null");
  // Create a new HashMap to ensure it's mutable internally but safe from external modification
  this.materialSpecificActions = new HashMap<>(Objects.requireNonNull(materialSpecificActions, "Material specific actions map cannot be null"));
 }

 // --- Getters ---
 public String getName() {
  return name;
 }

 public World getWorld() {
  return world;
 }

 public Location getCorner1() {
  return corner1;
 }

 public Location getCorner2() {
  return corner2;
 }

 public ZoneAction getDefaultAction() {
  return defaultAction;
 }

 // --- Setter (for defaultAction only, material-specific actions are modified via the map directly) ---
 public void setDefaultAction(@NotNull ZoneAction defaultAction) {
  this.defaultAction = Objects.requireNonNull(defaultAction, "Default action cannot be null");
 }

 /**
  * Returns an unmodifiable map of material-specific actions.
  *
  * @return An unmodifiable map.
  */
 public Map<Material, ZoneAction> getMaterialSpecificActions() {
  return Collections.unmodifiableMap(materialSpecificActions);
 }

 /**
  * Checks if a given location is within this zone's bounding box.
  *
  * @param loc The location to check.
  * @return true if the location is inside the zone, false otherwise.
  */
 public boolean contains(@NotNull Location loc) {
  // First, check if the location is in the same world as the zone
  if (!loc.getWorld().equals(this.world)) {
   return false;
  }

  // Get coordinates of the location
  double x = loc.getX();
  double y = loc.getY();
  double z = loc.getZ();

  // Calculate min and max coordinates for the bounding box
  // This handles cases where corner1 might have higher coordinates than corner2
  double minX = Math.min(corner1.getX(), corner2.getX());
  double minY = Math.min(corner1.getY(), corner2.getY());
  double minZ = Math.min(corner1.getZ(), corner2.getZ());

  double maxX = Math.max(corner1.getX(), corner2.getX());
  double maxY = Math.max(corner1.getY(), corner2.getY());
  double maxZ = Math.max(corner1.getZ(), corner2.getZ());

  // Check if the location's coordinates are within the bounding box
  return (x >= minX && x <= maxX) &&
          (y >= minY && y <= maxY) &&
          (z >= minZ && z <= maxZ);
 }

 /**
  * Overrides the equals method to compare AutoInformZone objects based on their name.
  * This ensures zones are considered equal if they have the same name.
  *
  * @param o The object to compare with.
  * @return true if the objects are equal, false otherwise.
  */
 @Override
 public boolean equals(Object o) {
  if (this == o) return true;
  if (o == null || getClass() != o.getClass()) return false;
  AutoInformZone that = (AutoInformZone) o;
  return name.equals(that.name); // Only compare by name as it's the unique identifier
 }

 /**
  * Overrides the hashCode method to be consistent with the equals method.
  *
  * @return The hash code for this zone.
  */
 @Override
 public int hashCode() {
  return Objects.hash(name); // Hash based on name
 }

 /**
  * Provides a string representation of the zone for debugging or logging.
  *
  * @return A string describing the zone.
  */
 @Override
 public String toString() {
  return "AutoInformZone{" +
          "name='" + name + '\'' +
          ", world=" + world.getName() +
          ", corner1=" + String.format("(%.0f,%.0f,%.0f)", corner1.getX(), corner1.getY(), corner1.getZ()) +
          ", corner2=" + String.format("(%.0f,%.0f,%.0f)", corner2.getX(), corner2.getY(), corner2.getZ()) +
          ", defaultAction=" + defaultAction +
          ", materialSpecificActions=" + materialSpecificActions.size() + " entries" +
          '}';
 }
}