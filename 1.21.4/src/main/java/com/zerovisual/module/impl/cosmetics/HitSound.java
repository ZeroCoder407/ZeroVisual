package com.zerovisual.module.impl.cosmetics;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.setting.ModeSetting;
import com.zerovisual.setting.NumberSetting;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;

import java.util.Random;

/**
 * A sound when you hit an entity. Played only for you.
 * "Custom" uses the sound event zerovisual:hit.custom, which a resource pack can replace with any .ogg.
 */
public class HitSound extends Module {
	private static final SoundEvent CUSTOM = SoundEvent.of(Identifier.of(ZeroVisualClient.MOD_ID, "hit.custom"));

	public final ModeSetting sound = add(new ModeSetting("Sound", "Metallic", "Metallic", "Bell", "Pling", "Bass", "Custom"));
	public final NumberSetting volume = add(new NumberSetting("Volume", 0.8, 0.1, 1, 0.05));

	private final Random random = new Random();

	public HitSound() {
		super("HitSound", "Hit sound on your target (only you hear it)", Category.COSMETICS);
	}

	public void onAttack(Entity target) {
		if (!(target instanceof LivingEntity)) return;
		float variation = 0.94f + random.nextFloat() * 0.12f;
		SoundEvent event;
		float pitch;
		switch (sound.get()) {
			case "Bell" -> {
				event = SoundEvents.BLOCK_NOTE_BLOCK_BELL.value();
				pitch = 1.4f;
			}
			case "Pling" -> {
				event = SoundEvents.BLOCK_NOTE_BLOCK_PLING.value();
				pitch = 1.6f;
			}
			case "Bass" -> {
				event = SoundEvents.BLOCK_NOTE_BLOCK_BASS.value();
				pitch = 0.9f;
			}
			case "Custom" -> {
				event = CUSTOM;
				pitch = 1f;
			}
			default -> {
				// a short, high anvil clink
				event = SoundEvents.BLOCK_ANVIL_LAND;
				pitch = 1.9f;
			}
		}
		mc.getSoundManager().play(PositionedSoundInstance.master(event, pitch * variation, volume.getFloat() * (sound.is("Metallic") ? 0.35f : 1f)));
	}
}
