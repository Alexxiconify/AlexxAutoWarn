package net.Alexxiconify.alexxAutoWarn;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.function.Consumer;

public interface AutoWarnAPI {
 Zone getZoneAt ( Location loc );

 Collection < Zone > getZonesAt ( Location loc ); // For nested/prioritized zones

 Collection < Zone > getAllZones ( );

 void registerZoneActionListener ( Consumer < ZoneActionEvent > listener );

 void registerCustomAction ( String actionName , ZoneCustomAction action );

 boolean isActionAllowed ( Player player , Location loc , Material mat , String action );

 Zone getHighestPriorityZoneAt ( Location loc );

 void registerZoneEnterListener ( Consumer < ZoneEnterEvent > listener );

 void registerZoneLeaveListener ( Consumer < ZoneLeaveEvent > listener );

 void registerZonePriorityChangeListener ( Consumer < ZonePriorityChangeEvent > listener );
}