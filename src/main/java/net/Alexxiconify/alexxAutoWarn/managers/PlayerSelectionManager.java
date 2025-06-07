package net.Alexxiconify.alexxAutoWarn.managers;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages temporary wand selections (pos1 and pos2) for each player.
 */
public class PlayerSelectionManager {

 // Stores player UUID to a map of "pos1"/"pos2" to Location
 private final Map<UUID, Map<String, Location>> playerSelections = new ConcurrentHashMap<>();

 /**
  * Sets a selection point for a player.
  *
  * @param player   The player.
  * @param type     The type of selection ("pos1" or "pos2").
  * @param location The selected location.
  */
 public void setSelection(@NotNull Player player, @NotNull String type, @NotNull Location location) {
  playerSelections.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>()).put(type, location);
 }

 /**
  * Gets a selection point for a player.
  *
  * @param player The player.
  * @param type   The type of selection ("pos1" or "pos2").
  * @return The selected location, or null if not set.
  */
 @Nullable
 public Location getSelection(@NotNull Player player, @NotNull String type) {
  Map<String, Location> selections = playerSelections.get(player.getUniqueId());
  return selections != null ? selections.get(type) : null;
 }

 /**
  * Clears all selections for a specific player.
  *
  * @param player The player.
  */
 public void clearSelections(@NotNull Player player) {
  playerSelections.remove(player.getUniqueId());
 }

 /**
  * Checks if a player has both pos1 and pos2 selections set.
  *
  * @param player The player.
  * @return true if both selections are set, false otherwise.
  */
 public boolean hasBothSelections(@NotNull Player player) {
  Map<String, Location> selections = playerSelections.get(player.getUniqueId());
  return selections != null && selections.containsKey("pos1") && selections.containsKey("pos2");
 }
}