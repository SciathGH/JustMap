package ru.bulldog.justmap.util.storage;

import java.io.File;
import java.nio.file.Path;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.storage.VersionedChunkStorage;

import ru.bulldog.justmap.JustMap;
import ru.bulldog.justmap.mixins.SessionAccessor;
import ru.bulldog.justmap.util.DataUtil;
import ru.bulldog.justmap.util.Dimension;

public final class StorageUtil {
	
	private StorageUtil() {}
	
	private final static FabricLoader fabricLoader = FabricLoader.getInstance();
	private final static Path GAME_DIR = fabricLoader.getGameDir();
	private final static Path GAME_CONFIG_DIR = fabricLoader.getConfigDir();
	private final static Path MAP_DATA_DIR = GAME_DIR.resolve(JustMap.MODID);
	private final static Path MAP_CONFIG_DIR = GAME_CONFIG_DIR.resolve(JustMap.MODID);
	private final static Path MAP_SKINS_DIR = MAP_CONFIG_DIR.resolve("skins");
	private final static Path MAP_ICONS_DIR = MAP_CONFIG_DIR.resolve("icons");
	
	private static File filesDir = new File(MAP_DATA_DIR.toFile(), "undefined");
	private static String currentDim = "unknown";
	
	public static VersionedChunkStorage getChunkStorage(ServerWorld world) {
		File regionDir = new File(savesDir(world), "region");
		return new VersionedChunkStorage(regionDir, world.getServer().getDataFixer(), true);
	}
	
	public static File savesDir(ServerWorld world) {
		if (world == null || !(world instanceof ServerWorld)) return null;
		return ((SessionAccessor) world.getServer()).getServerSession().getWorldDirectory(world.getRegistryKey());
	}
	
	public static File configDir() {
		File mapConfigDir = MAP_CONFIG_DIR.toFile();
		if (!mapConfigDir.exists()) {
			mapConfigDir.mkdirs();
		}
		return mapConfigDir;
	}
	
	@Environment(EnvType.CLIENT)
	public static File skinsDir() {
		File mapSkinsDir = MAP_SKINS_DIR.toFile();
		if (!mapSkinsDir.exists()) {
			mapSkinsDir.mkdirs();
		}
		return mapSkinsDir;
	}
	
	@Environment(EnvType.CLIENT)
	public static File iconsDir() {
		File iconsDir = MAP_ICONS_DIR.toFile();
		if (!iconsDir.exists()) {
			iconsDir.mkdirs();
		}
		return iconsDir;
	}
	
	@Environment(EnvType.CLIENT)
	public static File cacheDir(World world) {
		RegistryKey<DimensionType> dimKey = null;
		if (world != null) {
			dimKey = world.getDimensionRegistryKey();			
			String dimension = dimKey.getValue().getPath();
			if (!currentDim.equals(dimension)) {
				currentDim = dimension;
			}			
		}

		File cacheDir = new File(filesDir(), String.format("cache/%s", currentDim));
		if (dimKey != null) {
			int dimId = Dimension.getId(dimKey);
			if (dimId != Integer.MIN_VALUE) {
				File oldDir = new File(filesDir(), String.format("cache/DIM%d", dimId));
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
		MinecraftClient minecraft = DataUtil.getMinecraft();		
		ServerInfo serverInfo = minecraft.getCurrentServerEntry();
		File mapDataDir = MAP_DATA_DIR.toFile();
		if (minecraft.isIntegratedServerRunning()) {
			MinecraftServer server = minecraft.getServer();
			String name = scrubNameFile(server.getSaveProperties().getLevelName());
			filesDir = new File(mapDataDir, String.format("local/%s", name));
		} else if (serverInfo != null) {
			String name = scrubNameFile(serverInfo.name);
			filesDir = new File(mapDataDir, String.format("servers/%s", name));
		}
		
		if (!filesDir.exists()) {
			filesDir.mkdirs();
		}
		
		return filesDir;
	}

	private static String scrubNameFile(String input) {
		input = input.replaceAll("[/\\ ]+", "_");
		input = input.replaceAll("[:|\\<\\>\"\\?\\*]", "_");

		return input;
	}
}