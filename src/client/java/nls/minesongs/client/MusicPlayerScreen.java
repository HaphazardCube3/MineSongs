package nls.minesongs.client;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public class MusicPlayerScreen extends Screen {
    private TextFieldWidget urlField;

    public MusicPlayerScreen() {
        super(Text.literal("MineSongs Player"));
    }

    @Override
    protected void init() {
        super.init();

        // URL input field - WIDER and longer max length
        urlField = new TextFieldWidget(
                this.textRenderer,
                this.width / 2 - 150, 60, 300, 20,  // Wider: 300 instead of 200
                Text.literal("Paste YouTube URL here")
        );
        urlField.setMaxLength(1000);  // Much longer to fit full YouTube URLs
        urlField.setPlaceholder(Text.literal("Paste full YouTube URL here..."));
        this.addDrawableChild(urlField);

        // Play button
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Play"), button -> {
            String url = urlField.getText();
            if (!url.isEmpty()) {
                // Log the full URL to see what's being sent
                nls.minesongs.Minesongs.LOGGER.info("Full URL received: {}", url);
                nls.minesongs.MusicManager.playFromURL(url);
            }
        }).dimensions(this.width / 2 - 100, 90, 200, 20).build());

        // Play/Pause button
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Play/Pause"), button -> {
            nls.minesongs.MusicManager.togglePlayPause();
        }).dimensions(this.width / 2 - 100, 120, 200, 20).build());

        // Stop button
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Stop"), button -> {
            nls.minesongs.MusicManager.skipTrack();
        }).dimensions(this.width / 2 - 100, 150, 200, 20).build());

        // Current track status
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Status"), button -> {
            boolean playing = nls.minesongs.MusicManager.isIsPlaying();
            String current = nls.minesongs.MusicManager.getCurrentTrack();
            nls.minesongs.Minesongs.LOGGER.info("Status - Playing: {}, Track: {}", playing, current);
        }).dimensions(this.width / 2 - 100, 180, 200, 20).build());
    }

    @Override
    public void render(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFF);

        // Display current track status
        boolean playing = nls.minesongs.MusicManager.isIsPlaying();
        String status = playing ? "Now Playing" : "Stopped";
        context.drawTextWithShadow(this.textRenderer, Text.literal("Status: " + status), this.width / 2 - 150, 40, 0xFFFFFF);

        super.render(context, mouseX, mouseY, delta);
    }
}