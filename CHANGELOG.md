# 変更履歴

このプロジェクトのすべての注目すべき変更はこのファイルに記録します。

形式は [Keep a Changelog](https://keepachangelog.com/ja/1.1.0/) に準拠し、
バージョニングは [Semantic Versioning](https://semver.org/lang/ja/) に従います。

---

## [1.0.1] - 2026-04-19

### 追加

- `activeAt(LocalDate)` — 指定日時点で有効な自治体レコードを返す（lgCode ごと最新1件）
- `findByNameStrict(String)` — name 列のみを対象とした部分一致検索（reason 列誤ヒットを回避）
- `estatAppId()` — e-Stat appId を返す static メソッド（環境変数 `ESTAT_APP_ID` 優先、フォールバックあり）
- JUnit 5 テスト追加（loadBundled / findByName / findByNameStrict / activeAt / timeline / changesSince）
- CI ワークフロー追加（`.github/workflows/ci.yml`）

### 変更なし（後方互換維持）

- `findByName(String)` の挙動は変更なし（name + reason 両列検索）
- `MunicipalityChange` の signature は変更なし

---

## [1.0.0] - 2025-07-01

### 追加

- 初回リリース — e-Stat 廃置分合データ（1970〜2024年）を jar 同梱で提供
- `MunicipalityHistory.loadBundled()` — 外部ファイル不要でロード
- `MunicipalityHistory.load(Path)` — 外部 CSV ファイルからロード
- `findByCode(String)` — 標準地域コード（5桁）で検索
- `findByName(String)` — 自治体名の部分一致検索
- `findByPrefecture(String)` — 都道府県の全変遷取得
- `timeline(String)` — 地域コードの変遷タイムライン（日付昇順）
- `changesSince(LocalDate)` — 指定日以降の変更一覧
- `changeCountByYear()` — 年別変更件数マップ
- `MunicipalityChange` record — 8フィールド（lgCode, prefecture, district, districtKana, municipality, municipalityKana, effectiveDate, reason）
- Maven Central 公開（`org.unlaxer:municipality-history:1.0.0`）
- CSV データ 3,507 件 jar 同梱（97 KB）
