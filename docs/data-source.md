# データソース

## 出典

[e-Stat（政府統計の総合窓口）— 廃置分合等情報](https://www.e-stat.go.jp/municipalities/cities/absorption-separation-of-municipalities)

総務省が管理する公式統計データです。市区町村の合併・改称・区の新設廃止などの変遷が網羅されています。

---

## 収録範囲

| 項目 | 値 |
|---|---|
| 開始年 | 1970 年 |
| 終了年 | **2024 年**（2024-01-01 が最終レコード） |
| レコード数 | 3,507 件 |
| ファイルサイズ | 97 KB（jar 同梱） |

### 「1970〜2028年」表記について

README および一部ドキュメントで「1970〜2028年」と記載しています。
これは e-Stat データ上の将来予定年を含む**表示範囲**であり、
**実際に jar に収録されているレコードの最終日付は 2024-01-01 です。**

2022年・2023年のレコードが存在しないのは、該当期間に廃置分合等が行われていないためです。
2025年以降の予定合併は現バージョンには含まれていません。

---

## 含まれる変更種別

| 種別 | 説明 |
|---|---|
| 市制施行 | 町・村が市に昇格 |
| 政令指定都市施行 | 市が政令指定都市に移行 |
| 編入合併 | 既存自治体への吸収合併 |
| 新設合併 | 複数自治体が合併して新自治体を設立 |
| 改称 | 自治体名の変更 |
| 郡変更 | 郡の再編 |
| 区の新設・廃止 | 政令市の行政区設置・廃止 |

---

## CSV 列構成

e-Stat からダウンロードした CSV の列構成は以下の通りです。

| 列番号 | 列名 | 対応フィールド | 備考 |
|---|---|---|---|
| 0 | 標準地域コード | `lgCode` | 5桁の地方公共団体コード |
| 1 | 都道府県 | `prefecture` | 「北海道」など |
| 2 | 政令市・郡・支庁・振興局等 | `district` | 郡なし自治体では空文字 |
| 3 | 政令市・郡等（ふりがな） | `districtKana` | |
| 4 | 市区町村 | `municipality` | |
| 5 | 市区町村（ふりがな） | `municipalityKana` | |
| 6 | 廃置分合等施行年月日 | `effectiveDate` | YYYY-MM-DD 形式 |
| 7 | 改正事由 | `reason` | 旧自治体コードを含む自由文 |

---

## データ取得スクリプト

```bash
# Playwright が必要
npx playwright install chromium
node scripts/download-estat.mjs
```

スクリプトは e-Stat の廃置分合等情報ページから CSV を自動ダウンロードします。
取得した CSV は `data/estat-haichi.csv` に保存されます。

---

## ライセンスと利用条件

e-Stat のデータは [統計データ利用規約](https://www.e-stat.go.jp/terms-of-use) に基づき提供されています。
出典を明記することで商用・非商用を問わず利用可能です。

---

## 関連リソース

- [e-Stat 廃置分合等情報](https://www.e-stat.go.jp/municipalities/cities/absorption-separation-of-municipalities)
- [総務省 市区町村コード一覧](https://www.soumu.go.jp/denshijiti/code.html)
- `reason` 列の自由文解析方針 → [decisions/reason-parsing.md](decisions/reason-parsing.md)
