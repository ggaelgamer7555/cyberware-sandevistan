package com.example.sandevistan;

import java.util.List;

import flaxbeard.cyberware.api.CyberwareAPI;
import flaxbeard.cyberware.api.CyberwareUpdateEvent;
import flaxbeard.cyberware.api.ICyberwareUserData;
import flaxbeard.cyberware.api.item.EnableDisableHelper;
import flaxbeard.cyberware.api.item.ICyberware.EnumSlot;
import flaxbeard.cyberware.api.item.IMenuItem;
import flaxbeard.cyberware.common.item.ItemCyberware;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Sandevistan: implante de columna (slot BONE) que, al activarse con el menu radial
 * o un hotkey de Cyberware, acelera al portador y "frena" a los enemigos cercanos.
 * Consume energia del implante mientras esta activo y se sobrecalienta tras un tiempo.
 */
public class ItemSandevistan extends ItemCyberware implements IMenuItem {

    // --- Ajustes (cambialos a gusto) ---
    private static final int POWER_COST = 8;          // energia por pulso
    private static final int POWER_INTERVAL = 10;     // cada cuantos ticks se cobra
    public static final int MAX_ACTIVE_TICKS = 20 * 15;  // 15 s de uso continuo
    public static final int COOLDOWN_TICKS = 20 * 30;    // 30 s de enfriamiento
    private static final double SLOW_RADIUS = 12.0D;

    private static final String TAG_ACTIVE_TICKS = "sandeActive";
    private static final String TAG_COOLDOWN = "sandeCooldown";

    public ItemSandevistan() {
        super("sandevistan", EnumSlot.BONE, new String[] { "sandevistan" });
        setEssenceCost(18);
        MinecraftForge.EVENT_BUS.register(this);
    }

    // ---------- IMenuItem (menu radial / hotkey de Cyberware) ----------
    @Override
    public boolean hasMenu(ItemStack stack) {
        return true;
    }

    @Override
    public void use(Entity entity, ItemStack stack) {
        EnableDisableHelper.toggle(stack);
    }

    @Override
    public String getUnlocalizedLabel(ItemStack stack) {
        return EnableDisableHelper.getUnlocalizedLabel(stack);
    }

    @Override
    public float[] getColor(ItemStack stack) {
        return EnableDisableHelper.isEnabled(stack) ? new float[] { 1.0F, 0.2F, 0.4F } : null;
    }

    @Override
    public boolean isIncompatible(ItemStack stack, ItemStack other) {
        return other.getItem() == this;
    }

    // ---------- Logica por tick ----------
    @SubscribeEvent
    public void onCyberwareUpdate(CyberwareUpdateEvent event) {
        EntityLivingBase owner = event.getEntityLiving();
        if (owner.world.isRemote) return;

        ICyberwareUserData data = event.getCyberwareUserData();
        ItemStack installed = data.getCyberware(getCachedStack(0));
        if (!installed.isEmpty()) runLogic(owner, data, installed);
        sync(owner, installed);
    }

    private void runLogic(EntityLivingBase owner, ICyberwareUserData data, ItemStack installed) {

        NBTTagCompound nbt = CyberwareAPI.getCyberwareNBT(installed);
        int cooldown = nbt.getInteger(TAG_COOLDOWN);
        if (cooldown > 0) nbt.setInteger(TAG_COOLDOWN, cooldown - 1);

        if (!EnableDisableHelper.isEnabled(installed)) {
            nbt.setInteger(TAG_ACTIVE_TICKS, 0);
            return;
        }

        // Aun enfriandose: se apaga solo
        if (cooldown > 0) {
            EnableDisableHelper.toggle(installed);
            return;
        }

        int active = nbt.getInteger(TAG_ACTIVE_TICKS) + 1;
        nbt.setInteger(TAG_ACTIVE_TICKS, active);

        // Cobro de energia; si no alcanza, se apaga
        if (active % POWER_INTERVAL == 0 && !data.usePower(installed, POWER_COST)) {
            shutDown(owner, installed, nbt, 0);
            return;
        }
        // Sobrecalentamiento
        if (active >= MAX_ACTIVE_TICKS) {
            shutDown(owner, installed, nbt, COOLDOWN_TICKS);
            owner.addPotionEffect(new PotionEffect(MobEffects.NAUSEA, 100, 0));
            owner.addPotionEffect(new PotionEffect(MobEffects.WEAKNESS, 200, 1));
            return;
        }

        // Efecto: el portador va rapido...
        owner.addPotionEffect(new PotionEffect(MobEffects.SPEED, 5, 4, true, false));
        owner.addPotionEffect(new PotionEffect(MobEffects.HASTE, 5, 3, true, false));
        owner.addPotionEffect(new PotionEffect(MobEffects.JUMP_BOOST, 5, 1, true, false));

        // ...y el resto del mundo, lento
        AxisAlignedBB box = owner.getEntityBoundingBox().grow(SLOW_RADIUS);
        List<EntityLivingBase> nearby = owner.world.getEntitiesWithinAABB(EntityLivingBase.class, box);
        for (EntityLivingBase other : nearby) {
            if (other == owner || other instanceof EntityPlayer) continue;
            other.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 5, 4, true, false));
            other.addPotionEffect(new PotionEffect(MobEffects.MINING_FATIGUE, 5, 2, true, false));
        }
    }

    private void shutDown(EntityLivingBase owner, ItemStack installed, NBTTagCompound nbt, int cooldown) {
        nbt.setInteger(TAG_ACTIVE_TICKS, 0);
        nbt.setInteger(TAG_COOLDOWN, cooldown);
        if (EnableDisableHelper.isEnabled(installed)) EnableDisableHelper.toggle(installed);
    }

    /** Avisa a los clientes del estado (para imagenes residuales y barras). */
    private void sync(EntityLivingBase owner, ItemStack installed) {
        boolean active = !installed.isEmpty() && EnableDisableHelper.isEnabled(installed);
        int activeTicks = 0, cooldown = 0;
        if (!installed.isEmpty()) {
            NBTTagCompound nbt = CyberwareAPI.getCyberwareNBT(installed);
            if (active) activeTicks = nbt.getInteger(TAG_ACTIVE_TICKS);
            cooldown = nbt.getInteger(TAG_COOLDOWN);
        }
        boolean busy = active || cooldown > 0;
        NBTTagCompound ed = owner.getEntityData();
        boolean wasBusy = ed.getBoolean("sandeBusy");
        if ((busy && owner.ticksExisted % 4 == 0) || busy != wasBusy) {
            ed.setBoolean("sandeBusy", busy);
            SandeStatePacket p = new SandeStatePacket(owner.getEntityId(), active, activeTicks, cooldown);
            SandevistanMod.network.sendToAllTracking(p, owner);
            if (owner instanceof EntityPlayerMP) SandevistanMod.network.sendTo(p, (EntityPlayerMP) owner);
        }
    }
}
