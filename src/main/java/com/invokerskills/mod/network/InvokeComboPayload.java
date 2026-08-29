package com.invokerskills.mod.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Пакет клиент -> сервер: "игрок собрал комбинацию номер comboId (0-9)".
 * Реальный эффект заклинания выполняется на сервере (SpellCaster),
 * клиент только считает нажатия клавиш и шлёт готовый номер комбо.
 */
public record InvokeComboPayload(int comboId) implements CustomPayload {

    public static final CustomPayload.Id<InvokeComboPayload> ID =
            new CustomPayload.Id<>(Identifier.of("invokerskills", "invoke_combo"));

    public static final PacketCodec<RegistryByteBuf, InvokeComboPayload> CODEC =
            PacketCodec.tuple(PacketCodecs.VAR_INT, InvokeComboPayload::comboId, InvokeComboPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
