package world.bentobox.magiccobblestonegenerator.config;

import java.util.HashSet;
import java.util.Set;
import world.bentobox.bentobox.api.configuration.ConfigComment;
import world.bentobox.bentobox.api.configuration.ConfigEntry;
import world.bentobox.bentobox.api.configuration.ConfigObject;
import world.bentobox.bentobox.api.configuration.StoreAt;

@StoreAt(filename = "config.yml", path = "addons/MagicCobblestoneGenerator")
@ConfigComment("Simplified MagicCobblestoneGenerator Configuration")
public class Settings implements ConfigObject {
	public Settings() {
	}

	public int getDefaultActiveGeneratorCount() {
		return defaultActiveGeneratorCount;
	}
	public void setDefaultActiveGeneratorCount(int count) {
		this.defaultActiveGeneratorCount = count;
	}

	public Set<String> getDisabledGameModes() {
		return disabledGameModes;
	}
	public void setDisabledGameModes(Set<String> modes) {
		this.disabledGameModes = modes;
	}

	public String getPlayerMainCommand() {
		return playerMainCommand;
	}
	public void setPlayerMainCommand(String cmd) {
		this.playerMainCommand = cmd;
	}

	public String getAdminMainCommand() {
		return adminMainCommand;
	}
	public void setAdminMainCommand(String cmd) {
		this.adminMainCommand = cmd;
	}

	@ConfigComment("Maximum active generators per island. (Default 1)")
	@ConfigEntry(path = "default-active-generators")
	private int defaultActiveGeneratorCount = 1;

	@ConfigEntry(path = "disabled-gamemodes")
	private Set<String> disabledGameModes = new HashSet<>();

	@ConfigEntry(path = "commands.player.main")
	private String playerMainCommand = "generator";

	@ConfigEntry(path = "commands.admin.main")
	private String adminMainCommand = "generator";
}
