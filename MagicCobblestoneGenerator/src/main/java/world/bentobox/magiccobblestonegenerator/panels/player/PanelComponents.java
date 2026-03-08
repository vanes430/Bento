package world.bentobox.magiccobblestonegenerator.panels.player;

import java.util.*;
import org.bukkit.*;
import org.bukkit.inventory.ItemStack;
import world.bentobox.bentobox.api.panels.PanelItem;
import world.bentobox.bentobox.api.panels.builders.PanelItemBuilder;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.util.Util;
import world.bentobox.magiccobblestonegenerator.database.objects.*;
import world.bentobox.magiccobblestonegenerator.utils.Utils;

public class PanelComponents {
	public static void fillTiers(GeneratorUserPanel p, Map<Integer, PanelItem> items, User u) {
		for (int i = 0; i < 5; i++) {
			if (i >= p.tiers.size()) break;
			GeneratorTierObject t = p.tiers.get(i);
			boolean owned = t.isDefaultGenerator() || p.data.getPurchasedTiers().contains(t.getUniqueId());
			boolean active = p.data.getActiveGeneratorList().contains(t.getUniqueId());
			PanelItemBuilder b = new PanelItemBuilder().icon(owned ? t.getGeneratorIcon() : new ItemStack(Material.GRAY_DYE))
				.name(Util.translateColorCodes(t.getFriendlyName() + " &7(" + getLevelLabel(u, i) + "&7)"))
				.description(getLore(u, t, owned, active)).glow(active);
			items.put(11 + i, b.build());
		}
	}

	public static void fillActions(GeneratorUserPanel p, Map<Integer, PanelItem> items, User u) {
		boolean hasActive = !p.data.getActiveGeneratorList().isEmpty();
		int cur = -1;
		for (int i = 0; i < p.tiers.size(); i++) if (p.data.getPurchasedTiers().contains(p.tiers.get(i).getUniqueId())) cur = i;
		final int high = cur;
		boolean canActivate = high >= 0 && p.data.getActiveGeneratorList().isEmpty();
		items.put(18, new PanelItemBuilder()
			.icon(hasActive ? Material.RED_DYE : Material.GREEN_DYE)
			.name(u.getTranslation(hasActive ? "stone-generator.ui.status_active" : "stone-generator.ui.status_inactive"))
			.description(u.getTranslation("stone-generator.ui.click_to_switch"))
			.clickHandler((panel, user, c, i) -> {
				if (hasActive) {
					p.data.getActiveGeneratorList().clear();
					p.addon.getAddonManager().saveGeneratorData(p.data);
				} else if (canActivate) {
					p.addon.getAddonManager().activate(user, p.island, p.data, p.tiers.get(high));
				}
				GeneratorUserPanel.openPanel(p.addon, p.island.getWorld(), user);
				return true;
			}).build());
		fillUpgrade(p, items, high, u);
	}

	private static void fillUpgrade(GeneratorUserPanel p, Map<Integer, PanelItem> items, int cur, User u) {
		int nextIdx = cur + 1;
		if (nextIdx >= p.tiers.size()) items.put(26, new PanelItemBuilder().icon(Material.OBSIDIAN).name(u.getTranslation("stone-generator.ui.max_level")).build());
		else {
			GeneratorTierObject next = p.tiers.get(nextIdx);
			if (next.isDefaultGenerator()) {
				nextIdx++;
				if (nextIdx >= p.tiers.size()) {
					items.put(26, new PanelItemBuilder().icon(Material.OBSIDIAN).name(u.getTranslation("stone-generator.ui.max_level")).build());
					return;
				}
				next = p.tiers.get(nextIdx);
			}
			final GeneratorTierObject upgradeTier = next;
			items.put(26, new PanelItemBuilder().icon(Material.LIME_CONCRETE)
				.name(Util.translateColorCodes(u.getTranslation("stone-generator.ui.upgrade_to") + upgradeTier.getFriendlyName()))
				.description(Arrays.asList(
					Util.translateColorCodes(u.getTranslation("stone-generator.ui.cost") + "$" + upgradeTier.getGeneratorTierCost()),
					"",
					u.getTranslation("stone-generator.ui.owner_only")))
				.clickHandler((panel, user, c, i) -> {
					if (!user.getUniqueId().equals(p.island.getOwner())) {
						Utils.sendMessage(user, u.getTranslation("stone-generator.messages.owner-only"));
						return true;
					}
					if (!p.addon.getAddonManager().canPurchase(user, p.data, upgradeTier)) {
						double cost = upgradeTier.getGeneratorTierCost();
						double balance = p.addon.getPlugin().getVault().map(v -> v.getBalance(user)).orElse(0.0);
						Utils.sendMessage(user, u.getTranslation("stone-generator.messages.not-enough-money",
							"[number]", String.format("%.2f", cost), "[balance]", String.format("%.2f", balance)));
						return true;
					}
					p.addon.getAddonManager().purchase(user, p.island, p.data, upgradeTier);
					p.data.getActiveGeneratorList().clear();
					p.addon.getAddonManager().activate(user, p.island, p.data, upgradeTier);
					GeneratorUserPanel.openPanel(p.addon, p.island.getWorld(), user);
					return true;
				}).build());
		}
	}

	private static List<String> getLore(User u, GeneratorTierObject t, boolean owned, boolean active) {
		List<String> l = new ArrayList<>(); t.getDescription().forEach(s -> l.add(Util.translateColorCodes(s)));
		l.add(""); l.add(u.getTranslation("stone-generator.ui.blocks_title"));
		double tot = t.getBlockChanceMap().lastKey(), last = 0;
		for (var e : t.getBlockChanceMap().entrySet()) {
			double ch = (e.getKey() - last) / tot;
			l.add(Util.translateColorCodes(" &8- " + Utils.prettifyObject(e.getValue()) + ": &7" + String.format("%.1f%%", ch * 100)));
			last = e.getKey();
		}
		l.add(""); l.add(active ? u.getTranslation("stone-generator.gui.descriptions.generator.status.active") : (owned ? u.getTranslation("stone-generator.ui.owned") : u.getTranslation("stone-generator.ui.locked") + "$" + t.getGeneratorTierCost()));
		return l;
	}

	private static String getLevelLabel(User u, int i) { return u.getTranslation("stone-generator.ui.level", "Level") + " " + (i + 1); }
}
