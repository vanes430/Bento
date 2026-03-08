package world.bentobox.magiccobblestonegenerator.managers;

import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.magiccobblestonegenerator.StoneGeneratorAddon;
import world.bentobox.magiccobblestonegenerator.database.objects.GeneratorDataObject;
import world.bentobox.magiccobblestonegenerator.database.objects.GeneratorTierObject;
import world.bentobox.magiccobblestonegenerator.utils.Utils;

public class EconomyManager {
	private final StoneGeneratorAddon addon;
	public EconomyManager(StoneGeneratorAddon addon) { this.addon = addon; }

	public boolean canActivate(User u, GeneratorDataObject d, GeneratorTierObject t) {
		if (!d.getPurchasedTiers().contains(t.getUniqueId())) return false;
		return !this.addon.isVaultProvided() || t.getActivationCost() <= 0 || this.addon.getPlugin().getVault().map(v -> v.has(u, t.getActivationCost())).orElse(true);
	}

	public boolean canPurchase(User u, GeneratorDataObject d, GeneratorTierObject t) {
		if (d.getPurchasedTiers().contains(t.getUniqueId())) return false;
		return !this.addon.isVaultProvided() || t.getGeneratorTierCost() <= 0 || this.addon.getPlugin().getVault().map(v -> v.has(u, t.getGeneratorTierCost())).orElse(true);
	}

	public void activate(User u, Island i, GeneratorDataObject d, GeneratorTierObject t) {
		if (this.canActivate(u, d, t)) {
			if (this.addon.isVaultProvided() && t.getActivationCost() > 0)
				this.addon.getPlugin().getVault().ifPresent(v -> v.withdraw(u, t.getActivationCost()));
			d.getActiveGeneratorList().add(t.getUniqueId());
			this.addon.getAddonManager().saveGeneratorData(d);
			Utils.sendMessage(u, "&eActivated: " + t.getFriendlyName());
		}
	}

	public void purchase(User u, Island i, GeneratorDataObject d, GeneratorTierObject t) {
		if (this.canPurchase(u, d, t)) {
			if (this.addon.isVaultProvided() && t.getGeneratorTierCost() > 0)
				this.addon.getPlugin().getVault().ifPresent(v -> v.withdraw(u, t.getGeneratorTierCost()));
			d.getPurchasedTiers().add(t.getUniqueId());
			this.addon.getAddonManager().saveGeneratorData(d);
			Utils.sendMessage(u, "&aPurchased: " + t.getFriendlyName());
		}
	}
}
