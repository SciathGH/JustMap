package ru.bulldog.justmap.map.data;

import ru.bulldog.justmap.client.config.ClientParams;
import ru.bulldog.justmap.util.ColorUtil;
import ru.bulldog.justmap.util.Colors;
import ru.bulldog.justmap.util.DataUtil;
import ru.bulldog.justmap.util.Dimension;
import ru.bulldog.justmap.util.tasks.TaskManager;

import net.minecraft.world.World;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.gen.ChunkRandom;

import java.lang.ref.SoftReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChunkData {
	
	public final static ChunkLevel EMPTY_LEVEL = new ChunkLevel(-1);
	
	private final static TaskManager chunkUpdater = TaskManager.getManager("chunk-updater", 2);
	
	private final DimensionData mapData;
	private final Map<Layer, ChunkLevel[]> levels = new ConcurrentHashMap<>();
	private final ChunkPos chunkPos;
	private World world;
	private SoftReference<WorldChunk> worldChunk;
	private boolean outdated = false;
	private boolean purged = false;
	private boolean slime = false;
	private boolean saved = true;
	private long refreshed = 0;
	
	public boolean saving = false;
	public long updated = 0;
	public long requested = 0;
	
	private Object levelLock = new Object();
	
	public ChunkData(DimensionData data, World world, WorldChunk lifeChunk) {
		this(data, world, lifeChunk.getPos());
		this.updateWorldChunk(lifeChunk);
	}
	
	public ChunkData(DimensionData data, World world, ChunkPos pos) {
		this.mapData = data;
		this.world = world;
		this.chunkPos = pos;
		this.worldChunk = new SoftReference<>(world.getChunk(pos.x, pos.z));

		RegistryKey<DimensionType> dimType = world.getDimensionRegistryKey();
		ServerWorld serverWorld = DataUtil.getServerWorld();
		if (serverWorld != null && Dimension.isOverworld(dimType)) {
			this.slime = ChunkRandom.getSlimeRandom(chunkPos.x, chunkPos.z,
					serverWorld.getSeed(), 987234911L).nextInt(10) == 0;
		}		
		if (Dimension.isNether(dimType)) {
			initLayer(Layer.NETHER);
		} else {
			initLayer(Layer.SURFACE);
			initLayer(Layer.CAVES);
		}
	}
	
	public ChunkData resetChunk() {
		synchronized (levelLock) {
			this.levels.clear();
		}
		this.outdated = true;
		this.updated = 0;
		
		return this;
	}
	
	private void initLayer(Layer layer) {
		int levels = this.world.getDimensionHeight() / layer.height;		
		this.levels.put(layer, new ChunkLevel[levels]);
	}
	
	private ChunkLevel getChunkLevel(Layer layer, int level) {
		synchronized (levelLock) {
			if (!levels.containsKey(layer)) {
				initLayer(layer);
			}
			
			ChunkLevel chunkLevel;
			try {
				chunkLevel = this.levels.get(layer)[level];
				if (chunkLevel == null) {
					chunkLevel = new ChunkLevel(level);
					this.levels.get(layer)[level] = chunkLevel;
				}
			} catch (ArrayIndexOutOfBoundsException ex) {
				chunkLevel = EMPTY_LEVEL;
			}
			return chunkLevel;
		}
	}
	
	public ChunkPos getPos() {
		return this.chunkPos;
	}
	
	public int getX() {
		return this.chunkPos.x;
	}
	
	public int getZ() {
		return this.chunkPos.z;
	}
	
	public int[] getHeighmap(Layer layer, int level) {
		return this.getChunkLevel(layer, level).heightmap;
	}
	
	public WorldChunk getWorldChunk() {
		return this.worldChunk.get();
	}
	
	public BlockState getBlockState(Layer layer, int level, BlockPos pos) {
		return this.getChunkLevel(layer, level).getBlockState(pos.getX() & 15, pos.getZ() & 15);
	}
	
	public void setBlockState(Layer layer, int level, BlockPos pos, BlockState blockState) {
		this.getChunkLevel(layer, level).setBlockState(pos.getX() & 15, pos.getZ() & 15, blockState);
	}
	
	private boolean checkUpdating(Layer layer, int level) {
		return this.getChunkLevel(layer, level).updating;
	}
	
	public void updateWorldChunk(WorldChunk lifeChunk) {
		if (lifeChunk != null && !lifeChunk.isEmpty()) {
			this.worldChunk = new SoftReference<>(lifeChunk);
		}
	}
	
	public WorldChunk updateWorldChunk() {
		WorldChunk currentChunk = this.worldChunk.get();
		if(currentChunk == null || currentChunk.isEmpty()) {
			WorldChunk lifeChunk = this.world.getChunk(getX(), getZ());
			if (lifeChunk == null || lifeChunk.isEmpty()) {
				return this.mapData.getEmptyChunk();
			}
			this.updateWorldChunk(lifeChunk);
			return lifeChunk;
		}
		return currentChunk;
	}
	
	public boolean update(Layer layer, int level, boolean forceUpdate) {
		if (purged || checkUpdating(layer, level)) return false;
		if (!outdated && forceUpdate) {
			this.outdated = forceUpdate;
		}
		long currentTime = System.currentTimeMillis();
		if (!outdated && currentTime - updated < ClientParams.chunkUpdateInterval) return false;
		
		WorldChunk worldChunk = this.updateWorldChunk();
		chunkUpdater.execute(() -> {
			if (worldChunk.isEmpty() || !this.isChunkLoaded()) return;
			this.updateChunkData(worldChunk, layer, level);
			if (saveNeeded() && !DataUtil.getMap().isWorldmap()) {
				BlockPos.Mutable chunkBlockPos = this.chunkPos.getCenterBlockPos().mutableCopy();
				chunkBlockPos.setY(level * layer.height);
				RegionData region = this.mapData.getRegion(world, chunkBlockPos, DataUtil.getMap().isWorldmap());
				if (region.getLayer().equals(layer) && region.getLevel() == level) {
					region.writeChunkData(this);
				}
			}
		});
		
		return true;
	}
	
	public ChunkData updateHeighmap(WorldChunk worldChunk, Layer layer, int level, boolean skipWater) {
		if (worldChunk.isEmpty()) return this;		
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				int y = MapProcessor.getTopBlockY(worldChunk, layer, level, x, z, skipWater);
				
				int index = x + (z << 4);
				ChunkLevel chunkLevel = this.getChunkLevel(layer, level);
				if (y != -1) {
					chunkLevel.updateHeightmap(x, z, y);
				} else if (getHeighmap(layer, level)[index] != -1) {
					chunkLevel.clear(x, z);					
					this.saved = false;
				}
			}
		}		
		return this;
	}
	
	private void updateChunkData(WorldChunk worldChunk, Layer layer, int level) {
		ChunkLevel chunkLevel = this.getChunkLevel(layer, level);
		chunkLevel.updating = true;
		

		boolean waterTint = ClientParams.alternateColorRender && ClientParams.waterTint;
		boolean skipWater = !(ClientParams.hideWater || waterTint);
		long currentTime = System.currentTimeMillis();
		if (currentTime - chunkLevel.updated > ClientParams.chunkLevelUpdateInterval) {
			this.updateHeighmap(worldChunk, layer, level, skipWater);
		}
		
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				int index = x + (z << 4);
				
				int posX = x + (chunkPos.x << 4);
				int posZ = z + (chunkPos.z << 4);
				int posY = this.getHeighmap(layer, level)[index];
				
				if (posY < 0) continue;
				
				BlockPos blockPos = new BlockPos(posX, posY, posZ);
				BlockState blockState = this.getBlockState(layer, level, blockPos);
				BlockState worldState = worldChunk.getBlockState(blockPos);
				if(outdated || !blockState.equals(worldState) || currentTime - refreshed > 60000) {
					int color = ColorUtil.blockColor(worldChunk, blockPos);
					if (color != -1) {
						int heightDiff = MapProcessor.heightDifference(this, layer, level, x, posY, z, skipWater);
						
						chunkLevel.setBlockState(x, z, worldState);
						
						int height = layer.height;
						int bottom = 0, baseHeight = 0;
						if (layer == Layer.NETHER) {
							bottom = level * height;
							baseHeight = 128;
						} else if (layer == Layer.SURFACE) {
							bottom = this.world.getSeaLevel();
							baseHeight = 256;
						} else {
							bottom = level * height;
							baseHeight = 32;
						}
						
						float topoLevel = ((float) (posY - bottom) / baseHeight);						
						
						chunkLevel.topomap[index] = (int) (topoLevel * 100);
						chunkLevel.colormap[index] = color;
						chunkLevel.levelmap[index] = heightDiff;
						
						this.saved = false;
					}
				} else if (chunkLevel.colormap[index] != -1) {
					int heightDiff = MapProcessor.heightDifference(this, layer, level, x, posY, z, skipWater);
					if (chunkLevel.levelmap[index] != heightDiff) {
						chunkLevel.levelmap[index] = heightDiff;
						this.saved = false;
					}
				}
			}
		}
		
		this.updated = currentTime;
		this.refreshed = currentTime;
		this.outdated = false;

		chunkLevel.updating = false;
	}
	
	public int[] getColorData(Layer layer, int level) {
		ChunkLevel chunkLevel = this.getChunkLevel(layer, level);
		int[] colordata = new int[256];
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				int index = x + (z << 4);
				colordata[index] = this.getBlockColor(chunkLevel, index);
			}
		}
		return colordata;
	}
	
	public int getBlockColor(Layer layer, int level, int x, int z) {
		return this.getBlockColor(getChunkLevel(layer, level), x + (z << 4));
	}
	
	private int getBlockColor(ChunkLevel chunkLevel, int index) {
		int color = chunkLevel.colormap[index];
		if (color != -1) {
			int heightDiff = chunkLevel.levelmap[index];
			float topoLevel = chunkLevel.topomap[index] / 100F;
			return ColorUtil.proccessColor(color, heightDiff, topoLevel);
		}
		return Colors.BLACK;
	}
	
	public boolean saveNeeded() {
		return !this.saved;
	}
	
	public void setSaved() {
		this.saved = true;
	}
	
	public boolean isChunkLoaded() {
		return this.mapData.isChunkLoaded(chunkPos);
	}
	
	public boolean hasSlime() {
		return this.slime;
	}
	
	public void updateWorld(World world) {
		if (!this.world.equals(world)) {
			this.resetChunk();
			this.world = world;
		}
	}
}
