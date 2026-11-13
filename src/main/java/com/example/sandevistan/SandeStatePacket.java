package com.example.sandevistan;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Servidor -> clientes: estado del Sandevistan de una entidad (para efectos y barras). */
public class SandeStatePacket implements IMessage {
    public int entityId;
    public boolean active;
    public int activeTicks;
    public int cooldown;

    public SandeStatePacket() {}

    public SandeStatePacket(int entityId, boolean active, int activeTicks, int cooldown) {
        this.entityId = entityId;
        this.active = active;
        this.activeTicks = activeTicks;
        this.cooldown = cooldown;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        entityId = buf.readInt();
        active = buf.readBoolean();
        activeTicks = buf.readInt();
        cooldown = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(entityId);
        buf.writeBoolean(active);
        buf.writeInt(activeTicks);
        buf.writeInt(cooldown);
    }

    public static class Handler implements IMessageHandler<SandeStatePacket, IMessage> {
        @Override
        public IMessage onMessage(SandeStatePacket msg, MessageContext ctx) {
            SandevistanMod.proxy.handleState(msg);
            return null;
        }
    }
}
