package nls.minesongs;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;

public class MusicManager {
    private static boolean isPlaying = false;
    private static String currentTrack = "";
    private static ScheduledExecutorService executor;
    private static Clip currentClip;
    private static FloatControl volumeControl;
    private static float currentVolume = 80.0f; // Default volume 80%

    // Queue system
    private static Queue<String> songQueue = new LinkedList<>();
    private static boolean isLooping = false;

    // NEW: Track manual pauses
    private static boolean wasManuallyPaused = false;

    public static void playFromURL(String url) {
        // Store current state to avoid unnecessary "Stopped" notification
        boolean wasSomethingPlaying = (currentClip != null && isPlaying);

        // Call stop without triggering HUD notification when immediately starting new song
        stopCurrentPlaybackSilent();
        currentTrack = url;
        wasManuallyPaused = false;

        executor = Executors.newSingleThreadScheduledExecutor();
        executor.execute(() -> {
            try {
                Minesongs.LOGGER.info("Attempting to play: {}", url);

                String audioUrl = url;

                // Handle YouTube URLs
                if (url.contains("youtube.com") || url.contains("youtu.be")) {
                    Minesongs.LOGGER.info("Detected YouTube URL, extracting audio...");
                    audioUrl = extractWithYtDlp(url);
                    if (audioUrl == null) {
                        Minesongs.LOGGER.error("Failed to extract YouTube audio");
                        playNextInQueue(); // Try next song if this one fails
                        return;
                    }
                }
                // Handle Spotify URLs (will need more complex setup)
                else if (url.contains("spotify.com")) {
                    Minesongs.LOGGER.error("Spotify integration requires additional setup");
                    playNextInQueue(); // Try next song
                    return;
                }

                Minesongs.LOGGER.info("Playing audio from: {}", audioUrl);

                AudioInputStream audioStream;
                if (audioUrl.startsWith("file://")) {
                    // Local file
                    String filePath = audioUrl.substring(7); // Remove "file://" prefix
                    File audioFile = new File(filePath);
                    audioStream = AudioSystem.getAudioInputStream(audioFile);
                } else {
                    // Web URL
                    audioStream = AudioSystem.getAudioInputStream(new URL(audioUrl));
                }

                AudioFormat format = audioStream.getFormat();
                Minesongs.LOGGER.info("Audio format: {} Hz, {} bit, {} channels, {}",
                        format.getSampleRate(),
                        format.getSampleSizeInBits(),
                        format.getChannels(),
                        format.getEncoding());

                // Check if format is supported
                if (!isFormatSupported(format)) {
                    Minesongs.LOGGER.warn("Audio format not directly supported, attempting conversion...");
                    // Try to convert to a supported format
                    audioStream = convertToSupportedFormat(audioStream);
                    format = audioStream.getFormat();
                    Minesongs.LOGGER.info("Converted format: {} Hz, {} bit, {} channels, {}",
                            format.getSampleRate(),
                            format.getSampleSizeInBits(),
                            format.getChannels(),
                            format.getEncoding());
                }

                DataLine.Info info = new DataLine.Info(Clip.class, format);
                if (!AudioSystem.isLineSupported(info)) {
                    Minesongs.LOGGER.error("No audio line supported for this format");
                    playNextInQueue(); // Try next song
                    return;
                }

                currentClip = (Clip) AudioSystem.getLine(info);
                currentClip.open(audioStream);

                // Setup volume control
                setupVolumeControl();

                // UPDATED LineListener with pause detection
                currentClip.addLineListener(event -> {
                    if (event.getType() == LineEvent.Type.STOP) {
                        Minesongs.LOGGER.info("LineEvent STOP detected - isPlaying: {}, wasManuallyPaused: {}",
                                isPlaying, wasManuallyPaused);

                        // Only advance if this wasn't a manual pause
                        if (isPlaying && !wasManuallyPaused) {
                            Minesongs.LOGGER.info("Song finished naturally, checking queue...");
                            if (isLooping) {
                                currentClip.setFramePosition(0);
                                currentClip.start();
                                Minesongs.LOGGER.info("Looping current song");
                            } else {
                                playNextInQueue();
                            }
                        } else if (wasManuallyPaused) {
                            Minesongs.LOGGER.info("Stop event due to manual pause - keeping clip alive");
                            // Don't close the clip or advance queue
                        }
                    }
                });

                currentClip.start();
                isPlaying = true;
                wasManuallyPaused = false;
                Minesongs.LOGGER.info("Playback started successfully!");

                // NEW: Trigger HUD notification when song starts playing
                triggerHudNotification(true, extractSongTitleFromUrl(url));

            } catch (Exception e) {
                Minesongs.LOGGER.error("Failed to play audio: {}", e.getMessage());
                e.printStackTrace();
                isPlaying = false;
                playNextInQueue(); // Try next song if this one fails
            }
        });
    }

