# Galaxy S26 Ultra Local ADB Utility

Galaxy S26 Ultra自身からLocal ADBへ接続し、Samsung固有のカメラシャッター音強制設定を確認・変更するための、自己管理・自己署名を前提としたAndroidアプリです。

Phase 2のLocal ADB PoCまで完了しています。通常の`Settings.System` APIによる変更はSamsung側に拒否されましたが、USBを外した実機でLocal ADBのpairing、接続、固定`echo hello`に成功しました。設定を扱うMVPはまだありません。

## 対象操作

release版で許可する操作は次の3つだけです。

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
4. ⏳ read-only MVPを実装する。
5. 0/1への変更とread-back verificationを追加する。
6. dependency verification、manifest検査、backup除外を行う。

次のコマンドを使用します。

```shell
./gradlew assembleDebug
./gradlew test
./gradlew lint
./gradlew connectedAndroidTest
```

### Phase 1 probe（検証完了）

probeはdebug buildだけに存在し、広いspecial accessである`WRITE_SETTINGS`を一時的に要求します。release buildにはこの権限と画面を含めません。

```shell
./gradlew installDebug
```

SC-53Gではspecial access付与後も`Settings.System.putInt`が`IllegalArgumentException`で拒否されました。再確認する場合、端末で「Local Tweaks Probe」を開き、「設定変更の許可を開く」からspecial accessを付与します。検証後はspecial accessを解除してください。

## 開発に参加する場合

変更前に[AGENTS.md](AGENTS.md)を読み、セキュリティ制約とテスト要件を確認してください。新しい権限やdependencyを追加する場合は、目的、version、repository、license、攻撃面への影響を記録してください。

## 注意事項

本プロジェクトはSamsung Electronicsとは提携していない非公式プロジェクトです。利用する地域の法令、端末の利用条件、周囲への配慮に従い、自己責任で使用してください。

## ライセンス

このリポジトリは[MIT License](LICENSE)で公開します。
