package com.example.sandevistan;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

@Mod(modid = SandevistanMod.MODID, name = "Cyberware: Sandevistan", version = "1.0.0",
     dependencies = "required-after:cyberware")
public class SandevistanMod {
    public static final String MODID = "cyberware_sandevistan";

    @SidedProxy(clientSide = "com.example.sandevistan.ClientProxy",
                serverSide = "com.example.sandevistan.CommonProxy")
    public static CommonProxy proxy;

    public static SimpleNetworkWrapper network;
    public static ItemSandevistan sandevistan;

    @EventHandler
    public void preInit(FMLPreInitializationEvent e) {
        network = NetworkRegistry.INSTANCE.newSimpleChannel("cw_sandevistan"); // max 20 chars
        network.registerMessage(SandeStatePacket.Handler.class, SandeStatePacket.class, 0, Side.CLIENT);

        // Igual que hace Cyberware: el constructor de ItemCyberwareBase registra el item.
        sandevistan = new ItemSandevistan();
        proxy.preInit();
    }
}
