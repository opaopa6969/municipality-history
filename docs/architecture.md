# アーキテクチャ

## 概要

`municipality-history` は e-Stat 廃置分合データを **インメモリ辞書** として提供するライブラリです。
起動時に jar 同梱の CSV を読み込み、複数のインデックスマップを構築します。外部 DB への依存はありません。

---

## コアモデル — `MunicipalityChange`

廃置分合等情報の **1 レコード** を表す Java `record` です。

```java
public record MunicipalityChange(
    String    lgCode,           // 標準地域コード（5桁）
    String    prefecture,       // 都道府県名
    String    district,         // 郡・政令市・振興局等
    String    districtKana,     // 郡等（ふりがな）
    String    municipality,     // 市区町村名
    String    municipalityKana, // 市区町村名（ふりがな）
    LocalDate effectiveDate,    // 廃置分合等施行年月日
    String    reason            // 改正事由（自由文）
)
```

### フィールド詳細

| フィールド | 型 | 説明 |
|---|---|---|
| `lgCode` | `String` | 総務省 5 桁地方公共団体コード。廃止された自治体のコードも含む。 |
| `prefecture` | `String` | 「北海道」「東京都」など都道府県名（助詞付き） |
| `district` | `String` | 郡・政令指定都市・支庁・振興局の名称。単独市町村は空文字。 |
| `districtKana` | `String` | `district` のひらがな表記 |
| `municipality` | `String` | 市区町村名（郡を含まない単体名）。政令市本体や郡なし自治体は空文字になる場合がある。 |
| `municipalityKana` | `String` | `municipality` のひらがな表記 |
| `effectiveDate` | `LocalDate` | 廃置分合等の施行日。未記載の場合 `null`。 |
| `reason` | `String` | 改正事由。旧自治体コードや関係自治体名を含む自由文。複数行になる場合あり。 |

### `fullName()` ヘルパー

```java
/** 完全な市区町村名（郡・政令市名 + 市区町村名） */
public String fullName() {
    if (municipality.isEmpty()) return district;
    return district + municipality;
}
```

---

## `MunicipalityHistory` — 辞書クラス

### 初期化とインデックス構造

```
loadBundled() / load(Path)
        ↓
   CSV 全行を解析（MunicipalityChange.fromCsvLine）
        ↓
   effectiveDate 昇順でソート
        ↓
   ┌─────────────────────────────────────────────┐
   │  changes: List<MunicipalityChange>  (全件)  │
   │  byCode:  Map<lgCode, List<...>>            │
   │  byPrefecture: Map<prefecture, List<...>>   │
   └─────────────────────────────────────────────┘
```

すべてのコレクションは **不変（`List.copyOf`）** で初期化後は変更されません。

### 旧コードの扱い

`lgCode` は廃止された自治体のコードもそのまま収録されています。
合併後に消滅したコード（例: `01303` 石狩郡石狩町）も `byCode` に登録されるため、
旧コードで `findByCode` を呼ぶと廃止前後の変遷レコードが返ります。

---

## 検索 API と注意点

### `findByName(String name)` — 誤ヒット警告

```java
public List<MunicipalityChange> findByName(String name) {
    return changes.stream()
        .filter(c -> c.fullName().contains(name) || c.reason().contains(name))
        .toList();
}
```

`reason`（改正事由）列も検索対象に含まれます。
`reason` は「○○市(12345)と△△町(12346)が合併し…」のような自由文であり、
**検索語が関係自治体名として言及されている場合も一致**します。

例: `findByName("石狩市")` を呼んだとき、石狩市自体のレコードだけでなく、
「石狩市に編入」と記載された他自治体のレコードも返ります。

`fullName()` のみで絞り込む場合は以下のように stream を追加してください。

```java
history.findByName("石狩").stream()
    .filter(c -> c.fullName().contains("石狩"))
    .toList();
```

### `activeAt(LocalDate date)` — 未実装

Javadoc にのみ記載されています。現バージョン (1.0.0) では実装されていません。

```
// MunicipalityHistory には activeAt() メソッドは存在しない。
// 「指定日時点で存在した自治体一覧」は手動で構築する必要がある。
// 将来バージョンで実装予定（BACKLOG.md 参照）。
```

---

## CSV パース

`MunicipalityChange.fromCsvLine` は RFC 4180 相当の簡易 CSV パーサを内包しています。
ダブルクォート内のカンマ・改行を正しく処理し、`""` エスケープに対応します。
パースに失敗した行は `null` を返してスキップします（ログ出力なし）。

---

## スレッド安全性

`MunicipalityHistory` は不変オブジェクトです。
`loadBundled()` / `load()` はスレッドセーフではありませんが、
構築後のインスタンスは**複数スレッドから安全に共有できます**。
アプリケーション起動時に一度だけ構築し、static フィールドやシングルトンとして保持することを推奨します。
