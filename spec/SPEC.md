# municipality-history 仕様書

バージョン: 1.0.1
最終更新: 2026-04-19
作成者: opaopa6969 / Claude Sonnet 4.6

---

## 目次

1. [概要](#1-概要)
2. [機能仕様](#2-機能仕様)
3. [データ永続化層](#3-データ永続化層)
4. [ステートマシン](#4-ステートマシン)
5. [ビジネスロジック](#5-ビジネスロジック)
6. [API / 外部境界](#6-api--外部境界)
7. [UI](#7-ui)
8. [設定](#8-設定)
9. [依存](#9-依存)
10. [非機能要件](#10-非機能要件)
11. [テスト戦略](#11-テスト戦略)
12. [デプロイ / 運用](#12-デプロイ--運用)

---

## 1. 概要

### 1.1 プロジェクト目的

`municipality-history` は、日本の自治体統廃合履歴（廃置分合等）をプログラムから参照するためのインメモリ辞書ライブラリです。

日本では市区町村が合併・改称・市制施行・政令指定都市移行などのイベントにより継続的に変遷しており、1970 年以降だけで 3,507 件の変遷が記録されています。特に 2003〜2010 年の「平成の大合併」では 2,444 件が集中し、2005 年単年で 1,243 件というピークに達しました。

本ライブラリは総務省管理の e-Stat（政府統計の総合窓口）廃置分合等情報をデータソースとし、このデータを jar ファイルに同梱して提供します。利用者は Maven/Gradle で依存を追加するだけで、外部ファイルや外部 DB への接続なしに自治体変遷データを検索できます。

### 1.2 対象ユーザー

- **住所処理システム開発者**: 旧自治体名を現在の自治体名に解決する機能が必要な場合
- **統計・GIS 分析者**: 特定時点での自治体一覧や変遷データを必要とする場合
- **自治体情報システム開発者**: 地域コードの追跡・自治体の沿革表示が必要な場合
- **研究者**: 日本の行政区分変遷データを機械処理したい場合

### 1.3 プロジェクト識別情報

| 項目 | 値 |
|---|---|
| プロジェクト名 | `municipality-history` |
| groupId | `org.unlaxer` |
| artifactId | `municipality-history` |
| 現在バージョン | 1.0.1 |
| ライセンス | MIT |
| リポジトリ | https://github.com/opaopa6969/municipality-history |
| Maven Central | https://central.sonatype.com/artifact/org.unlaxer/municipality-history |
| 開発者 | Hisayuki Ookubo (opaopa6969@gmail.com) |

### 1.4 データ概要

| 項目 | 値 |
|---|---|
| 出典 | e-Stat 廃置分合等情報 |
| 出典 URL | https://www.e-stat.go.jp/municipalities/cities/absorption-separation-of-municipalities |
| 実データ期間 | 1970 年〜2024 年（最終レコード 2024-01-01） |
| 総レコード数 | 3,507 件 |
| jar 同梱 CSV サイズ | 97 KB |
| エンコーディング | UTF-8 |

### 1.5 主要ユースケース

#### UC-01: 旧自治体名の解決

住所検索システムで「石狩町」のような廃止済み自治体名を現在の「石狩市」に変換する。

```java
var history = MunicipalityHistory.loadBundled();
// 「石狩町」が属していた変遷を取得
var changes = history.findByNameStrict("石狩町");
// reason 列から新自治体名を参照
```

#### UC-02: 地域コードの追跡

合併前の 5 桁コードが現在どのコードに対応するか調べる。

```java
// 廃止済みコード（石狩郡石狩町）の変遷
var timeline = history.timeline("01303");
// → [1996-09-01: 石狩町が石狩市(01215)に市制施行]
```

#### UC-03: 特定日時点の自治体一覧取得

統計分析で「2005 年 4 月 1 日時点の全自治体」を取得する。

```java
var active = history.activeAt(LocalDate.of(2005, 4, 1));
// → lgCode ごと最新1件のスナップショット
```

#### UC-04: 変遷履歴の表示

ある自治体がどのような経緯で現在の形になったかを時系列で表示する。

```java
history.timeline("01100").forEach(c ->
    System.out.printf("[%s] %s%n", c.effectiveDate(), c.reason()));
```

#### UC-05: 平成の大合併分析

2003〜2010 年の大規模合併期間の変遷データを集計する。

```java
var heisei = history.changesSince(LocalDate.of(2003, 1, 1)).stream()
    .filter(c -> c.effectiveDate().isBefore(LocalDate.of(2010, 1, 1)))
    .toList();
// → 2,444件
```

### 1.6 スコープ外

本ライブラリが現バージョンで対応しないもの:

- 1970 年以前の変遷データ（明治・大正・昭和初期の行政区域変更）
- 2025 年以降の予定合併データ（現バージョン未収録）
- `reason` 列の構造化パース（旧コード→新コードの自動変換テーブル）
- REST API・Web UI
- GeoJSON 境界データとの統合

---

## 2. 機能仕様

### 2.1 概要

本ライブラリが提供する機能は以下のカテゴリに分類されます。

| カテゴリ | メソッド |
|---|---|
| データロード | `loadBundled()`, `load(Path)` |
| コード検索 | `findByCode(String)` |
| 名称検索 | `findByName(String)`, `findByNameStrict(String)`, `findByPrefecture(String)` |
| 時系列検索 | `activeAt(LocalDate)`, `timeline(String)`, `changesSince(LocalDate)` |
| 集計・メタ | `changeCountByYear()`, `size()`, `prefectures()` |
| 設定 | `estatAppId()` |

### 2.2 データロード機能

#### 2.2.1 `loadBundled()`

```java
public static MunicipalityHistory loadBundled() throws IOException
```

jar ファイルに同梱された CSV からデータをロードします。

**処理フロー:**

1. `MunicipalityHistory.class.getResourceAsStream("/data/estat-haichi.csv")` でクラスパスから CSV ストリームを取得
2. `InputStreamReader(UTF-8)` でデコード
3. 全行をパースして `List<MunicipalityChange>` を構築
4. `effectiveDate` 昇順でソート（null は `LocalDate.MIN` 扱い）
5. 不変インスタンスを生成して返す

**戻り値:** 初期化済みの `MunicipalityHistory` インスタンス

**例外:**
- `IOException`: クラスパス上の `/data/estat-haichi.csv` が見つからない場合

**推奨理由:** 外部ファイルへの依存がなく、依存を追加するだけで動作する。

#### 2.2.2 `load(Path csvPath)`

```java
public static MunicipalityHistory load(Path csvPath) throws IOException
```

指定した外部 CSV ファイルからデータをロードします。

**処理フロー:**

1. `Files.newBufferedReader(csvPath)` でファイルを開く
2. 以降の処理は `loadBundled()` と同一

**用途:**
- e-Stat から最新データをダウンロードした場合（`scripts/download-estat.mjs` 実行後）
- テスト用の独自 CSV を使いたい場合

**例外:**
- `IOException`: ファイルが存在しない・読み取れない場合

### 2.3 コード検索

#### 2.3.1 `findByCode(String lgCode)`

```java
public List<MunicipalityChange> findByCode(String lgCode)
```

総務省の標準地域コード（5桁）で変遷レコードを検索します。

**引数:**
- `lgCode`: 5桁の地方公共団体コード（例: `"01100"`, `"13101"`）

**戻り値:**
- 該当する全変遷レコードのリスト（変更なしの場合は空リスト `List.of()`）

**実装詳細:**
- `byCode` HashMap から O(1) で取得
- 廃止済みコードも検索可能（合併前の旧コードも `byCode` に登録されている）

**使用例:**
```java
// 札幌市（01100）の全変遷
var changes = history.findByCode("01100");
// → 1972-04-01: 政令指定都市施行（1件）など

// 廃止済みコード（石狩郡石狩町）
var changes = history.findByCode("01303");
// → 1996-09-01: 石狩市に市制施行（廃止前の変遷が返る）

// 存在しないコード
var empty = history.findByCode("99999");
// → []
```

**注意:** 廃止済みコードの場合、廃止前のレコードが返ります。「現在有効か」の判定には `activeAt` を併用してください。

### 2.4 名称検索

#### 2.4.1 `findByName(String name)`

```java
public List<MunicipalityChange> findByName(String name)
```

自治体名の部分一致検索。`fullName()` と `reason` 列の両方を対象とします。

**引数:**
- `name`: 検索キーワード（部分一致）

**戻り値:**
- `c.fullName().contains(name) || c.reason().contains(name)` を満たす全レコード

**計算量:** O(n)（n = 総レコード数 = 3,507）

**後方互換性:** v1.0.0 から動作変更なし。

**`reason` 列への検索ヒットについて:**

`reason`（改正事由）列は自由文であり、合併に関わった旧自治体名・新自治体名が記述されています。このため、`findByName("石狩市")` を呼ぶと次のレコードが全て返ります。

| マッチ元 | 対象レコード |
|---|---|
| `fullName()` ヒット | 石狩市自身の変遷レコード（市制施行、編入等）|
| `reason` ヒット | `reason` 中に「石狩市(01215)に編入合併」と記載された厚田村・浜益村等のレコード |

これは後方互換性のため維持されている仕様です。`reason` 誤ヒットを避けたい場合は `findByNameStrict` を使用してください。

**使用例:**
```java
// 「石狩市」を含む全変更（reason 列込み）
var changes = history.findByName("石狩市");

// 「大阪市」を含む全変更
var changes = history.findByName("大阪市");
```

#### 2.4.2 `findByNameStrict(String name)`

```java
public List<MunicipalityChange> findByNameStrict(String name)
```

自治体名の部分一致検索。`fullName()` のみを対象とします（v1.0.1 追加）。

**引数:**
- `name`: 検索キーワード（部分一致）

**戻り値:**
- `c.fullName().contains(name)` を満たす全レコード

**計算量:** O(n)

**`findByName` との差異:**

```java
List<MunicipalityChange> loose  = history.findByName("石狩市");
List<MunicipalityChange> strict = history.findByNameStrict("石狩市");

// strict は loose の部分集合
// strict.size() <= loose.size() が常に成立
// strict の全レコードは fullName().contains("石狩市") == true
```

**使用例:**
```java
// 名称のみでの厳密な検索
var changes = history.findByNameStrict("石狩市");
// → 石狩市自身の変遷レコードのみ（他自治体の reason ヒットを含まない）

// 存在しない名称
var empty = history.findByNameStrict("存在しない自治体名");
// → []
```

#### 2.4.3 `findByPrefecture(String prefecture)`

```java
public List<MunicipalityChange> findByPrefecture(String prefecture)
```

都道府県名で変遷レコードを検索します。

**引数:**
- `prefecture`: 都道府県名（助詞付き）。例: `"北海道"`, `"東京都"`, `"大阪府"`, `"愛知県"`

**戻り値:**
- 該当都道府県の全変遷レコード（空の場合は `List.of()`）

**実装詳細:**
- `byPrefecture` HashMap から O(1) で取得

**使用例:**
```java
// 北海道の全変遷
var changes = history.findByPrefecture("北海道");

// 東京都の全変遷
var changes = history.findByPrefecture("東京都");

// 都道府県一覧の確認
Set<String> prefs = history.prefectures(); // 47都道府県すべて含む
```

### 2.5 時系列検索

#### 2.5.1 `activeAt(LocalDate date)`

```java
public List<MunicipalityChange> activeAt(LocalDate date)
```

指定日時点で有効（存在）していた自治体レコードを返します（v1.0.1 追加）。

**引数:**
- `date`: 基準日（例: `LocalDate.of(2005, 4, 1)`）

**「有効」の定義:**
- `effectiveDate` が `date` 以前
- かつ同一 `lgCode` のレコード群の中で最も新しい `effectiveDate` を持つ（最新スナップショット）

**戻り値:**
- lgCode ごとに最新の 1 件のみ
- `lgCode` 昇順でソート
- `effectiveDate` が null のレコードは除外

**実装詳細:**

```java
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
```

**使用例:**
```java
// 2005年4月1日時点の自治体スナップショット
var active2005 = history.activeAt(LocalDate.of(2005, 4, 1));
// → lgCode ごとに最新1件のみ（重複なし）

// 1972年12月31日時点
var active1972 = history.activeAt(LocalDate.of(1972, 12, 31));

// 将来日付も指定可能（最新レコードが返る）
var activeFuture = history.activeAt(LocalDate.of(2030, 1, 1));
```

**注意:**
- 廃止済みコードの場合、廃止前の最新レコードが返ります（廃止という概念は本メソッドでは扱いません）
- `effectiveDate` が指定日より後のレコードは除外されます
- lgCode ごとに必ず 1 件以下のみ返ります

#### 2.5.2 `timeline(String lgCode)`

```java
public List<MunicipalityChange> timeline(String lgCode)
```

地域コードの変遷タイムラインを `effectiveDate` 昇順で返します。

**引数:**
- `lgCode`: 標準地域コード（5桁）

**戻り値:**
- 対象 lgCode の全変遷レコードを `effectiveDate` 昇順でソートしたリスト
- `effectiveDate` が null のものは `LocalDate.MIN` として先頭扱い
- 該当なしの場合は空リスト

**実装詳細:**

`findByCode(lgCode)` の結果を日付昇順ソートしたものを返します。

```java
public List<MunicipalityChange> timeline(String lgCode) {
    return findByCode(lgCode).stream()
            .sorted(Comparator.comparing(
                c -> c.effectiveDate() != null ? c.effectiveDate() : LocalDate.MIN))
            .toList();
}
```

**使用例:**
```java
// 石狩市（01215）の変遷タイムライン
var tl = history.timeline("01215");
tl.forEach(c -> System.out.printf("[%s] %s%n", c.effectiveDate(), c.reason()));
// [1996-09-01] 石狩町(01303)が石狩市(01215)に市制施行
// [2005-10-01] 厚田村(01304)、浜益村(01305)が石狩市(01215)に編入合併

// 札幌市（01100）の変遷
var tl = history.timeline("01100");
// [1972-04-01] 政令指定都市施行
// → 区（01101〜）の新設など
```

#### 2.5.3 `changesSince(LocalDate since)`

```java
public List<MunicipalityChange> changesSince(LocalDate since)
```

指定日以降に廃置分合等が行われた変遷レコードを返します。

**引数:**
- `since`: 開始日（この日以降、当日を含む）

**フィルタ条件:**
- `effectiveDate != null`
- `effectiveDate >= since`（`!effectiveDate.isBefore(since)` と等価）

**戻り値:**
- 条件を満たす全レコード（ロード時ソート順を維持）

**使用例:**
```java
// 2020年以降の全変更
var recent = history.changesSince(LocalDate.of(2020, 1, 1));

// 平成の大合併（2003-2010）の変更
var heisei = history.changesSince(LocalDate.of(2003, 1, 1)).stream()
    .filter(c -> c.effectiveDate().isBefore(LocalDate.of(2010, 1, 1)))
    .toList();
// → 2,444件

// 最近1年の変更
var lastYear = history.changesSince(LocalDate.now().minusYears(1));
```

### 2.6 集計・メタ機能

#### 2.6.1 `changeCountByYear()`

```java
public Map<Integer, Long> changeCountByYear()
```

年ごとの変更件数を返します。

**戻り値:**
- `TreeMap<Integer, Long>`（年 → 件数、年昇順）
- `effectiveDate` が null のレコードは除外

**主要な件数（参考）:**

| 年 | 件数 | 主な背景 |
|---|---|---|
| 1972 | 多数 | 1972 年 4 月の政令指定都市施行（札幌等） |
| 2005 | 1,243 | 平成の大合併ピーク |
| 2006 | 多数 | 平成の大合併（沖縄等） |
| 2024 | 数件 | 浜松市の区再編等 |

**使用例:**
```java
Map<Integer, Long> byYear = history.changeCountByYear();
byYear.forEach((year, count) ->
    System.out.printf("%d年: %d件%n", year, count));
// 2005年が 1,243 件でピーク
```

#### 2.6.2 `size()`

```java
public int size()
```

ロードされた全レコード数を返します。

**戻り値:** バンドル CSV では `3507`

#### 2.6.3 `prefectures()`

```java
public Set<String> prefectures()
```

データに含まれる都道府県名の集合を返します。

**戻り値:**
- `TreeSet<String>`（ソート順）
- 47 都道府県すべてが含まれます（「北海道」「東京都」「大阪府」「愛知県」等）

---

## 3. データ永続化層

### 3.1 アーキテクチャ方針

本ライブラリは外部 DB を使用しない **インメモリ辞書** 方式を採用しています。

設計判断の背景:

1. **配布の簡潔さ** — jar 1 本で動作。外部サービスへの依存なし
2. **高速アクセス** — 起動後はすべてインメモリ。ネットワーク遅延なし
3. **データサイズ** — 3,507 件・97 KB は常時メモリ保持に適したサイズ
4. **不変性** — 廃置分合データは過去の事実であり更新されない（バージョン固定）

### 3.2 バンドル CSV の詳細

#### 3.2.1 ファイル仕様

| 項目 | 値 |
|---|---|
| クラスパス上のパス | `/data/estat-haichi.csv` |
| ソースディレクトリ | `data/estat-haichi.csv`（`pom.xml` で resources 設定） |
| 文字コード | UTF-8（BOM なし） |
| 改行コード | LF（ただし reason 列は CR+LF を含む場合あり） |
| ファイルサイズ | 97 KB |
| 総行数（概算） | 4,491 行（マルチライン reason 含む） |
| データレコード数 | 3,507 件 |
| ヘッダー行 | 1 行（日本語列名） |

#### 3.2.2 Maven リソース設定

```xml
<build>
    <resources>
        <resource>
            <directory>data</directory>
            <targetPath>data</targetPath>
        </resource>
    </resources>
</build>
```

`data/estat-haichi.csv` が jar の `/data/estat-haichi.csv` にバンドルされます。

### 3.3 CSV 列構成

e-Stat から取得した CSV の列構成は以下の通りです。

| 列番号 | 列名（CSV ヘッダー） | Java フィールド | 型 | 備考 |
|---|---|---|---|---|
| 0 | 標準地域コード | `lgCode` | `String` | 5桁。廃止済みコードを含む |
| 1 | 都道府県 | `prefecture` | `String` | 「北海道」「東京都」等 |
| 2 | 政令市・郡・支庁・振興局等 | `district` | `String` | 郡なし自治体では空文字 |
| 3 | 政令市・郡等（ふりがな） | `districtKana` | `String` | ひらがな |
| 4 | 市区町村 | `municipality` | `String` | 政令市本体等では空文字の場合あり |
| 5 | 市区町村（ふりがな） | `municipalityKana` | `String` | ひらがな |
| 6 | 廃置分合等施行年月日 | `effectiveDate` | `LocalDate`（null 可） | YYYY-MM-DD 形式。空白の場合 null |
| 7 | 改正事由 | `reason` | `String` | 自由文。複数行（`\n`）を含む場合あり |

#### 実データサンプル

```
"01100","北海道","札幌市","さっぽろし","","","1972-04-01","札幌市(01201)の札幌市(01100)への政令指定都市施行
中央区(01101)、北区(01102)、東区(01103)、白石区(01104)、豊平区(01105)、南区(01106)、西区(01107)の新設"
```

```
"47374","沖縄県","宮古郡","みやこぐん","伊良部町","いらぶちょう","2005-10-01","城辺町(47371)、下地町(47372)、上野村(47373)、伊良部町(47374)、平良市(47206)が合併し、宮古島市(47214)を新設"
```

### 3.4 CSV パーサの実装

`MunicipalityChange.fromCsvLine(String)` が内部で RFC 4180 準拠の簡易 CSV パーサを実装しています。

#### 3.4.1 仕様

- ダブルクォート囲みのフィールド内のカンマを正しく処理
- ダブルクォート囲みのフィールド内の改行（マルチライン）を正しく処理
- `""` エスケープ対応（ダブルクォートのエスケープ）
- フィールド数が 8 未満の行は `null` を返してスキップ
- パース中の例外（日付フォーマットエラー等）は `null` を返してスキップ（ログ出力なし）

#### 3.4.2 パーサ実装

```java
private static String[] parseCsv(String line) {
    List<String> fields = new ArrayList<>();
    StringBuilder cur = new StringBuilder();
    boolean inQ = false;
    for (int i = 0; i < line.length(); i++) {
        char c = line.charAt(i);
        if (c == '"') {
            if (inQ && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                cur.append('"'); i++;  // "" → " のエスケープ解除
            } else inQ = !inQ;
        } else if (c == ',' && !inQ) {
            fields.add(cur.toString()); cur.setLength(0);
        } else cur.append(c);
    }
    fields.add(cur.toString());
    return fields.toArray(new String[0]);
}
```

**注意:** このパーサは `\n` を含むマルチライン reason の行を単一行として渡すことを前提とします。ファイルを `readLine()` で読む場合、reason の改行で行が分割されるケースがあります。実際のロード実装では BufferedReader の `readLine()` を使用しており、e-Stat CSV の改行を正しく処理するためのロジックが必要です。

### 3.5 インメモリインデックス構造

`MunicipalityHistory` は構築時に 3 つのコレクションを初期化します。

```
loadBundled() / load(Path)
        │
        ▼
 CSV 全行を解析（MunicipalityChange.fromCsvLine）
        │
        ▼
 effectiveDate 昇順でソート（null は LocalDate.MIN 扱い）
        │
        ▼
 ┌────────────────────────────────────────────────────────────┐
 │ changes: List<MunicipalityChange>               (全件・不変) │
 │ byCode:  Map<String, List<MunicipalityChange>>   (lgCode別) │
 │ byPrefecture: Map<String, List<MunicipalityChange>> (都道府県別) │
 └────────────────────────────────────────────────────────────┘
```

#### 3.5.1 `changes`

- 型: `List<MunicipalityChange>`（`List.copyOf()` による不変リスト）
- 全 3,507 件を `effectiveDate` 昇順で保持
- `findByName`, `findByNameStrict`, `changesSince`, `activeAt` の全件スキャンに使用

#### 3.5.2 `byCode`

- 型: `Map<String, List<MunicipalityChange>>`（`HashMap`）
- `lgCode` → 対応する変遷レコードリスト
- `findByCode`, `timeline`, `activeAt` で使用
- 廃止済みコードもキーとして含まれる

#### 3.5.3 `byPrefecture`

- 型: `Map<String, List<MunicipalityChange>>`（`HashMap`）
- `prefecture` → 対応する変遷レコードリスト
- `findByPrefecture`, `prefectures()` で使用

### 3.6 不変性とスレッド安全性

**構築フェーズ（スレッドセーフでない）:**
- `loadBundled()` / `load()` はスレッドセーフではありません
- 単一スレッドでの呼び出しが必要です

**使用フェーズ（スレッドセーフ）:**
- 構築後のインスタンスは不変オブジェクト
- `changes` は `List.copyOf()` で不変
- 全メソッドは読み取り専用操作のみ
- 複数スレッドから安全に共有可能

**推奨パターン:**
```java
// アプリケーション起動時に1度だけ初期化
private static final MunicipalityHistory HISTORY;
static {
    try {
        HISTORY = MunicipalityHistory.loadBundled();
    } catch (IOException e) {
        throw new ExceptionInInitializerError(e);
    }
}
```

---

## 4. ステートマシン

N/A

本ライブラリはステートマシンを持ちません。`MunicipalityHistory` は構築後に内部状態が変化しない純粋不変オブジェクトです。

### 4.1 ライフサイクル

| フェーズ | 説明 | 遷移 |
|---|---|---|
| 未初期化 | インスタンス未生成 | `loadBundled()` / `load()` 呼び出しで初期化フェーズへ |
| 初期化中 | CSV ロード・ソート・インデックス構築 | 完了後に使用可能フェーズへ。失敗時は `IOException` |
| 使用可能 | 読み取り専用クエリ受付 | 状態変化なし。GC されるまで維持 |

状態変化は「初期化中 → 使用可能」の 1 回限りです。「使用可能 → 別の状態」への遷移は存在しません。

### 4.2 データの時間的性質

`MunicipalityHistory` 自体は不変ですが、`activeAt(LocalDate)` メソッドを通じて **データ内の時間軸** を参照できます。つまり「データとしての歴史的変遷」はありますが、オブジェクトの「状態遷移」はありません。

---

## 5. ビジネスロジック

### 5.1 `reason` 列の自由文方針

#### 5.1.1 概要

`reason`（改正事由）列は e-Stat のデータをそのまま保持する自由文フィールドです。合併・市制施行等の詳細が自然言語で記述されており、旧自治体名・旧コードを含みます。

現バージョンでは **構造化パースを行わず文字列として保持** します。

#### 5.1.2 主なフォーマットパターン

**パターン A: 市制施行・町制施行**
```
石狩町(01303)が石狩市(01215)に市制施行
```
- 旧自治体（コード）が 1 件
- 新自治体（コード）が 1 件
- `「が〜に市制施行」` / `「が〜に町制施行」` のフォーマット

**パターン B: 新設合併（複数→1件）**
```
城辺町(47371)、下地町(47372)、上野村(47373)、伊良部町(47374)、平良市(47206)が合併し、宮古島市(47214)を新設
```
- 旧自治体（コード）が複数件（読点区切り）
- 新自治体（コード）が 1 件
- `「が合併し、〜を新設」` のフォーマット

**パターン C: 編入合併（複数→既存）**
```
厚田村(01304)、浜益村(01305)が石狩市(01215)に編入合併
```
- 旧自治体（コード）が複数件
- 吸収先自治体（コード）が 1 件
- `「が〜に編入合併」` のフォーマット

**パターン D: 政令指定都市施行（区新設を伴う）**
```
札幌市(01201)の札幌市(01100)への政令指定都市施行
中央区(01101)、北区(01102)、東区(01103)、白石区(01104)、豊平区(01105)、南区(01106)、西区(01107)の新設
```
- 複数行にわたる記述
- 区の新設を含む

**パターン E: 区の再編**
```
浜松市（22130）の区の再編（７区から３区へ再編）　中区（22131）、東区（22132）、西区（22133）、南区（22134）、北区（22135）（三方原地区／初生町、三方原町、東三方町、豊岡町、三幸町、大原町、根洗町）を中央区（22138）に再編　北区（22135）（三方原地区以外）、浜北区（22136）を浜名区（22139）に再編　天竜区（22137）を天竜区（22140）に再編（区名、区域の変更なし）
```
- 1 件の reason が非常に長い場合がある
- コードが全角括弧で囲まれているケースもある

#### 5.1.3 非解析の理由

1. **誤解析リスク**: フォーマットが完全統一されておらず、正規表現処理で一部ケースが誤った結果を返す可能性
2. **API 安定性**: 解析結果を公開 API にすると将来の修正が breaking change になる
3. **十分な利用価値**: `reason` 文字列そのままの表示・ログ出力で実用的なケースが大半

#### 5.1.4 将来の拡張候補

BACKLOG に記録されている以下の機能は構造化パースを前提とします。

- **旧コード→新コード変換テーブル**: パターン A/B/C から `(旧コード, 新コード)` ペアを生成
- **合併ネットワーク**: 複数の廃置分合を有向グラフで表現し、「どこに吸収されたか」を辿れる API

実装する場合は、まずパターン A〜D のカバレッジを測定し、カバーできないケースの件数・割合を確認してから API 設計に進むことを推奨します。

### 5.2 `activeAt` の有効定義

#### 5.2.1 「有効な自治体」の定義

指定日時点で有効な自治体レコードとは:

1. `effectiveDate` が指定日 **以前**（当日を含む）
2. かつ同一 `lgCode` のレコード群の中で **最も新しい `effectiveDate`** を持つもの

この定義により、lgCode ごとに最新の 1 件のみが返されます。

#### 5.2.2 設計上の制約と注意点

**廃止済みコードの扱い:**

本実装は「コードが廃止された」という概念を持ちません。合併で消滅したコード（例: `01303` 石狩郡石狩町）であっても、`activeAt(LocalDate.of(2005, 1, 1))` では廃止前の最新レコードが返されます。

ある自治体が特定日時点で「廃止済みか否か」を判定するには、`reason` 列の解析が必要ですが、現バージョンでは提供していません。

**lgCode の重複なし保証:**

`activeAt` の戻り値は lgCode ごとに最大 1 件のみです。件数と `distinct().count()` は常に一致します（テストで検証済み）。

### 5.3 旧コード追跡

`lgCode` は廃止された自治体のコードもそのまま収録されています。

**設計思想:** e-Stat データをそのまま保持することで、利用者が独自のコード変換ロジックを実装できるようにしています。

| lgCode | 自治体 | 状態 |
|---|---|---|
| `01100` | 札幌市 | 現存（1972-04-01 以降） |
| `01215` | 石狩市 | 現存（1996-09-01 市制施行後） |
| `01303` | 石狩郡石狩町 | 廃止（1996-09-01 石狩市に市制施行） |
| `01304` | 石狩郡厚田村 | 廃止（2005-10-01 石狩市に編入合併） |

廃止済みコードで `findByCode` を呼ぶと廃止前の変遷レコードが返ります。これにより、旧システムが持つ旧コードから変遷を追跡できます。

### 5.4 `fullName()` の結合ロジック

```java
public String fullName() {
    if (municipality.isEmpty()) return district;
    return district + municipality;
}
```

**ケース分類:**

| `district` | `municipality` | `fullName()` の例 |
|---|---|---|
| `"石狩郡"` | `"石狩町"` | `"石狩郡石狩町"` |
| `"札幌市"` | `"中央区"` | `"札幌市中央区"` |
| `"札幌市"` | `""` | `"札幌市"` |
| `""` | `"石狩市"` | `"石狩市"`（district が空なので municipality のみ返る） |

**注意:** `district` が空で `municipality` が空でない場合、`district + municipality` は `municipality` のみになります。`municipality.isEmpty()` のチェックが先に来るため、`municipality` が空の場合は `district` が返ります。

### 5.5 日付処理のルール

#### 5.5.1 ソート時の null 扱い

ロード時のソートとタイムライン生成で `effectiveDate` が null の場合は `LocalDate.MIN` として扱います。

```java
changes.sort(Comparator.comparing(
    c -> c.effectiveDate() != null ? c.effectiveDate() : LocalDate.MIN));
```

これにより null レコードは常にリストの先頭に配置されます。

#### 5.5.2 フィルタ時の null 除外

`activeAt`・`changesSince` では `effectiveDate != null` のチェックにより null レコードが自動除外されます。

```java
// activeAt
.filter(c -> c.effectiveDate() != null && !c.effectiveDate().isAfter(date))

// changesSince
.filter(c -> c.effectiveDate() != null && !c.effectiveDate().isBefore(since))
```

#### 5.5.3 将来日付の扱い

`activeAt(LocalDate.of(2030, 1, 1))` のように将来日付を指定した場合、現在収録されている最新レコード（2024-01-01 以前）が返ります。エラーにはなりません。

### 5.6 `findByName` における reason ヒットの位置づけ

`findByName` が `reason` 列も検索対象とすることは **設計上の意図した動作** です。

**意図:** 関連する変遷をまとめて取得したい場合に有用です。例えば「石狩市に関係する全変遷（石狩市自身の変遷 + 石狩市に吸収された自治体の変遷）」を 1 回のクエリで取得できます。

**回避策:** 名称のみで絞り込む場合は `findByNameStrict` を使用するか、以下のように追加フィルタを適用します。

```java
// reason ヒットを除外
history.findByName("石狩市").stream()
    .filter(c -> c.fullName().contains("石狩市"))
    .toList();
```

---

## 6. API / 外部境界

### 6.1 公開 API 一覧

本ライブラリの公開 API はバージョン間の互換性を保証します（Semantic Versioning 準拠）。

#### 6.1.1 `MunicipalityHistory` クラス（公開メソッド）

| メソッドシグネチャ | 追加バージョン | 戻り値型 | 説明 |
|---|---|---|---|
| `loadBundled()` | 1.0.0 | `MunicipalityHistory` | jar 同梱 CSV からロード |
| `load(Path)` | 1.0.0 | `MunicipalityHistory` | 外部 CSV からロード |
| `size()` | 1.0.0 | `int` | 全レコード数 |
| `findByCode(String)` | 1.0.0 | `List<MunicipalityChange>` | lgCode 検索 |
| `findByName(String)` | 1.0.0 | `List<MunicipalityChange>` | 名称+reason 部分一致 |
| `findByNameStrict(String)` | 1.0.1 | `List<MunicipalityChange>` | 名称のみ部分一致 |
| `findByPrefecture(String)` | 1.0.0 | `List<MunicipalityChange>` | 都道府県検索 |
| `activeAt(LocalDate)` | 1.0.1 | `List<MunicipalityChange>` | 指定日時点で有効な自治体 |
| `timeline(String)` | 1.0.0 | `List<MunicipalityChange>` | 変遷タイムライン |
| `changesSince(LocalDate)` | 1.0.0 | `List<MunicipalityChange>` | 指定日以降の変更 |
| `changeCountByYear()` | 1.0.0 | `Map<Integer, Long>` | 年別変更件数 |
| `prefectures()` | 1.0.0 | `Set<String>` | 都道府県一覧 |
| `estatAppId()` | 1.0.1 | `String` | e-Stat appId 取得 |

#### 6.1.2 `MunicipalityChange` record（公開フィールドアクセサ）

| メソッド | 型 | 説明 |
|---|---|---|
| `lgCode()` | `String` | 標準地域コード（5桁） |
| `prefecture()` | `String` | 都道府県名 |
| `district()` | `String` | 郡・政令市・振興局等（空文字の場合あり） |
| `districtKana()` | `String` | 郡等（ふりがな）（空文字の場合あり） |
| `municipality()` | `String` | 市区町村名（空文字の場合あり） |
| `municipalityKana()` | `String` | 市区町村名（ふりがな）（空文字の場合あり） |
| `effectiveDate()` | `LocalDate` | 廃置分合等の施行日（null の場合あり） |
| `reason()` | `String` | 改正事由（自由文・複数行あり） |
| `fullName()` | `String` | district + municipality の結合名 |

### 6.2 パッケージ構成

```
org.unlaxer.municipality
├── MunicipalityHistory.java   // 辞書クラス（公開）
└── MunicipalityChange.java    // データモデル record（公開）
```

**注:** 現バージョンは 2 クラスのみのシンプルな構成です。将来的に CLAUDE.md の `model/`, `parser/`, `store/`, `query/`, `api/` パッケージ分割が行われる可能性があります。

### 6.3 例外仕様

| 例外クラス | 発生条件 | 対処 |
|---|---|---|
| `IOException` | `loadBundled()`: クラスパス上の CSV が見つからない | jar の再ビルドを確認 |
| `IOException` | `load(Path)`: 指定ファイルが存在しない・読み取れない | パスを確認 |
| なし（空リスト返却） | 検索結果が 0 件 | null チェック不要 |
| なし（スキップ） | CSV 行のパース失敗 | ログ出力なし |

### 6.4 後方互換性ポリシー

- **マイナーバージョン（1.x）**: 後方互換を維持。新メソッドの追加のみ
- **`findByName`**: v1.0.0 の動作（reason 列込み検索）を変更しない
- **`MunicipalityChange` の fields**: 追加はするが削除・型変更しない

### 6.5 非公開 API

以下は内部実装であり、バージョン間の互換性を保証しません。

| メソッド | クラス | 備考 |
|---|---|---|
| `fromCsvLine(String)` | `MunicipalityChange` | package-private |
| `parseCsv(String)` | `MunicipalityChange` | private static |
| `unquote(String)` | `MunicipalityChange` | private static |
| `parseDate(String)` | `MunicipalityChange` | private static |
| `loadFromReader(BufferedReader, List)` | `MunicipalityHistory` | private static |
| `main(String[])` | `MunicipalityHistory` | CLI（ライブラリ API ではない） |

### 6.6 外部システム境界

本ライブラリ実行時は外部システムへの接続を行いません。

| 外部システム | 用途 | ライブラリ実行時 | データ更新時 |
|---|---|---|---|
| e-Stat API | 廃置分合データ取得 | 不要 | `scripts/download-estat.mjs` で使用 |
| Maven Central | ライブラリ配布 | 依存解決のみ（初回のみ） | `mvn deploy` で使用 |
| GitHub | ソース管理・CI | 不要 | git push / GitHub Actions |

### 6.7 利用例（エンドツーエンド）

```java
import org.unlaxer.municipality.MunicipalityHistory;
import org.unlaxer.municipality.MunicipalityChange;
import java.time.LocalDate;

// 初期化（アプリ起動時に1度のみ）
var history = MunicipalityHistory.loadBundled();

// 1. lgCode で変遷を検索
List<MunicipalityChange> sapporo = history.findByCode("01100");
System.out.println("札幌市の変遷: " + sapporo.size() + "件");

// 2. 名称検索
List<MunicipalityChange> ishikari = history.findByNameStrict("石狩市");
ishikari.forEach(c -> System.out.printf("[%s] %s: %s%n",
    c.effectiveDate(), c.fullName(), c.reason().split("\n")[0]));

// 3. 平成の大合併
long heiseiCount = history.changesSince(LocalDate.of(2003, 1, 1)).stream()
    .filter(c -> c.effectiveDate().isBefore(LocalDate.of(2010, 1, 1)))
    .count();
System.out.println("平成の大合併: " + heiseiCount + "件");

// 4. 2005年4月1日時点の自治体数
int activeIn2005 = history.activeAt(LocalDate.of(2005, 4, 1)).size();
System.out.println("2005年4月1日時点の自治体数: " + activeIn2005);
```

---

## 7. UI

N/A

本ライブラリは UI を提供しません。ピュア Java ライブラリとして API のみを公開します。

### 7.1 CLI（内部用途・デモ用）

`MunicipalityHistory.main(String[])` に簡易 CLI が実装されています。これはライブラリ API の一部ではなく、動作確認・デモ目的の内部ツールです。

**出力内容:**
1. ロード件数
2. 年別変更件数の ASCII 棒グラフ
3. 平成の大合併（2003〜2010 年）の件数と先頭 10 件
4. 石狩市の変遷サンプル

**実行方法:**
```bash
# Maven exec プラグインで実行
mvn -q exec:java -Dexec.args="data/estat-haichi.csv"

# デフォルト引数（data/estat-haichi.csv）を使用
mvn -q exec:java
```

### 7.2 将来の UI 計画

BACKLOG に以下の UI 拡張が記録されています。

- **REST API + 検索 UI**: japanpost-history と同様の Javalin 構成
  - `GET /api/info` — メタ情報
  - `GET /api/municipalities/{lgCode}` — lgCode で取得
  - `GET /api/municipalities/search?q=` — 名称検索
  - `GET /api/municipalities/active?date=` — 指定日時点の一覧
  - 静的 UI: `src/main/resources/static/index.html`

- **合併ネットワーク可視化**: どの自治体がどこに吸収されたかのグラフ表示

---

## 8. 設定

### 8.1 環境変数

#### 8.1.1 `ESTAT_APP_ID`

| 項目 | 値 |
|---|---|
| 変数名 | `ESTAT_APP_ID` |
| 必須 | 任意 |
| デフォルト値 | `24edfb042993e87548e75f8e26f6f5421646a6fe` |
| 用途 | e-Stat API の appId |
| 参照メソッド | `MunicipalityHistory.estatAppId()` |

**優先順位:**
1. 環境変数 `ESTAT_APP_ID` が設定されていればその値を使用
2. 未設定または空白の場合はコード内のデフォルト値を使用

```java
public static String estatAppId() {
    String envId = System.getenv("ESTAT_APP_ID");
    if (envId != null && !envId.isBlank()) {
        return envId;
    }
    return "24edfb042993e87548e75f8e26f6f5421646a6fe";
}
```

**用途:**
- `scripts/download-estat.mjs` でデータを更新する際に e-Stat API キーが必要
- ライブラリ本体の動作（データロード・検索）では不要

### 8.2 Maven 設定

#### 8.2.1 ライブラリとして使用する場合

```xml
<dependency>
    <groupId>org.unlaxer</groupId>
    <artifactId>municipality-history</artifactId>
    <version>1.0.1</version>
</dependency>
```

Gradle (Kotlin DSL):
```kotlin
implementation("org.unlaxer:municipality-history:1.0.1")
```

Gradle (Groovy DSL):
```groovy
implementation 'org.unlaxer:municipality-history:1.0.1'
```

#### 8.2.2 本プロジェクトのビルド設定

```xml
<properties>
    <maven.compiler.release>21</maven.compiler.release>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <junit.version>5.11.4</junit.version>
</properties>
```

### 8.3 CLI 引数

`MunicipalityHistory.main(String[])` の引数仕様:

| 位置 | 引数 | デフォルト | 説明 |
|---|---|---|---|
| `args[0]` | CSV パス | `"data/estat-haichi.csv"` | ロードする CSV ファイルのパス |

### 8.4 データ取得スクリプト

`scripts/download-estat.mjs` は Playwright を使用して e-Stat からデータをダウンロードします。

**前提条件:**
- Node.js インストール済み
- Playwright インストール済み

**実行手順:**
```bash
# Playwright の chromium をインストール（初回のみ）
npx playwright install chromium

# e-Stat からデータをダウンロード
node scripts/download-estat.mjs
# → data/estat-haichi.csv が更新される
```

### 8.5 CI 設定

`.github/workflows/ci.yml` に GitHub Actions ワークフローが設定されています（v1.0.1 追加）。

---

## 9. 依存

### 9.1 実行時依存

#### 9.1.1 Java 21

| 項目 | 値 |
|---|---|
| バージョン | 21 以上（LTS） |
| 必須理由 | `record` 型・sealed class・パターンマッチング等を使用 |
| コンパイルターゲット | `maven.compiler.release=21` |

Java 21 を最低要件とする詳細な理由:

| 機能 | 使用箇所 |
|---|---|
| `record` | `MunicipalityChange` の定義 |
| `List.copyOf()` | `MunicipalityHistory` コンストラクタの不変リスト生成 |
| `var` キーワード | ローカル変数型推論 |
| `Map.of()` / `List.of()` | 空コレクション返却 |
| Stream API | 全検索メソッド |
| `LocalDate` | 日付処理 |
| テキストブロック（将来） | |
| 仮想スレッド（将来の非同期ロード拡張） | |

#### 9.1.2 `jp.go.digital:abr-utils`

| 項目 | 値 |
|---|---|
| groupId | `jp.go.digital` |
| artifactId | `abr-utils` |
| バージョン | 0.9.4 |
| 用途 | 住所基盤レジストリユーティリティ |
| 関連プロジェクト | https://github.com/opaopa6969/ABRUtils |

### 9.2 テスト依存

#### 9.2.1 JUnit 5

| 項目 | 値 |
|---|---|
| groupId | `org.junit.jupiter` |
| artifactId | `junit-jupiter` |
| バージョン | 5.11.4 |
| スコープ | `test` |
| 使用アノテーション | `@Test`, `@BeforeAll` |
| アサーション | `assertTrue`, `assertFalse`, `assertEquals`, `assertNotNull` |

### 9.3 ビルドプラグイン

| プラグイン | バージョン | 用途 | 備考 |
|---|---|---|---|
| `maven-compiler-plugin` | 3.13.0 | Java 21 コンパイル | |
| `maven-surefire-plugin` | 3.5.2 | JUnit 5 テスト実行 | |
| `exec-maven-plugin` | 3.5.0 | CLI 実行 | `mvn exec:java` |
| `maven-source-plugin` | 3.3.1 | ソース jar 生成 | Maven Central 要件 |
| `maven-javadoc-plugin` | 3.11.2 | Javadoc jar 生成 | Maven Central 要件 |
| `maven-gpg-plugin` | 3.2.7 | GPG 署名 | Maven Central 要件 |
| `central-publishing-maven-plugin` | 0.7.0 | Maven Central 公開 | Sonatype Central |

### 9.4 外部ツール（データ更新時のみ）

| ツール | バージョン | 用途 |
|---|---|---|
| Node.js | 18 以上推奨 | `scripts/download-estat.mjs` 実行 |
| Playwright | 最新版 | e-Stat からの CSV ダウンロード |
| chromium | Playwright 管理 | Playwright のブラウザエンジン |

### 9.5 依存関係図

```
municipality-history 1.0.1
├── [runtime] jp.go.digital:abr-utils:0.9.4
└── [test]    org.junit.jupiter:junit-jupiter:5.11.4
                └── org.junit.jupiter:junit-jupiter-api:5.11.4
                └── org.junit.jupiter:junit-jupiter-engine:5.11.4
```

---

## 10. 非機能要件

### 10.1 対応データ期間

| 項目 | 値 |
|---|---|
| 実データ開始年 | 1970 年（最初のレコード: 1971-04-01 等） |
| 実データ終了年 | 2024 年（最終レコード: 2024-01-01） |
| 表示上の対象範囲 | 1970〜2028 年（e-Stat の記載に準拠） |
| 2022〜2023 年のデータ | 該当期間に廃置分合等なし（レコード 0 件） |
| 2025 年以降 | 現バージョン未収録 |

**「1970〜2028 年」表記について:**

README および一部ドキュメントで「1970〜2028 年」と記載しています。これは e-Stat データ上の将来予定年を含む **表示範囲** であり、実際に jar に収録されているレコードの最終日付は 2024-01-01 です。

**データの空白年:**

2022 年・2023 年のレコードが存在しないのは、該当期間に廃置分合等が実施されていないためです。データの欠損ではありません。

### 10.2 パフォーマンス要件

#### 10.2.1 計算量

| 操作 | 計算量 | 実装詳細 |
|---|---|---|
| `findByCode` | O(1) | `byCode` HashMap 参照 |
| `findByPrefecture` | O(1) | `byPrefecture` HashMap 参照 |
| `findByName` | O(n) | `changes` リストの全件スキャン |
| `findByNameStrict` | O(n) | `changes` リストの全件スキャン |
| `activeAt` | O(n) | `byCode.values()` の全件スキャン |
| `changesSince` | O(n) | `changes` リストの全件スキャン |
| `changeCountByYear` | O(n) | `changes` リストの全件スキャン + `groupingBy` |
| `timeline` | O(k log k) | k = 対象 lgCode のレコード数 |
| `size` | O(1) | `changes.size()` |
| `prefectures` | O(p) | `byPrefecture.keySet()` → `TreeSet` 変換、p = 47 |
| ロード（`loadBundled`） | O(n log n) | CSV 読み込み O(n) + ソート O(n log n) |

n = 3,507 件と小さいため、O(n) の操作も実用的な速度で動作します。

#### 10.2.2 起動時間

- `loadBundled()` の実行時間: JVM ウォームアップ込みで数十〜数百ミリ秒程度（環境依存）
- アプリケーション起動時に 1 度だけ呼び出すことを推奨

#### 10.2.3 メモリ使用量

| コレクション | 概算サイズ |
|---|---|
| バンドル CSV | 97 KB |
| `changes` リスト（3,507 件の record） | 数百 KB |
| `byCode` HashMap | 数百 KB |
| `byPrefecture` HashMap | 数十 KB |
| 合計（概算） | 2〜5 MB 以内 |

### 10.3 信頼性

- **エラーハンドリング**: パース失敗行はスキップ（例外伝播なし）
- **null 安全**: 検索結果が 0 件の場合は `List.of()`（null 非返却）
- **不変性**: 構築後のインスタンスは変更不可

### 10.4 保守性

- **コード量**: 2 クラス・合計 200 行以内の小規模実装
- **テスト**: 14 テストケースで主要 API をカバー
- **Javadoc**: 主要メソッドに日本語 Javadoc 記述

### 10.5 拡張性

将来バージョンでの追加を検討している機能（BACKLOG 参照）:

| 機能 | 優先度 | 依存する前提 |
|---|---|---|
| 旧コード→新コード変換テーブル | 高 | `reason` 列の構造化パース |
| 合併ネットワークの有向グラフ | 中 | 旧コード→新コード変換テーブル |
| REST API + 検索 UI | 中 | Javalin 依存追加 |
| e-Stat 定期更新 CI | 低 | GitHub Actions 設定 |
| Geoshape 歴史的行政区域との統合 | 低 | 外部データソース |

### 10.6 データ品質

e-Stat からのデータ品質に関する既知の事項:

- `effectiveDate` が null のレコードが一部存在する（本ライブラリは null 許容で対応済み）
- `reason` 列のフォーマットが統一されていない（解析しない設計判断で対応済み）
- 一部のコードで全角括弧と半角括弧が混在する（文字列として保持するため影響なし）

---

## 11. テスト戦略

### 11.1 テスト方針

本ライブラリのテストは以下の方針に基づいています。

1. **外部依存なし**: バンドル CSV を使用するため e-Stat API への接続不要
2. **本番データでのテスト**: モックではなく実データで動作確認
3. **API サニティチェック**: 全公開 API のスモークテスト
4. **境界値テスト**: 空結果・存在しないコード・過去日・未来日
5. **後方互換確認**: v1.0.1 で追加された `findByNameStrict` と既存 `findByName` の包含関係

### 11.2 テスト環境

| 項目 | 値 |
|---|---|
| テストフレームワーク | JUnit 5（junit-jupiter 5.11.4） |
| テスト実行 | Maven Surefire Plugin 3.5.2 |
| テストクラス | `MunicipalityHistoryTest` |
| クラスパス | `src/test/java/org/unlaxer/municipality/MunicipalityHistoryTest.java` |
| テスト実行方法 | `mvn test` |
| CI | GitHub Actions（`.github/workflows/ci.yml`） |

### 11.3 テストのセットアップ

```java
@BeforeAll
static void loadBundled() throws IOException {
    history = MunicipalityHistory.loadBundled();
}
```

`@BeforeAll` により、クラス内の全テスト実行前に 1 度だけ `loadBundled()` を呼び出します。

### 11.4 テスト一覧（14 件）

#### グループ 1: データロード確認（2件）

| テスト名 | 検証内容 | 期待値 |
|---|---|---|
| `bundledCsvLoadsSuccessfully` | バンドル CSV がロードされ 3,000 件以上のレコードが存在すること | `history.size() > 3000` |
| `prefecturesNotEmpty` | 都道府県一覧が空でなく「北海道」が含まれること | `!prefectures.isEmpty() && prefectures.contains("北海道")` |

```java
@Test
void bundledCsvLoadsSuccessfully() {
    assertTrue(history.size() > 3000, "Expected 3000+ records, got: " + history.size());
}

@Test
void prefecturesNotEmpty() {
    assertFalse(history.prefectures().isEmpty());
    assertTrue(history.prefectures().contains("北海道"));
}
```

#### グループ 2: `findByCode` テスト（2件）

| テスト名 | 検証内容 | 期待値 |
|---|---|---|
| `findByCode_sapporo` | `"01100"`（札幌市）で 1 件以上返り、全件の lgCode が一致すること | `!result.isEmpty()` かつ全件 `lgCode == "01100"` |
| `findByCode_unknown_returnsEmpty` | 存在しないコード `"99999"` で空リストが返ること | `result.isEmpty()` |

```java
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
```

#### グループ 3: `findByName` テスト（1件）

| テスト名 | 検証内容 | 期待値 |
|---|---|---|
| `findByName_includesReasonHits` | `"石狩市"` で name/reason いずれかにヒットし 1 件以上返ること | `!result.isEmpty()` |

```java
@Test
void findByName_includesReasonHits() {
    List<MunicipalityChange> result = history.findByName("石狩市");
    assertFalse(result.isEmpty(), "石狩市 should appear in name or reason");
}
```

#### グループ 4: `findByNameStrict` テスト（2件）

| テスト名 | 検証内容 | 期待値 |
|---|---|---|
| `findByNameStrict_onlyNameColumn` | `"石狩市"` で 1 件以上返り、全件の `fullName()` に `"石狩市"` が含まれること | 全件 `fullName().contains("石狩市") == true` |
| `findByNameStrict_noReasonOnlyHits` | `findByNameStrict` の結果が `findByName` の結果以下の件数であること | `strict.size() <= loose.size()` |

```java
@Test
void findByNameStrict_onlyNameColumn() {
    List<MunicipalityChange> strict = history.findByNameStrict("石狩市");
    assertFalse(strict.isEmpty(), "石狩市 should appear in fullName");
    assertTrue(strict.stream().allMatch(c -> c.fullName().contains("石狩市")));
}

@Test
void findByNameStrict_noReasonOnlyHits() {
    List<MunicipalityChange> loose = history.findByName("石狩市");
    List<MunicipalityChange> strict = history.findByNameStrict("石狩市");
    assertTrue(strict.size() <= loose.size(),
            "strict results must be a subset of loose results");
}
```

#### グループ 5: `activeAt` テスト（4件）

| テスト名 | 検証内容 | 期待値 |
|---|---|---|
| `activeAt_1972_returnsRecords` | 1972-12-31 時点で 1 件以上返り、全件の `effectiveDate` が指定日以前であること | 全件 `effectiveDate <= 1972-12-31` |
| `activeAt_2005_heiseiMerger` | 2005-04-01 と 2004-04-01 の結果がいずれも null でないこと | `active2005 != null && active2004 != null` |
| `activeAt_futureDate_returnsRecords` | 2030-01-01 で 1 件以上返ること | `!active.isEmpty()` |
| `activeAt_lgCodeUniqueness` | 2020-01-01 時点の結果で lgCode ごとに最大 1 件であること | `distinct lgCode count == list size` |

```java
@Test
void activeAt_lgCodeUniqueness() {
    List<MunicipalityChange> active = history.activeAt(LocalDate.of(2020, 1, 1));
    long distinctCodes = active.stream().map(MunicipalityChange::lgCode).distinct().count();
    assertEquals(distinctCodes, active.size(),
            "activeAt should return at most one record per lgCode");
}
```

#### グループ 6: `timeline` テスト（1件）

| テスト名 | 検証内容 | 期待値 |
|---|---|---|
| `timeline_isSortedByDate` | `"01100"` のタイムラインが `effectiveDate` 昇順であること | 隣接する全ペアで `prev <= curr` |

```java
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
```

#### グループ 7: `changesSince` テスト（1件）

| テスト名 | 検証内容 | 期待値 |
|---|---|---|
| `changesSince_2003_notEmpty` | 2003-01-01 以降のレコードが 1 件以上返り、全件の `effectiveDate` が 2003-01-01 以降であること | 全件 `effectiveDate >= 2003-01-01` |

#### グループ 8: `estatAppId` テスト（1件）

| テスト名 | 検証内容 | 期待値 |
|---|---|---|
| `estatAppId_defaultIsNotBlank` | `estatAppId()` が null でなく、空白でないこと | `id != null && !id.isBlank()` |

### 11.5 テスト実行コマンド

```bash
# 全テスト実行
mvn test

# テストスキップでビルド
mvn package -DskipTests -Dgpg.skip=true

# 特定テストクラスの実行
mvn test -Dtest=MunicipalityHistoryTest

# 特定テストメソッドの実行
mvn test -Dtest=MunicipalityHistoryTest#findByCode_sapporo
```

### 11.6 テストカバレッジ状況

| API | テスト | カバー内容 |
|---|---|---|
| `loadBundled()` | `bundledCsvLoadsSuccessfully` | 正常系 |
| `findByCode` | `findByCode_sapporo`, `findByCode_unknown_returnsEmpty` | 正常系・空結果 |
| `findByName` | `findByName_includesReasonHits` | reason ヒット確認 |
| `findByNameStrict` | `findByNameStrict_onlyNameColumn`, `findByNameStrict_noReasonOnlyHits` | 正常系・包含関係 |
| `activeAt` | 4件 | 過去日・平成大合併期・未来日・lgCode一意性 |
| `timeline` | `timeline_isSortedByDate` | ソート順 |
| `changesSince` | `changesSince_2003_notEmpty` | フィルタ結果 |
| `estatAppId` | `estatAppId_defaultIsNotBlank` | デフォルト値 |
| `prefectures` | `prefecturesNotEmpty` | 正常系 |
| `load(Path)` | なし | 未テスト |
| `findByPrefecture` | なし | 未テスト |
| `changeCountByYear` | なし | 未テスト |
| `size` | `bundledCsvLoadsSuccessfully` | 間接的にカバー |

### 11.7 テスト未カバー領域と追加候補

以下のテストケースの追加が推奨されます（BACKLOG 候補）:

- `load(Path)` の正常系・異常系
- `findByPrefecture` の正常系・存在しない都道府県
- `changeCountByYear` のピーク年（2005）確認
- `activeAt` の返却件数の厳密な検証
- 大量データのパフォーマンステスト

---

## 12. デプロイ / 運用

### 12.1 Maven Central 公開情報

| 項目 | 値 |
|---|---|
| groupId | `org.unlaxer` |
| artifactId | `municipality-history` |
| 現在バージョン | 1.0.1 |
| Maven Central URL | https://central.sonatype.com/artifact/org.unlaxer/municipality-history |
| 公開サービス | Sonatype Central Publishing |
| 公開コマンド | `mvn deploy` |

### 12.2 公開手順

#### 12.2.1 前提条件

- GPG 鍵ペアが生成・公開鍵サーバーに登録済み
- `~/.m2/settings.xml` に Sonatype Central の認証情報が設定済み
- `MAVEN_GPG_PASSPHRASE` 等の環境変数が設定済み（CI の場合は Secrets 設定）

#### 12.2.2 公開コマンド

```bash
# バージョン更新（pom.xml の <version> タグを更新）
# vim pom.xml または Maven Versions Plugin を使用

# ビルド・テスト・GPG 署名・Maven Central デプロイ
mvn deploy

# テストをスキップして公開（緊急時のみ）
mvn deploy -DskipTests
```

#### 12.2.3 pom.xml のプラグイン設定

```xml
<!-- GPG 署名 -->
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-gpg-plugin</artifactId>
    <version>3.2.7</version>
    <executions>
        <execution>
            <id>sign-artifacts</id>
            <phase>verify</phase>
            <goals><goal>sign</goal></goals>
        </execution>
    </executions>
</plugin>

<!-- Maven Central 公開 -->
<plugin>
    <groupId>org.sonatype.central</groupId>
    <artifactId>central-publishing-maven-plugin</artifactId>
    <version>0.7.0</version>
    <extensions>true</extensions>
    <configuration>
        <publishingServerId>central</publishingServerId>
    </configuration>
</plugin>
```

### 12.3 ビルドコマンド一覧

```bash
# コンパイルのみ
mvn -q compile

# テスト実行
mvn test

# jar 生成（テスト・署名スキップ）
mvn package -DskipTests -Dgpg.skip=true

# ローカルリポジトリにインストール（他プロジェクトからの参照用）
mvn install -DskipTests -Dgpg.skip=true

# CLI デモ実行
mvn -q exec:java -Dexec.args="data/estat-haichi.csv"

# Maven Central へデプロイ
mvn deploy
```

### 12.4 公開アーティファクト構成

`mvn deploy` 実行時に Maven Central に公開されるファイル:

| ファイル | 内容 | 要件 |
|---|---|---|
| `municipality-history-{version}.jar` | メイン jar（CSV データ同梱・97 KB） | Maven Central 必須 |
| `municipality-history-{version}-sources.jar` | ソースコード（maven-source-plugin） | Maven Central 必須 |
| `municipality-history-{version}-javadoc.jar` | Javadoc（maven-javadoc-plugin） | Maven Central 必須 |
| `municipality-history-{version}.pom` | POM ファイル | Maven Central 必須 |
| 各 `.asc` ファイル | GPG 署名（maven-gpg-plugin） | Maven Central 必須 |
| 各 `.md5` / `.sha1` ファイル | チェックサム | 自動生成 |

### 12.5 データ更新手順

#### 12.5.1 通常の更新フロー

e-Stat の最新データを取得して新バージョンをリリースする手順:

```bash
# 1. e-Stat からデータをダウンロード
npx playwright install chromium  # 初回のみ
node scripts/download-estat.mjs
# → data/estat-haichi.csv が更新される

# 2. ビルドして動作確認
mvn package -DskipTests -Dgpg.skip=true
mvn test

# 3. バージョン更新（pom.xml の version を変更）
# 例: 1.0.1 → 1.0.2

# 4. CHANGELOG.md 更新

# 5. コミット・タグ作成
git add data/estat-haichi.csv pom.xml CHANGELOG.md
git commit -m "chore: update e-Stat data and bump version to 1.0.2"
git tag v1.0.2

# 6. Maven Central へデプロイ
mvn deploy
```

#### 12.5.2 データバンドルの仕組み

```xml
<!-- pom.xml resources 設定 -->
<resources>
    <resource>
        <directory>data</directory>
        <targetPath>data</targetPath>
    </resource>
</resources>
```

`data/estat-haichi.csv` が jar の `/data/estat-haichi.csv` にコピーされます。これにより `MunicipalityHistory.class.getResourceAsStream("/data/estat-haichi.csv")` で読み込めます。

### 12.6 バージョン履歴

[Semantic Versioning](https://semver.org/) に準拠します。

| バージョン | リリース日 | 変更種別 | 主な内容 |
|---|---|---|---|
| 1.0.0 | 2025-07-01 | 初回リリース | 基本検索 API・CSV バンドル・Maven Central 公開 |
| 1.0.1 | 2026-04-19 | マイナー追加 | `activeAt`・`findByNameStrict`・`estatAppId()` 追加、テスト拡充、CI 追加 |

変更詳細は `CHANGELOG.md` を参照してください。

### 12.7 運用上の注意事項

#### 12.7.1 シングルトン推奨

`MunicipalityHistory` インスタンスはアプリケーション起動時に 1 度だけ生成し、static フィールドで保持することを推奨します。

**理由:**
- `loadBundled()` は CSV ファイルの読み込み・ソート・インデックス構築を行うため、起動コストが発生します
- 構築後のインスタンスは不変であり、複数スレッドから安全に共有できます

**アンチパターン（避けること）:**
```java
// 毎回ロードするのは非効率
public void doSomething() {
    var history = MunicipalityHistory.loadBundled(); // 毎呼び出しでロード
    history.findByCode("01100");
}
```

**推奨パターン:**
```java
// アプリケーションレベルのシングルトン
@Component // Spring Boot 等の場合
public class MunicipalityService {
    private final MunicipalityHistory history;

    public MunicipalityService() throws IOException {
        this.history = MunicipalityHistory.loadBundled(); // 1度のみ
    }
}
```

#### 12.7.2 null 安全性

- 全検索メソッドは結果が 0 件の場合に `List.of()`（空の不変リスト）を返します
- `null` を返すメソッドはありません（`effectiveDate` フィールドを除く）
- `effectiveDate` が null のレコードが一部存在します。`effectiveDate()` の戻り値は null チェックが必要です

#### 12.7.3 reason 列の改行

`reason` 列は複数行のテキストを含む場合があります（`\n` で区切られます）。表示時は `reason().split("\n")[0]` で先頭行のみを取得するか、全体を表示してください。

```java
// 先頭行のみ表示
System.out.println(change.reason().split("\n")[0]);

// 全行表示
System.out.println(change.reason());
```

### 12.8 関連プロジェクト

| プロジェクト | リポジトリ | 関連 |
|---|---|---|
| japanpost-history | https://github.com/opaopa6969/japanpost-history | 時系列郵便番号辞書（2007〜現在） |
| ABRUtils | https://github.com/opaopa6969/ABRUtils | 住所基盤レジストリ検索ライブラリ（runtime 依存） |

---

*本仕様書は `municipality-history` v1.0.1 の実装に基づいて作成されました。*
*ソースコード: `/home/opa/work/municipality-history/`*
