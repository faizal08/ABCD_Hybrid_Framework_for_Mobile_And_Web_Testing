package com.eit.automation.utils;

import org.jcodec.api.awt.AWTSequenceEncoder;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.common.model.Rational;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class VideoRecorder {
    private AWTSequenceEncoder encoder;
    private ScheduledExecutorService worker;
    private Robot robot;
    private File videoFile;
    private volatile boolean isRecording = false;
    private SeekableByteChannel out; // Added for resource management

    /**
     * NO-ARGUMENT CONSTRUCTOR
     * Satisfies the static initialization in Main.java
     */
    public VideoRecorder() {
        try {
            this.robot = new Robot();
        } catch (AWTException e) {
            System.err.println("❌ Robot capture not supported: " + e.getMessage());
        }
    }

    /**
     * Initializes and starts a new MP4 recording session.
     */
    public void startRecording(String directory, String fileName) throws Exception {
        // Force .mp4 extension
        if (!fileName.toLowerCase().endsWith(".mp4")) {
            fileName = fileName.replaceAll("\\.[^.]+$", "") + ".mp4";
        }

        File dir = new File(directory);
        if (!dir.exists()) dir.mkdirs();

        this.videoFile = new File(dir, fileName);

        // FIX: In JCodec 0.2.5, we use NIOUtils.writableChannel directly
        // which returns a SeekableByteChannel
        this.out = NIOUtils.writableChannel(videoFile);

        // Use Rational.R(10, 1) for 10 FPS
        this.encoder = new AWTSequenceEncoder(out, Rational.R(10, 1));

        this.isRecording = true;

        // Background thread for Ryzen 7 optimization
        worker = Executors.newSingleThreadScheduledExecutor();
        worker.scheduleAtFixedRate(this::captureFrame, 0, 100, TimeUnit.MILLISECONDS);

        System.out.println("🎥 MP4 Recording started: " + videoFile.getName());
    }

    /**
     * Captures a single frame of the desktop
     */
    private void captureFrame() {
        if (!isRecording || encoder == null) return;
        try {
            Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
            BufferedImage screenFullImage = robot.createScreenCapture(screenRect);

            synchronized (this) {
                if (encoder != null) {
                    encoder.encodeImage(screenFullImage);
                }
            }
        } catch (Exception e) {
            // Ignore capture errors to keep Selenium running
        }
    }

    /**
     * Stops the recording and finalizes the MP4 file
     */
    public void stopRecording() throws Exception {
        this.isRecording = false;

        if (worker != null) {
            worker.shutdown();
            worker.awaitTermination(5, TimeUnit.SECONDS);
        }

        synchronized (this) {
            if (encoder != null) {
                encoder.finish(); // Finalizes the MP4 metadata
                encoder = null;
            }
            if (out != null) {
                out.close(); // Correctly closes the file channel
                out = null;
            }
        }
        System.out.println("✅ MP4 Video saved: " + videoFile.getAbsolutePath());
    }
}