package com.example.sandevistan;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class ClientProxy extends CommonProxy {
    private SandeClient client;

    @Override
    public void preInit() {
        client = new SandeClient();
        MinecraftForge.EVENT_BUS.register(client);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Override
    public void handleState(SandeStatePacket msg) {
        Minecraft.getMinecraft().addScheduledTask(() -> client.update(msg));
    }

    @SubscribeEvent
    public void onModels(ModelRegistryEvent e) {
        ModelLoader.setCustomModelResourceLocation(SandevistanMod.sandevistan, 0,
            new ModelResourceLocation(SandevistanMod.sandevistan.getRegistryName(), "inventory"));
    }
}
