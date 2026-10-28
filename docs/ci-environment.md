# CI の環境別設定

workflow の環境差分は Repository Variables で設定します。
caulis / opaopa6969 で同じ YAML を使い、runner を変えるために片側だけを commit しないでください。
設定場所は Settings → Secrets and variables → Actions → Variables です。

| Variable | caulis | opaopa6969 |
|---|---|---|
| `CI_RUNNER_LABELS` | `["ubuntu-latest"]` | `["self-hosted","Linux","X64","municipality-history-ci"]` |

Actions の依存キャッシュは `CI_ACTIONS_CACHE_ENABLED=false` で無効にできます（未設定時は有効）。
個人側では runner の論理パスと実体パスが異なり `hashFiles` が依存ファイルを見つけられないため
`false`、caulis 側は `true` に設定しています。Maven / pip 自体のローカルキャッシュとテストは有効です。

runner の変数は JSON 配列で、未設定時は `["ubuntu-latest"]` です。
`runs-on` は runner 起動前に評価されるため、シェル変数や Environment の変数ではなく
Repository / Organization Variables の `vars` を使います。認証情報は Secrets に保持します。

通常のソース変更は caulis 側の PR に集約します。個人側へのコード同期は
vacant-dev-workspace の `ws mirror` で fast-forward に限定して実施します。
環境設定は Git の同期対象に含まれません。

関連: https://github.com/caulis/vacant-dev-workspace/issues/22
