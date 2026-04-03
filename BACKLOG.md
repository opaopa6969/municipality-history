# municipality-history バックログ

## 他プロジェクトとの連携

### municipality-history → japanpost-history
- [x] ~~PostcodeWithContext~~ — 郵便番号変遷に統廃合理由を付与（japanpost-history側で実装済み）
- **地域コードでの自動紐づけ強化** — 現在は名前マッチ。lgCodeベースで正確に紐づけ

### municipality-history → ABRUtils
- **旧市町村名→現市町村名マッピングテーブル** — ABRの住所検索で旧名でも引けるように
- **PostcodeAccuracyChecker の no_result 分析** — 「この住所が見つからないのは合併で名前が変わったから」を判定
- **ABR free-search に合併情報バッジ** — 「この自治体は2005年に合併しました」を検索結果に付与

## データ拡充

- **Geoshape 歴史的行政区域データセット** — 明治以降の行政区域（現在のデータは1970年〜）
- **e-Statの定期更新** — Playwright スクリプトで最新データを自動取得
- **合併前後の地域コード対応表** — 旧コード → 新コードの変換テーブル構築
- **GeoJSON境界データの統合** — 自治体の境界変遷を地図上で可視化

## 機能改善

- REST API + 検索UI（japanpost-historyと同様のJavalin構成）
- 合併ネットワークの可視化（どの自治体がどこに吸収されたかのグラフ）
