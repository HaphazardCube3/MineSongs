package nls.minesongs;

import javax.sound.sampled.*;
import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

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

    public static void playFromURL(String url) {
        stopCurrentPlayback();
        currentTrack = url;

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

                // Add listener to automatically play next song when current finishes
                currentClip.addLineListener(event -> {
                    if (event.getType() == LineEvent.Type.STOP && isPlaying) {
                        // Only trigger next song if we're still playing (not paused)
                        Minesongs.LOGGER.info("Song finished, checking queue...");
                        if (isLooping) {
                            // Loop current song
                            currentClip.setFramePosition(0);
                            currentClip.start();
                            Minesongs.LOGGER.info("Looping current song");
                        } else {
                            playNextInQueue();
                        }
                    }
                });

                currentClip.start();
                isPlaying = true;
                Minesongs.LOGGER.info("Playback started successfully!");

            } catch (Exception e) {
                Minesongs.LOGGER.error("Failed to play audio: {}", e.getMessage());
                e.printStackTrace();
                isPlaying = false;
                playNextInQueue(); // Try next song if this one fails
            }
        });
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
            playFromURL(nextUrl);
        } else {
            Minesongs.LOGGER.info("Queue is empty");
            stopCurrentPlayback();
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

    public static void togglePlayPause() {
        if (currentClip != null && currentClip.isOpen()) {
            if (isPlaying) {
                currentClip.stop();
                isPlaying = false;
                Minesongs.LOGGER.info("Playback paused");
            } else {
                currentClip.start();
                isPlaying = true;
                Minesongs.LOGGER.info("Playback resumed");
            }
        } else {
            Minesongs.LOGGER.warn("No audio clip to play/pause");
        }
    }

    public static void skipTrack() {
        Minesongs.LOGGER.info("Skipping current track");
        playNextInQueue();
    }

    public static void stopCurrentPlayback() {
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
        Minesongs.LOGGER.info("Playback stopped");
    }

    public static boolean isIsPlaying() {
        return isPlaying;
    }

    public static String getCurrentTrack() {
        return currentTrack;
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