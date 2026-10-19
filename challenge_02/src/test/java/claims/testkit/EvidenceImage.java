package claims.testkit;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Set;

import javax.imageio.ImageIO;

/** Renders text lines as a terminal-style PNG screenshot (Java2D only, works headless). */
public final class EvidenceImage {

    public record Line(String text, Color color) {
    }

    public record Rendered(byte[] png, int width, int height) {
    }

    public static final Color TEXT = new Color(0xD4D4D4);
    public static final Color DIM = new Color(0x808080);
    public static final Color KEY = new Color(0x9CDCFE);
    public static final Color PROMPT = new Color(0xDCDCAA);
    public static final Color GREEN = new Color(0x4EC9B0);
    public static final Color RED = new Color(0xF14C4C);
    public static final Color YELLOW = new Color(0xE5C07B);

    private static final Color BACKGROUND = new Color(0x1E1E1E);
    private static final Color TITLE_BAR = new Color(0x323233);
    private static final Color[] WINDOW_DOTS = {new Color(0xFF5F56), new Color(0xFFBD2E), new Color(0x27C93F)};

    private static final int PADDING = 14;
    private static final int TITLE_BAR_HEIGHT = 28;
    private static final int MIN_WIDTH = 820;
    private static final int MAX_CHARS = 170;
    private static final Font FONT = monospacedFont(14);

    private EvidenceImage() {
    }

    public static Line line(String text) {
        return new Line(text, TEXT);
    }

    public static Line line(String text, Color color) {
        return new Line(text, color);
    }

    public static Rendered render(String windowTitle, List<Line> lines) throws IOException {
        List<Line> clipped = lines.stream().map(EvidenceImage::clip).toList();

        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        Graphics2D pg = probe.createGraphics();
        FontMetrics fm = pg.getFontMetrics(FONT);
        int lineHeight = fm.getHeight() + 2;
        int textWidth = clipped.stream().mapToInt(l -> fm.stringWidth(l.text())).max().orElse(0);
        pg.dispose();

        int width = Math.max(MIN_WIDTH, textWidth + PADDING * 2);
        int height = TITLE_BAR_HEIGHT + PADDING * 2 + clipped.size() * lineHeight;
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g.setColor(BACKGROUND);
        g.fillRect(0, 0, width, height);
        g.setColor(TITLE_BAR);
        g.fillRect(0, 0, width, TITLE_BAR_HEIGHT);
        for (int i = 0; i < WINDOW_DOTS.length; i++) {
            g.setColor(WINDOW_DOTS[i]);
            g.fillOval(PADDING + i * 20, 8, 12, 12);
        }
        Font titleFont = FONT.deriveFont(13f);
        g.setFont(titleFont);
        g.setColor(DIM);
        String title = clip(line(windowTitle)).text();
        g.drawString(title, (width - g.getFontMetrics().stringWidth(title)) / 2, 19);

        g.setFont(FONT);
        int y = TITLE_BAR_HEIGHT + PADDING + fm.getAscent();
        for (Line l : clipped) {
            g.setColor(l.color());
            g.drawString(l.text(), PADDING, y);
            y += lineHeight;
        }
        g.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return new Rendered(out.toByteArray(), width, height);
    }

    private static Line clip(Line l) {
        String text = l.text().replace("\t", "    ");
        return new Line(text.length() > MAX_CHARS ? text.substring(0, MAX_CHARS - 3) + "..." : text, l.color());
    }

    /** First installed monospaced family; falls back to the logical "Monospaced" font. */
    private static Font monospacedFont(int size) {
        Set<String> installed = Set.of(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
        for (String family : List.of("Consolas", "DejaVu Sans Mono", "Menlo", "Courier New")) {
            if (installed.contains(family)) {
                return new Font(family, Font.PLAIN, size);
            }
        }
        return new Font(Font.MONOSPACED, Font.PLAIN, size);
    }
}
