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
    private static final int RIGHT_MARGIN = 72;
    private static final int RIGHT_X = CARD_WIDTH - RIGHT_MARGIN;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public byte[] generateShareImage(
            WorkoutSession session,
            User user,
            MultipartFile photoFile) throws IOException {

        log.info("generateShareImage: sessionId={} hasPhoto={} photoSize={}",
                session.getId(),
                (photoFile != null && !photoFile.isEmpty()),
                (photoFile != null ? photoFile.getSize() : 0));

        BufferedImage card = new BufferedImage(CARD_WIDTH, CARD_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = card.createGraphics();

        try {
            // Enable anti-aliasing
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            // 1. Draw gradient background
            drawGradientBackground(g2d);

            // 2. Draw user photo if provided
            boolean hasPhoto = photoFile != null && !photoFile.isEmpty();
            if (hasPhoto) {
                drawUserPhoto(g2d, photoFile);
                // 3. Draw right-side gradient overlay for text readability
                drawRightSideGradient(g2d);
            }

            // 4. Draw text content on right side
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
            log.info("Photo format: contentType={}", photoFile.getContentType());

            BufferedImage photo = ImageIO.read(photoFile.getInputStream());

            if (photo == null) {
                log.error("ImageIO.read returned null — unsupported format? contentType={}", photoFile.getContentType());
                return;
            }

            log.info("Photo loaded: {}x{}", photo.getWidth(), photo.getHeight());

            // Scale to cover 1080x1920, maintaining aspect ratio
            BufferedImage scaledPhoto = scalePhotoCover(photo, CARD_WIDTH, CARD_HEIGHT);
            g2d.drawImage(scaledPhoto, 0, 0, null);

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

    private void drawRightSideGradient(Graphics2D g2d) {
        // Dark gradient on right side for text readability — darker and wider
        Color rightDark = new Color(0, 0, 0, 191);   // rgba(0,0,0,0.75)
        Color middle = new Color(0, 0, 0, 0);        // rgba(0,0,0,0.0)

        Paint gradient = new java.awt.GradientPaint(
                CARD_WIDTH, 0, rightDark,
                (float) (CARD_WIDTH * 0.40), 0, middle);
        g2d.setPaint(gradient);
        g2d.fillRect(0, 0, CARD_WIDTH, CARD_HEIGHT);
    }

    private void drawTextContent(Graphics2D g2d, WorkoutSession session, User user) {
        Font baseFont = loadDmSansFont();
        Color white = Color.WHITE;
        Color labelEmerald = new Color(93, 202, 165);      // Lighter emerald #5DCAA5
        Color wynnersWhite = new Color(255, 255, 255, 230); // Nearly full white

        // Extract stat values
        Integer duration = session.getDurationMins() != null ? session.getDurationMins() : 0;
        Integer sets = session.getTotalSets() != null ? session.getTotalSets() : 0;
        BigDecimal volume = session.getTotalVolumeKg() != null ? session.getTotalVolumeKg() : BigDecimal.ZERO;

        // Vertically centered layout around y = 960

        // y ~820: "WYNNERS" — 28px, letter-spacing 10, nearly full white, bold, right-aligned
        drawRightAlignedSpacedText(g2d, "W Y N N E R S", baseFont, 28, Font.BOLD, wynnersWhite, RIGHT_X, 820, 10);

        // y ~850: thin line — 60px wide, 2px tall, right edge at RIGHT_X, emerald
        drawRightAlignedLine(g2d, 850, 60, 2, labelEmerald);

        // Volume section
        // y ~910: "VOLUME" label — 28px, lighter emerald #5DCAA5, letter-spacing 6, right-aligned
        drawRightAlignedSpacedText(g2d, "VOLUME", baseFont, 28, Font.PLAIN, labelEmerald, RIGHT_X, 910, 6);
        // y ~960: "360 kg" value — 48px, white, right-aligned
        drawRightAlignedText(g2d, volume.intValue() + " kg", baseFont, 48, Font.PLAIN, white, RIGHT_X, 960);

        // Sets section
        // y ~1030: "SETS" label — 28px, lighter emerald #5DCAA5, letter-spacing 6, right-aligned
        drawRightAlignedSpacedText(g2d, "SETS", baseFont, 28, Font.PLAIN, labelEmerald, RIGHT_X, 1030, 6);
        // y ~1080: "3" value — 48px, white, right-aligned
        drawRightAlignedText(g2d, String.valueOf(sets), baseFont, 48, Font.PLAIN, white, RIGHT_X, 1080);

        // Time section
        // y ~1150: "TIME" label — 28px, lighter emerald #5DCAA5, letter-spacing 6, right-aligned
        drawRightAlignedSpacedText(g2d, "TIME", baseFont, 28, Font.PLAIN, labelEmerald, RIGHT_X, 1150, 6);
        // y ~1200: "45 min" value — 48px, white, right-aligned
        drawRightAlignedText(g2d, duration + " min", baseFont, 48, Font.PLAIN, white, RIGHT_X, 1200);
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

    private void drawRightAlignedText(Graphics2D g2d, String text, Font baseFont,
                                     int sizeInPixels, int style, Color color, int rightX, int y) {
        Font font = baseFont.deriveFont((float) sizeInPixels);
        if (style != Font.PLAIN) {
            font = font.deriveFont(style);
        }

        g2d.setFont(font);
        g2d.setColor(color);

        FontMetrics fm = g2d.getFontMetrics(font);
        int textWidth = fm.stringWidth(text);
        int x = rightX - textWidth;

        g2d.drawString(text, x, y);
    }

    private void drawRightAlignedSpacedText(Graphics2D g2d, String text, Font baseFont,
                                           int sizeInPixels, int style, Color color,
                                           int rightX, int y, int letterSpacing) {
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

        // Start from right and draw leftward
        int x = rightX - totalWidth;

        // Draw each character with spacing
        for (char c : text.toCharArray()) {
            g2d.drawString(String.valueOf(c), x, y);
            x += fm.charWidth(c) + letterSpacing;
        }
    }

    private void drawRightAlignedLine(Graphics2D g2d, int yPosition, int lineWidth,
                                     int lineHeight, Color color) {
        g2d.setColor(color);
        int x = RIGHT_X - lineWidth;
        g2d.fillRect(x, yPosition - lineHeight / 2, lineWidth, lineHeight);
    }
}
