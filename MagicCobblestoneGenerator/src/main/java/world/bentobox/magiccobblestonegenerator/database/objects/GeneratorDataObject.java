package world.bentobox.magiccobblestonegenerator.database.objects;

import java.util.*;
import java.util.concurrent.*;
import com.google.gson.annotations.Expose;
import world.bentobox.bentobox.database.objects.DataObject;
import world.bentobox.bentobox.database.objects.Table;

@Table(name = "GeneratorData")
public class GeneratorDataObject implements DataObject {
	@Expose
	private String uniqueId = "";
	@Expose
	private Set<String> unlockedTiers = ConcurrentHashMap.newKeySet();
	@Expose
	private Set<String> purchasedTiers = ConcurrentHashMap.newKeySet();
	@Expose
	private Set<String> activeGeneratorList = ConcurrentHashMap.newKeySet();

	@Override
	public String getUniqueId() {
		return this.uniqueId;
	}
	@Override
	public void setUniqueId(String uniqueId) {
		this.uniqueId = uniqueId;
	}

	public Set<String> getUnlockedTiers() {
		return unlockedTiers;
	}
	public Set<String> getPurchasedTiers() {
		return purchasedTiers;
	}
	public Set<String> getActiveGeneratorList() {
		return activeGeneratorList;
	}
}
