package ru.bulldog.justmap.util;

import java.io.File;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.dimension.DimensionType;

import ru.bulldog.justmap.JustMap;
import ru.bulldog.justmap.map.data.ChunkStorage;

public class StorageUtil {
	
	private final static FabricLoader fabricLoader = FabricLoader.getInstance();
	private final static File MAP_DATA_DIR = new File(fabricLoader.getGameDirectory(), JustMap.MODID + "/");
	private final static File MAP_CONFIG_DIR = new File(fabricLoader.getConfigDirectory(), String.format("/%s/", JustMap.MODID));
	private final static File MAP_SKINS_DIR = new File(MAP_CONFIG_DIR, "skins/");
	private final static File MAP_ICONS_DIR = new File(MAP_CONFIG_DIR, "icons/");
	
	private static ChunkStorage storage;
	private static File storageDir;
	private static File filesDir = new File(MAP_DATA_DIR, "undefined/");	
	private static String currentDim = "unknown";
	
	@Environment(EnvType.CLIENT)
	public static synchronized CompoundTag getCache(ChunkPos pos) {
		try {
			CompoundTag data = storage.getNbt(getCacheStorage(), pos);
			return data != null ? data : new CompoundTag();
		} catch (Exception ex) {
			return new CompoundTag();
		}		
	}
	
	@Environment(EnvType.CLIENT)
	public static synchronized void saveCache(ChunkPos pos, CompoundTag data) {
		storage.setTagAt(getCacheStorage(), pos, data);
	}
	
	@Environment(EnvType.CLIENT)
	public static File getCacheStorage() {
		storageDir = new File(cacheDir(), "chunk-data/");
		if (!storageDir.exists()) {
			storageDir.mkdirs();
		}		
		if (storage == null) {
			storage = new ChunkStorage();
		}
		
		return storageDir;
	}
	
	public static void closeStorage() {
		if (storage != null) {
			storage.close();
			storage = null;
		}
	}
	
	public static File configDir() {
		if (!MAP_CONFIG_DIR.exists()) {
			MAP_CONFIG_DIR.mkdirs();
		}
		return MAP_CONFIG_DIR;
	}
	
	@Environment(EnvType.CLIENT)
	public static File skinsDir() {
		if (!MAP_SKINS_DIR.exists()) {
			MAP_SKINS_DIR.mkdirs();
		}
		return MAP_SKINS_DIR;
	}
	
	@Environment(EnvType.CLIENT)
	public static File iconsDir() {
		if (!MAP_ICONS_DIR.exists()) {
			MAP_ICONS_DIR.mkdirs();
		}
		return MAP_ICONS_DIR;
	}
	
	@Environment(EnvType.CLIENT)
	public static File cacheDir() {
		RegistryKey<DimensionType> dimKey = null;
		MinecraftClient minecraft = MinecraftClient.getInstance();
		if (minecraft.world != null) {
			dimKey = minecraft.world.getDimensionRegistryKey();			
			String dimension = dimKey.getValue().getPath();
			if (!currentDim.equals(dimension)) {
				currentDim = dimension;
			}			
		}

		File cacheDir = new File(filesDir(), String.format("cache/%s/", currentDim));
		if (dimKey != null) {
			int dimId = Dimension.getId(dimKey);
			if (dimId != Integer.MIN_VALUE) {
				File oldDir = new File(filesDir(), String.format("cache/DIM%d/", dimId));
				if (oldDir.exists()) {
					oldDir.renameTo(cacheDir);
				}				
			}
		}
		
		if (!cacheDir.exists()) {
			cacheDir.mkdirs();
		}
		
		return cacheDir;
	}
	
	@Environment(EnvType.CLIENT)
	public static File filesDir() {
		MinecraftClient minecraft = MinecraftClient.getInstance();		
		ServerInfo serverInfo = minecraft.getCurrentServerEntry();
		if (minecraft.isIntegratedServerRunning()) {
			MinecraftServer server = minecraft.getServer();
			String name = scrubNameFile(server.getSaveProperties().getLevelName());
			filesDir = new File(MAP_DATA_DIR, String.format("local/%s/", name));
		} else if (serverInfo != null) {
			String name = scrubNameFile(serverInfo.name);
			filesDir = new File(MAP_DATA_DIR, String.format("servers/%s/", name));
		}
		
		if (!filesDir.exists()) {
			filesDir.mkdirs();
		}
		
		return filesDir;
	}
	
	public static void clearCache(File dir) {
		deleteDir(dir);
		dir.mkdirs();
	}
	
	public static void clearCache() {
		clearCache(cacheDir());
	}
	
	private static void deleteDir(File dir) {
		if (!dir.exists()) return;
		
		File[] files = dir.listFiles();
		if (files == null) {
			dir.delete();
			return;
		}
		
		for (File file : files) {
			if (file.isDirectory()) {
				deleteDir(file);
			} else {
				file.delete();
			}
		}
		dir.delete();
	}

	private static String scrubNameFile(String input) {
		input = input.replace("<", "_");
		input = input.replace(">", "_");
		input = input.replace(":", "_");
		input = input.replace("\"", "_");
		input = input.replace("/", "_");
		input = input.replace("\\", "_");
		input = input.replace("//", "_");
		input = input.replace("|", "_");
		input = input.replace("?", "_");
		input = input.replace("*", "_");

		return input;
	}
}