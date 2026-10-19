package claims;

import static claims.TestRunner.assertEquals;
import static claims.TestRunner.log;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

final class CsvIOTest {

    @TestCase(id = "CSV-01", title = "Parse quoted fields containing commas and escaped quotes",
            steps = "Parse CSV text (CRLF line ending):\na,b,c\n\"15,000\",\"say \"\"hi\"\"\",\"March 15, 2024\"",
            expected = "2 rows; row 2 = [15,000] [say \"hi\"] [March 15, 2024]")
    static void testParsesQuotedCommasAndEscapedQuotes() {
        String input = "a,b,c\r\n\"15,000\",\"say \"\"hi\"\"\",\"March 15, 2024\"\n";
        List<List<String>> rows = CsvIO.parse(input);
        log("input  : %s", visible(input));
        log("rows   : %d", rows.size());
        log("row 2  : %s", rows.get(1));
        assertEquals(2, rows.size());
        assertEquals(List.of("15,000", "say \"hi\"", "March 15, 2024"), rows.get(1));
    }

    @TestCase(id = "CSV-02", title = "Keep empty fields and skip blank lines",
            steps = "Parse CSV text: a,b,c\\n,,x\\n\\n",
            expected = "2 rows; row 2 = ['', '', 'x'] (empty fields kept, trailing blank line skipped)")
    static void testKeepsEmptyFieldsAndSkipsBlankLines() {
        String input = "a,b,c\n,,x\n\n";
        List<List<String>> rows = CsvIO.parse(input);
        log("input  : %s", visible(input));
        log("rows   : %d", rows.size());
        log("row 2  : %s (size %d)", rows.get(1), rows.get(1).size());
        assertEquals(List.of("", "", "x"), rows.get(1));
        assertEquals(2, rows.size());
    }

    @TestCase(id = "CSV-03", title = "Write then read back returns identical values",
            steps = "1. Write row [15,000 | ' padded ' | line<newline>break] to a temp CSV\n2. Read the file back",
            expected = "Values read back == values written (quoting/escaping is lossless)")
    static void testWriteThenReadRoundTrip() throws Exception {
        Path tmp = Files.createTempFile("csvio", ".csv");
        List<List<String>> data = List.of(List.of("15,000", " padded ", "line\nbreak"));
        CsvIO.write(tmp, List.of("x", "y", "z"), data);
        List<List<String>> read = CsvIO.read(tmp).subList(1, 2);
        log("written: %s", visible(data.toString()));
        log("file   : %s", visible(Files.readString(tmp)));
        log("read   : %s", visible(read.toString()));
        assertEquals(data, read);
        Files.delete(tmp);
    }

    private static String visible(String s) {
        return s.replace("\r", "\\r").replace("\n", "\\n");
    }
}
