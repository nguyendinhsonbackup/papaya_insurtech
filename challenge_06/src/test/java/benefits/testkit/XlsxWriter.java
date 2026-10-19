package benefits.testkit;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Minimal .xlsx writer (Office Open XML = zip of XML parts) — no dependencies.
 * Supports inline strings, numbers, a fixed set of styles, column widths, row heights,
 * single-row merges, internal hyperlinks, a frozen header row and PNG images.
 * Rows and columns are 1-based, as in Excel.
 */
public final class XlsxWriter {

    /** Order must match the {@code <cellXfs>} entries in {@link #styles()}. */
    public enum Style { DEFAULT, HEADER, CELL, CENTER, PASS, FAIL, ERROR, TITLE, LABEL, LINK, SECTION, DECIMAL, PERCENT }

    private static final int PX_PER_ROW = 20; // default row height 15pt at 96 dpi
    private static final long EMU_PER_PX = 9525;

    private static final String XML = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n";
    private static final String NS_MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
    private static final String NS_R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final String NS_PKG_REL = "http://schemas.openxmlformats.org/package/2006/relationships";
    private static final String REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/";

    private record Cell(Object value, Style style) {
    }

    private record Image(int row, int col, byte[] png, int widthPx, int heightPx) {
    }

    private record Link(int row, int col, String location, String display) {
    }

    public static final class Sheet {
        private final String name;
        private final TreeMap<Integer, TreeMap<Integer, Cell>> rows = new TreeMap<>();
        private final Map<Integer, Double> heights = new HashMap<>();
        private final TreeMap<Integer, Double> widths = new TreeMap<>();
        private final List<String> merges = new ArrayList<>();
        private final List<Link> links = new ArrayList<>();
        private final List<Image> images = new ArrayList<>();
        private boolean freezeTopRow;

        private Sheet(String name) {
            this.name = name;
        }

        public Sheet cell(int row, int col, Object value, Style style) {
            rows.computeIfAbsent(row, r -> new TreeMap<>()).put(col, new Cell(value, style));
            return this;
        }

        /** Writes the value in {@code fromCol} and merges it with the cells up to {@code toCol} (same style). */
        public Sheet mergedCell(int row, int fromCol, int toCol, Object value, Style style) {
            cell(row, fromCol, value, style);
            for (int c = fromCol + 1; c <= toCol; c++) {
                cell(row, c, null, style);
            }
            merges.add(ref(row, fromCol) + ":" + ref(row, toCol));
            return this;
        }

        public Sheet width(int col, double characters) {
            widths.put(col, characters);
            return this;
        }

        public Sheet height(int row, double points) {
            heights.put(row, points);
            return this;
        }

        /** Hyperlink to a location inside the workbook, e.g. {@code 'Evidence'!A12}. */
        public Sheet link(int row, int col, String location) {
            Cell c = rows.getOrDefault(row, new TreeMap<>()).get(col);
            links.add(new Link(row, col, location, c == null || c.value() == null ? location : c.value().toString()));
            return this;
        }

        /** PNG anchored at the top-left of the cell, shown at its native pixel size. */
        public Sheet image(int row, int col, byte[] png, int widthPx, int heightPx) {
            images.add(new Image(row, col, png, widthPx, heightPx));
            return this;
        }

        public Sheet freezeTopRow() {
            freezeTopRow = true;
            return this;
        }
    }

    private final List<Sheet> sheets = new ArrayList<>();

    public Sheet sheet(String name) {
        Sheet s = new Sheet(name);
        sheets.add(s);
        return s;
    }

    /** Number of default-height rows covered by an image of the given height. */
    public static int rowsFor(int heightPx) {
        return (int) Math.ceil(heightPx / (double) PX_PER_ROW);
    }

