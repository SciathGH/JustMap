package ru.bulldog.justmap.map.data;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.World;

public class WorldKey {
	private String worldId;
	private String worldName;
	private BlockPos worldPos;
	private final Identifier dimension;
	
	public WorldKey(RegistryKey<World> worldKey) {
		this.dimension = worldKey.getValue();
		this.worldId = this.dimension.toString();
	}
	
	public void setWorldName(String name) {
		this.worldName = name;
		if (worldPos != null) {
			this.worldId = String.format("%s_%s_%s", worldName, dimension, worldPos.toShortString());
		} else {
			this.worldId = String.format("%s_%s", worldName, dimension);
		}
	}
	
	public void setWorldPos(BlockPos worldPos) {
		this.worldPos = worldPos;
		if (worldName != null) {
			this.worldId = String.format("%s_%s_%s", worldName, dimension, worldPos.toShortString());
		} else {
			this.worldId = String.format("%s_%s", dimension, worldPos.toShortString());
		}
		
	}
	
	public String getName() {
		return this.worldName;
	}
	
	public BlockPos getWorldPos() {
		return this.worldPos;
	}
	
	public String getWorldId() {
		return this.worldId;
	}
	
	public void clearName() {
		this.worldName = null;
		if (worldPos != null) {
			this.worldId = String.format("%s_%s", dimension, worldPos.toShortString());
		} else {
			this.worldId = dimension.toString();
		}
	}
	
	public void clearWorldPos() {
		this.worldPos = null;
		if (worldName != null) {
			this.worldId = String.format("%s_%s", worldName, dimension);
		} else {
			this.worldId = dimension.toString();
		}
	}
	
	public String toFolder() {
		String folder = this.worldId.replaceAll(", ", "_");
		folder = folder.replaceAll("[\\/]+", "_");
		folder = folder.replaceAll("[,:|\\<\\>\"\\?\\*]", "_");
		
		return folder;
	}
	
	@Override
	public String toString() {
		return String.format("WorldKey [%s]", this.worldId);
	}
	
	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (!(obj instanceof WorldKey)) return false;
		WorldKey anotherKey = (WorldKey) obj;
		return this.worldId.equals(anotherKey.worldId);
	}
	
	@Override
	public int hashCode() {
		return this.worldId.hashCode();
	}
}
