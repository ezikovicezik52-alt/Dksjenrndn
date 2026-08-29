package com.invokerskills.mod;

import com.invokerskills.mod.network.InvokeComboPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Клиентская часть: три клавиши (Z, X, Y) = три "орба".
 * Как только нажаты ЛЮБЫЕ 3 клавиши подряд (порядок не важен, повторять можно),
 * комбинация автоматически отправляется на сервер и заклинание кастуется —
 * никакой отдельной кнопки "каст" (аналог R в Dota) нажимать не нужно.
 */
public class InvokerSkillsClient implements ClientModInitializer {

    private KeyBinding keyZ;
    private KeyBinding keyX;
    private KeyBinding keyY;

    private final List<Integer> buffer = new ArrayList<>();
    private int ticksSinceLast = 0;

    // Если между нажатиями пройдёт больше этого времени - комбо сбрасывается.
    // 60 тиков = 3 секунды. Поменяйте, если хотите быстрее/медленнее.
    private static final int TIMEOUT_TICKS = 60;

    @Override
    public void onInitializeClient() {
        keyZ = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.invokerskills.orb1", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_Z, "category.invokerskills.skills"));
        keyX = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.invokerskills.orb2", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_X, "category.invokerskills.skills"));
        keyY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.invokerskills.orb3", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_Y, "category.invokerskills.skills"));

        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    private void onTick(MinecraftClient client) {
        if (client.player == null) {
            buffer.clear();
            return;
        }

        boolean pressedSomething = false;

        // wasPressed() съедает по одному нажатию за вызов, поэтому в while,
        // если игрок как-то умудрится нажать одну клавишу дважды за тик.
        while (keyZ.wasPressed()) { addOrb(client, 0); pressedSomething = true; }
        while (keyX.wasPressed()) { addOrb(client, 1); pressedSomething = true; }
        while (keyY.wasPressed()) { addOrb(client, 2); pressedSomething = true; }

        if (pressedSomething) {
            ticksSinceLast = 0;
        } else if (!buffer.isEmpty()) {
            ticksSinceLast++;
            if (ticksSinceLast > TIMEOUT_TICKS) {
                buffer.clear();
                client.player.sendMessage(Text.literal("§7Комбо сброшено (таймаут)"), true);
            }
        }

        if (buffer.size() >= 3) {
            int comboId = computeCombo(buffer);
            ClientPlayNetworking.send(new InvokeComboPayload(comboId));
            buffer.clear();
            ticksSinceLast = 0;
        }
    }

    private void addOrb(MinecraftClient client, int orb) {
        buffer.add(orb);
        String[] letters = {"Z", "X", "Y"};
        StringBuilder sb = new StringBuilder();
        for (int o : buffer) sb.append(letters[o]);
        client.player.sendMessage(Text.literal("§7Комбо: §e" + sb), true);
    }

    /**
     * Порядок нажатий не важен (как в Dota: quas-wex-exort считается по количеству
     * каждого орба, а не по порядку). Поэтому сортируем и ищем в таблице из 10 комбинаций.
     */
    private int computeCombo(List<Integer> buf) {
        int[] sorted = buf.stream().mapToInt(Integer::intValue).sorted().toArray();
        int[][] table = {
                {0, 0, 0}, {0, 0, 1}, {0, 0, 2}, {0, 1, 1}, {0, 1, 2},
                {0, 2, 2}, {1, 1, 1}, {1, 1, 2}, {1, 2, 2}, {2, 2, 2}
        };
        for (int i = 0; i < table.length; i++) {
            if (table[i][0] == sorted[0] && table[i][1] == sorted[1] && table[i][2] == sorted[2]) {
                return i;
            }
        }
        return 0;
    }
}
