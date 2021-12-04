package com.stereowalker.survive;

import java.util.ArrayList;
import java.util.List;

import com.google.common.collect.Lists;
import com.stereowalker.survive.compat.OriginsCompat;
import com.stereowalker.survive.config.Config;
import com.stereowalker.survive.config.HygieneConfig;
import com.stereowalker.survive.config.ServerConfig;
import com.stereowalker.survive.config.TemperatureConfig;
import com.stereowalker.survive.config.ThirstConfig;
import com.stereowalker.survive.config.WellbeingConfig;
import com.stereowalker.survive.core.cauldron.SCauldronInteraction;
import com.stereowalker.survive.events.SurviveEvents;
import com.stereowalker.survive.json.ArmorJsonHolder;
import com.stereowalker.survive.json.BiomeTemperatureJsonHolder;
import com.stereowalker.survive.json.BlockTemperatureJsonHolder;
import com.stereowalker.survive.json.EntityTemperatureJsonHolder;
import com.stereowalker.survive.json.FoodJsonHolder;
import com.stereowalker.survive.json.PotionJsonHolder;
import com.stereowalker.survive.needs.IRoastedEntity;
import com.stereowalker.survive.network.protocol.game.ClientboundArmorDataTransferPacket;
import com.stereowalker.survive.network.protocol.game.ClientboundDrinkSoundPacket;
import com.stereowalker.survive.network.protocol.game.ClientboundSurvivalStatsPacket;
import com.stereowalker.survive.network.protocol.game.ServerboundArmorStaminaPacket;
import com.stereowalker.survive.network.protocol.game.ServerboundEnergyTaxPacket;
import com.stereowalker.survive.network.protocol.game.ServerboundInteractWithWaterPacket;
import com.stereowalker.survive.network.protocol.game.ServerboundThirstMovementPacket;
import com.stereowalker.survive.resource.ArmorDataManager;
import com.stereowalker.survive.resource.BiomeTemperatureDataManager;
import com.stereowalker.survive.resource.BlockTemperatureDataManager;
import com.stereowalker.survive.resource.EntityTemperatureDataManager;
import com.stereowalker.survive.resource.ItemConsummableDataManager;
import com.stereowalker.survive.resource.PotionDrinkDataManager;
import com.stereowalker.survive.spell.SSpells;
import com.stereowalker.survive.stat.SStats;
import com.stereowalker.survive.world.DataMaps;
import com.stereowalker.survive.world.effect.SEffects;
import com.stereowalker.survive.world.item.SItems;
import com.stereowalker.survive.world.level.CGameRules;
import com.stereowalker.survive.world.level.material.SFluids;
import com.stereowalker.unionlib.client.gui.screens.config.MinecraftModConfigsScreen;
import com.stereowalker.unionlib.config.ConfigBuilder;
import com.stereowalker.unionlib.mod.MinecraftMod;
import com.stereowalker.unionlib.network.PacketRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.gui.IIngameOverlay;
import net.minecraftforge.client.gui.OverlayRegistry;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.ForgeRegistries;

@Mod(value = "survive")
public class Survive extends MinecraftMod {

	public static final float DEFAULT_TEMP = 37.0F;
	public static final String MOD_ID = "survive";
	
	public static final Config CONFIG = new Config();
	public static final HygieneConfig HYGIENE_CONFIG = new HygieneConfig();
	public static final TemperatureConfig TEMPERATURE_CONFIG = new TemperatureConfig();
	public static final ThirstConfig THIRST_CONFIG = new ThirstConfig();
	public static final WellbeingConfig WELLBEING_CONFIG = new WellbeingConfig();
	
	public static boolean isPrimalWinterLoaded;
	public static final ItemConsummableDataManager consummableReloader = new ItemConsummableDataManager();
	public static final PotionDrinkDataManager potionReloader = new PotionDrinkDataManager();
	public static final ArmorDataManager armorReloader = new ArmorDataManager();
	public static final BlockTemperatureDataManager blockReloader = new BlockTemperatureDataManager();
	public static final BiomeTemperatureDataManager biomeReloader = new BiomeTemperatureDataManager();
	public static final EntityTemperatureDataManager entityReloader = new EntityTemperatureDataManager();
	private static Survive instance;
	
