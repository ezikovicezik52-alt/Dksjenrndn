package com.invokerskills.mod;

import com.invokerskills.mod.network.InvokeComboPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * Общий (common) инициализатор мода.
 * Регистрирует сетевой пакет и обработчик, который сервер вызывает,
 * когда клиент собрал комбинацию из 3 орбов (Z / X / Y).
 */
public class InvokerSkillsMod implements ModInitializer {

    public static final String MOD_ID = "invokerskills";

    @Override
    public void onInitialize() {
        // Регистрируем тип пакета клиент -> сервер
        PayloadTypeRegistry.playC2S().register(InvokeComboPayload.ID, InvokeComboPayload.CODEC);

        // Когда сервер получает пакет с id комбинации — кастуем заклинание
        ServerPlayNetworking.registerGlobalReceiver(InvokeComboPayload.ID, (payload, context) ->
                context.server().execute(() -> SpellCaster.cast(context.player(), payload.comboId()))
        );

        // Планировщик для временных блоков (ледяная стена и т.п.)
        TickScheduler.register();
    }
}
