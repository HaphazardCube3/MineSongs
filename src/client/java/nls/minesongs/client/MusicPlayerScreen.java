package nls.minesongs.client;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.client.gui.widget.SliderWidget;

public class MusicPlayerScreen extends Screen {
    private TextFieldWidget urlField;
    private VolumeSliderWidget volumeSlider;

    public MusicPlayerScreen() {
        super(Text.literal("MineSongs Player"));
    }

    @Override
    protected void init() {
        super.init();

        // URL input field
        urlField = new TextFieldWidget(
                this.textRenderer,
                this.width / 2 - 150, 60, 300, 20,
                Text.literal("Paste YouTube URL here")
        );
        urlField.setMaxLength(1000);
        urlField.setPlaceholder(Text.literal("Paste full YouTube URL here..."));

        // Add Enter key listener
        urlField.setChangedListener(text -> {
            // This gets called when text changes, but we'll handle Enter in keyPressed
        });

        this.addDrawableChild(urlField);

        // Volume Slider
        volumeSlider = new VolumeSliderWidget(
                this.width / 2 - 100, 90, 200, 20,
                Text.literal("Volume: " + (int)nls.minesongs.MusicManager.getVolume() + "%"),
                nls.minesongs.MusicManager.getVolume() / 100.0
        );
        this.addDrawableChild(volumeSlider);

        // Play button
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Play"), button -> {
            playCurrentURL();
        }).dimensions(this.width / 2 - 100, 120, 200, 20).build());

        // Add to Queue button
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Add to Queue"), button -> {
            String url = urlField.getText();
            if (!url.isEmpty()) {
                nls.minesongs.MusicManager.addToQueue(url);
                urlField.setText(""); // Clear field after adding
            }
        }).dimensions(this.width / 2 - 100, 150, 200, 20).build());

        // Play/Pause button
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Play/Pause"), button -> {
            nls.minesongs.MusicManager.togglePlayPause();
        }).dimensions(this.width / 2 - 100, 180, 200, 20).build());

        // Stop button
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Stop"), button -> {
            nls.minesongs.MusicManager.skipTrack();
        }).dimensions(this.width / 2 - 100, 210, 200, 20).build());

        // Next Song button
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Next Song"), button -> {
            nls.minesongs.MusicManager.skipToNext();
        }).dimensions(this.width / 2 - 100, 240, 200, 20).build());

        // Clear Queue button
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Clear Queue"), button -> {
            nls.minesongs.MusicManager.clearQueue();
        }).dimensions(this.width / 2 - 100, 270, 200, 20).build());

        // Loop toggle button
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Loop: " + (nls.minesongs.MusicManager.isLooping() ? "ON" : "OFF")), button -> {
            nls.minesongs.MusicManager.toggleLoop();
            button.setMessage(Text.literal("Loop: " + (nls.minesongs.MusicManager.isLooping() ? "ON" : "OFF")));
        }).dimensions(this.width / 2 - 100, 300, 200, 20).build());

        // Set initial focus to the URL field so user can type immediately
        this.setInitialFocus(urlField);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Check if Enter key was pressed (keyCode 257 is Enter, 335 is Numpad Enter)
        if ((keyCode == 257 || keyCode == 335) && this.getFocused() == urlField) {
            playCurrentURL();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void playCurrentURL() {
        String url = urlField.getText();
        if (!url.isEmpty()) {
            nls.minesongs.Minesongs.LOGGER.info("Playing URL (Enter pressed): {}", url);
            nls.minesongs.MusicManager.playFromURL(url);
        }
    }

    @Override
    public void render(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFF);

        // Display current track status
        boolean playing = nls.minesongs.MusicManager.isIsPlaying();
        String status = "Stopped";
        if (nls.minesongs.MusicManager.getCurrentTrack() != null && !nls.minesongs.MusicManager.getCurrentTrack().isEmpty()) {
            status = playing ? "Now Playing" : "Paused";
        }
        context.drawTextWithShadow(this.textRenderer, Text.literal("Status: " + status), this.width / 2 - 150, 40, 0xFFFFFF);

        // Display current volume
        context.drawTextWithShadow(this.textRenderer, Text.literal("Volume: " + (int)nls.minesongs.MusicManager.getVolume() + "%"), this.width / 2 - 150, 320, 0xFFFFFF);

        // Display queue information
        int queueSize = nls.minesongs.MusicManager.getQueueSize();
        context.drawTextWithShadow(this.textRenderer, Text.literal("Queue: " + queueSize + " songs"), this.width / 2 - 150, 340, 0xFFFFFF);

        // Display loop status
        String loopStatus = nls.minesongs.MusicManager.isLooping() ? "ON" : "OFF";
        context.drawTextWithShadow(this.textRenderer, Text.literal("Loop: " + loopStatus), this.width / 2 - 150, 360, 0xFFFFFF);

        // Display "Press Enter to play" hint
        context.drawTextWithShadow(this.textRenderer, Text.literal("Press Enter to play"), this.width / 2 - 150, 380, 0xAAAAAA);

        super.render(context, mouseX, mouseY, delta);
    }

    // Custom Slider Widget for Volume Control
    private static class VolumeSliderWidget extends SliderWidget {
        public VolumeSliderWidget(int x, int y, int width, int height, Text text, double value) {
            super(x, y, width, height, text, value);
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Text.literal("Volume: " + (int)(this.value * 100) + "%"));
        }

        @Override
        protected void applyValue() {
            nls.minesongs.MusicManager.setVolume((float)(this.value * 100));
        }
    }
}