    // NEW: Silent version of stopCurrentPlayback that doesn't trigger HUD notifications
    private static void stopCurrentPlaybackSilent() {
        // Only fully stop if not manually paused
        if (!wasManuallyPaused) {
            if (currentClip != null) {
                currentClip.stop();
                currentClip.close();
                currentClip = null;
                volumeControl = null;
            }
            if (executor != null && !executor.isShutdown()) {
                executor.shutdownNow();
            }
            isPlaying = false;
            Minesongs.LOGGER.info("Playback fully stopped (silent)");
            // NOTE: No HUD notification triggered here!
        } else {
            Minesongs.LOGGER.info("Playback already paused manually - skipping full stop");
        }
    }

    // NEW: Helper method to extract song title from URL
    private static String extractSongTitleFromUrl(String url) {
        if (url.contains("youtube.com") || url.contains("youtu.be")) {
            try {
                // Try to extract video title from YouTube URL
                if (url.contains("v=")) {
                    String videoId = url.substring(url.indexOf("v=") + 2);
                    if (videoId.contains("&")) {
                        videoId = videoId.substring(0, videoId.indexOf("&"));
                    }
                    return "YouTube Video (" + videoId.substring(0, Math.min(8, videoId.length())) + "...)";
                } else if (url.contains("youtu.be/")) {
                    String videoId = url.substring(url.indexOf("youtu.be/") + 9);
                    if (videoId.contains("?")) {
                        videoId = videoId.substring(0, videoId.indexOf("?"));
                    }
                    return "YouTube Video (" + videoId.substring(0, Math.min(8, videoId.length())) + "...)";
                }
                return "YouTube Music";
            } catch (Exception e) {
                return "YouTube Music";
            }
        }
        // For local files
        else if (url.startsWith("file://")) {
            String filePath = url.substring(7);
            File file = new File(filePath);
            String fileName = file.getName();
            // Remove extension
            if (fileName.contains(".")) {
                fileName = fileName.substring(0, fileName.lastIndexOf('.'));
            }
            return fileName;
        }
        // For other URLs
        else if (url.startsWith("http")) {
            return "Online Audio";
        }
        return "Custom Audio";
    }

    // NEW: Method to trigger HUD notifications
    private static void triggerHudNotification(boolean playing, String songTitle) {
        try {
            // Use reflection to avoid direct dependency in case HUD class isn't available
            Class<?> hudClass = Class.forName("nls.minesongs.client.MusicHud");
            java.lang.reflect.Method method = hudClass.getMethod("onPlaybackStateChanged", boolean.class, String.class);
            method.invoke(null, playing, songTitle);
            Minesongs.LOGGER.info("HUD notification triggered: {} - {}", playing ? "Playing" : "Paused", songTitle);
        } catch (Exception e) {
            // Silently fail if HUD class isn't available (shouldn't happen in normal operation)
            Minesongs.LOGGER.debug("Could not trigger HUD notification: {}", e.getMessage());
        }
    }

    // Queue management methods
    public static void addToQueue(String url) {
        songQueue.offer(url);
        Minesongs.LOGGER.info("Added to queue: {}. Queue size: {}", url, songQueue.size());
    }

    public static void playNextInQueue() {
        if (!songQueue.isEmpty()) {
            String nextUrl = songQueue.poll();
            Minesongs.LOGGER.info("Playing next in queue: {}", nextUrl);
            wasManuallyPaused = false; // Reset for new track
            playFromURL(nextUrl);
        } else {
            Minesongs.LOGGER.info("Queue is empty - fully stopping playback");
            // Force stop even if manually paused
            if (currentClip != null) {
                currentClip.stop();
                currentClip.close();
                currentClip = null;
            }
            if (executor != null && !executor.isShutdown()) {
                executor.shutdownNow();
            }
            isPlaying = false;
            wasManuallyPaused = false;

            // NEW: Trigger stopped notification when queue is empty
            triggerHudNotification(false, "Queue empty");
        }
    }

