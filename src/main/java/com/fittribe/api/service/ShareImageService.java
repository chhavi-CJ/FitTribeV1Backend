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
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;

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
        // Multi-stop gradient overlay fading from top to bottom
        // Top: rgba(0,0,0,0.05) → Middle: rgba(0,0,0,0.0) → 55%: rgba(0,0,0,0.25)
        // → 85%: rgba(0,0,0,0.6) → Bottom: rgba(0,0,0,0.7)

        Color[] colors = {
                new Color(0, 0, 0, 13),    // 0% - rgba(0,0,0,0.05)
                new Color(0, 0, 0, 0),    // 25% - rgba(0,0,0,0.0)
                new Color(0, 0, 0, 64),   // 55% - rgba(0,0,0,0.25)
                new Color(0, 0, 0, 153),  // 85% - rgba(0,0,0,0.6)
                new Color(0, 0, 0, 179)   // 100% - rgba(0,0,0,0.7)
        };

        float[] stops = {0.0f, 0.25f, 0.55f, 0.85f, 1.0f};

        Paint overlayGradient = new LinearGradientPaint(
                0, 0,
                0, CARD_HEIGHT,
                stops, colors,
                MultipleGradientPaint.CycleMethod.NO_CYCLE);

        g2d.setPaint(overlayGradient);
        g2d.fillRect(0, 0, CARD_WIDTH, CARD_HEIGHT);
    }

    private void drawTextContent(Graphics2D g2d, WorkoutSession session, User user) {
        Font baseFont = loadDmSansFont();
        Color white = Color.WHITE;
        Color emerald = new Color(0x1D, 0x9E, 0x75);
        Color veryTransparentWhite = new Color(255, 255, 255, 89);

        // Extract stat values
        Integer duration = session.getDurationMins() != null ? session.getDurationMins() : 0;
        Integer sets = session.getTotalSets() != null ? session.getTotalSets() : 0;
        BigDecimal volume = session.getTotalVolumeKg() != null ? session.getTotalVolumeKg() : BigDecimal.ZERO;

        // y ~1650: "45 min" (48px, white)
        drawCenteredText(g2d, duration + " min", baseFont, 48, Font.PLAIN, white, 1650);

        // y ~1610: "Time" (28px, emerald, letter-spaced)
        drawSpacedText(g2d, "Time", baseFont, 28, Font.PLAIN, emerald, 1610, 6);

        // y ~1560: "3" (48px, white)
        drawCenteredText(g2d, String.valueOf(sets), baseFont, 48, Font.PLAIN, white, 1560);

        // y ~1520: "Sets" (28px, emerald, letter-spaced)
        drawSpacedText(g2d, "Sets", baseFont, 28, Font.PLAIN, emerald, 1520, 6);

        // y ~1470: "360 kg" (48px, white)
        drawCenteredText(g2d, volume.intValue() + " kg", baseFont, 48, Font.PLAIN, white, 1470);

        // y ~1430: "Volume" (28px, emerald, letter-spaced)
        drawSpacedText(g2d, "Volume", baseFont, 28, Font.PLAIN, emerald, 1430, 6);

        // y ~1390: thin horizontal line (80px wide, 2px tall, centered, rgba(255,255,255,51))
        drawHorizontalLine(g2d, 1390, 80, 2, new Color(255, 255, 255, 51));

        // y ~1365: "W Y N N E R S" (28px, very transparent white, letter-spaced with 10px)
        drawSpacedText(g2d, "W Y N N E R S", baseFont, 28, Font.PLAIN, veryTransparentWhite, 1365, 10);
    }

    private Font loadDmSansFont() {
        try {
            // Try to load DM Sans from resources
            return Font.createFont(Font.TRUETYPE_FONT,
                    getClass().getResourceAsStream("/fonts/DMSans-Bold.ttf"))
                    .deriveFont(32f);
        } catch (Exception e) {
            log.debug("DM Sans font not found, using DejaVu Sans fallback");
            return new Font("DejaVu Sans", Font.BOLD, 32);
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

    private void drawSpacedText(Graphics2D g2d, String text, Font baseFont,
                               int sizeInPixels, int style, Color color,
                               int yPosition, int letterSpacing) {
        Font font = baseFont.deriveFont((float) sizeInPixels);
        if (style != Font.PLAIN) {
            font = font.deriveFont(style);
        }

        g2d.setFont(font);
        g2d.setColor(color);
        FontMetrics fm = g2d.getFontMetrics();

        // Calculate total width with letter spacing
        int totalWidth = 0;
        for (char c : text.toCharArray()) {
            totalWidth += fm.charWidth(c) + letterSpacing;
        }
        totalWidth -= letterSpacing;

        // Start x position to center
        int x = (CARD_WIDTH - totalWidth) / 2;

        // Draw each character with spacing
        for (char c : text.toCharArray()) {
            g2d.drawString(String.valueOf(c), x, yPosition);
            x += fm.charWidth(c) + letterSpacing;
        }
    }

    private void drawHorizontalLine(Graphics2D g2d, int yPosition, int width,
                                   int height, Color color) {
        g2d.setColor(color);
        int x = (CARD_WIDTH - width) / 2;
        g2d.fillRect(x, yPosition - height / 2, width, height);
    }
}
