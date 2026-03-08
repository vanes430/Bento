//
// Created by BONNe
// Copyright - 2022
//

package world.bentobox.bentobox.listeners.teleports;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.util.Vector;

import io.canvasmc.canvas.event.EntityPortalAsyncEvent;
import org.eclipse.jdt.annotation.NonNull;

import world.bentobox.bentobox.BentoBox;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.util.Util;

/**
 * Handles portal teleportation between dimensions for all entities. Uses Canvas
 * EntityPortalAsyncEvent for Folia compatibility.
 *
 * Logic: - When entity enters portal → teleport to island in target dimension -
 * Get entity's owner island in target dimension (Nether/End) - Teleport entity
 * to island location
 *
 * @author tastybento and BONNe
 */
public class PortalTeleportListener extends AbstractTeleportListener implements Listener {
	/**
	 * Instantiates a new Portal teleportation listener.
	 *
	 * @param plugin
	 *            the plugin
	 */
	public PortalTeleportListener(@NonNull BentoBox plugin) {
		super(plugin);
	}

	// ---------------------------------------------------------------------
	// Section: Listeners
	// ---------------------------------------------------------------------

	/**
	 * Handles portal teleportation using Canvas EntityPortalAsyncEvent. Teleports
	 * entity to their owner's island in target dimension.
	 *
	 * @param event
	 *            - EntityPortalAsyncEvent (Canvas)
	 */
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onEntityPortal(EntityPortalAsyncEvent event) {
		Entity entity = event.getEntity();
		UUID entityId = entity.getUniqueId();
		UUID ownerId = null;

		// Get owner UUID for entities (passengers, pets, etc.)
		if (entity instanceof Player player) {
			ownerId = player.getUniqueId();
		} else if (entity.getPassengers() != null && !entity.getPassengers().isEmpty()) {
			// Entity has passengers, use first passenger as owner
			Entity passenger = entity.getPassengers().get(0);
			if (passenger instanceof Player player) {
				ownerId = player.getUniqueId();
			}
		}

		World fromWorld = event.getFrom();
		World toWorld = event.getTo();

		// Check if from world is a BentoBox world
		World overWorld = Util.getWorld(fromWorld);
		if (overWorld == null || !this.plugin.getIWM().inWorld(overWorld)) {
			return;
		}

		// Determine target environment - ONLY handle NETHER, skip END
		World.Environment targetEnv = toWorld.getEnvironment();
		if (targetEnv != World.Environment.NETHER) {
			return; // Only handle Nether, let End use vanilla
		}

		// Check if already in teleport or target world not allowed
		if (this.inTeleport.contains(entityId) || !this.isAllowedInConfig(overWorld, targetEnv)) {
			return;
		}

		// Get target BentoBox world
		World targetWorld = this.getNetherEndWorld(overWorld, targetEnv);
		if (targetWorld == null) {
			return;
		}

		// Get island - use owner UUID if available, otherwise entity location
		Island currentIsland = ownerId != null ? this.plugin.getIslands().getIsland(overWorld, ownerId) : null;
		if (currentIsland == null) {
			currentIsland = this.plugin.getIslands().getIslandAt(entity.getLocation()).orElse(null);
		}

		if (currentIsland == null) {
			return;
		}

		// Get the SAME island in target dimension
		Island targetIsland = this.plugin.getIslands().getIsland(targetWorld, currentIsland.getOwner());
		if (targetIsland == null) {
			return;
		}

		// Calculate destination location - Nether only
		Location spawnPoint = targetIsland.getSpawnPoint(targetEnv);
		Location destLoc = spawnPoint != null ? spawnPoint : targetIsland.getCenter().clone();
		if (spawnPoint == null) {
			destLoc.setWorld(targetWorld);
		}

		// Mark as in teleport and cancel vanilla event
		this.inTeleport.add(entityId);
		event.setCancelled(true);

		// Teleport entity using current region scheduler
		Bukkit.getRegionScheduler().run(this.plugin, entity.getLocation(), task -> {
			if (!entity.isValid()) {
				this.inTeleport.remove(entityId);
				return;
			}

			entity.teleportAsync(destLoc).thenRun(() -> {
				entity.setVelocity(new Vector(0, 0, 0));
				entity.setFallDistance(0);
				this.inTeleport.remove(entityId);
			}).exceptionally(ex -> {
				this.inTeleport.remove(entityId);
				return null;
			});
		});
	}

	// ---------------------------------------------------------------------
	// Section: Cleanup
	// ---------------------------------------------------------------------

	/**
	 * Clean up inTeleport set if entity/player disconnects during teleport
	 */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
		this.inTeleport.remove(event.getPlayer().getUniqueId());
	}
}
