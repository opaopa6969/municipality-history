# はじめに

## 必要環境

- Java 21 以上
- Maven 3.x（または Gradle 対応）

---

## Maven 設定

`pom.xml` に依存を追加するだけで使えます。外部ファイルは不要です。

```xml
<dependency>
    <groupId>org.unlaxer</groupId>
    <artifactId>municipality-history</artifactId>
    <version>1.0.0</version>
</dependency>
```

Gradle (Kotlin DSL) の場合:

```kotlin
implementation("org.unlaxer:municipality-history:1.0.0")
```

---

## 最初の 5 行

```java
import org.unlaxer.municipality.MunicipalityHistory;

var history = MunicipalityHistory.loadBundled(); // jar 同梱 CSV から自動ロード

history.findByName("石狩").forEach(c ->
    System.out.println(c.effectiveDate() + " " + c.fullName() + " " + c.reason()));
```

出力例:

```
1996-09-01 石狩市 石狩町(01303)が石狩市(01215)に市制施行
2005-10-01 石狩市 厚田村(01304)、浜益村(01305)が石狩市(01215)に編入合併
```

---

## 基本的な使い方

### 辞書のロード

```java
// jar 同梱 CSV（推奨 — 外部ファイル不要）
var history = MunicipalityHistory.loadBundled();

// 外部 CSV ファイルから（ダウンロードした最新データを使う場合）
var history = MunicipalityHistory.load(Path.of("data/estat-haichi.csv"));
```

### 地域コードで検索

```java
// 札幌市（01100）の全変遷
List<MunicipalityChange> changes = history.findByCode("01100");
for (var c : changes) {
    System.out.printf("%s: %s%n", c.effectiveDate(), c.reason());
}
```

### 自治体名で検索（部分一致）

```java
// 「石狩」を含む全変更
var changes = history.findByName("石狩");
```

> **注意:** `findByName` は `reason`（改正事由）列も検索します。
> 関係自治体名として記載されたレコードも一致します。
> 詳細は [architecture.md](architecture.md#findbyname-の誤ヒット警告) を参照してください。

### 都道府県の全変遷

```java
var changes = history.findByPrefecture("北海道");
System.out.printf("北海道の変遷: %d件%n", changes.size());
```

### 変遷タイムライン

```java
var timeline = history.timeline("01215"); // 石狩市
timeline.forEach(c ->
    System.out.printf("[%s] %s%n", c.effectiveDate(), c.reason()));
```

### 指定日以降の変更

```java
// 2020年以降の全変更
var recent = history.changesSince(LocalDate.of(2020, 1, 1));

// 平成の大合併（2003〜2010年）
var heisei = history.changesSince(LocalDate.of(2003, 1, 1)).stream()
    .filter(c -> c.effectiveDate().isBefore(LocalDate.of(2010, 1, 1)))
    .toList();
System.out.printf("平成の大合併: %d件%n", heisei.size()); // → 2,444件
```

### 年別件数

```java
Map<Integer, Long> byYear = history.changeCountByYear();
byYear.forEach((year, count) ->
    System.out.printf("%d年: %d件%n", year, count));
// 2005年が 1,243 件でピーク（平成の大合併）
```

### メタ情報

```java
System.out.println(history.size());        // 3,507
System.out.println(history.prefectures()); // [北海道, 青森県, ..., 沖縄県]
```

---

## MunicipalityChange レコードのフィールド

| メソッド | 型 | 内容 |
|---|---|---|
| `lgCode()` | `String` | 標準地域コード（5桁） |
| `prefecture()` | `String` | 都道府県名 |
| `district()` | `String` | 郡・政令市・振興局等 |
| `districtKana()` | `String` | 郡等（ふりがな） |
| `municipality()` | `String` | 市区町村名 |
| `municipalityKana()` | `String` | 市区町村名（ふりがな） |
| `effectiveDate()` | `LocalDate` | 廃置分合等の施行日 |
| `reason()` | `String` | 改正事由（自由文、複数行あり） |
| `fullName()` | `String` | `district + municipality` の結合名 |

---

## データ更新

jar に同梱の CSV は固定バージョンです。e-Stat の最新データを取得するには:

```bash
npx playwright install chromium
node scripts/download-estat.mjs
```

ダウンロードした CSV を `MunicipalityHistory.load(Path)` で読み込んでください。
データソースの詳細は [data-source.md](data-source.md) を参照してください。
