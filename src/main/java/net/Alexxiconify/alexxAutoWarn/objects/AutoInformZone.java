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
 private final String name;
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
  this.world = corner1.getWorld(); // World is taken from corner1
  this.corner1 = corner1;
  this.corner2 = corner2;
  this.defaultAction = defaultAction;
  // Create a new HashMap to ensure the passed map is not modified externally
  this.materialSpecificActions = new HashMap<>(materialSpecificActions);
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
  * Gets an unmodifiable map of material-specific actions.
  *
  * @return A map of materials to their specific actions.
  */
 public Map<Material, net.Alexxiconify.alexxAutoWarn.objects.ZoneAction> getMaterialSpecificActions() {
  return Collections.unmodifiableMap(materialSpecificActions);
 }

 /**
  * Retrieves the action for a specific material within this zone.
  * If no specific action is defined for the material, the zone's default action is returned.
  *
  * @param material The material to check.
  * @return The ZoneAction for the given material, or the default action if not specifically defined.
  */
 @NotNull
 public ZoneAction getMaterialAction(@NotNull Material material) {
  return materialSpecificActions.getOrDefault(material, defaultAction);
 }


 // --- Setters for mutable properties ---

 public void setDefaultAction(net.Alexxiconify.alexxAutoWarn.objects.ZoneAction defaultAction) {
  this.defaultAction = defaultAction;
 }

 /**
  * Sets a specific action for a material within this zone.
  *
  * @param material The material to set the action for.
  * @param action   The action to apply to the material.
  */
 public void setMaterialAction(@NotNull Material material, net.Alexxiconify.alexxAutoWarn.objects.ZoneAction action) {
  this.materialSpecificActions.put(material, action);
 }

 /**
  * Removes a specific material action, reverting to the default action for that material.
  *
  * @param material The material to remove the specific action for.
  * @return The previously set ZoneAction for the material, or null if none was set.
  */
 public ZoneAction removeMaterialAction(@NotNull Material material) {
  return this.materialSpecificActions.remove(material);
 }

 /**
  * Sets the first corner of the zone.
  *
  * @param corner1 The new first corner location.
  */
 public void setCorner1(@NotNull Location corner1) {
  this.corner1 = corner1;
  this.world = corner1.getWorld(); // Update world reference if corner1 changes
 }

 /**
  * Sets the second corner of the zone.
  * @param corner2 The new second corner location.
  */
 public void setCorner2(@NotNull Location corner2) {
  this.corner2 = corner2;
 }

 /**
  * Checks if the given location is within this zone's boundaries.
  * This method considers the min/max coordinates for the bounding box.
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
          corner2.equals(that.corner2); // Only compare defining properties
 }

 @Override
 public int hashCode() {
  return Objects.hash(name, world, corner1, corner2);
 }
}