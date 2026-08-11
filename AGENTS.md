# Repository Guidelines

## プロジェクト構成

このリポジトリは現在、Local ADB PoCを行うPhase 2にある。プロダクト要件とセキュリティ要件は`docs/requirements.md`、実装方式の判断は`docs/technical-design.md`、実機結果は`docs/device-validation.md`を正とする。

Androidモジュールは`app/`に置く。Phase 1の一時的なprobeは`app/src/debug/`だけに配置する。製品用Kotlinコードは`app/src/main/java/`以下で責務別に分け、Local ADB基盤を`adb/`、ドメインとRepositoryを`feature/shutter/`、Jetpack Compose画面を`ui/`へ置く。リソースは`app/src/main/res/`、Unit Testは`app/src/test/`、Instrumented Testは`app/src/androidTest/`に配置する。

## ビルド・テスト・開発コマンド

- `./gradlew assembleDebug` — debug APKをビルドする。
- `./gradlew installDebug` — 接続端末へPhase 1 probeをインストールする。
- `./gradlew test` — JVM上のUnit Testを実行する。
- `./gradlew connectedAndroidTest` — 接続端末でInstrumented Testを実行する。
- `./gradlew lint` — Androidの静的解析を実行する。

文書だけの変更でも、コミット前に`git diff --check`と`git status --short`を確認する。

## コーディング規約と命名

Kotlinは4スペースでインデントする。型とCompose関数は`UpperCamelCase`、関数とpropertyは`lowerCamelCase`、定数は`UPPER_SNAKE_CASE`とする。UI、ドメイン、ADB transportの責務を分離する。汎用command実行APIではなく、`setForcedShutterSoundSettingToZero()`のような型付き操作を使用する。Androidプロジェクト作成時に導入するformatterとlint設定に従う。

## テスト方針

ポートとペアリングコードの検証、設定値のparse、接続状態、timeout、PUT後のGETによるread-backをテストする。テスト名は`readReturnsNotSetForNullOutput`のように観測可能な振る舞いを表す。権限、dependency、manifestを変更した場合はmerged manifestを確認する。実ADBはGalaxy S26 Ultraで検証し、OS、One UI、CSC、build numberを記録する。

## セキュリティと設定

releaseコードで許可するのは、要件に定義された3つの固定settings操作だけとする。Phase 1の`WRITE_SETTINGS`とprobe Activityはdebug source setから外へ出さない。任意shell入力、Shizuku、mDNS/LAN探索、Analytics、外部API通信を追加しない。ADB hostはnumeric loopback `127.0.0.1`に固定する。ペアリングコード、秘密鍵、署名素材、認証情報をログ出力またはコミットしない。

## コミットとPull Request

既存履歴に合わせ、`docs: add requirements and technical design`のようなConventional Commits形式を使用する。prefixは`docs:`、`feat:`、`fix:`、`test:`などに限定し、1コミットの目的を絞る。Pull Requestには変更範囲、セキュリティへの影響、実行したテスト、新しい権限やdependencyとそのlicenseを記載する。関連Issueをリンクし、UI変更にはスクリーンショットを添付する。