    public static void clearQueue() {
        songQueue.clear();
        Minesongs.LOGGER.info("Queue cleared");
    }

    public static void skipToNext() {
        Minesongs.LOGGER.info("Skipping to next song");
        playNextInQueue();
    }

    public static List<String> getQueue() {
        return new ArrayList<>(songQueue);
    }

    public static int getQueueSize() {
        return songQueue.size();
    }

    public static void toggleLoop() {
        isLooping = !isLooping;
        Minesongs.LOGGER.info("Loop mode: {}", isLooping ? "ON" : "OFF");
    }

    public static boolean isLooping() {
        return isLooping;
    }

    // UPDATED togglePlayPause method with HUD notifications
    public static void togglePlayPause() {
        if (currentClip != null && currentClip.isOpen()) {
            boolean wasRunning = currentClip.isRunning();
            if (wasRunning) {
                // Pause the playback
                currentClip.stop();
                isPlaying = false;
                wasManuallyPaused = true; // Mark as manually paused
                Minesongs.LOGGER.info("Playback manually paused - clip kept alive");

                // NEW: Trigger paused notification
                triggerHudNotification(false, extractSongTitleFromUrl(currentTrack));
            } else {
                // Resume playback
                currentClip.start();
                isPlaying = true;
                wasManuallyPaused = false; // Reset manual pause flag
                Minesongs.LOGGER.info("Playback manually resumed");

                // NEW: Trigger playing notification
                triggerHudNotification(true, extractSongTitleFromUrl(currentTrack));
            }
        } else {
            Minesongs.LOGGER.warn("No audio clip available to play/pause");
        }
    }

    // UPDATED skipTrack with HUD notifications
    public static void skipTrack() {
        Minesongs.LOGGER.info("Skipping current track");
        String currentSong = extractSongTitleFromUrl(currentTrack);
        playNextInQueue();

        // NEW: Show skipping notification
        try {
            Class<?> hudClass = Class.forName("nls.minesongs.client.MusicHud");
            java.lang.reflect.Method method = hudClass.getMethod("showStopped", String.class);
            method.invoke(null, "Skipped: " + currentSong);
        } catch (Exception e) {
            // Silently fail
        }
    }

    // UPDATED stopCurrentPlayback to respect manual pauses
    public static void stopCurrentPlayback() {
        // Only fully stop if not manually paused
        if (!wasManuallyPaused) {
            if (currentClip != null) {
                currentClip.stop();
                currentClip.close();
                currentClip = null;
                volumeControl = null;
            }
            if (executor != null && !executor.isShutdown()) {
                executor.shutdownNow();
            }
            isPlaying = false;
            Minesongs.LOGGER.info("Playback fully stopped");

            // NEW: Trigger stopped notification
            triggerHudNotification(false, "Playback stopped");
        } else {
            Minesongs.LOGGER.info("Playback already paused manually - skipping full stop");
        }
    }

    private static void setupVolumeControl() {
        if (currentClip != null && currentClip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            volumeControl = (FloatControl) currentClip.getControl(FloatControl.Type.MASTER_GAIN);
            setVolume(currentVolume); // Apply current volume
            Minesongs.LOGGER.info("Volume control initialized");
        } else {
            volumeControl = null;
            Minesongs.LOGGER.warn("Volume control not supported for this audio clip");
        }
    }

    public static void setVolume(float volume) {
        // Clamp volume between 0 and 100
        currentVolume = Math.max(0, Math.min(100, volume));

        if (volumeControl != null) {
            try {
                // Convert from 0-100 scale to decibels
                float min = volumeControl.getMinimum();
                float max = volumeControl.getMaximum();
                // Logarithmic scale for better perceived volume control
                float db = min + (max - min) * (float)(Math.log10(currentVolume / 10.0 + 1));
                volumeControl.setValue(db);
                Minesongs.LOGGER.info("Volume set to: {}% ({} dB)", currentVolume, db);
            } catch (Exception e) {
                Minesongs.LOGGER.error("Failed to set volume: {}", e.getMessage());
            }
        }
    }

    public static float getVolume() {
        return currentVolume;
    }

    public static boolean isIsPlaying() {
        return isPlaying;
    }

    public static String getCurrentTrack() {
        return currentTrack;
    }

