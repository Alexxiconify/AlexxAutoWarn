package net.Alexxiconify.alexxAutoWarn.utils;

import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Utility class for handling Bukkit Location objects,
 * including conversion to and from string representations.
 */
public class LocationUtil {

 private LocationUtil() {
  // Private constructor to prevent instantiation
 }

 /**
  * Converts a Location object to a compact string format: "worldName,x,y,z".
  *
  * @param location The Location object to convert.
  * @return A string representation of the location, or null if the location or world is null.
  */
 @Nullable
 public static String toCommaString(@Nullable Location location) {
  if (location == null || location.getWorld() == null) {
   return null;
  }
  return String.format("%s,%.2f,%.2f,%.2f",
          location.getWorld().getName(),
          location.getX(),
          location.getY(),
          location.getZ());
 }

 /**
  * Converts a Location object to a block-based string format: "World: [X, Y, Z]".
  * This is useful for displaying locations to players in a more readable format.
  *
  * @param location The Location object to convert.
  * @return A readable string representation of the location, or "N/A" if the location or world is null.
  */
 @NotNull
 public static String toBlockString(@Nullable Location location) {
  if (location == null || location.getWorld() == null) {
   return "N/A";
  }
  return String.format("World: %s [%d, %d, %d]",
          location.getWorld().getName(),
          location.getBlockX(),
          location.getBlockY(),
          location.getBlockZ());
 }

 /**
  * Parses a string (format: "worldName,x,y,z") into a Bukkit Location object.
  *
  * @param locationString The string representation of the location.
  * @param defaultWorld   A default world to use if the world name from the string is invalid or null.
  *                       Can be null, in which case the returned Location might have a null world if parsing fails.
  * @return The parsed Location object, or null if the string format is invalid.
  */
 @Nullable
 public static Location fromCommaString(@Nullable String locationString, @Nullable World defaultWorld) {
  if (locationString == null || locationString.isEmpty()) {
   return null;
  }
  try {
   String[] parts = locationString.split(",");
   if (parts.length == 4) {
    String worldName = parts[0];
    double x = Double.parseDouble(parts[1]);
    double y = Double.parseDouble(parts[2]);
    double z = Double.parseDouble(parts[3]);

    World world = org.bukkit.Bukkit.getWorld(worldName);
    if (world == null) {
     world = defaultWorld; // Use default world if the specified world isn't loaded
    }
    return new Location(world, x, y, z);
   }
  } catch (NumberFormatException e) {
   // Log this error in your plugin's logger if necessary
   // For now, just return null
  }
  return null;
 }
}