package org.unlaxer.municipality;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

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
    void estatAppId_returnsEnvVarWhenSet() {
        // ESTAT_APP_ID が設定されている環境でのみ値を検証する
        String envId = System.getenv("ESTAT_APP_ID");
        if (envId != null && !envId.isBlank()) {
            String id = MunicipalityHistory.estatAppId();
            assertNotNull(id);
            assertFalse(id.isBlank());
            assertEquals(envId, id);
        }
    }

    @Test
    void estatAppId_throwsWhenEnvVarNotSet() {
        // ESTAT_APP_ID が未設定のとき IllegalStateException がスローされることを確認する
        String envId = System.getenv("ESTAT_APP_ID");
        if (envId == null || envId.isBlank()) {
            assertThrows(IllegalStateException.class, MunicipalityHistory::estatAppId,
                    "ESTAT_APP_ID が未設定のとき IllegalStateException がスローされるべき");
        }
    }

    // ---- 壊れると困るのに検証されていない振る舞い（公開 API 境界値・不変条件）----

    /**
     * findByName("") は {@code String#contains("")} が常に true となるため
     * 全レコードを返す。この挙動は呼び出し元が暗黙に依存しうる境界値であり、
     * 将来「空文字は不正」と弾くように変更すると壊れるため固定化する。
     */
    @Test
    void findByName_emptyString_returnsAllRecords() {
        List<MunicipalityChange> result = history.findByName("");
        assertEquals(history.size(), result.size(),
                "空文字列は全レコードに部分一致するため size と同数になる");
    }

    /**
     * findByNameStrict("") も同様に全レコードを返す。
     * findByName と findByNameStrict の空文字列に対する挙動は一致すべき。
     */
    @Test
    void findByNameStrict_emptyString_returnsAllRecords() {
        List<MunicipalityChange> result = history.findByNameStrict("");
        assertEquals(history.size(), result.size(),
                "findByNameStrict も空文字列で全レコードを返す");
    }

    /**
     * findByCode(null) は空リストを返す。
     * 実データに lgCode=null は存在しないため HashMap#getOrDefault(null) は
     * 空リストを返すが、null 入力で NPE を投げず安全に空を返すことを固定化する。
     */
    @Test
    void findByCode_null_returnsEmptyWithoutNPE() {
        // 実データに lgCode=null はないため、null キーでの問い合わせは空リストになる
        List<MunicipalityChange> result = history.findByCode(null);
        assertNotNull(result, "null コードでもリストオブジェクトは返す（NPE しない）");
        assertTrue(result.isEmpty(), "存在しないコード（null 含む）は空リスト");
    }

    /**
     * activeAt が返すレコードには isAbolished()==true のものが一切含まれない。
     * この不変条件は「廃止済み自治体を有効として返さない」という activeAt の核心だが、
     * 既存テストでは件数やソート順しか検証されていなかった。実データ全体で検証する。
     */
    @Test
    void activeAt_neverContainsAbolishedRecords() {
        // データ末尾の 2024-01-01 より後なら全レコードが候補に入る
        List<MunicipalityChange> active = history.activeAt(LocalDate.of(2025, 1, 1));
        assertFalse(active.isEmpty(), "データ期間内の全自治体が候補に入る");
        long abolishedCount = active.stream().filter(MunicipalityChange::isAbolished).count();
        assertEquals(0L, abolishedCount,
                "activeAt は廃止済みレコードを含めてはならない: " + abolishedCount + " 件含まれる");
    }

    /**
     * 集計系3 API の一貫性: changeCountByYear の合計件数と
     * changesSince(LocalDate.MIN) の件数と、effectiveDate が非 null のレコード数は
     * すべて一致する（同じ「effectiveDate != null」基準で集計しているため）。
     * いずれかが別基準で null を除外/含入すると壊れる回帰検出用。
     */
    @Test
    void aggregationAPIs_areConsistentAboutNullDates() {
        // changeCountByYear の合計
        long sumByYear = history.changeCountByYear().values().stream()
                .mapToLong(Long::longValue).sum();
        // changesSince(MIN) は effectiveDate != null かつ !isBefore(MIN) = effectiveDate != null と同値
        long sinceMin = history.changesSince(LocalDate.MIN).size();
        // effectiveDate が非 null のレコード数（全件ベース）
        long nonNullDates = history.findByName("").stream()  // = 全件
                .filter(c -> c.effectiveDate() != null)
                .count();

        assertEquals(nonNullDates, sumByYear,
                "changeCountByYear の合計は effectiveDate 非 null 件数と一致すべき");
        assertEquals(nonNullDates, sinceMin,
                "changesSince(LocalDate.MIN) は effectiveDate 非 null 件数と一致すべき");
    }

    /**
     * loadBundled を複数回呼ぶと同じ件数のインスタンスが返る。
     * クラスパスリソースの再読み込みが冪等であることを検証（ストリームの close 忘れ等で壊れる）。
     */
    @Test
    void loadBundled_isIdempotentAcrossMultipleCalls() throws IOException {
        int first = history.size();
        MunicipalityHistory second = MunicipalityHistory.loadBundled();
        assertEquals(first, second.size(),
                "loadBundled を複数回呼んでも同じ件数がロードされる");
    }
}
