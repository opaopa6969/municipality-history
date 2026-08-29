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

    // ---- isAbolished() ----

    /** 既存3パターン（編入・政令・廃止）は従来通り true を返す。 */
    @Test
    void isAbolished_編入パターン() {
        // 01232 亀田市: 亀田市(01232)が函館市(01202)に編入（廃止側）
        MunicipalityChange c = new MunicipalityChange(
                "01232", "北海道", "", "", "亀田市", "かめだし",
                LocalDate.of(1973, 12, 1), "亀田市(01232)が函館市(01202)に編入");
        assertTrue(c.isAbolished());
    }

    @Test
    void isAbolished_直接廃止パターン() {
        MunicipalityChange c = new MunicipalityChange(
                "04560", "宮城県", "", "", "桃生郡", "もものぐん",
                LocalDate.of(2005, 4, 1), "桃生郡(04560)の廃止");
        assertTrue(c.isAbolished());
    }

    /** 合併・新設で廃止側: 末尾が (別コード)を新設 なら true。(#12) */
    @Test
    void isAbolished_合併新設_廃止側_括弧コードあり() {
        // 01335 上磯町: 上磯町(01335)、大野町(01336)が合併し、北斗市(01236)を新設
        MunicipalityChange c = new MunicipalityChange(
                "01335", "北海道", "渡島支庁", "おしましちょう", "上磯町", "かみいそちょう",
                LocalDate.of(2006, 2, 1),
                "上磯町(01335)、大野町(01336)が合併し、北斗市(01236)を新設");
        assertTrue(c.isAbolished(), "合併・新設の廃止側（括弧コードあり）は廃止のはず");
    }

    /** 合併・新設で新設側: 末尾が (自身のコード)を新設 なら false。(#12 退行防止) */
    @Test
    void isAbolished_合併新設_新設側_括弧コードあり() {
        // 01236 北斗市: 新設側
        MunicipalityChange c = new MunicipalityChange(
                "01236", "北海道", "", "", "北斗市", "ほくとし",
                LocalDate.of(2006, 2, 1),
                "上磯町(01335)、大野町(01336)が合併し、北斗市(01236)を新設");
        assertFalse(c.isAbolished(), "合併・新設の新設側（括弧コードあり）は存続のはず");
    }

    /** 合併・新設で存続（名前一致）: 末尾が Xを新設（括弧コードなし）で X=自身のmunicipality なら false。(#12 退行防止) */
    @Test
    void isAbolished_合併新設_存続側_名前一致() {
        // 01206 釧路市: 釧路市を新設（同一名称を継承した存続）
        MunicipalityChange c = new MunicipalityChange(
                "01206", "北海道", "", "", "釧路市", "くしろし",
                LocalDate.of(2005, 10, 11),
                "釧路市(01206)、阿寒町(01666)、音別町(01669)が合併し、釧路市を新設");
        assertFalse(c.isAbolished(), "合併・新設の存続側（名前一致）は廃止でないはず");
    }

    /** 合併・新設で廃止側: 末尾が Xを新設（括弧コードなし）で X≠自身のmunicipality なら true。(#12) */
    @Test
    void isAbolished_合併新設_廃止側_名前不一致() {
        // 01344 砂原町: 砂原町(01344)、森町(01345)が合併し、森町を新設（森町は存続、砂原町は廃止）
        MunicipalityChange c = new MunicipalityChange(
                "01344", "北海道", "渡島支庁", "おしましちょう", "砂原町", "さわらちょう",
                LocalDate.of(2005, 4, 1),
                "砂原町(01344)、森町(01345)が合併し、森町を新設");
        assertTrue(c.isAbolished(), "合併・新設の廃止側（名前不一致）は廃止のはず");
    }

    /** 合併・新設で存続側（名前一致）: 上記の対になる存続側。(#12 退行防止) */
    @Test
    void isAbolished_合併新設_存続側_名前一致_森町() {
        // 01345 森町: 森町を新設（同一名称で存続）
        MunicipalityChange c = new MunicipalityChange(
                "01345", "北海道", "渡島支庁", "おしましちょう", "森町", "もりまち",
                LocalDate.of(2005, 4, 1),
                "砂原町(01344)、森町(01345)が合併し、森町を新設");
        assertFalse(c.isAbolished(), "合併・新設の存続側（名前一致・森町）は廃止でないはず");
    }

    /** 合併・新設が複数行にまたぐ（末尾に「郡の廃止」が続く）reason でも、
     *  最初の「を新設」行で新設側判定が正しく動くこと。(#12 実データ対応) */
    @Test
    void isAbolished_合併新設_複数行reason_新設側() {
        // 19213 甲州市: 複数行 reason で新設側
        MunicipalityChange c = new MunicipalityChange(
                "19213", "山梨県", "", "", "甲州市", "こうしゅうし",
                LocalDate.of(2005, 11, 1),
                "塩山市(19203)、勝沼町(19304)、大和村(19305)が合併し、甲州市(19213)を新設\n東山梨郡(19300)の廃止");
        assertFalse(c.isAbolished(), "複数行 reason でも新設側（19213）は存続のはず");
    }

    @Test
    void isAbolished_合併新設_複数行reason_廃止側() {
        // 19203 塩山市: 上記の廃止側
        MunicipalityChange c = new MunicipalityChange(
                "19203", "山梨県", "", "", "塩山市", "しおやまし",
                LocalDate.of(2005, 11, 1),
                "塩山市(19203)、勝沼町(19304)、大和村(19305)が合併し、甲州市(19213)を新設\n東山梨郡(19300)の廃止");
        assertTrue(c.isAbolished(), "複数行 reason でも廃止側（19203）は廃止のはず");
    }

    /** 合併・新設に該当しない reason は false。 */
    @Test
    void isAbolished_無関係なreason() {
        MunicipalityChange c = new MunicipalityChange(
                "01100", "北海道", "札幌市", "さっぽろし", "中央区", "ちゅうおうく",
                LocalDate.of(1972, 4, 1), "政令指定都市施行");
        assertFalse(c.isAbolished());
    }
}
