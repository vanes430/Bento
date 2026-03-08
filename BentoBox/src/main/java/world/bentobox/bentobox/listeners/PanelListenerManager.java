package world.bentobox.bentobox.listeners;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType.SlotType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.InventoryView;

import world.bentobox.bentobox.BentoBox;
import world.bentobox.bentobox.api.panels.Panel;
import world.bentobox.bentobox.api.panels.PanelItem;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.util.Util;

public class PanelListenerManager implements Listener {

	private static final HashMap<UUID, Panel> openPanels = new HashMap<>();

	@EventHandler(priority = EventPriority.LOW)
	public void onInventoryClick(InventoryClickEvent event) {
		User user = User.getInstance(event.getWhoClicked());
		InventoryView view = event.getView();

		if (openPanels.containsKey(user.getUniqueId())
				&& openPanels.get(user.getUniqueId()).getInventory().equals(event.getInventory())) {
			event.setCancelled(true);

			if (Util.stripColor(view.getTitle())
					.equals(Util.stripColor(openPanels.get(user.getUniqueId()).getName()))) {
				if (BentoBox.getInstance().getSettings().isClosePanelOnClickOutside()
						&& event.getSlotType().equals(SlotType.OUTSIDE)) {
					event.getWhoClicked().closeInventory();
					return;
				}

				Panel panel = openPanels.get(user.getUniqueId());
				PanelItem pi = panel.getItems().get(event.getRawSlot());

				// Wrap EVERYTHING in GlobalRegionScheduler for Folia compatibility
				Bukkit.getGlobalRegionScheduler().run(BentoBox.getInstance(), task -> {
					if (pi != null) {
						pi.getClickHandler()
								.ifPresent(handler -> handler.onClick(panel, user, event.getClick(), event.getSlot()));
					}

					panel.getListener().ifPresent(l -> {
						l.onInventoryClick(user, event);
						l.refreshPanel();
					});
				});

			} else {
				openPanels.remove(user.getUniqueId());
				user.closeInventory();
			}
		}
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onInventoryClose(InventoryCloseEvent event) {
		if (openPanels.containsKey(event.getPlayer().getUniqueId())) {
			openPanels.get(event.getPlayer().getUniqueId()).getListener().ifPresent(l -> l.onInventoryClose(event));
			openPanels.remove(event.getPlayer().getUniqueId());
		}
	}

	@EventHandler(priority = EventPriority.NORMAL)
	public void onLogOut(PlayerQuitEvent event) {
		openPanels.remove(event.getPlayer().getUniqueId());
	}

	@EventHandler(priority = EventPriority.NORMAL)
	public void onPluginDisable(PluginDisableEvent event) {
		if (event.getPlugin().getName().equals("BentoBox")) {
			closeAllPanels();
		}
	}

	public static void closeAllPanels() {
		new ArrayList<>(openPanels.values())
				.forEach(p -> new ArrayList<>(p.getInventory().getViewers()).forEach(HumanEntity::closeInventory));
	}

	public static Map<UUID, Panel> getOpenPanels() {
		return openPanels;
	}

}
