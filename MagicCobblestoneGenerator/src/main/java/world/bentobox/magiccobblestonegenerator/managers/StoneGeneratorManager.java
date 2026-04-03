package world.bentobox.magiccobblestonegenerator.managers;

import java.util.*;
import java.util.concurrent.*;
import org.bukkit.*;
import org.jetbrains.annotations.*;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.Database;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.magiccobblestonegenerator.StoneGeneratorAddon;
import world.bentobox.magiccobblestonegenerator.database.objects.*;

public class StoneGeneratorManager {
	private final StoneGeneratorAddon addon;
	private final Map<String, GeneratorTierObject> tierCache = new ConcurrentHashMap<>();
	private final Map<String, GeneratorDataObject> dataCache = new ConcurrentHashMap<>();
	private final Database<GeneratorTierObject> tierDb;
	private final Database<GeneratorDataObject> dataDb;
	private final IslandDataManager islandManager;
	private final EconomyManager economyManager;

	public StoneGeneratorManager(StoneGeneratorAddon addon) {
		this.addon = addon;
		this.tierDb = new Database<>(addon, GeneratorTierObject.class);
		this.dataDb = new Database<>(addon, GeneratorDataObject.class);
		this.islandManager = new IslandDataManager(addon);
		this.economyManager = new EconomyManager(addon);
	}

	public void load() {
		tierCache.clear();
		tierDb.loadObjects().stream().limit(5).forEach(t -> tierCache.put(t.getUniqueId(), t));
	}

	public void reload() {
		dataCache.clear();
		load();
	}
	public void saveGeneratorTier(GeneratorTierObject t) {
		tierDb.saveObject(t);
	}
	public void saveGeneratorData(GeneratorDataObject d) {
		dataDb.saveObject(d);
	}

	public IslandDataManager getIslandDataManager() {
		return islandManager;
	}
	public Map<String, GeneratorTierObject> getTierCache() {
		return tierCache;
	}

	public GeneratorTierObject getGeneratorByID(String id) {
		return id == null ? null : tierCache.get(id);
	}
	public Map<String, GeneratorDataObject> getDataCache() {
		return dataCache;
	}
	public Database<GeneratorDataObject> getDataDb() {
		return dataDb;
	}
	public GeneratorDataObject getGeneratorData(Island island) {
		return islandManager.getOrCreate(island);
	}
	public GeneratorDataObject validateIslandData(Island island) {
		return islandManager.validate(island);
	}
	public void addWorld(World w) {
		islandManager.addWorld(w);
	}
	public boolean canOperate(World w) {
		return islandManager.canOperate(w);
	}
	public boolean isWorldSupported(World w) {
		return canOperate(w);
	}

	public @Nullable GeneratorTierObject getGeneratorTier(Island island, Location loc,
			GeneratorTierObject.GeneratorType type) {
		if (island == null)
			return findDefault(loc.getWorld(), type);
		GeneratorDataObject data = getGeneratorData(island);
		return data.getActiveGeneratorList().stream().map(this::getGeneratorByID).filter(Objects::nonNull)
				.filter(g -> g.isDeployed() && g.getGeneratorType().includes(type))
				.max(Comparator.comparing(GeneratorTierObject::getPriority)).orElse(findDefault(loc.getWorld(), type));
	}

	private @Nullable GeneratorTierObject findDefault(World w, GeneratorTierObject.GeneratorType t) {
		String gm = addon.getPlugin().getIWM().getAddon(w).map(a -> a.getDescription().getName().toLowerCase())
				.orElse("");
		return tierCache.values().stream().filter(
				g -> g.isDefaultGenerator() && g.getGeneratorType().includes(t) && g.getUniqueId().startsWith(gm))
				.max(Comparator.comparing(GeneratorTierObject::getPriority)).orElse(null);
	}

	public List<GeneratorTierObject> getAllTiers(World w) {
		String gm = addon.getPlugin().getIWM().getAddon(w).map(a -> a.getDescription().getName().toLowerCase())
				.orElse("");
		return tierCache.values().stream().filter(g -> g.getUniqueId().startsWith(gm))
				.sorted(Comparator.comparing(GeneratorTierObject::isDefaultGenerator).reversed()
						.thenComparing(GeneratorTierObject::getPriority))
				.toList();
	}

	public void activate(User u, Island i, GeneratorDataObject d, GeneratorTierObject t) {
		economyManager.activate(u, i, d, t);
	}
	public void purchase(User u, Island i, GeneratorDataObject d, GeneratorTierObject t) {
		economyManager.purchase(u, i, d, t);
	}
	public boolean canActivate(User u, GeneratorDataObject d, GeneratorTierObject t) {
		return economyManager.canActivate(u, d, t);
	}
	public boolean canPurchase(User u, GeneratorDataObject d, GeneratorTierObject t) {
		return economyManager.canPurchase(u, d, t);
	}
	public void deactivate(User u, GeneratorDataObject d, GeneratorTierObject t) {
		if (d.getActiveGeneratorList().remove(t.getUniqueId()))
			saveGeneratorData(d);
	}
}
