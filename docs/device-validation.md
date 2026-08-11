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

この観測だけでは、Auto BlockerがWireless Debugging経由のLocal ADBも遮断すると判断しない。Phase 2でUSB接続を外した状態のpairing、接続、固定command実行を別途検証する。
