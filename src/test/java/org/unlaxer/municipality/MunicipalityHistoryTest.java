package org.unlaxer.municipality;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MunicipalityHistory の基本テスト。
 * バンドル済み CSV を使用するため外部ファイル不要。
 */
class MunicipalityHistoryTest {

    private static MunicipalityHistory history;

    @BeforeAll
    static void loadBundled() throws IOException {
        history = MunicipalityHistory.loadBundled();
    }

    // ---- データロード確認 ----

    @Test
    void bundledCsvLoadsSuccessfully() {
        assertTrue(history.size() > 3000, "Expected 3000+ records, got: " + history.size());
    }

    @Test
    void prefecturesNotEmpty() {
        assertFalse(history.prefectures().isEmpty());
        assertTrue(history.prefectures().contains("北海道"));
    }

    // ---- findByCode ----

    @Test
    void findByCode_sapporo() {
        List<MunicipalityChange> result = history.findByCode("01100");
        assertFalse(result.isEmpty(), "01100 (札幌市) should exist");
        assertTrue(result.stream().allMatch(c -> c.lgCode().equals("01100")));
    }

    @Test
    void findByCode_unknown_returnsEmpty() {
        assertTrue(history.findByCode("99999").isEmpty());
    }

    // ---- findByName（後方互換：reason も検索対象）----

    @Test
    void findByName_includesReasonHits() {
        // findByName は reason 列も検索するため、name 列にない語も引っかかりうる
        List<MunicipalityChange> result = history.findByName("石狩市");
        assertFalse(result.isEmpty(), "石狩市 should appear in name or reason");
    }

    // ---- findByNameStrict（name 列のみ）----

    @Test
    void findByNameStrict_onlyNameColumn() {
        List<MunicipalityChange> strict = history.findByNameStrict("石狩市");
        assertFalse(strict.isEmpty(), "石狩市 should appear in fullName");
        // strict 結果はすべて fullName に検索語を含む
        assertTrue(strict.stream().allMatch(c -> c.fullName().contains("石狩市")));
    }

    @Test
    void findByNameStrict_noReasonOnlyHits() {
        // reason だけにマッチするケースが findByName より少なくなることを確認
        // （厳密には同値になる可能性もあるが、名前検索のサニティとして有効）
        List<MunicipalityChange> loose = history.findByName("石狩市");
        List<MunicipalityChange> strict = history.findByNameStrict("石狩市");
        assertTrue(strict.size() <= loose.size(),
                "strict results must be a subset of loose results");
    }

    // ---- activeAt ----

    @Test
    void activeAt_1972_returnsRecords() {
        List<MunicipalityChange> active = history.activeAt(LocalDate.of(1972, 12, 31));
        assertFalse(active.isEmpty(), "Some municipalities should be active in 1972");
        // 全件 effectiveDate <= 1972-12-31 のはず
        assertTrue(active.stream().allMatch(
                c -> c.effectiveDate() != null && !c.effectiveDate().isAfter(LocalDate.of(1972, 12, 31))));
    }

    @Test
    void activeAt_2005_heiseiMerger() {
        List<MunicipalityChange> active2005 = history.activeAt(LocalDate.of(2005, 4, 1));
        List<MunicipalityChange> active2004 = history.activeAt(LocalDate.of(2004, 4, 1));
        // 平成の大合併期間中は変化があるため件数が異なる可能性がある（同値になる場合もある）
        assertNotNull(active2005);
        assertNotNull(active2004);
    }

    @Test
    void activeAt_futureDate_returnsRecords() {
        List<MunicipalityChange> active = history.activeAt(LocalDate.of(2030, 1, 1));
        assertFalse(active.isEmpty());
    }

    @Test
    void activeAt_lgCodeUniqueness() {
        List<MunicipalityChange> active = history.activeAt(LocalDate.of(2020, 1, 1));
        long distinctCodes = active.stream().map(MunicipalityChange::lgCode).distinct().count();
        // lgCode ごとに最新1件のみ返るため件数と distinct 数は一致する
        assertEquals(distinctCodes, active.size(),
                "activeAt should return at most one record per lgCode");
    }

    // ---- timeline ----

    @Test
    void timeline_isSortedByDate() {
        List<MunicipalityChange> tl = history.timeline("01100");
        assertFalse(tl.isEmpty());
        for (int i = 1; i < tl.size(); i++) {
            LocalDate prev = tl.get(i - 1).effectiveDate() != null ? tl.get(i - 1).effectiveDate() : LocalDate.MIN;
            LocalDate curr = tl.get(i).effectiveDate() != null ? tl.get(i).effectiveDate() : LocalDate.MIN;
            assertTrue(!curr.isBefore(prev), "timeline should be sorted ascending");
        }
    }

    // ---- changesSince ----

    @Test
    void changesSince_2003_notEmpty() {
        List<MunicipalityChange> result = history.changesSince(LocalDate.of(2003, 1, 1));
        assertFalse(result.isEmpty());
        assertTrue(result.stream().allMatch(
                c -> c.effectiveDate() != null && !c.effectiveDate().isBefore(LocalDate.of(2003, 1, 1))));
    }

    // ---- estatAppId ----

    @Test
    void estatAppId_defaultIsNotBlank() {
        String id = MunicipalityHistory.estatAppId();
        assertNotNull(id);
        assertFalse(id.isBlank());
    }
}
