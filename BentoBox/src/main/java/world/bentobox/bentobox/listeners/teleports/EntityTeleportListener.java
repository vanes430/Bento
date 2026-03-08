//
// Created by BONNe
// Copyright - 2022
//

package world.bentobox.bentobox.listeners.teleports;

import java.util.UUID;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.eclipse.jdt.annotation.NonNull;

import io.canvasmc.canvas.event.EntityPostTeleportAsyncEvent;

import world.bentobox.bentobox.BentoBox;

/**
 * Listener for entity post-teleport cleanup. Portal teleport logic is handled
 * by PortalTeleportListener.
 *
 * @author BONNe
 */
public class EntityTeleportListener implements Listener {
	private final BentoBox plugin;

	/**
	 * Instantiates a new Entity teleport listener.
	 *
	 * @param plugin
	 *            the plugin
	 */
	public EntityTeleportListener(@NonNull BentoBox plugin) {
		this.plugin = plugin;
	}

	// ---------------------------------------------------------------------
	// Section: Listeners
	// ---------------------------------------------------------------------

	/**
	 * Clean up inTeleport and teleportOrigin flags when entity is finished
	 * teleporting. Uses Canvas EntityPostTeleportAsyncEvent for Folia
	 * compatibility.
	 *
	 * @param event
	 *            - EntityPostTeleportAsyncEvent
	 */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onEntityPostTeleport(EntityPostTeleportAsyncEvent event) {
		UUID entityId = event.getEntity().getUniqueId();
		// Cleanup is handled by PortalTeleportListener via AbstractTeleportListener
		// This listener is kept for future entity-specific cleanup if needed
	}
}
