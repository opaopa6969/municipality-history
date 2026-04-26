# プロジェクト作法

## コーディング規約

- **Java 21** — record, sealed, pattern matching, virtual threads 活用
- **データモデル** — `record` で定義。イミュータブル。フィールドはprimitive優先
- **パッケージ構造** — `model/`, `parser/`, `store/`, `query/`, `api/` で責務分離
- **エラー処理** — 検査例外は呼び出し元に投げる。内部でログを出す場合はSystem.out/err
- **null** — 空文字やList.of()を使い、nullは避ける。外部入力のnullチェックは境界で
- **命名** — 日本語JavaDoc可。メソッド名・変数名は英語

## プロジェクト構造

```
src/main/java/org/unlaxer/<project>/
├── model/       # データモデル (record)
├── parser/      # パース・変換ロジック
├── store/       # DB/ファイルストア
├── query/       # 検索・クエリAPI
├── api/         # REST API (Javalin, optional)
└── App.java     # CLI エントリポイント
```

## ビルド・テスト

- `mvn -q compile` — コンパイル
- `mvn -q exec:java -Dexec.args="..."` — CLI実行
- `mvn package -DskipTests -Dgpg.skip=true` — jar生成
- `mvn install -DskipTests -Dgpg.skip=true` — ローカルinstall
- `mvn deploy` — Maven Central publish

## 共通パターン

### スナップショット戦略
1. まずRDB (PostgreSQL) に投入して動くものを作る
2. インメモリ辞書に変換（HashMap/TreeMap/カラムナー配列）
3. バイナリスナップショットに書き出し（String Pool + GZIP）
4. スナップショットからの高速ロード（DB不要）

### CLI パターン
```java
public static void main(String[] args) {
    String command = args[0];
    switch (command) {
        case "load" -> ...;
        case "query" -> ...;
        case "build-snapshot" -> ...;
        default -> printUsage();
    }
}
```

### REST API パターン (Javalin)
- 依存はoptional
- `GET /api/info` — メタ情報
- `GET /api/<resource>/{id}` — 単体取得
- `GET /api/<resource>/search?q=` — 検索
- JSON レスポンスは `Map.of()` / `LinkedHashMap` で構築
- 静的UIは `src/main/resources/static/index.html`

## ドキュメント

- **README.md** — Maven dependency、API例、データソース（日本語）
- **BACKLOG.md** — 未着手タスク、気づき
- **docs/*.md** — 設計ノート、分析結果

## Git

- コミットメッセージは英語、1-2行で要点
- `Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>`
- 機能単位でコミット、大きくまとめすぎない

## このプロジェクト固有

- **リポ**: https://github.com/opaopa6969/municipality-history (public, Maven Central)
- **概要**: 自治体統廃合履歴（1970-2028、3,507件、e-Stat）
- **現状**: v1.0.0 Maven Central公開済み。CSVデータjar同梱。loadBundled()で外部ファイル不要
- **データ取得**: scripts/download-estat.mjs (Playwright)
- **e-Stat appId**: 24edfb042993e87548e75f8e26f6f5421646a6fe
