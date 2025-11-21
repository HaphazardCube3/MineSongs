package nls.minesongs.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

public class MusicHud {
    private static long notificationStartTime = 0;
    private static final long NOTIFICATION_DURATION = 5000; // 5 seconds
    private static String currentNotification = "";
    private static String lastAction = "";
    private static float animationProgress = 0f;
    private static final float ANIMATION_SPEED = 0.2f; // Smooth animation speed

    // Cache these to avoid recalculating every frame
    private static int lastScreenWidth = 0;
    private static int cachedWidth = 200;
    private static int cachedHeight = 40;

    public static void render(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) return;

        long currentTime = System.currentTimeMillis();
        long elapsed = currentTime - notificationStartTime;

        // Don't render if no notification or time expired
        if (currentNotification.isEmpty() || elapsed > NOTIFICATION_DURATION) {
            animationProgress = 0f;
            return;
        }

        // Calculate animation progress
        updateAnimationProgress(elapsed);

        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();

        // Recalculate dimensions only if screen size changed
        if (screenWidth != lastScreenWidth) {
            cachedWidth = Math.min(250, screenWidth / 3);
            lastScreenWidth = screenWidth;
        }

        // Calculate position with smooth animation
        int targetX = screenWidth - cachedWidth - 10;
        int x = calculateAnimatedX(targetX, cachedWidth);
        int y = 20;

        renderNotificationBox(context, x, y, tickDelta);
    }

    private static void updateAnimationProgress(long elapsed) {
        if (elapsed < 300) { // Slide in
            animationProgress = Math.min(1.0f, animationProgress + ANIMATION_SPEED);
        } else if (elapsed > NOTIFICATION_DURATION - 300) { // Slide out
            animationProgress = Math.max(0.0f, animationProgress - ANIMATION_SPEED);
        } else { // Stay visible
            animationProgress = 1.0f;
        }
    }

    private static int calculateAnimatedX(int targetX, int width) {
        // Smooth ease-out animation
        float easedProgress = 1.0f - (1.0f - animationProgress) * (1.0f - animationProgress);
        return targetX + (int)(width * (1 - easedProgress));
    }

    private static void renderNotificationBox(DrawContext context, int x, int y, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();

        // Draw notification background with rounded corners effect
        int alpha = (int)(200 * animationProgress);
        int backgroundColor = (alpha << 24) | 0x202020; // Semi-transparent dark background

        context.fill(x, y, x + cachedWidth, y + cachedHeight, backgroundColor);

        // Draw accent border
        int accentColor = getAccentColor();
        context.fill(x, y, x + cachedWidth, y + 2, accentColor); // Top border
        context.fill(x, y + cachedHeight - 2, x + cachedWidth, y + cachedHeight, accentColor); // Bottom border
        context.fill(x, y, x + 3, y + cachedHeight, accentColor); // Left border

        // Draw icon and status
        renderIconAndStatus(context, x, y, accentColor);

        // Draw song title
        renderSongTitle(context, x, y);

        // Draw progress bar
        renderProgressBar(context, x, y);
    }

    private static int getAccentColor() {
        switch (lastAction) {
            case "paused": return 0xFFFFFF55; // Yellow
            case "playing": return 0xFF55FF55; // Green
            case "stopped": return 0xFFFF5555; // Red
            default: return 0xFF5555FF; // Blue
        }
    }

    private static void renderIconAndStatus(DrawContext context, int x, int y, int accentColor) {
        MinecraftClient client = MinecraftClient.getInstance();

        String icon = getStatusIcon();
        String statusText = getStatusText();

        context.drawTextWithShadow(client.textRenderer, Text.literal(icon), x + 8, y + 8, accentColor);
        context.drawTextWithShadow(client.textRenderer, Text.literal(statusText), x + 25, y + 8, 0xCCCCCC);
    }

    private static String getStatusIcon() {
        switch (lastAction) {
            case "paused": return "⏸";
            case "playing": return "▶";
            case "stopped": return "⏹";
            default: return "🎵";
        }
    }

    private static String getStatusText() {
        switch (lastAction) {
            case "paused": return "Paused";
            case "playing": return "Now Playing";
            case "stopped": return "Stopped";
            default: return "Music";
        }
    }

    private static void renderSongTitle(DrawContext context, int x, int y) {
        MinecraftClient client = MinecraftClient.getInstance();

        // Truncate song title to fit
        String displayTrack = currentNotification;
        int maxLength = (cachedWidth - 16) / 6; // Approximate characters that fit
        if (displayTrack.length() > maxLength) {
            displayTrack = displayTrack.substring(0, maxLength - 3) + "...";
        }

        context.drawTextWithShadow(client.textRenderer, Text.literal(displayTrack), x + 8, y + 22, 0xFFFFFF);
    }

    private static void renderProgressBar(DrawContext context, int x, int y) {
        long currentTime = System.currentTimeMillis();
        long elapsed = currentTime - notificationStartTime;
        float progress = 1.0f - (float)elapsed / NOTIFICATION_DURATION;

        if (progress > 0 && animationProgress > 0.9f) { // Only show when fully visible
            int progressWidth = (int)((cachedWidth - 16) * progress);
            int progressColor = getAccentColor();
            context.fill(x + 8, y + cachedHeight - 6, x + 8 + progressWidth, y + cachedHeight - 4, progressColor);
        }
    }

    // Public methods to show notifications
    public static void showNowPlaying(String songTitle) {
        setNotification(songTitle, "playing");
    }

    public static void showPaused(String songTitle) {
        setNotification(songTitle, "paused");
    }

    public static void showStopped(String songTitle) {
        setNotification(songTitle, "stopped");
    }

    private static void setNotification(String songTitle, String action) {
        currentNotification = songTitle != null ? songTitle : "Unknown Song";
        lastAction = action;
        notificationStartTime = System.currentTimeMillis();
        animationProgress = 0f; // Reset animation
    }

    // Call this from your MusicManager when playback state changes
    public static void onPlaybackStateChanged(boolean isPlaying, String currentTrack) {
        if (currentTrack == null || currentTrack.isEmpty()) {
            showStopped("No track playing");
            return;
        }

        if (isPlaying) {
            showNowPlaying(currentTrack);
        } else {
            showPaused(currentTrack);
        }
    }
}