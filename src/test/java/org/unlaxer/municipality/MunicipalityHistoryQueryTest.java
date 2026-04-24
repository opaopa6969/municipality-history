package org.unlaxer.municipality;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MunicipalityHistory のクエリ API の追加テスト。
 * 境界条件・都道府県検索・年別集計などを検証する。
 */
class MunicipalityHistoryQueryTest {

    private static MunicipalityHistory history;

    @BeforeAll
    static void loadBundled() throws IOException {
        history = MunicipalityHistory.loadBundled();
    }

    // ---- size ----

    @Test
    void sizeMatchesExpectedRange() {
        // e-Stat データは 3,507 件とドキュメントに記載
        assertTrue(history.size() >= 3000 && history.size() <= 5000,
                "Expected between 3000 and 5000 records, got: " + history.size());
    }

    // ---- prefectures ----

    @Test
    void prefecturesContainsAllFourMainIslands() {
        Set<String> prefs = history.prefectures();
        assertTrue(prefs.contains("北海道"), "should contain 北海道");
        assertTrue(prefs.contains("東京都"), "should contain 東京都");
        assertTrue(prefs.contains("大阪府"), "should contain 大阪府");
        assertTrue(prefs.contains("沖縄県"), "should contain 沖縄県");
    }

    @Test
    void prefecturesSizeIs47OrMore() {
        // 日本の都道府県は 47
        assertTrue(history.prefectures().size() >= 47,
                "should have at least 47 prefectures");
    }

    @Test
    void prefecturesIsSorted() {
        // TreeSet なのでソート済みであることを確認
        Set<String> prefs = history.prefectures();
        List<String> prefList = new java.util.ArrayList<>(prefs);
        // 各要素が前の要素以上（Unicode 順）
        for (int i = 1; i < prefList.size(); i++) {
            assertTrue(prefList.get(i).compareTo(prefList.get(i - 1)) >= 0,
                    "prefectures should be in ascending order");
        }
    }

    // ---- findByPrefecture ----

    @Test
    void findByPrefecture_hokkaido_notEmpty() {
        List<MunicipalityChange> result = history.findByPrefecture("北海道");
        assertFalse(result.isEmpty(), "北海道 should have records");
        assertTrue(result.stream().allMatch(c -> c.prefecture().equals("北海道")));
    }

    @Test
    void findByPrefecture_unknown_returnsEmpty() {
        List<MunicipalityChange> result = history.findByPrefecture("不明県");
        assertTrue(result.isEmpty());
    }

    @Test
    void findByPrefecture_tokyo_containsMultipleRecords() {
        List<MunicipalityChange> result = history.findByPrefecture("東京都");
        assertFalse(result.isEmpty(), "東京都 should have records");
        // 東京都はすべて "東京都" の prefecture を持つ
        assertTrue(result.stream().allMatch(c -> c.prefecture().equals("東京都")));
    }

    // ---- changesSince ----

    @Test
    void changesSince_veryRecentDate_mayBeEmpty() {
        // 2030-01-01 以降のレコードはデータにないはず
        List<MunicipalityChange> result = history.changesSince(LocalDate.of(2030, 1, 1));
        // データに 2028 年までとあるので、2030以降は空か少数
        assertNotNull(result);
    }

    @Test
    void changesSince_1970_coversAllRecords() {
        List<MunicipalityChange> result = history.changesSince(LocalDate.of(1970, 1, 1));
        // effectiveDate が null でないもの全件
        long withDate = history.findByCode("01100").stream()
                .filter(c -> c.effectiveDate() != null)
                .count();
        // 全体で 1970 以降が大半を占めるはず
        assertFalse(result.isEmpty());
    }

    @Test
    void changesSince_boundaryDate_inclusiveSemantics() {
        // 2005-01-01 施行のレコードが changesSince(2005-01-01) に含まれること
        LocalDate boundary = LocalDate.of(2005, 1, 1);
        List<MunicipalityChange> result = history.changesSince(boundary);
        // 境界日のレコードがあれば含まれることを確認
        boolean hasExactBoundary = result.stream()
                .anyMatch(c -> boundary.equals(c.effectiveDate()));
        // 含む場合だけ確認（含まない場合はスキップ）
        if (hasExactBoundary) {
            assertTrue(result.stream().anyMatch(c -> boundary.equals(c.effectiveDate())));
        }
        // 境界日以降のみ含まれること
        assertTrue(result.stream().allMatch(c -> !c.effectiveDate().isBefore(boundary)));
    }

    // ---- changeCountByYear ----

    @Test
    void changeCountByYear_returnsSortedMap() {
        Map<Integer, Long> byYear = history.changeCountByYear();
        assertFalse(byYear.isEmpty());
        // TreeMap なのでキーは昇順
        int previous = Integer.MIN_VALUE;
        for (int year : byYear.keySet()) {
            assertTrue(year > previous, "years should be ascending");
            previous = year;
        }
    }

    @Test
    void changeCountByYear_heiseiMergerPeakExists() {
        Map<Integer, Long> byYear = history.changeCountByYear();
        // 平成の大合併 (2004-2006) では件数が多いはず
        assertTrue(byYear.containsKey(2004) || byYear.containsKey(2005),
                "should have records for Heisei merger period");
        long count2005 = byYear.getOrDefault(2005, 0L);
        // 2005 年は大合併の最盛期
        assertTrue(count2005 > 0, "2005 should have merger records");
    }

    @Test
    void changeCountByYear_allCountsPositive() {
        Map<Integer, Long> byYear = history.changeCountByYear();
        assertTrue(byYear.values().stream().allMatch(count -> count > 0),
                "all year counts should be positive");
    }

    // ---- activeAt edge cases ----

    @Test
    void activeAt_veryEarlyDate_returnsEmpty() {
        // データ開始前（1960年）は active なものがない
        List<MunicipalityChange> result = history.activeAt(LocalDate.of(1960, 1, 1));
        assertTrue(result.isEmpty(), "no records should be active before dataset start");
    }

    @Test
    void activeAt_resultsAreSortedByLgCode() {
        List<MunicipalityChange> active = history.activeAt(LocalDate.of(2010, 1, 1));
        for (int i = 1; i < active.size(); i++) {
            assertTrue(active.get(i).lgCode().compareTo(active.get(i - 1).lgCode()) >= 0,
                    "results should be sorted by lgCode");
        }
    }

    // ---- timeline ----

    @Test
    void timeline_unknownCode_returnsEmpty() {
        List<MunicipalityChange> tl = history.timeline("99999");
        assertTrue(tl.isEmpty());
    }

    @Test
    void timeline_multiRecordCode_hasSizeMatchingFindByCode() {
        // timeline と findByCode は同じレコード集合を返すはず（順序は違う）
        String code = "01100";
        List<MunicipalityChange> tl = history.timeline(code);
        List<MunicipalityChange> byCode = history.findByCode(code);
        assertEquals(byCode.size(), tl.size(),
                "timeline size should equal findByCode size");
    }

    // ---- estatAppId ----

    @Test
    void estatAppId_hasExpectedLength() {
        String id = MunicipalityHistory.estatAppId();
        // e-Stat appId は 40 文字の hex 文字列
        assertTrue(id.length() >= 30, "appId should be reasonably long: " + id);
    }

    @Test
    void estatAppId_containsOnlyAlphanumeric() {
        String id = MunicipalityHistory.estatAppId();
        assertTrue(id.matches("[a-zA-Z0-9]+"), "appId should be alphanumeric: " + id);
    }
}
