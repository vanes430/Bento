package world.bentobox.magiccobblestonegenerator;

import org.bukkit.Material;
import world.bentobox.bentobox.api.addons.Addon;
import world.bentobox.bentobox.api.addons.GameModeAddon;
import world.bentobox.bentobox.api.configuration.Config;
import world.bentobox.bentobox.api.flags.Flag;
import world.bentobox.bentobox.api.flags.clicklisteners.CycleClick;
import world.bentobox.bentobox.managers.RanksManager;
import world.bentobox.magiccobblestonegenerator.commands.admin.GeneratorAdminCommand;
import world.bentobox.magiccobblestonegenerator.commands.player.GeneratorPlayerCommand;
import world.bentobox.magiccobblestonegenerator.config.Settings;
import world.bentobox.magiccobblestonegenerator.listeners.VanillaGeneratorListener;
import world.bentobox.magiccobblestonegenerator.managers.StoneGeneratorImportManager;
import world.bentobox.magiccobblestonegenerator.managers.StoneGeneratorManager;
import world.bentobox.magiccobblestonegenerator.tasks.MagicGenerator;

public class StoneGeneratorAddon extends Addon {
	@Override
	public void onLoad() {
		super.onLoad();
		this.saveDefaultConfig();
		this.saveResource("generator.yml", false);
		this.settings = new Config<>(this, Settings.class).loadConfigObject();
		StoneGeneratorAddon.instance = this;
	}

	@Override
	public void onEnable() {
		this.stoneGeneratorImportManager = new StoneGeneratorImportManager(this);
		this.stoneGeneratorManager = new StoneGeneratorManager(this);
		this.stoneGeneratorManager.load();
		this.getPlugin().getAddonsManager().getGameModeAddons().forEach(this::hookIntoGameMode);
		this.generator = new MagicGenerator(this);
		registerListener(new VanillaGeneratorListener(this));
		this.registerFlag(MAGIC_COBBLESTONE_GENERATOR);
		this.registerFlag(MAGIC_COBBLESTONE_GENERATOR_PERMISSION);
	}

	@Override
	public void onDisable() {
	}

	private void hookIntoGameMode(GameModeAddon gameMode) {
		this.stoneGeneratorManager.addWorld(gameMode.getOverWorld());
		if (gameMode.getWorldSettings().isNetherIslands())
			this.stoneGeneratorManager.addWorld(gameMode.getNetherWorld());
		if (gameMode.getWorldSettings().isEndIslands())
			this.stoneGeneratorManager.addWorld(gameMode.getEndWorld());
		MAGIC_COBBLESTONE_GENERATOR.addGameModeAddon(gameMode);
		MAGIC_COBBLESTONE_GENERATOR_PERMISSION.addGameModeAddon(gameMode);
		gameMode.getPlayerCommand().ifPresent(pc -> new GeneratorPlayerCommand(this, pc));
		gameMode.getAdminCommand().ifPresent(ac -> new GeneratorAdminCommand(this, ac));
		if (this.stoneGeneratorManager.getAllTiers(gameMode.getOverWorld()).isEmpty()) {
			this.stoneGeneratorImportManager.importFile(null, gameMode.getOverWorld());
		}
	}

	public Settings getSettings() {
		return this.settings;
	}
	public MagicGenerator getGenerator() {
		return this.generator;
	}
	public StoneGeneratorManager getAddonManager() {
		return this.stoneGeneratorManager;
	}
	public StoneGeneratorImportManager getImportManager() {
		return this.stoneGeneratorImportManager;
	}
	public boolean isVaultProvided() {
		return this.getPlugin().getVault().isPresent();
	}
	public static StoneGeneratorAddon getInstance() {
		return instance;
	}

	private Settings settings;
	private StoneGeneratorManager stoneGeneratorManager;
	private StoneGeneratorImportManager stoneGeneratorImportManager;
	private MagicGenerator generator;
	private static StoneGeneratorAddon instance;

	public final static Flag MAGIC_COBBLESTONE_GENERATOR = new Flag.Builder("MAGIC_COBBLESTONE_GENERATOR",
			Material.DIAMOND_PICKAXE).type(Flag.Type.SETTING).defaultSetting(true).build();

	public final static Flag MAGIC_COBBLESTONE_GENERATOR_PERMISSION = new Flag.Builder(
			"MAGIC_COBBLESTONE_GENERATOR_PERMISSION", Material.DIAMOND_PICKAXE).type(Flag.Type.PROTECTION)
			.defaultRank(RanksManager.SUB_OWNER_RANK)
			.clickHandler(new CycleClick("MAGIC_COBBLESTONE_GENERATOR_PERMISSION", RanksManager.MEMBER_RANK,
					RanksManager.OWNER_RANK))
			.build();
}
