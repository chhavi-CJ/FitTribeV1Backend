package com.fittribe.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fittribe.api.entity.WorkoutSession;
import com.fittribe.api.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;

@Service
public class ShareImageService {
    private static final Logger log = LoggerFactory.getLogger(ShareImageService.class);

    private static final int CARD_WIDTH = 1080;
    private static final int CARD_HEIGHT = 1920;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public byte[] generateShareImage(
            WorkoutSession session,
            User user,
            MultipartFile photoFile) throws IOException {

        BufferedImage card = new BufferedImage(CARD_WIDTH, CARD_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = card.createGraphics();

        try {
            // Enable anti-aliasing
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            // 1. Draw gradient background
            drawGradientBackground(g2d);

            // 2. Draw user photo if provided
            if (photoFile != null && !photoFile.isEmpty()) {
                drawUserPhoto(g2d, photoFile);
            }

            // 3. Draw text content on top
            drawTextContent(g2d, session, user);

            // Convert to PNG bytes
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(card, "png", baos);
            return baos.toByteArray();

        } finally {
            g2d.dispose();
        }
    }

    private void drawGradientBackground(Graphics2D g2d) {
        Color topColor = new Color(0x1a, 0x3d, 0x2a);
        Color bottomColor = new Color(0x0a, 0x1a, 0x0f);

        Paint gradient = new java.awt.GradientPaint(
                0, 0, topColor,
                CARD_WIDTH, CARD_HEIGHT, bottomColor);
        g2d.setPaint(gradient);
        g2d.fillRect(0, 0, CARD_WIDTH, CARD_HEIGHT);
    }

    private void drawUserPhoto(Graphics2D g2d, MultipartFile photoFile) {
        try {
            BufferedImage photo = ImageIO.read(photoFile.getInputStream());
            if (photo == null) return;

            // Scale to cover 1080x1920, maintaining aspect ratio
            BufferedImage scaledPhoto = scalePhotoCover(photo, CARD_WIDTH, CARD_HEIGHT);
            g2d.drawImage(scaledPhoto, 0, 0, null);

            // Draw dark overlay gradient
            drawPhotoOverlay(g2d);
        } catch (IOException e) {
            log.warn("Failed to load/process user photo", e);
        }
    }

    private BufferedImage scalePhotoCover(BufferedImage src, int targetWidth, int targetHeight) {
        double srcAspect = (double) src.getWidth() / src.getHeight();
        double targetAspect = (double) targetWidth / targetHeight;

        int scaledWidth, scaledHeight, srcX, srcY;

        if (srcAspect > targetAspect) {
            // Photo is wider, crop left/right
            scaledHeight = src.getHeight();
            scaledWidth = (int) (scaledHeight * targetAspect);
            srcY = 0;
            srcX = (src.getWidth() - scaledWidth) / 2;
        } else {
            // Photo is taller, crop top/bottom
            scaledWidth = src.getWidth();
            scaledHeight = (int) (scaledWidth / targetAspect);
            srcX = 0;
            srcY = (src.getHeight() - scaledHeight) / 2;
        }

        BufferedImage cropped = src.getSubimage(srcX, srcY, scaledWidth, scaledHeight);
        BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = scaled.createGraphics();
        g2d.drawImage(cropped, 0, 0, targetWidth, targetHeight, null);
        g2d.dispose();
        return scaled;
    }

    private void drawPhotoOverlay(Graphics2D g2d) {
        // Semi-transparent black gradient overlay
        Color topOverlay = new Color(0, 0, 0, 89);   // rgba(0,0,0,0.35)
        Color bottomOverlay = new Color(0, 0, 0, 140);  // rgba(0,0,0,0.55)

        Paint overlayGradient = new java.awt.GradientPaint(
                0, 0, topOverlay,
                0, CARD_HEIGHT, bottomOverlay);
        g2d.setPaint(overlayGradient);
        g2d.fillRect(0, 0, CARD_WIDTH, CARD_HEIGHT);
    }

    private void drawTextContent(Graphics2D g2d, WorkoutSession session, User user) {
        Color limeGreen = new Color(0xC8, 0xF1, 0x35);
        Color white = Color.WHITE;
        Color lightGray = new Color(255, 255, 255, 127);

        // Load font (try DM Sans, fallback to SansSerif)
        Font dmSans = loadDmSansFont();

        // a. "WORKOUT COMPLETE" — y: ~480px
        drawCenteredText(g2d, "WORKOUT COMPLETE",
                dmSans, 32, Font.BOLD, limeGreen, 480);

        // b. Workout name — y: ~560px
        String workoutName = session.getName() != null ? session.getName() : "Session";
        drawCenteredText(g2d, workoutName,
                dmSans, 64, Font.BOLD, white, 560);

        // c. Three stat boxes — y: ~680px
        BigDecimal volume = session.getTotalVolumeKg() != null ? session.getTotalVolumeKg() : BigDecimal.ZERO;
        Integer sets = session.getTotalSets() != null ? session.getTotalSets() : 0;
        Integer duration = session.getDurationMins() != null ? session.getDurationMins() : 0;

        drawStatBox(g2d, dmSans, limeGreen, lightGray,
                volume.intValue(), "KG VOL", 120, 680);
        drawStatBox(g2d, dmSans, limeGreen, lightGray,
                sets, "SETS", 390, 680);
        drawStatBox(g2d, dmSans, limeGreen, lightGray,
                duration, "MIN", 660, 680);

        // d. Streak line — y: ~940px
        Integer streak = user.getStreak() != null ? user.getStreak() : 0;
        String streakText = "🔥 Day " + streak + " streak";
        drawCenteredText(g2d, streakText,
                dmSans, 36, Font.PLAIN, limeGreen, 940);

        // e. "WYNNERS" watermark — y: ~1050px
        drawCenteredText(g2d, "W Y N N E R S",
                dmSans, 28, Font.PLAIN, new Color(255, 255, 255, 63), 1050);
    }

    private Font loadDmSansFont() {
        try {
            // Try to load DM Sans from resources
            return Font.createFont(Font.TRUETYPE_FONT,
                    getClass().getResourceAsStream("/fonts/DMSans-Bold.ttf"))
                    .deriveFont(32f);
        } catch (Exception e) {
            log.debug("DM Sans font not found, using SansSerif fallback");
            return new Font("SansSerif", Font.BOLD, 32);
        }
    }

    private void drawCenteredText(Graphics2D g2d, String text,
                                 Font baseFont, int sizeInPixels, int style,
                                 Color color, int yPosition) {
        Font font = baseFont.deriveFont((float) sizeInPixels);
        if (style != Font.PLAIN) {
            font = font.deriveFont(style);
        }

        g2d.setFont(font);
        g2d.setColor(color);

        FontMetrics fm = g2d.getFontMetrics(font);
        int textWidth = fm.stringWidth(text);
        int x = (CARD_WIDTH - textWidth) / 2;

        g2d.drawString(text, x, yPosition);
    }

    private void drawStatBox(Graphics2D g2d, Font baseFont,
                            Color numberColor, Color labelColor,
                            int value, String label, int xPosition, int yPosition) {
        int boxWidth = 300;
        int boxHeight = 180;
        int cornerRadius = 20;

        // Draw box background with transparency
        g2d.setColor(new Color(255, 255, 255, 25));
        RoundRectangle2D.Double rect = new RoundRectangle2D.Double(
                xPosition, yPosition, boxWidth, boxHeight, cornerRadius, cornerRadius);
        g2d.fill(rect);

        // Draw box border
        g2d.setColor(new Color(255, 255, 255, 20));
        g2d.setStroke(new BasicStroke(1f));
        g2d.draw(rect);

        // Draw stat number
        Font numberFont = baseFont.deriveFont(56f).deriveFont(Font.BOLD);
        g2d.setFont(numberFont);
        g2d.setColor(numberColor);
        FontMetrics fm = g2d.getFontMetrics(numberFont);
        String valueStr = String.valueOf(value);
        int numberX = xPosition + (boxWidth - fm.stringWidth(valueStr)) / 2;
        int numberY = yPosition + 110;
        g2d.drawString(valueStr, numberX, numberY);

        // Draw label below
        Font labelFont = baseFont.deriveFont(24f).deriveFont(Font.PLAIN);
        g2d.setFont(labelFont);
        g2d.setColor(labelColor);
        fm = g2d.getFontMetrics(labelFont);
        int labelX = xPosition + (boxWidth - fm.stringWidth(label)) / 2;
        int labelY = numberY + 35;
        g2d.drawString(label, labelX, labelY);
    }
}
