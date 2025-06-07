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
 * Represents a defined AutoInform protection zone.
 * Stores its name, bounding box (two corners), default action for materials,
 * and specific actions for individual materials within the zone.
 */
public class AutoInformZone {
 private String name;
 private World world;
 private Location corner1;
 private Location corner2;
 private final Map<Material, ZoneAction> materialSpecificActions;
 private ZoneAction defaultAction;

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
  this.world = corner1.getWorld(); // Assume both corners are in the same world
  this.corner1 = corner1;
  this.corner2 = corner2;
  this.defaultAction = defaultAction;
  this.materialSpecificActions = new HashMap<>(materialSpecificActions); // Create a mutable copy
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

 /**
  * Returns an unmodifiable map of material-specific actions.
  */
 public Map<Material, ZoneAction> getMaterialSpecificActions() {
  return Collections.unmodifiableMap(materialSpecificActions);
 }

 /**
  * Gets the specific action for a given material within this zone.
  * If no specific action is defined, the default action for the zone is returned.
  *
  * @param material The material to check.
  * @return The ZoneAction for the material, or the default action if not specifically defined.
  */
 public ZoneAction getMaterialAction(@NotNull Material material) {
  return materialSpecificActions.getOrDefault(material, defaultAction);
 }

 // --- Setters (for updating zones after creation) ---

 public void setName(@NotNull String name) {
  this.name = name;
 }

 public void setCorner1(@NotNull Location corner1) {
  this.corner1 = corner1;
  // Ensure world is updated if corner1 is in a different world (though typically zones stay in one world)
  this.world = corner1.getWorld();
 }

 public void setCorner2(@NotNull Location corner2) {
  this.corner2 = corner2;
  // Ensure world is updated if corner2 is in a different world
  this.world = corner2.getWorld();
 }

 public void setDefaultAction(@NotNull ZoneAction defaultAction) {
  this.defaultAction = defaultAction;
 }

 /**
  * Sets a specific action for a material within this zone.
  *
  * @param material The material.
  * @param action   The action to set for the material.
  */
 public void setMaterialAction(@NotNull Material material, @NotNull ZoneAction action) {
  materialSpecificActions.put(material, action);
 }

 /**
  * Removes a material-specific action from this zone.
  *
  * @param material The material whose action to remove.
  * @return true if the action was removed, false if it didn't exist.
  */
 public boolean removeMaterialAction(@NotNull Material material) {
  return materialSpecificActions.remove(material) != null;
 }


 /**
  * Checks if a given location is within the bounds of this zone's bounding box.
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
  return name.equals(that.name); // Zone names are unique identifiers
 }

 @Override
 public int hashCode() {
  return Objects.hash(name);
 }
}