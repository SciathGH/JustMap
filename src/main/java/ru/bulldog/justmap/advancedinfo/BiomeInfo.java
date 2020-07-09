package ru.bulldog.justmap.advancedinfo;

import net.minecraft.class_5458;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.biome.Biome;

import ru.bulldog.justmap.client.config.ClientParams;
import ru.bulldog.justmap.util.RenderUtil.TextAlignment;
import ru.bulldog.justmap.util.PosUtil;

public class BiomeInfo extends InfoText {

	private String title;
	private Identifier currentBiome;
	
	public BiomeInfo() {
		super("Void");
		this.title = "Biome: ";
	}
	
	public BiomeInfo(TextAlignment alignment, String title) {
		super(alignment, "Void");
		this.title = title;
	}

	@Override
	public void update() {
		this.setVisible(ClientParams.showBiome);
		if (visible && minecraft.world != null) {
			Biome biome = minecraft.world.getBiome(PosUtil.currentPos());
			Identifier biomeId = class_5458.field_25933.getId(biome);
			if (biomeId != null && !biomeId.equals(currentBiome)) {
				this.currentBiome = biomeId;
				this.setText(title + this.getTranslation());
			} else if (biomeId == null) {
				this.setText(title + "Unknown");
			}
		}
	}

	private String getTranslation() {
		return I18n.translate(Util.createTranslationKey("biome", currentBiome));
	}
}
