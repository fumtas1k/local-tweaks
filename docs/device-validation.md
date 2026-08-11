# Galaxy S26 Ultra 実機検証記録

検証日: 2026-08-11

## 検証環境

| 項目 | 値 |
|---|---|
| 端末 | Samsung Galaxy S26 Ultra（SC-53G） |
| Android | 16 |
| One UI | 8.5 |
| CSC | DCM |
| Build ID | `BP4A.251205.006.SC53GOMS1AZF2` |
| Build fingerprint | `samsung/SC-53G/m3q:16/BP4A.251205.006/SC53GOMS1AZF2_QCM1AZF2:user/release-keys` |

端末のシリアル番号、IMEIなどの個体識別情報は記録していない。

## SettingsProvider検証

対象キー:

```text
csc_pref_camera_forced_shuttersound_key
```

Mac上のGoogle公式ADBからGET、PUT、GETによるread-backを実施した。

| 操作 | 結果 |
|---|---|
| 初期値GET | `1` |
| `1`から`0`へPUT後のGET | `0` |
| `0`から`1`へPUT後のGET | `1` |
| 最終的に`0`へPUT後のGET | `0` |

GETと双方向のPUTは、この端末・buildで成功した。シェルの`&`でPUTとGETを並列実行した際は古い値を取得したため、検証結果から除外した。read-backはPUT完了後に直列実行する。

## Samsung Cameraの挙動

| setting | サウンドモード | 観測結果 |
|---:|---|---|
| `1` | マナーモード | 比較的大きな強制シャッター音が鳴り、音量を調整できなかった。 |
| `0` | マナーモード | シャッター音は鳴らなかった。 |
| `0` | 通常 | `1`の強制音とは異なるシャッター音が鳴った。音量調整可否は未確認。 |

この結果から、`0`はシャッター音を常に無効化する値ではなく、少なくとも本環境では強制音を解除して端末のサウンドモードに従わせる値と推測する。他のCSC、build、Samsung Camera versionへ一般化しない。

## Phase 0判定

Phase 0の成功条件を満たした。

## Phase 1: 通常Android API検証

debug buildだけに`WRITE_SETTINGS`を宣言し、ユーザーがspecial accessを許可した状態で`Settings.System` APIを検証した。

| 操作 | 結果 |
|---|---|
| `Settings.System.canWrite(context)` | `true` |
| `Settings.System.getString(...)` | `0`を取得 |
| `Settings.System.putInt(..., 1)` | `IllegalArgumentException` |
| 例外後のADB GET | `0` |

例外メッセージ:

```text
You cannot keep your settings in the secure settings.
```

special accessを付与しても、このSamsungキーを通常の`Settings.System.putInt`では変更できない。書き込みは成立せず、値は`0`のままだった。最初のprobeは`SecurityException`だけを捕捉していたため終了したが、`IllegalArgumentException`を失敗結果として画面表示するよう修正した。修正版では同じ操作を行ってもprocessは終了せず、「通常Android APIでは変更不可」と表示されることを実機で確認した。

## Phase 1判定

通常Android API方式は不採用とし、Phase 2のLocal ADB PoCへ進む。probeの`WRITE_SETTINGS`、Activity、設定変更コードは引き続きdebug source setだけに置き、releaseへ含めない。

## 開発時のADB接続に関する注意

MacとのUSB ADB接続が途中で切れ、Gradleから`No connected devices`と判定される事象が発生した。このときGalaxyのAuto BlockerがONになっており、OFFにするとUSB ADB接続が回復した。

[Samsung公式仕様](https://www.samsung.com/us/support/answer/ANS10003636/)でも、Auto BlockerはUSBケーブル経由のcommandを遮断する。実機では手動でOFFにした後、時間経過後に再びONになっていたことを観測したが、再有効化の正確な条件は未確認である。USB ADBを使う開発作業の前にAuto Blockerの状態を確認する。

Phase 2の検証中にもAuto Blockerが再びONになり、USB経由のAPKインストールが途中で失敗して実機がADB一覧から消えた。OFFへ戻した直後は`unauthorized`となり、端末上でUSBデバッグを再許可すると復旧した。再有効化の条件は引き続き不明である。

この観測だけでは、Auto BlockerがWireless Debugging経由のLocal ADBも遮断すると判断しない。今回のLocal ADB検証はAuto BlockerをOFFにし、USBケーブルを外した状態で行った。

## Phase 2: Local ADB PoC

PoCはdebug buildだけに`libadb-android` 3.1.1、pairing UI、固定`echo hello`を追加した。接続先hostはコード内の`127.0.0.1`に固定し、入力欄はペアリング用ポート、ASCII 6桁コード、接続用ポートだけとした。

### Android Keystore RSA直接利用

最初に、Android Keystoreで生成したnon-exportable RSA private keyをTLS key managerへ直接渡した。custom ConscryptとAndroid標準TLS providerの両方でPairを試したが、いずれもTLS handshake中のRSA処理で失敗した。

```text
SSLHandshakeException: ... RSA routines:OPENSSL_internal:internal error
```

custom Conscryptでは`native_crypto.cc:741`、標準providerでは`native_crypto.cc:690`付近の同種エラーだった。この端末と`libadb-android` 3.1.1の組み合わせでは、Android Keystore RSA鍵の直接利用を不採用とする。

### AES-GCM保護したsoftware RSA鍵

software RSA 2048 key pairへ切り替え、PKCS#8 private keyをAndroid Keystore内のnon-exportable AES-256-GCM鍵で暗号化してアプリprivate storageへ保存した。平文private keyのencoded byte array、GCM IV、暗号文の一時byte arrayは処理後に上書きする。manifestではbackupを無効化し、data extraction rulesでもアプリデータをcloud backupとdevice transferから除外している。

USBケーブルを外し、Wi-FiとWireless DebuggingをONにした状態で次を確認した。

| 操作 | 結果 |
|---|---|
| `127.0.0.1:<pairing-port>`へ6桁コードでPair | 成功 |
| `127.0.0.1:<connection-port>`へ接続 | 成功 |
| debug固定`echo hello` | `hello` |
| APK上書き後、保存済み資格情報で再接続 | 成功、再pairing不要 |

最初のecho試行では、remote commandの正常終了をライブラリがEOFではなく`IOException: Stream closed.`として返した。受信済み出力を保持し、この既知のclose通知だけをEOFとして扱うよう修正した後、応答が正確に`hello`であることを確認した。

コードレビュー後、資格情報破損時の安全な削除と再pairing案内、process内で直列化したADB session、stream read timeout、応答上限、ランダムな証明書serialを追加した。既存のペアリング資格情報を保持したままAPKを上書きし、USBを外した実機で再度`Connect`と固定`echo hello`を実行して`hello`を確認した。

## Phase 2判定

SC-53Gの現行buildでは`libadb-android`方式を条件付き採用とする。Android Keystore RSA鍵の直接利用は避け、AES-GCMで保護したsoftware RSA鍵を使う。Phase 3では汎用stream APIをinfrastructure内部へ閉じ込め、固定のsetting GETだけを実装する。
