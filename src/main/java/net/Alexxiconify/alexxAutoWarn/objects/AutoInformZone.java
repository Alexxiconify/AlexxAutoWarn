package net.Alexxiconify.alexxAutoWarn.objects;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a defined AutoInform protection zone.
 * Stores its name, bounding box (two corners), default action for materials,
 * and specific actions for individual materials within the zone.
 */
public class AutoInformZone {
 private final String name;
 private final World world;
 private final Location corner1;
 private final Location corner2;
 private final Map<Material, ZoneAction> materialSpecificActions; // This map should be final for immutability
 private ZoneAction defaultAction; // Made non-final to allow updates via setter

 /**
  * Constructs a new AutoInformZone.
  *
  * @param name The unique name of the zone.
  * @param corner1 The first corner of the zone (any order).
  * @param corner2 The second corner of the zone (any order).
  * @param defaultAction The default action for materials not specifically defined.
  * @param materialSpecificActions A map of materials to their specific actions within this zone.
  */
 public AutoInformZone(@NotNull String name, @NotNull Location corner1, @NotNull Location corner2,
                       @NotNull ZoneAction defaultAction, @NotNull Map<Material, ZoneAction> materialSpecificActions) {
  this.name = name;
  // Ensure both corners are in the same world
  if (!corner1.getWorld().equals(corner2.getWorld())) {
   throw new IllegalArgumentException("Zone corners must be in the same world.");
  }
  this.world = corner1.getWorld(); // Store the world object
  this.corner1 = corner1;
  this.corner2 = corner2;
  this.defaultAction = defaultAction;
  // Create a new HashMap to avoid external modification and ensure internal mutability for updates
  this.materialSpecificActions = new HashMap<>(materialSpecificActions);
 }

 public @NotNull String getName() {
  return name;
 }

 public @NotNull World getWorld() {
  return world;
 }

 public @NotNull Location getCorner1() {
  return corner1;
 }

 public @NotNull Location getCorner2() {
  return corner2;
 }

 public net.Alexxiconify.alexxAutoWarn.objects.ZoneAction getDefaultAction() {
  return defaultAction;
 }

 // Setter for defaultAction to allow updates
 public void setDefaultAction(@NotNull ZoneAction defaultAction) {
  this.defaultAction = defaultAction;
 }

 /**
  * Gets a mutable map of material-specific actions.
  * This allows the ZoneManager to update specific actions.
  * If true immutability is desired, this should return an unmodifiable map
  * and updates would require creating a new AutoInformZone object.
  * For the current implementation, returning a mutable map is fine.
  *
  * @return A mutable Map from Material to ZoneAction.
  */
 public @NotNull Map<Material, ZoneAction> getMaterialSpecificActions() {
  return materialSpecificActions;
 }

 /**
  * Checks if a given location is within this zone's bounding box.
  * The check is inclusive of the boundary blocks.
  *
  * @param loc The location to check.
  * @return true if the location is within the zone, false otherwise.
  */
 public boolean contains(@NotNull Location loc) {
  // Must be in the same world
  if (!loc.getWorld().equals(this.world)) return false;

  // Get the block coordinates of the location
  double x = loc.getX();
  double y = loc.getY();
  double z = loc.getZ();

  // Calculate min and max coordinates for the bounding box
  double minX = Math.min(corner1.getX(), corner2.getX());
  double minY = Math.min(corner1.getY(), corner2.getY());
  double minZ = Math.min(corner1.getZ(), corner2.getZ());

  double maxX = Math.max(corner1.getX(), corner2.getX());
  double maxY = Math.max(corner1.getY(), corner2.getY());
  double maxZ = Math.max(corner1.getZ(), corner2.getZ());

  // Check if the location is within the min/max bounds (inclusive)
  return (x >= minX && x <= maxX) &&
          (y >= minY && y <= maxY) &&
          (z >= minZ && z <= maxZ);
 }

 @Override
 public boolean equals(Object o) {
  if (this == o) return true;
  if (o == null || getClass() != o.getClass()) return false;
  AutoInformZone that = (AutoInformZone) o;
  return name.equals(that.name) &&
          world.equals(that.world) &&
          corner1.equals(that.corner1) &&
          corner2.equals(that.corner2) &&
          defaultAction == that.defaultAction &&
          materialSpecificActions.equals(that.materialSpecificActions);
 }

 @Override
 public int hashCode() {
  return Objects.hash(name, world, corner1, corner2, defaultAction, materialSpecificActions);
 }
}