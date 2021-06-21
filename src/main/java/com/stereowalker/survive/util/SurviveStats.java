package com.stereowalker.survive.util;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundNBT;

public abstract class SurviveStats {
	public abstract void tick(PlayerEntity player);
	public abstract void read(CompoundNBT compound);
	public abstract void write(CompoundNBT compound);
	public abstract void save(LivingEntity player);
	public abstract boolean shouldTick();
	
	public void baseTick(PlayerEntity player) {
		if (shouldTick()) {
			tick(player);
			save(player);
		}
	}
}