    // NEW: Get current song title for display
    public static String getCurrentSongTitle() {
        if (currentTrack == null || currentTrack.isEmpty()) {
            return "No track playing";
        }
        return extractSongTitleFromUrl(currentTrack);
    }

    // NEW: Debug method to check audio state
    public static void debugAudioState() {
        Minesongs.LOGGER.info("=== Audio State Debug ===");
        Minesongs.LOGGER.info("currentClip: {}", currentClip);
        if (currentClip != null) {
            Minesongs.LOGGER.info("isOpen: {}", currentClip.isOpen());
            Minesongs.LOGGER.info("isRunning: {}", currentClip.isRunning());
            Minesongs.LOGGER.info("isActive: {}", currentClip.isActive());
            Minesongs.LOGGER.info("Frame Length: {}", currentClip.getFrameLength());
            Minesongs.LOGGER.info("Frame Position: {}", currentClip.getFramePosition());
        }
        Minesongs.LOGGER.info("isPlaying: {}", isPlaying);
        Minesongs.LOGGER.info("wasManuallyPaused: {}", wasManuallyPaused);
        Minesongs.LOGGER.info("Current Track: {}", currentTrack);
        Minesongs.LOGGER.info("=== End Debug ===");
    }

    private static String extractWithYtDlp(String youtubeUrl) {
        try {
            String ytDlpPath = "C:\\Users\\sdb18\\AppData\\Local\\Programs\\Python\\Python311\\Scripts\\yt-dlp.exe";

            Minesongs.LOGGER.info("Using yt-dlp at: {}", ytDlpPath);

            // Create temp directory for downloads
            File tempDir = new File(System.getProperty("java.io.tmpdir"), "minesongs");
            tempDir.mkdirs();

            // CLEAN UP ALL FILES FIRST to prevent playing wrong song
            cleanupAllFiles();

            // Get FFmpeg path
            String ffmpegPath = findFfmpegPath();
            if (ffmpegPath != null) {
                Minesongs.LOGGER.info("Using FFmpeg at: {}", ffmpegPath);
            }

            // Force WAV format - Java has best native support for WAV
            List<String> command = new ArrayList<>();
            command.add(ytDlpPath);
            command.add("-x");                      // Extract audio
            command.add("--audio-format");
            command.add("wav");                     // Force WAV format (best Java compatibility)
            command.add("--audio-quality");
            command.add("0");                       // Best quality
            command.add("-o");
            command.add(tempDir.getAbsolutePath() + "/%(id)s.%(ext)s"); // Use video ID instead of title
            command.add("--no-playlist");           // Don't download playlists

            if (ffmpegPath != null) {
                command.add("--ffmpeg-location");
                command.add(ffmpegPath);
            }

            command.add("--no-warnings");
            command.add("--force-overwrites");      // Overwrite existing files
            command.add(youtubeUrl);

            Minesongs.LOGGER.info("Executing command: {}", String.join(" ", command));
            Process process = Runtime.getRuntime().exec(command.toArray(new String[0]));

            // Read output and error
            BufferedReader outputReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));

            String line;
            while ((line = outputReader.readLine()) != null) {
                Minesongs.LOGGER.info("yt-dlp: {}", line);
            }
            while ((line = errorReader.readLine()) != null) {
                Minesongs.LOGGER.info("yt-dlp error: {}", line);
            }

            int exitCode = process.waitFor();
            Minesongs.LOGGER.info("yt-dlp exit code: {}", exitCode);

            // Extract video ID from URL to find the correct file
            String videoId = extractVideoId(youtubeUrl);
            if (videoId != null) {
                File[] files = tempDir.listFiles((dir, name) -> name.toLowerCase().startsWith(videoId.toLowerCase()));
                if (files != null && files.length > 0) {
                    String localPath = files[0].getAbsolutePath();
                    Minesongs.LOGGER.info("Successfully converted audio to WAV: {}", localPath);
                    return "file:///" + localPath.replace("\\", "/");
                }
            }

