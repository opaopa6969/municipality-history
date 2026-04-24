package org.unlaxer.municipality;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MunicipalityChange record の単体テスト。
 * ファイルロード不要なスタティックファクトリとメソッドを検証する。
 */
class MunicipalityChangeTest {

    // ---- fullName() ----

    @Test
    void fullName_districtOnly_whenMunicipalityIsEmpty() {
        // municipality が空のとき district のみ返す（政令市等）
        MunicipalityChange change = new MunicipalityChange(
                "01100", "北海道", "札幌市", "さっぽろし", "", "", LocalDate.of(1972, 4, 1), "reason"
        );
        assertEquals("札幌市", change.fullName());
    }

    @Test
    void fullName_districtPlusMunicipality_whenBothPresent() {
        MunicipalityChange change = new MunicipalityChange(
                "01101", "北海道", "札幌市", "さっぽろし", "中央区", "ちゅうおうく",
                LocalDate.of(1972, 4, 1), "reason"
        );
        assertEquals("札幌市中央区", change.fullName());
    }

    @Test
    void fullName_municipalityAlone_whenDistrictIsEmpty() {
        MunicipalityChange change = new MunicipalityChange(
                "99001", "東京都", "", "", "多摩市", "たまし", LocalDate.of(1970, 1, 1), ""
        );
        // district が空なら district + municipality = "" + "多摩市" = "多摩市"
        assertEquals("多摩市", change.fullName());
    }

    // ---- fromCsvLine ----

    @Test
    void fromCsvLine_parsesWellFormedLine() {
        String line = "\"01100\",\"北海道\",\"札幌市\",\"さっぽろし\",\"\",\"\",\"1972-04-01\",\"政令指定都市施行\"";
        MunicipalityChange change = MunicipalityChange.fromCsvLine(line);
        assertNotNull(change);
        assertEquals("01100", change.lgCode());
        assertEquals("北海道", change.prefecture());
        assertEquals("札幌市", change.district());
        assertEquals(LocalDate.of(1972, 4, 1), change.effectiveDate());
        assertEquals("政令指定都市施行", change.reason());
    }

    @Test
    void fromCsvLine_returnsNullForTooFewColumns() {
        String line = "\"01100\",\"北海道\",\"札幌市\"";
        MunicipalityChange change = MunicipalityChange.fromCsvLine(line);
        assertNull(change);
    }

    @Test
    void fromCsvLine_handlesBlankDate() {
        String line = "\"99999\",\"XX\",\"TestDistrict\",\"td\",\"TestCity\",\"tc\",\"\",\"reason\"";
        MunicipalityChange change = MunicipalityChange.fromCsvLine(line);
        assertNotNull(change);
        assertNull(change.effectiveDate());
    }

    @Test
    void fromCsvLine_stripsQuotesAndWhitespace() {
        String line = "\" 01100 \",\" 北海道 \",\"札幌市\",\"さっぽろし\",\"\",\"\",\"1972-04-01\",\"reason\"";
        MunicipalityChange change = MunicipalityChange.fromCsvLine(line);
        assertNotNull(change);
        assertEquals("01100", change.lgCode());
        assertEquals("北海道", change.prefecture());
    }

    @Test
    void fromCsvLine_returnsNullForUnparsableDate() {
        String line = "\"01100\",\"北海道\",\"札幌市\",\"さっぽろし\",\"\",\"\",\"not-a-date\",\"reason\"";
        MunicipalityChange change = MunicipalityChange.fromCsvLine(line);
        assertNull(change);
    }

    // ---- record field access ----

    @Test
    void recordFields_allAccessible() {
        LocalDate date = LocalDate.of(2005, 3, 1);
        MunicipalityChange change = new MunicipalityChange(
                "12345", "東京都", "TestDistrict", "testdistrictkana",
                "TestCity", "testcitykana", date, "merge reason"
        );
        assertEquals("12345", change.lgCode());
        assertEquals("東京都", change.prefecture());
        assertEquals("TestDistrict", change.district());
        assertEquals("testdistrictkana", change.districtKana());
        assertEquals("TestCity", change.municipality());
        assertEquals("testcitykana", change.municipalityKana());
        assertEquals(date, change.effectiveDate());
        assertEquals("merge reason", change.reason());
    }
}