	public static boolean isCombatLoaded() {
		return ModList.get().isLoaded("combat");
	}
	public static boolean isOriginsLoaded() {
		return ModList.get().isLoaded("origins");
	}
	
	public Survive() 
	{
		super("survive", new ResourceLocation(MOD_ID, "textures/icon.png"), LoadType.BOTH);
		instance = this;
		ConfigBuilder.registerConfig(ServerConfig.class);
		ConfigBuilder.registerConfig(CONFIG);
		ConfigBuilder.registerConfig(HYGIENE_CONFIG);
		ConfigBuilder.registerConfig(TEMPERATURE_CONFIG);
		ConfigBuilder.registerConfig(THIRST_CONFIG);
		ConfigBuilder.registerConfig(WELLBEING_CONFIG);
		final IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
		modEventBus.addListener(this::setup);
		modEventBus.addListener(this::clientRegistries);
//		MinecraftForge.EVENT_BUS.register(this);
		isPrimalWinterLoaded = ModList.get().isLoaded("primalwinter");
		if (isCombatLoaded()) {
			SSpells.registerAll(modEventBus);
			SStats.registerAll(modEventBus);
		}
		if (isOriginsLoaded()) {
			OriginsCompat.initOriginsPatcher();
		}
	}
	
	@Override
	public List<Class<?>> getRegistries() {
		return Lists.newArrayList(SItems.class);
	}
	
	@Override
	public void registerMessages(SimpleChannel channel) {
		int netID = -1;
		PacketRegistry.registerMessage(channel, netID++, ServerboundArmorStaminaPacket.class, (packetBuffer) -> {return new ServerboundArmorStaminaPacket(packetBuffer);});
		channel.registerMessage(netID++, ClientboundSurvivalStatsPacket.class, ClientboundSurvivalStatsPacket::encode, ClientboundSurvivalStatsPacket::decode, ClientboundSurvivalStatsPacket::handle);
		channel.registerMessage(netID++, ServerboundThirstMovementPacket.class, ServerboundThirstMovementPacket::encode, ServerboundThirstMovementPacket::decode, ServerboundThirstMovementPacket::handle);
		channel.registerMessage(netID++, ServerboundInteractWithWaterPacket.class, ServerboundInteractWithWaterPacket::encode, ServerboundInteractWithWaterPacket::decode, ServerboundInteractWithWaterPacket::handle);
		channel.registerMessage(netID++, ClientboundDrinkSoundPacket.class, ClientboundDrinkSoundPacket::encode, ClientboundDrinkSoundPacket::decode, ClientboundDrinkSoundPacket::handle);
		channel.registerMessage(netID++, ServerboundEnergyTaxPacket.class, ServerboundEnergyTaxPacket::encode, ServerboundEnergyTaxPacket::decode, ServerboundEnergyTaxPacket::handle);
		channel.registerMessage(netID++, ClientboundArmorDataTransferPacket.class, ClientboundArmorDataTransferPacket::encode, ClientboundArmorDataTransferPacket::decode, ClientboundArmorDataTransferPacket::handle);
	}
	
	//TODO: FInd Somewhere to put all these
	public static void registerDrinkDataForItem(ResourceLocation location, FoodJsonHolder drinkData) {
		DataMaps.Server.consummableItem.put(location, drinkData);
	}
	public static void registerDrinkDataForPotion(ResourceLocation location, PotionJsonHolder consummableData) {
		DataMaps.Server.potionDrink.put(location, consummableData);
	}
	public static void registerArmorTemperatures(ResourceLocation location, ArmorJsonHolder armorData) {
		DataMaps.Server.armor.put(location, armorData);
	}
	public static void registerBlockTemperatures(ResourceLocation location, BlockTemperatureJsonHolder drinkData) {
		DataMaps.Server.blockTemperature.put(location, drinkData);
	}
	public static void registerEntityTemperatures(ResourceLocation location, EntityTemperatureJsonHolder drinkData) {
		DataMaps.Server.entityTemperature.put(location, drinkData);
	}
	public static void registerBiomeTemperatures(ResourceLocation location, BiomeTemperatureJsonHolder biomeData) {
		DataMaps.Server.biomeTemperature.put(location, biomeData);
	}

