package net.Alexxiconify.alexxAutoWarn.region;

import org.bukkit.Location;
import org.bukkit.World;

public class RegionSelection {
 private String regionName;
 private World world;
 private Location pos1;
 private Location pos2;

 public RegionSelection(String regionName, World world) {
  this.regionName = regionName;
  this.world = world;
 }

 public String getRegionName() {
  return regionName;
 }

 public World getWorld() {
  return world;
 }

 public Location getPos1() {
  return pos1;
 }

 public void setPos1(Location pos1) {
  this.pos1 = pos1;
 }

 public Location getPos2() {
  return pos2;
 }

 public void setPos2(Location pos2) {
  this.pos2 = pos2;
 }

 public boolean isComplete() {
  return pos1 != null && pos2 != null && pos1.getWorld().equals(pos2.getWorld());
 }
}