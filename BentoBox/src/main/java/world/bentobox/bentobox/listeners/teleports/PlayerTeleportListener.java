//
// Created by BONNe
// Copyright - 2022
//

package world.bentobox.bentobox.listeners.teleports;

import java.util.UUID;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.eclipse.jdt.annotation.NonNull;

import world.bentobox.bentobox.BentoBox;

/**
 * Fallback listener for player teleportation. Handles cleanup and edge cases
 * not covered by PortalTeleportListener.
 *
 * @author tastybento and BONNe
 */
public class PlayerTeleportListener extends AbstractTeleportListener implements Listener {
	/**
	 * Instantiates a new Portal teleportation listener.
	 *
	 * @param plugin
	 *            the plugin
	 */
	public PlayerTeleportListener(@NonNull BentoBox plugin) {
		super(plugin);
	}

	// ---------------------------------------------------------------------
	// Section: Listeners
	// ---------------------------------------------------------------------

	/**
	 * Remove inTeleport flag when player exits the portal area
	 *
	 * @param event
	 *            player move event
	 */
	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	public void onExitPortal(PlayerMoveEvent event) {
		UUID playerId = event.getPlayer().getUniqueId();

		if (!this.inTeleport.contains(playerId)) {
			return;
		}

		if (event.getTo() != null && !event.getTo().getBlock().getType().equals(org.bukkit.Material.NETHER_PORTAL)) {
			// Player exits nether portal area.
			this.inTeleport.remove(playerId);
		}
	}

	/**
	 * Clean up inTeleport set if player disconnects during teleport
	 */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
		this.inTeleport.remove(event.getPlayer().getUniqueId());
	}
}
