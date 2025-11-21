package nls.minesongs.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class MinesongsClient implements ClientModInitializer {
    private static KeyBinding playPauseKey;
    private static KeyBinding skipKey;
    private static KeyBinding openGuiKey;

    @Override
    public void onInitializeClient() {
        // Register key bindings
        playPauseKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.minesongs.play_pause",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_P,
                "category.minesongs.music"
        ));

        skipKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.minesongs.skip",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_N,
                "category.minesongs.music"
        ));

        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.minesongs.open_gui",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_M,
                "category.minesongs.music"
        ));

        // ✅ ADD THIS: Register HUD rendering
        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
            MusicHud.render(drawContext, tickCounter.getTickDelta(true));
        });

        // Register tick event for key handling
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (playPauseKey.wasPressed()) {
                nls.minesongs.MusicManager.togglePlayPause();
            }
            while (skipKey.wasPressed()) {
                nls.minesongs.MusicManager.skipTrack();
            }
            while (openGuiKey.wasPressed()) {
                client.setScreen(new MusicPlayerScreen());
            }
        });
    }
}