	@Override
	@OnlyIn(Dist.CLIENT)
	public Screen getConfigScreen(Minecraft mc, Screen previousScreen) {
		return new MinecraftModConfigsScreen(previousScreen, new TranslatableComponent("gui.survive.config.title"), HYGIENE_CONFIG, TEMPERATURE_CONFIG, THIRST_CONFIG, WELLBEING_CONFIG, CONFIG);
	}

	public void debug(Object message) {
		if (CONFIG.debugMode)getLogger().debug(message);
	}

	private void setup(final FMLCommonSetupEvent event)
	{
		SCauldronInteraction.bootStrap();
//		BrewingRecipes.addBrewingRecipes();
		CGameRules.init();
		SurviveEvents.registerHeatMap();
		
		for(Item item : ForgeRegistries.ITEMS) {
			if (item.isEdible())
				DataMaps.Server.defaultFood.put(item.getRegistryName(), item.getFoodProperties());
		}
	}
	
	IIngameOverlay TIRED_ELEMENT;
	IIngameOverlay HEAT_STROKE_ELEMENT;

	public void clientRegistries(final FMLClientSetupEvent event)
	{
		RenderType frendertype = RenderType.translucent();
		ItemBlockRenderTypes.setRenderLayer(SFluids.PURIFIED_WATER, frendertype);
		ItemBlockRenderTypes.setRenderLayer(SFluids.FLOWING_PURIFIED_WATER, frendertype);
		TIRED_ELEMENT = OverlayRegistry.registerOverlayTop("Tired", (gui, mStack, partialTicks, screenWidth, screenHeight) -> {
	        gui.setupOverlayRenderState(true, false);
	        renderTiredOverlay(gui);
	    });
		HEAT_STROKE_ELEMENT = OverlayRegistry.registerOverlayTop("Heat Stroke", (gui, mStack, partialTicks, screenWidth, screenHeight) -> {
	        gui.setupOverlayRenderState(true, false);
	        renderHeatStroke(gui);
	    });
	}
	
	@OnlyIn(Dist.CLIENT)
	public static void renderTiredOverlay(Gui gui) {
		if (CONFIG.tired_overlay) {
			if (Minecraft.getInstance().player.hasEffect(SEffects.TIREDNESS)) {
				Minecraft.getInstance().getProfiler().push("tired");
				int amplifier = Minecraft.getInstance().player.getEffect(SEffects.TIREDNESS).getAmplifier() + 1;
				amplifier/=(CONFIG.tiredTimeStacks/5);
				amplifier = Mth.clamp(amplifier, 0, 4);
				gui.renderTextureOverlay(Survive.getInstance().location("textures/misc/sleep_overlay_"+(amplifier)+".png"), 0.5F);
				Minecraft.getInstance().getProfiler().pop();
			}
		}
	}
	
	@OnlyIn(Dist.CLIENT)
    public static void renderHeatStroke(Gui gui)
    {
        if (((IRoastedEntity)gui.minecraft.player).getTicksRoasted() > 0) {
        	gui.renderTextureOverlay(Survive.getInstance().location("textures/misc/burning_overlay.png"), ((IRoastedEntity)gui.minecraft.player).getPercentRoasted());
        }
    }

	public static List<String> defaultDimensionMods() {
		List<String> dims = new ArrayList<String>();
		dims.add("minecraft:overworld,0.0");
		dims.add("minecraft:the_nether,0.0");
		dims.add("minecraft:the_end,0.0");
		return dims;
	}
	
	public static List<String> defaultArmorMods(){
		List<String> armorTemps = new ArrayList<String>();
		//Ars Nouveau
		armorTemps.add("ars_nouveau:novice_robes,3.0");
		armorTemps.add("ars_nouveau:novice_leggings,3.0");
		armorTemps.add("ars_nouveau:novice_hood,3.0");
		armorTemps.add("ars_nouveau:novice_boots,3.0");
		armorTemps.add("ars_nouveau:apprentice_robes,3.0");
		armorTemps.add("ars_nouveau:apprentice_leggings,3.0");
		armorTemps.add("ars_nouveau:apprentice_hood,3.0");
		armorTemps.add("ars_nouveau:apprentice_boots,3.0");
		armorTemps.add("ars_nouveau:archmage_robes,3.0");
		armorTemps.add("ars_nouveau:archmage_leggings,3.0");
		armorTemps.add("ars_nouveau:archmage_hood,3.0");
		armorTemps.add("ars_nouveau:archmage_boots,3.0");
		return armorTemps;
	}
	
