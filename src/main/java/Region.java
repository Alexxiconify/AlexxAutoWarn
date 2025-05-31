package net.Alexxiconify.alexxAutoWarn.region;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.stream.Collectors;

// This annotation is crucial for Bukkit's ConfigurationSerialization
@SerializableAs("AlexxAutoWarnRegion")
public class Region implements ConfigurationSerializable {

 private final String name;
 private final String worldName;
 private World world;
 private final Vector min; // The lower corner of the cuboid
 private final Vector max; // The upper corner of the cuboid

 private List<Material> bannedBlockPlacement;
 private boolean banChestInteraction;
 private List<Material> bannedItemUsage;

 public Region(String name, World world, Vector pos1, Vector pos2) {
  this.name = name;
  this.world = world;
  this.worldName = world.getName();
  // Calculate min and max vectors to ensure correct cuboid definition
  this.min = Vector.getMinimum(pos1, pos2);
  this.max = Vector.getMaximum(pos1, pos2);

  this.bannedBlockPlacement = new ArrayList<>();
  this.banChestInteraction = false; // Default to false
  this.bannedItemUsage = new ArrayList<>();
 }

 // Constructor for deserialization from config
 public Region(Map<String, Object> map) {
  this.name = (String) map.get("name");
  this.worldName = (String) map.get("world");
  this.world = Bukkit.getWorld(worldName);
  this.min = (Vector) map.get("min");
  this.max = (Vector) map.get("max");

  // Deserialize banned lists
  this.bannedBlockPlacement = ((List<?>) map.getOrDefault("banned_block_placement", new ArrayList<>()))
          .stream()
          .map(obj -> Material.valueOf((String) obj))
          .collect(Collectors.toList());
  this.banChestInteraction = (boolean) map.getOrDefault("ban_chest_interaction", false);
  this.bannedItemUsage = ((List<?>) map.getOrDefault("banned_item_usage", new ArrayList<>()))
          .stream()
          .map(obj -> Material.valueOf((String) obj))
          .collect(Collectors.toList());

  if (this.world == null) {
   Bukkit.getLogger().warning("AlexxAutoWarn: Region '" + name + "' refers to non-existent world: " + worldName);
  }
 }

 // Serialize the object to a Map for saving to config
 @Override
 public Map<String, Object> serialize() {
  Map<String, Object> map = new HashMap<>();
  map.put("name", name);
  map.put("world", worldName);
  map.put("min", min);
  map.put("max", max);
  map.put("banned_block_placement", bannedBlockPlacement.stream().map(Enum::name).collect(Collectors.toList()));
  map.put("ban_chest_interaction", banChestInteraction);
  map.put("banned_item_usage", bannedItemUsage.stream().map(Enum::name).collect(Collectors.toList()));
  return map;
 }

 // --- Getters ---
 public String getName() {
  return name;
 }

 public World getWorld() {
  if (world == null) {
   world = Bukkit.getWorld(worldName); // Attempt to re-fetch if null
  }
  return world;
 }

 public String getWorldName() {
  return worldName;
 }

 public Vector getMin() {
  return min;
 }

 public Vector getMax() {
  return max;
 }

 public List<Material> getBannedBlockPlacement() {
  return bannedBlockPlacement;
 }

 public boolean isBanChestInteraction() {
  return banChestInteraction;
 }

 public List<Material> getBannedItemUsage() {
  return bannedItemUsage;
 }

 // --- Setters (for configuration via GUI/commands) ---
 public void setBannedBlockPlacement(List<Material> bannedBlockPlacement) {
  this.bannedBlockPlacement = bannedBlockPlacement;
 }

 public void addBannedBlockPlacement(Material material) {
  if (!this.bannedBlockPlacement.contains(material)) {
   this.bannedBlockPlacement.add(material);
  }
 }

 public void removeBannedBlockPlacement(Material material) {
  this.bannedBlockPlacement.remove(material);
 }

 public void setBanChestInteraction(boolean banChestInteraction) {
  this.banChestInteraction = banChestInteraction;
 }

 public void setBannedItemUsage(List<Material> bannedItemUsage) {
  this.bannedItemUsage = bannedItemUsage;
 }

 public void addBannedItemUsage(Material material) {
  if (!this.bannedItemUsage.contains(material)) {
   this.bannedItemUsage.add(material);
  }
 }

 public void removeBannedItemUsage(Material material) {
  this.bannedItemUsage.remove(material);
 }

 // --- Utility method to check if a location is within this region ---
 public boolean contains(Location loc) {
  if (!loc.getWorld().getName().equals(worldName)) {
   return false;
  }
  return loc.getX() >= min.getX() && loc.getX() <= max.getX() + 1 && // +1 to include the block at max coord
          loc.getY() >= min.getY() && loc.getY() <= max.getY() + 1 &&
          loc.getZ() >= min.getZ() && loc.getZ() <= max.getZ() + 1;
 }

 // Override equals and hashCode for proper comparison in collections
 @Override
 public boolean equals(Object o) {
  if (this == o) return true;
  if (o == null || getClass() != o.getClass()) return false;
  Region region = (Region) o;
  return Objects.equals(name, region.name);
 }

 @Override
 public int hashCode() {
  return Objects.hash(name);
 }
}