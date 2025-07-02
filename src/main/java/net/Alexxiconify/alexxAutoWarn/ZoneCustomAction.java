package net.Alexxiconify.alexxAutoWarn;

import org.bukkit.Material;
import org.bukkit.entity.Player;

public interface ZoneCustomAction {
 void execute ( Player player , Zone zone , Material mat , String context );
}