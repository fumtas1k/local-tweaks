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

## Phase 3: read-only MVP

Phase 2のdebug APKへPhase 3版を上書きし、保存済み資格情報を保持したままSC-53Gで検証した。Compose UIから入力できる接続情報はポート番号と6桁のペアリングコードだけで、ADB hostは`127.0.0.1`、shell serviceは次の固定値である。

```text
shell:settings get system csc_pref_camera_forced_shuttersound_key
```

| 操作 | 結果 |
|---|---|
| 保存済み資格情報で接続 | 成功、再pairing不要 |
| 固定GET | `0` |
| Compose UIの現在値表示 | `Current value: 0` |
| 画面回転 | 接続用ポート、状態、現在値を保持 |
| USBケーブルを外した後の再GET | `0`、Local ADB単独で成功 |

Phase 1の`WRITE_SETTINGS`、Phase 2の固定`echo hello`、debug probe Activityは削除した。Unit Test、lint、debug/release build、dependency checksum検証に加え、release merged manifestのpermissionが`INTERNET`だけであることを自動検査した。

## Phase 3判定

固定GETの値を型付きで読み取り、SC-53GのCompose UIへraw値を表示する成功条件を満たした。次はPhase 4で、固定した0/1へのPUTと同一接続上のGETによるread-back verificationを追加する。

## Phase 4: write MVP

Phase 3版へ固定PUT 0/1とread-back verificationを追加し、同じ保存済み資格情報を保持したままSC-53Gで検証した。UI、ViewModel、feature repositoryはcommand文字列を扱わず、0と1への型付き操作だけを公開する。

| 操作 | 結果 |
|---|---|
| 現在値GET | `0` |
| 固定PUT 1後の同一session GET | `1`、UIでverified success |
| 固定PUT 0後の同一session GET | `0`、UIでverified success |
| Mac公式ADBによる最終値確認 | `0` |
| 資格情報リセット確認ダイアログ | 表示とキャンセルに成功 |
| 資格情報削除 | アプリ管理のADB identityだけを削除し、再起動案内を表示 |
| process再起動前の再pairing | 失敗（想定どおり） |
| process再起動後の再pairing | 成功 |
| 再pairing後の接続と固定GET | 成功、`0` |

書き込み結果はPUT streamの完了だけで判断せず、直後のGETが期待するraw値と一致した場合だけ成功とした。timeout、不正出力、transport失敗、read-back mismatchは別の型とUI状態で扱う。

## Phase 4判定

固定した3コマンドだけで読み取り、0/1への変更、read-back verificationが成立した。資格情報リセットと再pairing導線も実機で確認した。最終値は`0`へ戻した。次はPhase 5のhardeningと実機test matrixへ進む。

## Phase 5: hardening

Phase 4の実機操作で、資格情報削除後も再起動前にPairを押せるため、想定どおり失敗するものの案内が不十分だった。Phase 5では再起動必須を型付き状態として保持し、Pair、Connect、GET、PUT 0/1、再リセットを無効化した。

| 検証項目 | 結果 |
|---|---|
| 既存資格情報を保持したAPK上書き | 成功 |
| 資格情報削除後の案内 | process再起動必須を表示 |
| 再起動前のADB操作 | 全操作を無効化 |
| process再起動後の新規pairing | 成功 |
| 再pairing後の接続と固定GET | 成功、`0` |
| Mac公式ADBによる最終値確認 | `0` |
| strict dependency lock付きoffline build | Unit Test、lint、release検査すべて成功 |
| release merged manifest | permissionは`INTERNET`だけ、backup無効 |
| release source/artifact | 固定3コマンドだけ、旧probeとproduction loggingなし |

対象test matrixはSC-53G、Android 16、One UI 8.5、CSC DCM、Build ID `BP4A.251205.006.SC53GOMS1AZF2`で完了した。USBを外したLocal ADB、画面回転、0/1 read-back、資格情報削除と再pairingを含む。他のCSC、build、One UI 9は未検証であり、結果を一般化しない。

## Phase 5判定

現行の対象端末とbuildについてMVPのDefinition of Doneを満たした。dependencyまたはpermissionを変更する場合は、strict lock、SHA-256 verification、merged manifest、release artifactの検査を再実行する。

## OSアップデート後のペアリング消失（2026-09-10、ユーザー報告）

ユーザー報告によると、SC-53GのOSアップデート後に次の事象があった。

- 強制シャッター音設定がリセットされていた（ユーザー報告）。
- Wireless Debuggingの「ペア設定済みのデバイス」一覧が空になっていた（スクリーンショットで確認）。
- 端末側の接続用ポート表示と一致するポートをアプリへ入力しても、アプリは「接続に失敗しました」とだけ表示した。

アップデート後のbuild番号、失敗時にアプリ内部を通った例外経路（`ConnectFailure`のどの分類に該当したか）、再ペアリングで復旧したかどうかは、いずれも未記録・未検証である。原因は「ペアリング情報が端末側から失われた状態でのconnect試行」と推測されるが、これは推測であり、本記録は事実として確認できた3点（上記の箇条書き）とユーザー報告の範囲に限定する。

この事象を受け、接続失敗を`RestartRequired` / `PairingRequired` / `PortUnavailable` / `Other`に分類し、`PairingRequired`および汎用失敗の`Other`（`ConnectionFailed`）ではペアリングセクションを自動展開するようアプリを修正した（`docs/technical-design.md` 3.3、`docs/ui-design.md` の「状態表示」「接続設定」参照）。次回同様の事象が発生した場合は、build番号とアプリの表示状態（`MainStatus`）を記録し、この節を更新する。