	public static List<String> defaultWaterContainers() {
		List<String> water = new ArrayList<String>();
//		water.add("minecraft:sweet_berries,-2");
		water.add("farmersdelight:milk_bottle,4");
//		water.add("minecraft:pumpkin_pie,-4");
		return water;
	}
	
	public static List<String> defaultThirstContainers() {
		List<String> thirst = new ArrayList<String>();
//		thirst.add("minecraft:honey_bottle,1");
//		thirst.add("minecraft:pufferfish,-8");
//		thirst.add("minecraft:rotten_flesh,-4");
//		thirst.add("minecraft:poisonous_potato,-8");
		thirst.add("minecraft:spider_eye,-8");
		thirst.add("minecraft:bread,-6");
		thirst.add("minecraft:cookie,-1");
		thirst.add("farmersdelight:raw_pasta,-4");
		thirst.add("farmersdelight:pie_crust,-2");
		thirst.add("farmersdelight:slice_of_cake,-2");
		thirst.add("farmersdelight:slice_of_apple_pie,-2");
		thirst.add("farmersdelight:slice_of_sweet_berry_cheesecake,-2");
		thirst.add("farmersdelight:slice_of_chocolate_pie,-2");
		thirst.add("farmersdelight:sweet_berry_cookie,-1");
		thirst.add("farmersdelight:honey_cookie,-1");
		thirst.add("farmersdelight:cooked_rice,-4");
		return thirst;
	}
	
	public static List<String> defaultChilledContainers() {
		List<String> chilled = new ArrayList<String>();
//		chilled.add("minecraft:beetroot_soup,3");
		chilled.add("minecraft:potato,0");
		chilled.add("minecraft:carrot,0");
		chilled.add("create:builders_tea,8");
		chilled.add("farmersdelight:tomato_sauce,1");
		chilled.add("farmersdelight:cabbage,0");
		chilled.add("farmersdelight:cabbage_leaf,0");
		chilled.add("farmersdelight:tomato,0");
		chilled.add("farmersdelight:onion,0");
		chilled.add("farmersdelight:pumpkin_slice,0");
		chilled.add("farmersdelight:minced_beef,0");
		chilled.add("farmersdelight:mixed_salad,0");
		return chilled;
	}
	
	public static List<String> defaultHeatedContainers(){
		List<String> heated = new ArrayList<String>(); 
		heated.add("minecraft:rabbit_stew,0");
		heated.add("farmersdelight:hot_cocoa,4");
		heated.add("farmersdelight:beef_stew,1");
		heated.add("farmersdelight:chicken_soup,5");
		heated.add("farmersdelight:vegetable_soup,5");
		heated.add("farmersdelight:pumpkin_soup,5");
		heated.add("farmersdelight:nether_salad,0");
		heated.add("farmersdelight:dumplings,0");
		heated.add("farmersdelight:stuffed_pumpkin,0");
		heated.add("farmersdelight:fish_stew,1");
		heated.add("farmersdelight:baked_cod_stew,1");
		heated.add("farmersdelight:honey_glazed_ham,-2");
		heated.add("farmersdelight:pasta_with_meatballs,0");
		heated.add("farmersdelight:pasta_with_mutton_chop,0");
		heated.add("farmersdelight:vegetable_noodles,0");
		heated.add("farmersdelight:steak_and_potatoes,0");
		heated.add("farmersdelight:shepherds_pie,0");
		heated.add("farmersdelight:ratatouille,0");
		heated.add("farmersdelight:squid_ink_pasta,0");
		heated.add("farmersdelight:grilled_salmon,0");
		return heated;
	}
	public static Survive getInstance() {
		return instance;
	}
}
