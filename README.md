# municipality-history

日本の自治体統廃合（廃置分合等）の履歴データベース。e-Stat（政府統計の総合窓口）から1970年〜2028年の4,491件の自治体変遷データを収録。

## データ

| 項目 | 値 |
|------|-----|
| 期間 | 1970年 〜 2028年 |
| レコード数 | 4,491件 |
| 出典 | [e-Stat 廃置分合等情報](https://www.e-stat.go.jp/municipalities/cities/absorption-separation-of-municipalities) |

含まれる変更種別: 市制施行、政令指定都市施行、編入合併、新設合併、改称、郡変更、区の新設/廃止 等

## クイックスタート

```bash
mvn -q compile exec:java
```

## Java API

```java
var history = MunicipalityHistory.load(Path.of("data/estat-haichi.csv"));

// 地域コードで検索
history.findByCode("01100");  // 札幌市の変遷

// 自治体名で検索
history.findByName("石狩市"); // 石狩市関連の全変更

// 年別の変更件数
history.changeCountByYear();  // {1970=5, 1971=12, ..., 2005=432, ...}
```

## データ取得

`scripts/download-estat.mjs` でe-Statからデータをダウンロードできます（Playwright必要）。

```bash
npx playwright install chromium
node scripts/download-estat.mjs
```

## 関連プロジェクト

- [japanpost-history](https://github.com/opaopa6969/japanpost-history) — 時系列郵便番号辞書（2007〜現在）
- [ABRUtils](https://github.com/opaopa6969/ABRUtils) — 住所基盤レジストリ検索ライブラリ