    public void write(Path path) throws IOException {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        List<Integer> sheetsWithImages = new ArrayList<>();
        for (int i = 0; i < sheets.size(); i++) {
            if (!sheets.get(i).images.isEmpty()) {
                sheetsWithImages.add(i);
            }
        }
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path))) {
            put(zip, "[Content_Types].xml", contentTypes(sheetsWithImages.size()));
            put(zip, "_rels/.rels", XML + "<Relationships xmlns=\"" + NS_PKG_REL + "\">"
                    + rel("rId1", "officeDocument", "xl/workbook.xml") + "</Relationships>");
            put(zip, "xl/workbook.xml", workbook());
            put(zip, "xl/_rels/workbook.xml.rels", workbookRels());
            put(zip, "xl/styles.xml", styles());

            int imageNo = 0;
            for (int i = 0; i < sheets.size(); i++) {
                Sheet s = sheets.get(i);
                int drawingNo = sheetsWithImages.indexOf(i) + 1; // 0 = sheet không có ảnh
                put(zip, "xl/worksheets/sheet" + (i + 1) + ".xml", sheetXml(s, i == 0, drawingNo > 0));
                if (drawingNo == 0) {
                    continue;
                }
                put(zip, "xl/worksheets/_rels/sheet" + (i + 1) + ".xml.rels", XML + "<Relationships xmlns=\""
                        + NS_PKG_REL + "\">" + rel("rId1", "drawing", "../drawings/drawing" + drawingNo + ".xml")
                        + "</Relationships>");
                StringBuilder drawingRels = new StringBuilder(XML + "<Relationships xmlns=\"" + NS_PKG_REL + "\">");
                for (int k = 0; k < s.images.size(); k++) {
                    imageNo++;
                    putBytes(zip, "xl/media/image" + imageNo + ".png", s.images.get(k).png());
                    drawingRels.append(rel("rId" + (k + 1), "image", "../media/image" + imageNo + ".png"));
                }
                put(zip, "xl/drawings/_rels/drawing" + drawingNo + ".xml.rels", drawingRels + "</Relationships>");
                put(zip, "xl/drawings/drawing" + drawingNo + ".xml", drawingXml(s.images));
            }
        }
    }

    // ---- parts ----

    private String contentTypes(int drawings) {
        StringBuilder x = new StringBuilder(XML)
                .append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
                .append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
                .append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
                .append("<Default Extension=\"png\" ContentType=\"image/png\"/>")
                .append(override("/xl/workbook.xml", "spreadsheetml.sheet.main+xml"))
                .append(override("/xl/styles.xml", "spreadsheetml.styles+xml"));
        for (int i = 1; i <= sheets.size(); i++) {
            x.append(override("/xl/worksheets/sheet" + i + ".xml", "spreadsheetml.worksheet+xml"));
        }
        for (int i = 1; i <= drawings; i++) {
            x.append(override("/xl/drawings/drawing" + i + ".xml", "drawing+xml"));
        }
        return x.append("</Types>").toString();
    }

    private String workbook() {
        StringBuilder x = new StringBuilder(XML).append("<workbook xmlns=\"").append(NS_MAIN)
                .append("\" xmlns:r=\"").append(NS_R).append("\"><bookViews><workbookView/></bookViews><sheets>");
        for (int i = 0; i < sheets.size(); i++) {
            x.append("<sheet name=\"").append(esc(sheets.get(i).name)).append("\" sheetId=\"").append(i + 1)
                    .append("\" r:id=\"rId").append(i + 1).append("\"/>");
        }
        return x.append("</sheets></workbook>").toString();
    }

    private String workbookRels() {
        StringBuilder x = new StringBuilder(XML).append("<Relationships xmlns=\"").append(NS_PKG_REL).append("\">");
        for (int i = 0; i < sheets.size(); i++) {
            x.append(rel("rId" + (i + 1), "worksheet", "worksheets/sheet" + (i + 1) + ".xml"));
        }
        x.append(rel("rId" + (sheets.size() + 1), "styles", "styles.xml"));
        return x.append("</Relationships>").toString();
    }

    private static String sheetXml(Sheet s, boolean firstSheet, boolean hasDrawing) {
        StringBuilder x = new StringBuilder(XML).append("<worksheet xmlns=\"").append(NS_MAIN)
                .append("\" xmlns:r=\"").append(NS_R).append("\">");
        x.append("<sheetViews><sheetView workbookViewId=\"0\"").append(firstSheet ? " tabSelected=\"1\"" : "").append(">");
        if (s.freezeTopRow) {
            x.append("<pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/>")
                    .append("<selection pane=\"bottomLeft\" activeCell=\"A2\" sqref=\"A2\"/>");
        }
        x.append("</sheetView></sheetViews><sheetFormatPr defaultRowHeight=\"15\"/>");

        if (!s.widths.isEmpty()) {
            x.append("<cols>");
            s.widths.forEach((col, w) -> x.append("<col min=\"").append(col).append("\" max=\"").append(col)
                    .append("\" width=\"").append(w).append("\" customWidth=\"1\"/>"));
            x.append("</cols>");
        }

        x.append("<sheetData>");
        TreeSet<Integer> rowNumbers = new TreeSet<>(s.rows.keySet());
        rowNumbers.addAll(s.heights.keySet());
        for (int r : rowNumbers) {
            x.append("<row r=\"").append(r).append("\"");
            Double ht = s.heights.get(r);
            if (ht != null) {
                x.append(" ht=\"").append(ht).append("\" customHeight=\"1\"");
            }
            x.append(">");
            s.rows.getOrDefault(r, new TreeMap<>()).forEach((c, cell) -> x.append(cellXml(r, c, cell)));
            x.append("</row>");
        }
        x.append("</sheetData>");

        if (!s.merges.isEmpty()) {
            x.append("<mergeCells count=\"").append(s.merges.size()).append("\">");
            s.merges.forEach(m -> x.append("<mergeCell ref=\"").append(m).append("\"/>"));
            x.append("</mergeCells>");
        }
        if (!s.links.isEmpty()) {
            x.append("<hyperlinks>");
            s.links.forEach(l -> x.append("<hyperlink ref=\"").append(ref(l.row(), l.col())).append("\" location=\"")
                    .append(esc(l.location())).append("\" display=\"").append(esc(l.display())).append("\"/>"));
            x.append("</hyperlinks>");
        }
        x.append("<pageMargins left=\"0.7\" right=\"0.7\" top=\"0.75\" bottom=\"0.75\" header=\"0.3\" footer=\"0.3\"/>");
        if (hasDrawing) {
            x.append("<drawing r:id=\"rId1\"/>");
        }
        return x.append("</worksheet>").toString();
    }

    private static String cellXml(int row, int col, Cell cell) {
        String head = "<c r=\"" + ref(row, col) + "\" s=\"" + cell.style().ordinal() + "\"";
        Object v = cell.value();
        if (v == null) {
            return head + "/>";
        }
        if (v instanceof Number n) {
            return head + "><v>" + BigDecimal.valueOf(n.doubleValue()).toPlainString() + "</v></c>";
        }
        return head + " t=\"inlineStr\"><is><t xml:space=\"preserve\">" + esc(v.toString()) + "</t></is></c>";
    }

    private static String drawingXml(List<Image> images) {
        StringBuilder x = new StringBuilder(XML)
                .append("<xdr:wsDr xmlns:xdr=\"http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing\"")
                .append(" xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" xmlns:r=\"").append(NS_R)
                .append("\">");
        for (int k = 0; k < images.size(); k++) {
            Image im = images.get(k);
            long cx = im.widthPx() * EMU_PER_PX;
            long cy = im.heightPx() * EMU_PER_PX;
            x.append("<xdr:oneCellAnchor><xdr:from><xdr:col>").append(im.col() - 1)
                    .append("</xdr:col><xdr:colOff>0</xdr:colOff><xdr:row>").append(im.row() - 1)
                    .append("</xdr:row><xdr:rowOff>0</xdr:rowOff></xdr:from>")
                    .append("<xdr:ext cx=\"").append(cx).append("\" cy=\"").append(cy).append("\"/>")
                    .append("<xdr:pic><xdr:nvPicPr><xdr:cNvPr id=\"").append(k + 2).append("\" name=\"Evidence ")
                    .append(k + 1).append("\"/><xdr:cNvPicPr><a:picLocks noChangeAspect=\"1\"/></xdr:cNvPicPr>")
                    .append("</xdr:nvPicPr><xdr:blipFill><a:blip r:embed=\"rId").append(k + 1)
                    .append("\"/><a:stretch><a:fillRect/></a:stretch></xdr:blipFill>")
                    .append("<xdr:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"").append(cx).append("\" cy=\"")
                    .append(cy).append("\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></xdr:spPr>")
                    .append("</xdr:pic><xdr:clientData/></xdr:oneCellAnchor>");
        }
        return x.append("</xdr:wsDr>").toString();
    }

    private static String styles() {
        String fonts = "<fonts count=\"9\">"
                + font("", "")                              // 0 normal
                + font("<b/>", "")                          // 1 bold
                + font("<b/>", "FFFFFFFF")                  // 2 header
                + "<font><b/><sz val=\"16\"/><color rgb=\"FF1F4E78\"/><name val=\"Calibri\"/><family val=\"2\"/></font>" // 3 title
                + font("<b/>", "FF006100")                  // 4 pass
                + font("<b/>", "FF9C0006")                  // 5 fail
                + font("<b/>", "FF9C5700")                  // 6 error
                + font("<u/>", "FF0563C1")                  // 7 link
                + font("<b/>", "FF1F4E78")                  // 8 section
                + "</fonts>";
        String fills = "<fills count=\"7\"><fill><patternFill patternType=\"none\"/></fill>"
                + "<fill><patternFill patternType=\"gray125\"/></fill>"
                + fill("FF1F4E78") + fill("FFC6EFCE") + fill("FFFFC7CE") + fill("FFFFEB9C") + fill("FFDDEBF7")
                + "</fills>";
        String thin = "<left style=\"thin\"><color rgb=\"FFBFBFBF\"/></left><right style=\"thin\"><color rgb=\"FFBFBFBF\"/></right>"
                + "<top style=\"thin\"><color rgb=\"FFBFBFBF\"/></top><bottom style=\"thin\"><color rgb=\"FFBFBFBF\"/></bottom>";
        String borders = "<borders count=\"2\"><border><left/><right/><top/><bottom/><diagonal/></border>"
                + "<border>" + thin + "<diagonal/></border></borders>";
        String top = "<alignment vertical=\"top\" wrapText=\"1\"/>";
        String center = "<alignment horizontal=\"center\" vertical=\"top\" wrapText=\"1\"/>";
        String cellXfs = "<cellXfs count=\"13\">"
                + xf(0, 0, 0, 0, "")                                                            // DEFAULT
                + xf(0, 2, 2, 1, "<alignment horizontal=\"center\" vertical=\"center\" wrapText=\"1\"/>") // HEADER
                + xf(0, 0, 0, 1, top)                                                           // CELL
                + xf(0, 0, 0, 1, center)                                                        // CENTER
                + xf(0, 4, 3, 1, center)                                                        // PASS
                + xf(0, 5, 4, 1, center)                                                        // FAIL
                + xf(0, 6, 5, 1, center)                                                        // ERROR
                + xf(0, 3, 0, 0, "<alignment vertical=\"center\"/>")                            // TITLE
                + xf(0, 1, 6, 1, top)                                                           // LABEL
                + xf(0, 7, 0, 1, top)                                                           // LINK
                + xf(0, 8, 6, 0, "<alignment vertical=\"center\"/>")                            // SECTION
                + xf(2, 0, 0, 1, center)                                                        // DECIMAL 0.00
                + xf(10, 0, 0, 1, center)                                                       // PERCENT 0.00%
                + "</cellXfs>";
        return XML + "<styleSheet xmlns=\"" + NS_MAIN + "\">" + fonts + fills + borders
                + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
                + cellXfs + "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>"
                + "</styleSheet>";
    }

    // ---- helpers ----

    private static String font(String decoration, String argb) {
        return "<font>" + decoration + "<sz val=\"11\"/>" + (argb.isEmpty() ? "" : "<color rgb=\"" + argb + "\"/>")
                + "<name val=\"Calibri\"/><family val=\"2\"/></font>";
    }

    private static String fill(String argb) {
        return "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"" + argb + "\"/><bgColor indexed=\"64\"/></patternFill></fill>";
    }

    private static String xf(int numFmt, int font, int fill, int border, String alignment) {
        return "<xf numFmtId=\"" + numFmt + "\" fontId=\"" + font + "\" fillId=\"" + fill + "\" borderId=\"" + border
                + "\" xfId=\"0\" applyNumberFormat=\"1\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\""
                + " applyAlignment=\"1\">" + alignment + "</xf>";
    }

    private static String override(String part, String type) {
        return "<Override PartName=\"" + part + "\" ContentType=\"application/vnd.openxmlformats-officedocument."
                + type + "\"/>";
    }

    private static String rel(String id, String type, String target) {
        return "<Relationship Id=\"" + id + "\" Type=\"" + REL + type + "\" Target=\"" + target + "\"/>";
    }

    static String ref(int row, int col) {
        StringBuilder letters = new StringBuilder();
        for (int c = col; c > 0; c = (c - 1) / 26) {
            letters.insert(0, (char) ('A' + (c - 1) % 26));
        }
        return letters.toString() + row;
    }

    /** XML-escapes text and drops characters that are illegal in XML 1.0. */
    static String esc(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (char ch : s.replace("\r\n", "\n").toCharArray()) {
            switch (ch) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                default -> {
                    if (ch >= 0x20 || ch == '\n' || ch == '\t') {
                        sb.append(ch);
                    }
                }
            }
        }
        return sb.toString();
    }

    private static void put(ZipOutputStream zip, String name, String content) throws IOException {
        putBytes(zip, name, content.getBytes(StandardCharsets.UTF_8));
    }

    private static void putBytes(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(bytes);
        zip.closeEntry();
    }
}
