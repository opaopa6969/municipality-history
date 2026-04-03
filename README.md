# municipality-history

[![Maven Central](https://img.shields.io/maven-central/v/org.unlaxer/municipality-history)](https://central.sonatype.com/artifact/org.unlaxer/municipality-history)

日本の自治体統廃合（廃置分合等）の履歴データベース。e-Stat（政府統計の総合窓口）から1970年〜2028年の3,507件の自治体変遷データを収録。

**CSVデータはjar内に同梱** — 依存を追加するだけで外部ファイル不要で使えます。

## Maven

```xml
<dependency>
    <groupId>org.unlaxer</groupId>
    <artifactId>municipality-history</artifactId>
    <version>1.0.0</version>
</dependency>
```

## ライブラリとして使う

### 辞書のロード

```java
import org.unlaxer.municipality.MunicipalityHistory;

// jar同梱のCSVからロード（外部ファイル不要）
var history = MunicipalityHistory.loadBundled();

// または外部CSVファイルから
var history = MunicipalityHistory.load(Path.of("data/estat-haichi.csv"));
```

### 地域コードで検索

```java
// 札幌市(01100)の変遷
var changes = history.findByCode("01100");
// → [1972-04-01: 札幌市の政令指定都市施行, ...]
```

### 自治体名で検索（部分一致）

```java
// 「石狩」を含む全変更
var changes = history.findByName("石狩");
// → [1996-09-01: 石狩町が石狩市に市制施行,
//    2005-10-01: 厚田村・浜益村が石狩市に編入, ...]

for (var c : changes) {
    System.out.printf("%s [%s] %s%s: %s\n",
        c.effectiveDate(), c.lgCode(), c.prefecture(), c.fullName(),
        c.reason().split("\n")[0]);
}
```

### 都道府県の全変遷

```java
var changes = history.findByPrefecture("北海道");
// → 北海道内の全自治体変更（合併・市制施行・改称等）
```

### 地域コードの変遷タイムライン

```java
var timeline = history.timeline("01235"); // 石狩市
// → [1996-09-01: 市制施行, 2005-10-01: 厚田村・浜益村を編入]
```

### 指定日以降の変更

```java
// 2020年以降の全変更
var recent = history.changesSince(LocalDate.of(2020, 1, 1));

// 平成の大合併（2003-2010）の変更
var heisei = history.changesSince(LocalDate.of(2003, 1, 1)).stream()
    .filter(c -> c.effectiveDate().isBefore(LocalDate.of(2010, 1, 1)))
    .toList();
// → 2,444件
```

### 年別の変更件数

```java
Map<Integer, Long> byYear = history.changeCountByYear();
// → {1970=87, 1971=144, ..., 2005=1243, ...}
//   2005年が1,243件で圧倒的ピーク（平成の大合併）
```

### メタ情報

```java
history.size();         // 3,507件
history.prefectures();  // [北海道, 青森県, ..., 沖縄県]
```

## データ

| 項目 | 値 |
|------|-----|
| 期間 | 1970年 〜 2028年 |
| レコード数 | 3,507件 |
| 出典 | [e-Stat 廃置分合等情報](https://www.e-stat.go.jp/municipalities/cities/absorption-separation-of-municipalities) |
| jarサイズ | 97 KB（CSVデータ同梱） |

### 含まれる変更種別

市制施行、政令指定都市施行、編入合併、新設合併、改称、郡変更、区の新設/廃止 等

### CSV列構成

| 列 | 内容 |
|----|------|
| 標準地域コード | 5桁の地方公共団体コード |
| 都道府県 | |
| 政令市・郡・支庁・振興局等 | |
| 政令市・郡等（ふりがな） | |
| 市区町村 | |
| 市区町村（ふりがな） | |
| 廃置分合等施行年月日 | YYYY-MM-DD |
| 改正事由 | 合併の詳細（旧自治体コード等を含む） |

## データ取得スクリプト

`scripts/download-estat.mjs` でe-Statから最新データをダウンロードできます（Playwright必要）。

```bash
npx playwright install chromium
node scripts/download-estat.mjs
```

## 関連プロジェクト

- [japanpost-history](https://github.com/opaopa6969/japanpost-history) — 時系列郵便番号辞書（2007〜現在）
- [ABRUtils](https://github.com/opaopa6969/ABRUtils) — 住所基盤レジストリ検索ライブラリ
