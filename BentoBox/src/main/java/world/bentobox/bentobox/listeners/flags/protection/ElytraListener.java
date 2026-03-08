package world.bentobox.bentobox.listeners.flags.protection;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityToggleGlideEvent;

import io.canvasmc.canvas.event.EntityTeleportAsyncEvent;

import world.bentobox.bentobox.api.flags.FlagListener;
import world.bentobox.bentobox.lists.Flags;

public class ElytraListener extends FlagListener {

	/**
	 * Handle visitors using elytra
	 * 
	 * @param e
	 *            - event
	 */
	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	public void onGlide(EntityToggleGlideEvent e) {
		if (e.getEntity() instanceof Player player && !checkIsland(e, player, player.getLocation(), Flags.ELYTRA)) {
			player.setGliding(false);
		}
	}

	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	public void onGliding(EntityTeleportAsyncEvent e) {
		if (e.getEntity() instanceof Player player && getIWM().inWorld(e.getTo()) && player.isGliding()) {
			checkIsland(e, player, e.getTo(), Flags.ELYTRA);
		}
	}

}