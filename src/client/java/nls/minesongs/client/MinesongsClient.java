package nls.minesongs.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import nls.minesongs.MusicManager;  // Add this import

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

        // Register tick event for key handling
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (playPauseKey.wasPressed()) {
                MusicManager.togglePlayPause(); // Now this will work with the import
            }
            while (skipKey.wasPressed()) {
                MusicManager.skipTrack(); // Fixed the typo
            }
            while (openGuiKey.wasPressed()) {
                // Open music GUI
                client.setScreen(new MusicPlayerScreen());
            }
        });
    }
}