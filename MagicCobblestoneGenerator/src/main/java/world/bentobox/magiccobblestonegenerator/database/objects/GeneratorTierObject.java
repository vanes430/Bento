package world.bentobox.magiccobblestonegenerator.database.objects;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import com.google.gson.annotations.Expose;

import world.bentobox.bentobox.database.objects.DataObject;
import world.bentobox.bentobox.database.objects.Table;

/**
 * Simplified GeneratorTierObject.
 */
@Table(name = "GeneratorTier")
public class GeneratorTierObject implements DataObject {
	public GeneratorTierObject() {
	}

	@Override
	public String getUniqueId() {
		return this.uniqueId;
	}
	@Override
	public void setUniqueId(String uniqueId) {
		this.uniqueId = uniqueId;
	}

	public String getFriendlyName() {
		return friendlyName;
	}
	public void setFriendlyName(String friendlyName) {
		this.friendlyName = friendlyName;
	}

	public List<String> getDescription() {
		return description;
	}
	public void setDescription(List<String> description) {
		this.description = description;
	}

	public ItemStack getGeneratorIcon() {
		return generatorIcon.clone();
	}
	public void setGeneratorIcon(ItemStack generatorIcon) {
		this.generatorIcon = generatorIcon;
	}

	public GeneratorType getGeneratorType() {
		return generatorType;
	}
	public void setGeneratorType(GeneratorType generatorType) {
		this.generatorType = generatorType;
	}

	public boolean isDefaultGenerator() {
		return defaultGenerator;
	}
	public void setDefaultGenerator(boolean defaultGenerator) {
		this.defaultGenerator = defaultGenerator;
	}

	public int getPriority() {
		return priority;
	}
	public void setPriority(int priority) {
		this.priority = priority;
	}

	public double getGeneratorTierCost() {
		return generatorTierCost;
	}
	public void setGeneratorTierCost(double generatorTierCost) {
		this.generatorTierCost = generatorTierCost;
	}

	public double getActivationCost() {
		return activationCost;
	}
	public void setActivationCost(double activationCost) {
		this.activationCost = activationCost;
	}

	public boolean isDeployed() {
		return deployed;
	}
	public void setDeployed(boolean deployed) {
		this.deployed = deployed;
	}

	public TreeMap<Double, Material> getBlockChanceMap() {
		return blockChanceMap;
	}
	public void setBlockChanceMap(TreeMap<Double, Material> blockChanceMap) {
		this.blockChanceMap = blockChanceMap;
	}

	@Override
	public GeneratorTierObject clone() {
		GeneratorTierObject clone = new GeneratorTierObject();
		clone.setUniqueId(this.uniqueId);
		clone.setFriendlyName(this.friendlyName);
		clone.setGeneratorIcon(this.generatorIcon.clone());
		clone.setDescription(new ArrayList<>(this.description));
		clone.setGeneratorType(this.generatorType);
		clone.setDefaultGenerator(this.defaultGenerator);
		clone.setPriority(this.priority);
		clone.setGeneratorTierCost(this.generatorTierCost);
		clone.setActivationCost(this.activationCost);
		clone.setDeployed(this.deployed);
		clone.setBlockChanceMap(new TreeMap<>(this.blockChanceMap));
		return clone;
	}

	public enum GeneratorType {
		COBBLESTONE(1), STONE(2), BASALT(4), COBBLESTONE_OR_STONE(3), BASALT_OR_COBBLESTONE(5), BASALT_OR_STONE(6), ANY(
				7);
		GeneratorType(int id) {
			this.id = id;
		}
		public boolean includes(GeneratorType type) {
			return (this.id & type.id) != 0;
		}
		private final int id;
	}

	@Expose
	private String uniqueId;
	@Expose
	private String friendlyName;
	@Expose
	private List<String> description = Collections.emptyList();
	@Expose
	private ItemStack generatorIcon = new ItemStack(Material.STONE);
	@Expose
	private GeneratorType generatorType = GeneratorType.COBBLESTONE;
	@Expose
	private boolean defaultGenerator = false;
	@Expose
	private int priority = 0;
	@Expose
	private double generatorTierCost = 0.0;
	@Expose
	private double activationCost = 0.0;
	@Expose
	private boolean deployed = true;
	@Expose
	private TreeMap<Double, Material> blockChanceMap = new TreeMap<>();
}
