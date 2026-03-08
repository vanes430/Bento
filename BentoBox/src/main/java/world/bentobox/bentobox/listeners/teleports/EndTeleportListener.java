//
// Created by BONNe
// Copyright - 2022
//

package world.bentobox.bentobox.listeners.teleports;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.eclipse.jdt.annotation.NonNull;

import io.canvasmc.canvas.event.EntityPostTeleportAsyncEvent;

import world.bentobox.bentobox.BentoBox;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.util.Util;

/**
 * Handles player teleport to End world. When player enters End via END_PORTAL,
 * tracks them with PlayerMoveEvent and teleports to their End island home when
 * they move. Does nothing for NETHER_PORTAL.
 *
 * @author tastybento and BONNe
 */
public class EndTeleportListener implements Listener {
	private final BentoBox plugin;
	private final Set<UUID> playersToTrack;

	/**
	 * Instantiates a new End teleport listener.
	 *
	 * @param plugin
	 *            the plugin
	 */
	public EndTeleportListener(@NonNull BentoBox plugin) {
		this.plugin = plugin;
		this.playersToTrack = ConcurrentHashMap.newKeySet();
	}

	// ---------------------------------------------------------------------
	// Section: Listeners
	// ---------------------------------------------------------------------

	/**
	 * Handles player teleport - only tracks END_PORTAL, ignores NETHER_PORTAL
	 */
	@EventHandler(priority = EventPriority.NORMAL)
	public void onEntityPostTeleport(EntityPostTeleportAsyncEvent event) {
		if (!(event.getEntity() instanceof Player player)) {
			return; // Only handle Player
		}

		// Ignore NETHER_PORTAL - do nothing
		if (event.getCause() == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) {
			return;
		}

		// Only handle END_PORTAL
		if (event.getCause() != PlayerTeleportEvent.TeleportCause.END_PORTAL) {
			return;
		}

		World toWorld = event.getTo().getWorld();

		if (toWorld.getEnvironment() != World.Environment.THE_END) {
			return; // Only handle End teleport
		}

		// Check if this is a BentoBox End world
		World overWorld = world.bentobox.bentobox.util.Util.getWorld(toWorld);
		if (overWorld == null || !this.plugin.getIWM().inWorld(overWorld)) {
			return;
		}

		UUID playerId = player.getUniqueId();

		// Add player to tracking set - will teleport on move
		this.playersToTrack.add(playerId);
	}

	/**
	 * Handles player move - teleports tracked players to their End island home
	 */
	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	public void onPlayerMove(PlayerMoveEvent event) {
		Player player = event.getPlayer();
		UUID playerId = player.getUniqueId();

		// Check if being tracked, in End world, and in a BentoBox world
		World playerWorld = player.getWorld();
		World overWorld = Util.getWorld(playerWorld);
		if (!this.playersToTrack.contains(playerId) || playerWorld.getEnvironment() != World.Environment.THE_END
				|| overWorld == null || !this.plugin.getIWM().inWorld(overWorld)) {
			return;
		}

		// Remove from tracking first to prevent recursion
		this.playersToTrack.remove(playerId);

		// Get player's island in End
		Island endIsland = this.plugin.getIslands().getIsland(playerWorld, playerId);
		if (endIsland == null) {
			return;
		}

		// Get destination - use spawn point or center
		Location spawnPoint = endIsland.getSpawnPoint(World.Environment.THE_END);
		Location destLoc = spawnPoint != null ? spawnPoint : endIsland.getCenter().clone();
		if (spawnPoint == null) {
			destLoc.setWorld(playerWorld);
		}

		// Teleport to island home
		Bukkit.getRegionScheduler().run(this.plugin, player.getLocation(), task -> {
			if (player.isOnline() && player.getWorld().equals(playerWorld)) {
				player.teleportAsync(destLoc).thenRun(() -> {
					player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
					player.setFallDistance(0);
				});
			}
		});
	}

	/**
	 * Clean up tracking set if player disconnects
	 */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
		this.playersToTrack.remove(event.getPlayer().getUniqueId());
	}

	/**
	 * Clean up tracking if player changes world before moving
	 */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onPlayerChangedWorld(org.bukkit.event.player.PlayerChangedWorldEvent event) {
		this.playersToTrack.remove(event.getPlayer().getUniqueId());
	}
}
