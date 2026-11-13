package com.example.sandevistan;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.entity.Entity;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Efectos del cliente: imagenes residuales de colores y barras de duracion/sobrecalentamiento. */
@SideOnly(Side.CLIENT)
public class SandeClient {

    // --- Ajustes visuales ---
    private static final int GHOST_LIFE = 20;      // ticks que dura cada imagen residual
    private static final int GHOST_INTERVAL = 2;   // cada cuantos ticks se deja una imagen
    private static final int MAX_GHOSTS = 30;
    private static final float GHOST_ALPHA = 0.55F;
    private static final float HUE_DRIFT = 0.025F; // cuanto cambia el color por tick

    private static class State {
        boolean active;
        int activeTicks;
        int cooldown;
    }

    private static class Ghost {
        AbstractClientPlayer player;
        ModelPlayer model;
        double x, y, z;
        float bodyYaw, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch;
        float hue;
        int age;
    }

    private final Map<Integer, State> states = new HashMap<Integer, State>();
    private final List<Ghost> ghosts = new ArrayList<Ghost>();
    private final Random rand = new Random();

    public void update(SandeStatePacket msg) {
        if (!msg.active && msg.cooldown <= 0) {
            states.remove(msg.entityId);
            return;
        }
        State s = states.get(msg.entityId);
        if (s == null) {
            s = new State();
            states.put(msg.entityId, s);
        }
        s.active = msg.active;
        s.activeTicks = msg.activeTicks;
        s.cooldown = msg.cooldown;
    }

    // ---------- Tick: crear y envejecer imagenes residuales ----------
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null) {
            ghosts.clear();
            states.clear();
            return;
        }

        for (Iterator<Ghost> it = ghosts.iterator(); it.hasNext();) {
            if (++it.next().age >= GHOST_LIFE) it.remove();
        }

        if (mc.isGamePaused()) return;
        for (Map.Entry<Integer, State> en : states.entrySet()) {
            if (!en.getValue().active) continue;
            Entity ent = mc.world.getEntityByID(en.getKey());
            if (!(ent instanceof AbstractClientPlayer)) continue;
            if (ent.ticksExisted % GHOST_INTERVAL != 0 || ghosts.size() >= MAX_GHOSTS) continue;

            AbstractClientPlayer p = (AbstractClientPlayer) ent;
            RenderPlayer rp = mc.getRenderManager().getSkinMap().get(p.getSkinType());
            if (rp == null) continue;

            Ghost g = new Ghost();
            g.player = p;
            g.model = rp.getMainModel();
            g.x = p.posX; g.y = p.posY; g.z = p.posZ;
            g.bodyYaw = p.renderYawOffset;
            g.limbSwing = p.limbSwing;
            g.limbSwingAmount = p.limbSwingAmount;
            g.ageInTicks = p.ticksExisted;
            g.netHeadYaw = p.rotationYawHead - p.renderYawOffset;
            g.headPitch = p.rotationPitch;
            g.hue = rand.nextFloat();      // color base aleatorio
            ghosts.add(g);
        }
    }

    // ---------- Render de las imagenes residuales ----------
    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent e) {
        if (ghosts.isEmpty()) return;
        Minecraft mc = Minecraft.getMinecraft();
        Entity cam = mc.getRenderViewEntity();
        if (cam == null) return;
        float pt = e.getPartialTicks();
        double cx = cam.lastTickPosX + (cam.posX - cam.lastTickPosX) * pt;
        double cy = cam.lastTickPosY + (cam.posY - cam.lastTickPosY) * pt;
        double cz = cam.lastTickPosZ + (cam.posZ - cam.lastTickPosZ) * pt;

        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        GlStateManager.depthMask(false);

        for (Ghost g : ghosts) {
            double dx = g.x - cx, dy = g.y - cy, dz = g.z - cz;
            if (dx * dx + dy * dy + dz * dz < 1.0D) continue; // evita tapar la camara

            float life = (g.age + pt) / GHOST_LIFE;
            if (life >= 1.0F) continue;
            float alpha = GHOST_ALPHA * (1.0F - life);          // se desvanece
            float hue = (g.hue + (g.age + pt) * HUE_DRIFT) % 1.0F; // el color va cambiando
            int rgb = Color.HSBtoRGB(hue, 0.9F, 1.0F);
            GlStateManager.color(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F,
                                 (rgb & 255) / 255F, alpha);

            GlStateManager.pushMatrix();
            GlStateManager.translate(dx, dy, dz);
            GlStateManager.rotate(180.0F - g.bodyYaw, 0.0F, 1.0F, 0.0F);
            GlStateManager.scale(-1.0F, -1.0F, 1.0F);
            GlStateManager.scale(0.9375F, 0.9375F, 0.9375F);
            GlStateManager.translate(0.0F, -1.501F, 0.0F);

            ModelPlayer m = g.model;
            m.isChild = false;
            m.isRiding = false;
            m.swingProgress = 0.0F;
            m.isSneak = g.player.isSneaking();
            m.setRotationAngles(g.limbSwing, g.limbSwingAmount, g.ageInTicks, g.netHeadYaw, g.headPitch, 0.0625F, g.player);
            m.render(g.player, g.limbSwing, g.limbSwingAmount, g.ageInTicks, g.netHeadYaw, g.headPitch, 0.0625F);
            GlStateManager.popMatrix();
        }

        GlStateManager.depthMask(true);
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableBlend();
        GlStateManager.enableCull();
        GlStateManager.enableLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
    }

    // ---------- HUD: barras simples bajo la mira ----------
    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post e) {
        if (e.getType() != RenderGameOverlayEvent.ElementType.ALL) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;
        State s = states.get(mc.player.getEntityId());
        if (s == null) return;

        float heat = s.active ? s.activeTicks / (float) ItemSandevistan.MAX_ACTIVE_TICKS
                              : s.cooldown / (float) ItemSandevistan.COOLDOWN_TICKS;
        float duration = s.active ? 1.0F - heat : 0.0F;
        heat = Math.max(0, Math.min(1, heat));
        duration = Math.max(0, Math.min(1, duration));

        ScaledResolution res = e.getResolution();
        int w = 60, h = 4;
        int x = res.getScaledWidth() / 2 - w / 2;
        int y = res.getScaledHeight() / 2 + 16;

        // Barra 1: duracion restante (cian)
        drawBar(x, y, w, h, duration, 0xFF33CCFF);
        // Barra 2: sobrecalentamiento (amarillo -> rojo; naranja mientras se enfria)
        int heatColor = s.active ? lerp(0xFFFFDD33, 0xFFFF2222, heat) : 0xFFFF8800;
        drawBar(x, y + 7, w, h, heat, heatColor);
    }

    private static void drawBar(int x, int y, int w, int h, float frac, int color) {
        Gui.drawRect(x - 1, y - 1, x + w + 1, y + h + 1, 0xAA000000);
        Gui.drawRect(x, y, x + w, y + h, 0xFF202020);
        int fill = Math.round(w * frac);
        if (fill > 0) Gui.drawRect(x, y, x + fill, y + h, color);
    }

    private static int lerp(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int g = (int) (((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = (int) ((a & 255) + ((b & 255) - (a & 255)) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }
}
