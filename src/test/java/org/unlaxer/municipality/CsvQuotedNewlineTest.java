package org.unlaxer.municipality;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 引用符内に改行を含む CSV レコードの読み込みテスト。
 * e-Stat の「改正事由」は複数行にわたるため 1行 = 1レコードではない。
 */
class CsvQuotedNewlineTest {

    private static final String HEADER =
            "\"標準地域コード\",\"都道府県\",\"政令市・郡・支庁・振興局等\",\"政令市・郡・支庁・振興局等（ふりがな）\","
                    + "\"市区町村\",\"市区町村（ふりがな）\",\"廃置分合等施行年月日\",\"改正事由\"";

    private static Path write(Path dir, String body) throws IOException {
        Path csv = dir.resolve("test.csv");
        Files.writeString(csv, HEADER + "\n" + body, StandardCharsets.UTF_8);
        return csv;
    }

    // ---- hasUnclosedQuote ----

    @Test
    void hasUnclosedQuote_detectsOpenQuote() {
        assertTrue(MunicipalityChange.hasUnclosedQuote("\"01100\",\"北海道\",\"途中で改行"));
        assertFalse(MunicipalityChange.hasUnclosedQuote("\"01100\",\"北海道\""));
    }

    @Test
    void hasUnclosedQuote_treatsDoubledQuoteAsEscape() {
        assertFalse(MunicipalityChange.hasUnclosedQuote("\"a\"\"b\""));
        assertTrue(MunicipalityChange.hasUnclosedQuote("\"a\"\"b"));
    }

    // ---- 読み込み ----

    @Test
    void quotedNewlineFormsSingleRecord(@TempDir Path dir) throws IOException {
        Path csv = write(dir, """
                "01100","北海道","札幌市","さっぽろし","","","1972-04-01","1行目の事由
                2行目の事由"
                """);
        MunicipalityHistory h = MunicipalityHistory.load(csv);
        assertEquals(1, h.size(), "引用符内改行は1レコードとして読まれる");
        assertEquals("1行目の事由\n2行目の事由", h.findByCode("01100").get(0).reason());
    }

    @Test
    void mixedRecordsAreAllRead(@TempDir Path dir) throws IOException {
        Path csv = write(dir, """
                "01100","北海道","札幌市","さっぽろし","","","1972-04-01","単一行の事由"
                "01202","北海道","","","函館市","はこだてし","2004-12-01","複数行の
                事由その1
                事由その2"
                "01203","北海道","","","小樽市","おたるし","2005-10-01","また単一行"
                """);
        MunicipalityHistory h = MunicipalityHistory.load(csv);
        assertEquals(3, h.size());
        assertTrue(h.findByCode("01202").get(0).reason().contains("\n"));
        assertFalse(h.findByCode("01203").get(0).reason().contains("\n"));
    }

    @Test
    void unclosedQuoteAtEofIsSkippedWithoutLosingEarlierRecords(@TempDir Path dir) throws IOException {
        Path csv = write(dir, """
                "01100","北海道","札幌市","さっぽろし","","","1972-04-01","正常な事由"
                "01202","北海道","","","函館市","はこだてし","2004-12-01","閉じない引用符
                """);
        MunicipalityHistory h = MunicipalityHistory.load(csv);
        assertEquals(1, h.size(), "壊れた末尾レコードだけがスキップされる");
        assertFalse(h.findByCode("01100").isEmpty());
    }

    // ---- バンドルデータの回帰防止 ----

    @Test
    void bundledCsvLoadsAllLogicalRecords() throws IOException {
        MunicipalityHistory h = MunicipalityHistory.loadBundled();
        assertEquals(3507, h.size(),
                "物理行(4491)ではなく論理レコード(3507)が読まれること。引用符内改行を取りこぼすと減る");
    }

    @Test
    void bundledCsvKeepsMultilineReason() throws IOException {
        MunicipalityHistory h = MunicipalityHistory.loadBundled();
        MunicipalityChange sapporo = h.findByCode("01100").stream()
                .filter(c -> LocalDate.of(1972, 4, 1).equals(c.effectiveDate()))
                .findFirst().orElseThrow();
        assertTrue(sapporo.reason().contains("政令指定都市施行"));
        assertTrue(sapporo.reason().contains("中央区"), "2行目以降が失われていない");
    }
}
