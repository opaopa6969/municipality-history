package org.unlaxer.municipality;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 自治体統廃合の履歴辞書。
 *
 * <pre>
 * var history = MunicipalityHistory.load(Path.of("data/estat-haichi.csv"));
 *
 * // 地域コードで検索
 * var changes = history.findByCode("01100");
 *
 * // 自治体名で検索
 * var changes = history.findByName("石狩市");
 *
 * // 指定日時点で存在した自治体一覧
 * var active = history.activeAt(LocalDate.of(2005, 1, 1));
 *
 * // ある自治体の全変遷
 * var timeline = history.timeline("01100");
 * </pre>
 */
public class MunicipalityHistory {

    private final List<MunicipalityChange> changes;
    private final Map<String, List<MunicipalityChange>> byCode;
    private final Map<String, List<MunicipalityChange>> byPrefecture;

    private MunicipalityHistory(List<MunicipalityChange> changes) {
        this.changes = List.copyOf(changes);
        this.byCode = changes.stream().collect(Collectors.groupingBy(MunicipalityChange::lgCode));
        this.byPrefecture = changes.stream().collect(Collectors.groupingBy(MunicipalityChange::prefecture));
    }

    public static MunicipalityHistory load(Path csvPath) throws IOException {
        List<MunicipalityChange> changes = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(csvPath)) {
            loadFromReader(reader, changes);
        }
        changes.sort(Comparator.comparing(c -> c.effectiveDate() != null ? c.effectiveDate() : LocalDate.MIN));
        return new MunicipalityHistory(changes);
    }

    /** jar同梱のCSVからロード（クラスパスリソース） */
    public static MunicipalityHistory loadBundled() throws IOException {
        var is = MunicipalityHistory.class.getResourceAsStream("/data/estat-haichi.csv");
        if (is == null) throw new IOException("Bundled CSV not found in classpath: /data/estat-haichi.csv");
        List<MunicipalityChange> changes = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8))) {
            loadFromReader(reader, changes);
        }
        changes.sort(Comparator.comparing(c -> c.effectiveDate() != null ? c.effectiveDate() : LocalDate.MIN));
        return new MunicipalityHistory(changes);
    }

    private static void loadFromReader(BufferedReader reader, List<MunicipalityChange> changes) throws IOException {
        String line = reader.readLine(); // skip header
        while ((line = reader.readLine()) != null) {
            if (line.isBlank()) continue;
            MunicipalityChange change = MunicipalityChange.fromCsvLine(line);
            if (change != null) changes.add(change);
        }
    }

    /** 全レコード数 */
    public int size() { return changes.size(); }

    /** 地域コードで検索 */
    public List<MunicipalityChange> findByCode(String lgCode) {
        return byCode.getOrDefault(lgCode, List.of());
    }

    /** 自治体名（部分一致）で検索。name 列と reason 列の両方を対象とする（後方互換）。 */
    public List<MunicipalityChange> findByName(String name) {
        return changes.stream()
                .filter(c -> c.fullName().contains(name) || c.reason().contains(name))
                .toList();
    }

    /**
     * 自治体名（部分一致）で検索。name 列（district + municipality）のみを対象とする。
     * {@link #findByName(String)} の reason 列誤ヒットを回避したい場合に使用する。
     *
     * @param name 検索キーワード（部分一致）
     * @return 一致した変遷レコードのリスト
     */
    public List<MunicipalityChange> findByNameStrict(String name) {
        return changes.stream()
                .filter(c -> c.fullName().contains(name))
                .toList();
    }

    /**
     * 指定日時点で有効（存在）していた自治体レコードを返す。
     *
     * <p>「有効」の定義：{@code effectiveDate} が {@code date} 以前であり、
     * かつ同一 lgCode の次のレコードの {@code effectiveDate} より前（または次のレコードがない）。
     * すなわち、その日時点で最も新しい変遷レコードを持つ自治体を返す。</p>
     *
     * @param date 基準日
     * @return 指定日時点で有効な自治体変遷レコードのリスト（lgCode ごとに最新の1件）
     */
    public List<MunicipalityChange> activeAt(LocalDate date) {
        return byCode.values().stream()
                .map(codeChanges -> codeChanges.stream()
                        .filter(c -> c.effectiveDate() != null && !c.effectiveDate().isAfter(date))
                        .max(Comparator.comparing(MunicipalityChange::effectiveDate))
                        .orElse(null))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(MunicipalityChange::lgCode))
                .toList();
    }

    /** 都道府県の全変遷 */
    public List<MunicipalityChange> findByPrefecture(String prefecture) {
        return byPrefecture.getOrDefault(prefecture, List.of());
    }

    /** 地域コードの変遷タイムライン */
    public List<MunicipalityChange> timeline(String lgCode) {
        return findByCode(lgCode).stream()
                .sorted(Comparator.comparing(c -> c.effectiveDate() != null ? c.effectiveDate() : LocalDate.MIN))
                .toList();
    }

    /** 指定日以降に変更があった自治体 */
    public List<MunicipalityChange> changesSince(LocalDate since) {
        return changes.stream()
                .filter(c -> c.effectiveDate() != null && !c.effectiveDate().isBefore(since))
                .toList();
    }

    /** 年ごとの変更件数 */
    public Map<Integer, Long> changeCountByYear() {
        return changes.stream()
                .filter(c -> c.effectiveDate() != null)
                .collect(Collectors.groupingBy(c -> c.effectiveDate().getYear(), TreeMap::new, Collectors.counting()));
    }

    /** 都道府県一覧 */
    public Set<String> prefectures() {
        return new TreeSet<>(byPrefecture.keySet());
    }

    /**
     * e-Stat API の appId を返す。
     *
     * <p>優先順位:</p>
     * <ol>
     *   <li>環境変数 {@code ESTAT_APP_ID} が設定されていればその値</li>
     *   <li>フォールバック: CLAUDE.md に記載のデフォルト appId</li>
     * </ol>
     *
     * @return e-Stat appId 文字列
     */
    public static String estatAppId() {
        String envId = System.getenv("ESTAT_APP_ID");
        if (envId != null && !envId.isBlank()) {
            return envId;
        }
        return "24edfb042993e87548e75f8e26f6f5421646a6fe";
    }

    // CLI
    public static void main(String[] args) throws Exception {
        Path csvPath = Path.of(args.length > 0 ? args[0] : "data/estat-haichi.csv");
        var history = load(csvPath);
        System.out.printf("Loaded %,d municipality changes\n", history.size());

        // 年別件数
        System.out.println("\n=== Changes by year ===");
        var byyear = history.changeCountByYear();
        long peak = byyear.values().stream().mapToLong(Long::longValue).max().orElse(1);
        for (var entry : byyear.entrySet()) {
            int bars = (int)(entry.getValue() * 50 / peak);
            System.out.printf("  %d: %3d %s\n", entry.getKey(), entry.getValue(), "#".repeat(bars));
        }

        // 平成の大合併
        System.out.println("\n=== 平成の大合併 (2003-2010) ===");
        var heisei = history.changesSince(LocalDate.of(2003, 1, 1)).stream()
                .filter(c -> c.effectiveDate().isBefore(LocalDate.of(2010, 1, 1)))
                .toList();
        System.out.printf("  %d changes\n", heisei.size());
        heisei.stream().limit(10).forEach(c ->
                System.out.printf("  %s %s%s %s\n", c.effectiveDate(), c.prefecture(), c.fullName(), c.reason().split("\n")[0]));

        // 石狩市の例
        System.out.println("\n=== Sample: 石狩市 ===");
        history.findByName("石狩市").forEach(c ->
                System.out.printf("  %s [%s] %s%s: %s\n",
                        c.effectiveDate(), c.lgCode(), c.prefecture(), c.fullName(),
                        c.reason().split("\n")[0]));
    }
}
