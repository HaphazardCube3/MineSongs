package nls.minesongs;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Minesongs implements ModInitializer{
    public static final String MOD_ID ="minesongs";
    public static final Logger LOGGER =LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("MineSongs mod initializing!");

        // Register MP3 support
        try {
            javax.sound.sampled.AudioSystem.getAudioFileTypes();
        } catch (Exception e) {
            LOGGER.warn("Audio system initialization warning: {}", e.getMessage());
        }
    }
}
