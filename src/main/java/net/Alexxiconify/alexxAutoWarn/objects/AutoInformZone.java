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
 private final World world; // World is immutable for a zone
 private Location corner1; // Made non-final to allow updates
 private Location corner2; // Made non-final to allow updates
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
  this.world = corner1.getWorld();
  this.corner1 = corner1;
  this.corner2 = corner2;
  this.defaultAction = defaultAction;
  this.materialSpecificActions = new HashMap<>(materialSpecificActions);
 }

 // Getters
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

 public Map<Material, ZoneAction> getMaterialSpecificActions() {
  return Collections.unmodifiableMap(materialSpecificActions);
 }

 // Setters for mutable properties
 public void setCorner1(@NotNull Location corner1) {
  if (!corner1.getWorld().equals(this.world)) {
   throw new IllegalArgumentException("New corner1 must be in the same world as the existing zone. Zone world: " + this.world.getName() + ", Provided world: " + corner1.getWorld().getName());
  }
  this.corner1 = corner1;
 }

 public void setCorner2(@NotNull Location corner2) {
  if (!corner2.getWorld().equals(this.world)) {
   throw new IllegalArgumentException("New corner2 must be in the same world as the existing zone. Zone world: " + this.world.getName() + ", Provided world: " + corner2.getWorld().getName());
  }
  this.corner2 = corner2;
 }

 public void setDefaultAction(@NotNull ZoneAction defaultAction) {
  this.defaultAction = defaultAction;
 }

 public void addMaterialSpecificAction(@NotNull Material material, @NotNull ZoneAction action) {
  this.materialSpecificActions.put(material, action);
 }

 public void removeMaterialSpecificAction(@NotNull Material material) {
  this.materialSpecificActions.remove(material);
 }

 /**
  * Determines the effective action for a given material within this zone.
  * Checks material-specific actions first, then falls back to the default action.
  *
  * @param material The material to check.
  * @return The ZoneAction applicable to the material in this zone.
  */
 @NotNull
 public ZoneAction getEffectiveAction(@NotNull Material material) {
  return materialSpecificActions.getOrDefault(material, defaultAction);
 }


 /**
  * Checks if a given location is within this zone.
  * The check is inclusive of the boundary coordinates.
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
  return Objects.hash(name, world, corner1, corner2, materialSpecificActions, defaultAction);
 }

 @Override
 public String toString() {
  return "AutoInformZone{" +
          "name='" + name + '\'' +
          ", world=" + world.getName() +
          ", corner1=" + String.format("[%d, %d, %d]", corner1.getBlockX(), corner1.getBlockY(), corner1.getBlockZ()) +
          ", corner2=" + String.format("[%d, %d, %d]", corner2.getBlockX(), corner2.getBlockY(), corner2.getBlockZ()) +
          ", defaultAction=" + defaultAction +
          ", materialSpecificActions=" + materialSpecificActions +
          '}';
 }
}