package net.Alexxiconify.alexxAutoWarn;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public final class Zone {
 private final String name;
 private final String worldName;
 private final Vector min;
 private final Vector max;
 private final Action defaultAction;
 private final Map < Material, Action > materialActions;
 private final int priority;

 public Zone (
   @NotNull String name , World world , @NotNull Vector corner1 , @NotNull Vector corner2 ,
   @NotNull Action defaultAction , @NotNull Map < Material, Action > materialActions , int priority
 ) {
  this.name = name.toLowerCase ( );
  this.worldName = world.getName ( );
  this.min = Vector.getMinimum ( corner1 , corner2 );
  this.max = Vector.getMaximum ( corner1 , corner2 );
  this.defaultAction = defaultAction;
  this.materialActions = Collections.unmodifiableMap ( new EnumMap <> ( materialActions ) );
  this.priority = priority;
 }

 public boolean contains ( @NotNull Location loc ) {
  return loc.getWorld ( ).getName ( ).equals ( this.worldName ) &&
    loc.getX ( ) >= min.getX ( ) && loc.getX ( ) <= max.getX ( ) &&
    loc.getY ( ) >= min.getY ( ) && loc.getY ( ) <= max.getY ( ) &&
    loc.getZ ( ) >= min.getZ ( ) && loc.getZ ( ) <= max.getZ ( );
 }

 @NotNull
 public Action getActionFor ( @NotNull Material material ) {
  return materialActions.getOrDefault ( material , defaultAction );
 }

 @NotNull
 public String getName ( ) {
  return name;
 }

 @NotNull
 public String getWorldName ( ) {
  return worldName;
 }

 @NotNull
 public Vector getMin ( ) {
  return min;
 }

 @NotNull
 public Vector getMax ( ) {
  return max;
 }

 @NotNull
 public Action getDefaultAction ( ) {
  return defaultAction;
 }

 @NotNull
 public Map < Material, Action > getMaterialActions ( ) {
  return materialActions;
 }

 public int getPriority ( ) {
  return priority;
 }

 @Override
 public boolean equals ( Object o ) {
  if ( this == o ) return true;
  if ( o == null || getClass ( ) != o.getClass ( ) ) return false;
  Zone zone = ( Zone ) o;
  return name.equals ( zone.name );
 }

 @Override
 public int hashCode ( ) {
  return Objects.hash ( name );
 }

 public enum Action {
  DENY, ALERT, ALLOW
 }
}