            // Fallback: look for any WAV file (newest one)
            File[] files = tempDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".wav"));
            if (files != null && files.length > 0) {
                // Get the most recently modified file
                File newestFile = files[0];
                for (File file : files) {
                    if (file.lastModified() > newestFile.lastModified()) {
                        newestFile = file;
                    }
                }
                String localPath = newestFile.getAbsolutePath();
                Minesongs.LOGGER.info("Fallback to newest WAV file: {}", localPath);
                return "file:///" + localPath.replace("\\", "/");
            }

            Minesongs.LOGGER.error("No WAV file found after conversion");
            return null;

        } catch (Exception e) {
            Minesongs.LOGGER.error("yt-dlp download failed: {}", e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private static String extractVideoId(String youtubeUrl) {
        // Extract video ID from YouTube URL
        if (youtubeUrl.contains("v=")) {
            return youtubeUrl.substring(youtubeUrl.indexOf("v=") + 2, youtubeUrl.indexOf("v=") + 13);
        } else if (youtubeUrl.contains("youtu.be/")) {
            return youtubeUrl.substring(youtubeUrl.indexOf("youtu.be/") + 9);
        }
        return null;
    }

    private static void cleanupAllFiles() {
        try {
            File tempDir = new File(System.getProperty("java.io.tmpdir"), "minesongs");
            if (tempDir.exists()) {
                File[] files = tempDir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        file.delete();
                    }
                    Minesongs.LOGGER.info("Cleaned up all temporary files");
                }
            }
        } catch (Exception e) {
            Minesongs.LOGGER.warn("Failed to cleanup files: {}", e.getMessage());
        }
    }

    private static String findFfmpegPath() {
        // Use the exact path we found
        String ffmpegDir = "C:\\Program Files (x86)\\ffmpeg\\bin";
        File ffmpegExe = new File(ffmpegDir, "ffmpeg.exe");

        if (ffmpegExe.exists()) {
            Minesongs.LOGGER.info("Found FFmpeg at: {}", ffmpegExe.getAbsolutePath());
            return ffmpegDir;
        } else {
            Minesongs.LOGGER.error("FFmpeg not found at: {}", ffmpegExe.getAbsolutePath());

            // Fallback: try to find it in PATH
            try {
                Process process = Runtime.getRuntime().exec(new String[]{"where", "ffmpeg"});
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String path = reader.readLine();
                if (path != null && !path.isEmpty()) {
                    File foundFfmpeg = new File(path);
                    Minesongs.LOGGER.info("Found FFmpeg in PATH at: {}", foundFfmpeg.getAbsolutePath());
                    return foundFfmpeg.getParent();
                }
            } catch (Exception e) {
                // Ignore
            }

            return null;
        }
    }

    private static boolean isFormatSupported(AudioFormat format) {
        try {
            DataLine.Info info = new DataLine.Info(Clip.class, format);
            return AudioSystem.isLineSupported(info);
        } catch (Exception e) {
            Minesongs.LOGGER.warn("Format check failed: {}", e.getMessage());
            return false;
        }
    }

    private static AudioInputStream convertToSupportedFormat(AudioInputStream originalStream) throws Exception {
        AudioFormat originalFormat = originalStream.getFormat();

        // Convert to PCM signed, 44100 Hz, 16-bit, stereo (most compatible)
        AudioFormat targetFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                44100,  // Sample rate
                16,     // Sample size in bits
                2,      // Channels (stereo)
                4,      // Frame size (2 channels * 2 bytes per sample)
                44100,  // Frame rate
                false   // Little-endian
        );

        if (AudioSystem.isConversionSupported(targetFormat, originalFormat)) {
            return AudioSystem.getAudioInputStream(targetFormat, originalStream);
        } else {
            Minesongs.LOGGER.warn("Direct conversion not supported, using original format");
            return originalStream;
        }
    }

    // Cleanup old files to prevent disk space issues
    public static void cleanupOldFiles() {
        try {
            File tempDir = new File(System.getProperty("java.io.tmpdir"), "minesongs");
            if (tempDir.exists()) {
                File[] files = tempDir.listFiles();
                if (files != null) {
                    // Delete files older than 1 hour
                    long oneHourAgo = System.currentTimeMillis() - (60 * 60 * 1000);
                    int deletedCount = 0;
                    for (File file : files) {
                        if (file.lastModified() < oneHourAgo) {
                            if (file.delete()) {
                                deletedCount++;
                            }
                        }
                    }
                    Minesongs.LOGGER.info("Cleaned up {} old files", deletedCount);
                }
            }
        } catch (Exception e) {
            Minesongs.LOGGER.warn("Failed to cleanup old files: {}", e.getMessage());
        }
    }
}