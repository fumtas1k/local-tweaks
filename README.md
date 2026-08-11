# Galaxy S26 Ultra Local ADB Utility

Galaxy S26 Ultra自身からLocal ADBへ接続し、Samsung固有のカメラシャッター音強制設定を確認・変更するための、自己管理・自己署名を前提としたAndroidアプリです。

Phase 5のhardeningまで完了しています。通常の`Settings.System` APIによる変更はSamsung側に拒否されましたが、Local ADBのpairingと接続に成功し、固定したGETと0/1へのPUTを実行できます。PUT後は同じ接続上で必ずGETし、一致した場合だけ成功として表示します。

## 対象操作

現在のMVPで実装済みの操作は次の3つだけです。

```shell
settings get system csc_pref_camera_forced_shuttersound_key
settings put system csc_pref_camera_forced_shuttersound_key 0
settings put system csc_pref_camera_forced_shuttersound_key 1
```

`0`または`1`へ変更しても、実際のカメラ動作は地域、CSC、Android、One UI、Samsung Cameraのバージョンに依存します。本アプリは「シャッター音OFF」を保証せず、設定のraw値と検証結果を正確に表示します。

## 設計方針

- rootとShizukuを使用しない。
- Android標準のWireless Debuggingを使用する。
- ADB接続先を端末自身の`127.0.0.1`に固定する。
- MVPではmDNS、LAN探索、任意host入力を実装しない。
- PUT後は必ずGETし、期待値との一致を確認する。
- 任意shell、APK操作、ファイル転送、外部API通信を提供しない。
- ADB秘密鍵をAndroid KeystoreまたはKeystoreで保護した暗号文として保存し、backup対象から除外する。

## ドキュメント

- [要件定義](docs/requirements.md)
- [技術設計案](docs/technical-design.md)
- [実機検証記録](docs/device-validation.md)
- [Contributor Guide](AGENTS.md)

要件と設計が食い違う場合は、まず文書を更新して判断を明文化してから実装します。

## 開発状況

実装順序は次のとおりです。

1. ✅ Galaxy S26 Ultra実機で対象settingのGET・PUT・read-backを確認する。
2. ✅ debug variantで通常の`Settings.System` APIを検証し、変更不可を確認する。
3. ✅ Local ADBのpairing、接続、debug限定の固定`echo hello`をPoCする。
4. ✅ read-only MVPを実装し、実機でraw値を表示する。
5. ✅ 0/1への変更、read-back verification、資格情報リセットを追加する。
6. ✅ dependency lock/verification、manifest・artifact・backup・logging検査を追加する。

次のコマンドを使用します。

```shell
./gradlew assembleDebug
./gradlew test
./gradlew lint
./gradlew connectedAndroidTest
```

### Phase 5 hardened MVP

通常起動時は「ホーム」画面を表示します。ホームには接続状態と機能一覧があり、「接続設定」画面でWireless Debuggingの接続用ポートを入力して接続します。接続成功後はホームへ戻り、接続済みの「カメラ設定」カードから現在値の読み取りと、raw設定値に対応したON=1／OFF=0のSwitch操作を実行できます。未接続時のカメラ設定カードは利用できず、接続設定へ誘導します。未設定は「未設定」、0/1以外は解釈せずそのまま表示します。書き込み後のread-backが期待値と異なる場合は成功扱いにしません。接続設定とカメラ設定からはホームへ戻れます。

```shell
./gradlew installDebug
```

ADB資格情報は確認ダイアログから削除できます。削除後はアプリprocessを再起動し、Wireless Debuggingで再pairingしてください。Phase 1/2の一時的なprobe、`WRITE_SETTINGS`、固定`echo hello`は削除済みです。release manifestで許可するpermissionは`INTERNET`だけです。

通常の検証はstrict dependency lockを有効にしたオフラインbuildで実行できます。依存関係を意図的に変更した場合だけlockとchecksumを更新し、差分を確認してください。

```shell
./gradlew --offline test lint check
./gradlew :app:dependencies --write-locks
./gradlew --write-locks --write-verification-metadata sha256 test lint check
```

## 開発に参加する場合

変更前に[AGENTS.md](AGENTS.md)を読み、セキュリティ制約とテスト要件を確認してください。新しい権限やdependencyを追加する場合は、目的、version、repository、license、攻撃面への影響を記録してください。

## 注意事項

本プロジェクトはSamsung Electronicsとは提携していない非公式プロジェクトです。利用する地域の法令、端末の利用条件、周囲への配慮に従い、自己責任で使用してください。

## ライセンス

このリポジトリは[MIT License](LICENSE)で公開します。
