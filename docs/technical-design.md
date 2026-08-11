# Galaxy S26 Ultra Local ADB Utility — 技術設計案

更新日: 2026-08-11

## 1. 結論

MVPは次の構成で進める。

```text
Compose UI
  ↓ 型付き操作のみ
ShutterSoundRepository
  ↓ 固定コマンドのみ
LocalAdbGateway
  ↓ TLS pairing / ADB transport
127.0.0.1:<user-entered port>
  ↓
端末自身の adbd（shell UID 2000）
```

- PoCの第一候補は `libadb-android` とする。
- LADBは動作方式の参考にするが、コードやプリビルド `libadb.so` は取り込まない。
- MVPではmDNS discoveryを実装しない。
- 接続先ホストはコードで `127.0.0.1` に固定し、ユーザーが入力できるのはポート番号と6桁のペアリングコードだけにする。
- Android Keystore内の非exportable RSA鍵を直接使う方式は、実機TLS handshakeで失敗したため不採用とする。
- software RSA秘密鍵をKeystoreのAES-256-GCM鍵で暗号化し、アプリprivate storageへ保存する。
- releaseビルドで実行できる操作は設定値のGET、0へのPUT、1へのPUTだけとする。

この設計はGalaxy S26 Ultraの出荷時環境であるAndroid 16 / One UI 8.5を基準とする。One UI 9 betaを含む将来アップデートは実機回帰テストの対象にする。

## 2. 調査結果

### 2.1 Android Wireless Debugging

Android 11以降のWireless Debuggingは、従来の `adb tcpip 5555` とは別のTLS接続を使用する。ペアリングサービスと接続サービスは別ポートで、どちらも動的に変わり得る。

AOSPが定義するmDNS service typeは次のとおり。

```text
_adb-tls-pairing._tcp  ペアリング用
_adb-tls-connect._tcp  ペアリング後の接続用
```

ペアリング後のADB通信もTLSで暗号化される。ペアリングコード方式では、設定画面に6桁のコードとペアリング用IPアドレス・ポートが表示される。

MVPでは、この仕組みを次のように利用する。

1. アプリからWireless Debugging設定画面を開く。
2. ユーザーが「ペア設定コードによるデバイスのペア設定」を開く。
3. ユーザーがペアリング用ポートと6桁コードをアプリへ入力する。
4. アプリは `127.0.0.1:<pairing-port>` へpairingする。
5. ユーザーがWireless Debuggingのメイン画面に表示される接続用ポートを入力する。
6. アプリは `127.0.0.1:<connection-port>` へTLS接続する。

ペアリング用ポートと接続用ポートは同じものとして扱わない。

### 2.2 Galaxy S26 Ultraの基準環境

Samsungの公式情報ではGalaxy S26シリーズの出荷時OSはAndroid 16 / One UI 8.5。2026年5月からOne UI 9 betaも提供されているため、実機の正確なビルド番号をテスト記録に残す。

設計上はAndroid 16（API 36）を現在の基準とし、Android 17（API 37）のLocal Network permission変更も先行して考慮する。

### 2.3 LADB

LADBは端末内にADB実行ファイル相当のネイティブライブラリを同梱し、`localhost` のWireless Debuggingへpair/connectする実績がある。現行ソースはcompileSdk/targetSdk 36で、Android 16への追随実績も確認できる。

ただし採用しない理由は次のとおり。

- `jniLibs` に複数ABI向けプリビルド `libadb.so` を同梱しており、ビルド由来の監査が難しい。
- 現行LICENSEには、原作者以外がGoogle Play Storeへアップロードすることを禁止する追加条項がある。
- LADB自体が汎用shellを目的としており、本プロジェクトの最小機能方針と異なる。
- 現行のmDNS実装は、発見したIPが自端末IPと不一致でも候補から除外しないコードになっており、そのまま参考実装として採用できない。
- `WRITE_SECURE_SETTINGS`、位置情報、Wi-Fi状態変更など、本アプリには不要な権限も宣言している。

LADBから採用するのは、次の運用上の知見だけとする。

- 同一端末から `localhost:<pairing-port>` へpairingできる。
- pairing dialogを閉じるとコードが無効になる場合があるため、分割画面で操作する。
- pairing portとconnection portを分ける。
- connection portはWireless Debuggingの再起動などで変化し得る。

### 2.4 採用候補

| 候補 | TLS pairing | ライセンス | 評価 |
|---|---:|---|---|
| `libadb-android` 3.1.1 | 対応 | 本体はGPL-3.0-or-laterまたはApache-2.0。SPAKE2依存はLGPL-3.0 | PoC第一候補。Java APIで監査しやすいが、未監査・依存ライセンス・Keystore互換性の確認が必要 |
| AOSP adbの必要部分をAndroid向けにビルド | 対応 | 主にApache-2.0。構成要素ごとの確認が必要 | 公式実装に最も近いが、NDKビルド、更新追随、バイナリ再現性の負担が大きい。第二候補 |
| LADBの `libadb.so` | 対応 | Google Play制限付きの独自条項あり | 不採用。参考実装のみ |
| `dadb` | 未対応 | Apache-2.0 | shell v2や型付きエラーは魅力だが、物理端末では既存adb server前提でWireless Debugging pairingを提供しないため不採用 |

### 2.5 `libadb-android` の条件付き採用

`libadb-android` はpairing、TLS connection、mDNS、shell streamを提供する。2025年12月にSPAKE2依存更新があり、完全に放置されたプロジェクトではない。一方、README自身がセキュリティ監査未実施と明記している。

採用条件は次のすべてを満たすこと。

- Galaxy S26 Ultra実機でpair/connectできる。
- 接続先を `127.0.0.1` に固定できる。
- Android KeystoreのRSA private keyとcertificateを直接使用できる、または安全な暗号化保存へ切り替えられる。
- pairing codeがログ、例外メッセージ、永続ストレージへ残らない。
- 必要な依存のバージョン、commit、checksum、ライセンスを記録できる。
- LGPL-3.0のSPAKE2依存について、再配布時のnotice・source提供・改変/再リンク要件を満たせる。
- アプリからライブラリの汎用shell APIを公開しない。

PoCではJitPackの可変なブランチ指定を使わず、既知のversionまたはcommitへ固定する。解決済みAARと推移的依存のchecksumは`gradle/verification-metadata.xml`へ登録し、Gradleの依存検証で監査する。

### 2.6 Phase 2で解決した依存物

Local ADB関連dependencyはdebug configurationだけに置く。JitPackは`com.github.MuntashirAkon`配下だけをexclusive content filterで許可する。

| artifact | version / source | SHA-256 |
|---|---|---|
| `libadb-android` AAR | 3.1.1 / tag commit `c849886ebc6d48e7b46d967e78a6bb65c90c3b74` | `e6fcb495a20a507ca4a125afc9c554e838a84b3a15fbcc29f7deafd0f88bbb58` |
| `spake2-android` AAR | 2.2.1 / tag commit `7615ddd680b990e14513ebb66eac4cb0dbf82464` | `8798fb6c04b5d53a6307ed9481e9afe563227abd4e8b9e917c8514fa850071bb` |
| `bcprov-jdk15to18` JAR | 1.81 | `0ef9c4d9536719aaa1c92a7c694d87981fa9d4d64dd9479cc322a08aebbc651e` |
| `bcpkix-jdk15to18` JAR | 1.81 | `aa204a0c8b5f5564bf2b1a055e2550534b24217b23f63ea4cb762c908ff811d9` |
| `bcutil-jdk15to18` JAR | 1.81 | `82fc2c3bc17c06a6fc4e28b73adf1601a01b91a97f7b72c0cfbabaf8bf1f3fe7` |
| `conscrypt-android` AAR | 2.5.3 | `551ae4e301c571760d1791e647db6ed1dcb10d34dcae7aa12b67f220f2ce98d1` |

`libadb-android`はApache-2.0選択、ConscryptはApache-2.0、Bouncy Castleは同プロジェクトのpermissive licenseとして扱う。`spake2-android`はLGPL-3.0であり、配布前にnoticeとsource提供手順を実装する。checksumは今回解決したbinaryの監査記録であり、Release runtime classpathへdebug依存が混入しないGradle検査も実行する。

## 3. 接続設計

### 3.1 mDNSをMVPから外す理由

mDNS discoveryは同じLAN上の他ADB端末も発見する。サービス名だけでは自端末であることを安全に証明できず、LADBのようなヒューリスティック選択は「他端末へ接続しない」という要件を満たさない。

さらに、Android 16ではLocal Network制限のopt-in検証が始まり、Android 17 targetではLocal Network permissionが原則必須になる。MVPでmDNSを外せば次が成立する。

- LAN探索を行わない。
- IPアドレス入力欄を作らない。
- 位置情報権限、Wi-Fi scan権限、MulticastLockを不要にできる。
- 将来のLocal Network permissionへの依存を小さくできる。

connection portの手入力は操作が一段増えるが、MVPのセキュリティ優先順位には合致する。

### 3.2 Endpoint validation

Local ADB層は外部からhost文字列を受け取らない。

```kotlin
private val LOCAL_ADB_ADDRESS = InetAddress.getByAddress(
    byteArrayOf(127, 0, 0, 1)
)
```

ポート入力には次を強制する。

- ASCII数字のみ。
- `1..65535` の範囲。
- 先頭・末尾の空白だけは除去する。
- hostname、IPv4、IPv6、URI、区切り文字を受け付けない。
- 保存しない。画面回転中のstateに残す場合もプロセス内だけにする。

ライブラリへ渡す直前にもhostがloopbackであることをassertする。テストではLANアドレスやhostnameを渡せるpublic APIが存在しないことを確認する。

### 3.3 Pairing state machine

```text
Idle
  → OpeningWirelessDebuggingSettings
  → AwaitingPairingInput
  → Pairing
  → Paired
  → AwaitingConnectionPort
  → Connecting
  → Connected
  → ReadingSetting
```

失敗時は状態を分ける。

```text
InvalidPort
InvalidPairingCode
PairingDialogExpired
PairingRejected
TlsHandshakeFailed
AuthenticationFailed
ConnectionPortUnavailable
ConnectionFailed
ConnectionLost
```

pairing codeは6桁のASCII数字として検証し、pairing呼び出し後すぐメモリ上の参照を破棄する。Kotlin/JVMではStringの確実なzeroizeはできないため、ログ・saved state・clipboard・永続化へ渡さないことを主な防御とする。

### 3.4 再接続

ADB認証鍵はペアリング後も有効だが、connection portは変わる可能性がある。

- アプリ起動時に自動でポートscanしない。
- 直前のconnection portを永続保存しない。
- 接続が切れたら現在のconnection portを再入力してもらう。
- 認証失敗時は、設定画面で本アプリのpaired deviceをForgetしてから再pairingする導線を出す。
- アプリ内に「ADB資格情報を削除」操作を用意し、Keystore aliasと関連ファイルを削除する。

## 4. コマンド実行境界

### 4.1 型付き操作

UI、ViewModel、feature repositoryはcommand文字列を扱わない。

```kotlin
internal sealed interface ShutterOperation {
    data object Read : ShutterOperation
    data object SetZero : ShutterOperation
    data object SetOne : ShutterOperation
}

internal interface ShutterAdbDataSource {
    suspend fun read(): ShutterReadResult
    suspend fun setZero(): ShutterWriteResult
    suspend fun setOne(): ShutterWriteResult
}
```

ADBライブラリの `openStream`、`shell`、`connect(host, port)` など汎用APIはADB infrastructure package内部だけで使用する。

### 4.2 固定コマンド

releaseビルドのcommand tableは次の3つだけ。

```text
settings get system csc_pref_camera_forced_shuttersound_key
settings put system csc_pref_camera_forced_shuttersound_key 0
settings put system csc_pref_camera_forced_shuttersound_key 1
```

ADB shell serviceはargv APIではなくcommand文字列を受けるため、`List<String>` を渡すだけで安全になるとは見なさない。上記の完全な定数を操作ごとに選択し、文字列連結やshell escapingを実装しない。

PoCの `echo hello` はdebug source setまたはinstrumented testにのみ置き、release artifactへ含めない。

### 4.3 値のモデル

`Int?` は使用しない。未知値を失わず表示する。

```kotlin
sealed interface ForcedSettingValue {
    data object NotSet : ForcedSettingValue
    data class Present(val raw: String) : ForcedSettingValue
}
```

parse規則:

- 行末のCR/LFと外側whitespaceを除去する。
- 正確に `null` なら `NotSet`。
- それ以外は `Present(raw)`。
- `0` と `1` だけUI説明を付ける。
- 空文字、複数行、過大出力はprotocol errorとして扱う。
- 最大出力長を小さく制限する（例: 128 bytes）。

### 4.4 書き込み確認

PUT後は同じ接続上で必ずGETする。

```text
SetZero → GET → raw == "0" なら成功
SetOne  → GET → raw == "1" なら成功
```

PUTのexit codeが得られないライブラリでも、read-back mismatchを失敗として扱える。transport切断、timeout、異常出力、read-back mismatchを区別する。

「復元」は元の値が1だと確認できる場合にしか成立しないため、基本ラベルは「強制設定を0に変更」「強制設定を1に変更」とする。元の値が未設定または未知値なら、1への操作を「復元」と表示しない。

## 5. ADB鍵設計

### 5.1 不採用: Android Keystore RSA鍵を直接利用

RSA 2048 key pairを `AndroidKeyStore` providerで生成する。private key materialはexportできず、公開鍵とX.509 certificateは取得できる。

SC-53G実機では、custom ConscryptとAndroid標準TLS providerの両方でRSA処理の内部エラーとなりpairingできなかった。`libadb-android`のTLS key managerとnon-exportable Android Keystore RSA private keyの組み合わせには互換性がないと判断する。詳細は[`device-validation.md`](device-validation.md)を参照する。

### 5.2 採用: encrypted PKCS#8

software RSA 2048 key pairを生成し、PKCS#8 private keyを暗号化保存する。

```text
Android Keystore AES-256-GCM key
  ↓ encrypt/decrypt
PKCS#8 ADB private key ciphertext
  ↓
app private files（backup/transfer除外）
```

- AES keyはnon-exportable。
- GCM nonceを再利用しない。
- ciphertext、nonce、versionだけを `noBackupFilesDir` に置く。
- 復号済みbyte arrayは使用後に上書きする。
- Java PrivateKey objectの完全なzeroizeは保証できないため、process lifetimeを越えて保持しない。
- 復号・parse失敗時は鍵を再生成し、ユーザーへ再pairingを案内する。

この方式で実機のpairing、接続、アプリ再インストール後の再接続、固定`echo hello`まで成功した。

### 5.3 Backupと削除

- manifestで `android:allowBackup="false"` を明示する。
- Android 12以降のdevice-to-device transfer差異に備え、`dataExtractionRules` でもcloud backupとdevice transferから資格情報を除外する。
- ADB資格情報、connection port、pairing codeをbackup対象にしない。
- 「ADB資格情報を削除」でKeystore aliasと暗号化秘密鍵ファイルを削除し、libadbのTLS状態を避けるためプロセス再起動後の再pairingを案内する。
- アプリアンインストール後は再pairingが必要であることを正常動作とする。

## 6. Android権限

MVPのLocal ADB方式で必要と見込む権限は次だけ。

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

宣言しないもの:

- `WRITE_SETTINGS`
- `WRITE_SECURE_SETTINGS`
- `ACCESS_FINE_LOCATION`
- `ACCESS_COARSE_LOCATION`
- `ACCESS_WIFI_STATE`
- `CHANGE_WIFI_STATE`
- `NEARBY_WIFI_DEVICES`
- `ACCESS_LOCAL_NETWORK`

`INTERNET` は通常権限であり、外部通信をOSレベルでloopbackだけに制限しない。Network Security Configも任意TCP socketの宛先制限にはならない。したがって、外部通信防止は次で担保する。

- networking dependencyをADB実装以外に追加しない。
- hostをnumeric loopback定数にする。
- 任意URL、hostname、IPを受け取るAPIを作らない。
- analytics、crash reporting、update checkerを入れない。
- manifest merger後の権限一覧をCIで検査する。
- dependency graphとAPK内のnative libraryを検査する。

Android 17対応時は、loopback接続がLocal Network permissionなしで引き続き動作することをAPI 37実機またはemulatorで確認する。動作しない場合でも、LAN全体へ接続可能な権限を無条件で追加せず設計を再評価する。

## 7. 通常Android APIの検証

`Settings.System.putInt` の検証にはmanifestの `WRITE_SETTINGS` と、ユーザーが `ACTION_MANAGE_WRITE_SETTINGS` 画面で付与するspecial accessが必要。

この検証は別のdebug variantでのみ行い、Local ADB版release manifestへ `WRITE_SETTINGS` を残さない。

判定:

- 読み書きとread-backが実機で成功する場合: Local ADBを廃止できるか、権限の広さと鍵管理不要という利点を比較して再決定する。
- `SecurityException`、`IllegalArgumentException`、false return、read-back mismatchの場合: Local ADB PoCへ進む。

`WRITE_SETTINGS` は永続的なspecial accessで広い設定変更能力を与えるため、常にLocal ADBより「狭い」とは仮定しない。

### 7.1 実機判定（2026-08-11）

SC-53G、Android 16、One UI 8.5、CSC DCM、build `BP4A.251205.006.SC53GOMS1AZF2`で検証した。special access付与後、GETは成功したが、`Settings.System.putInt`は`IllegalArgumentException`（`You cannot keep your settings in the secure settings.`）で拒否された。値は変更されなかった。

通常Android API方式は不採用とし、Phase 2のLocal ADB PoCへ進む。詳細は[`device-validation.md`](device-validation.md)を参照する。

## 8. エラーとtimeout

内部エラー型は少なくとも次を持つ。

```text
WirelessDebuggingUnavailable
InvalidPairingInput
PairingExpired
PairingRejected
TlsHandshakeFailed
AuthenticationFailed
ConnectionFailed
ConnectionLost
CommandTimedOut
MalformedOutput
SettingNotSet
WriteRejected
ReadBackMismatch(expected, actual)
UnsupportedFirmware
```

目安となるtimeout:

- pairing: 15秒
- connect/TLS handshake: 10秒
- GET/PUT: 各5秒
- read-backを含むwrite全体: 12秒

timeout時はstream/socketをcloseし、同じ処理を自動で無限retryしない。ユーザー操作1回につき自動retryは最大1回とし、writeのretry前には必ずGETして既に期待値になっていないか確認する。

## 9. テスト計画

### Unit test

- port parser: 0、1、65535、65536、負数、空文字、全角数字、IP/URI混入。
- pairing code parser: 6桁以外、空白、全角数字。
- command tableが3固定値以外を生成しない。
- output parser: `null`、`0`、`1`、未知文字列、CRLF、空、複数行、過大出力。
- write read-back: success、not set、unexpected、timeout、disconnect、mismatch。
- public APIからhost/command文字列を渡せないこと。

### APK/manifest test

- merged manifestのuses-permissionがallowlistと一致する。
- exported componentがlauncher activity以外にない。
- release artifactに `echo hello` や汎用command UI文字列がない。
- release artifactに不要なnetwork SDK、LADB `libadb.so`、秘密鍵がない。
- dependency verificationが有効。

### 実機test matrix

最低限、次を記録する。

```text
device model
CSC / region
Android version
One UI version
build number
security patch level
app commit SHA
dependency lock/checksum
```

シナリオ:

1. fresh installからpairing。
2. app restart後にconnection portを再入力して接続。
3. `null` / `0` / `1` / 未知値の表示。
4. 1→0とread-back。
5. 0→1とread-back。
6. 間違ったpairing code。
7. pairing dialog timeout。
8. Wireless Debugging OFF中の操作。
9. connection port変更後の再接続。
10. paired deviceをForgetした後の再pairing。
11. ADB資格情報削除後の再pairing。
12. 外部LAN上に別ADB端末が存在しても接続しないこと。

## 10. 実装フェーズ

### Phase 0: 実機前提の確定

- Mac公式adbで対象settingのGET、0 PUT、1 PUT、read-backを確認する。
- 元のraw値を記録する。元が`1`ならテスト後に`1`へ戻し、それ以外なら安全な復旧手順を先に決める。
- Galaxy S26 UltraのOS、One UI、CSC、build numberを記録する。

### Phase 1: Android API debug probe

- debug variantだけで `WRITE_SETTINGS` を要求する。
- 対象キーのread/write/read-backを確認する。
- 結果を文書化してprobeをreleaseから除外する。

### Phase 2: Local ADB PoC

- UIはpairing port、6桁コード、connection port、状態表示だけ。
- hostは `127.0.0.1` 固定。
- debug限定 `echo hello` を実行する。
- Android Keystore直接利用を同時に検証する。
- library version/commit、推移的依存、license、checksumを記録する。

SC-53G実機で完了した。直接Keystore RSA方式は不採用となり、AES-GCM保護したsoftware RSA方式でpairing、接続、固定`echo hello`に成功した。

PoC終了時に、`libadb-android` 継続かAOSP native方式への切替をdecision recordとして残す。

### Phase 3: Read-only MVP

- debug commandを削除する。
- setting GETだけを実装する。
- 値とエラーを型付きでUI表示する。

### Phase 4: Write MVP

- 0/1 PUTを追加する。
- read-back verificationを追加する。
- credential reset、re-pairing導線を追加する。

SC-53G実機で完了した。0/1のPUT後に同一Local ADB sessionでGETし、期待値との一致を確認した。資格情報削除後はprocess再起動前のpairingを拒否し、再起動後に新しい6桁コードで再pairing、接続、GETへ成功した。

### Phase 5: Hardening

- dependency locking/verification。
- merged manifest allowlist test。
- release artifact inspection。
- backup/data extraction rules。
- production logging無効化。
- 実機test matrix完了。

## 11. 未解決事項とGo/No-Go

次はコードだけでは確定できず、追加の実機検証が必要。

- `settings` commandが対象キーを現在も書き換えられるか。
- Samsung Cameraが0/1をどう解釈するか。
- One UI 9 beta/stableで挙動が維持されるか。

SC-53Gの現行buildでは`127.0.0.1`へのpair/connectが成功した。Android Keystore RSA private keyの直接利用は失敗し、AES-GCM保護したsoftware RSA方式で成功した。settingのADB書き換えとCamera挙動はMac ADBでは確認済みだが、Local ADB実装からはPhase 4で再検証する。

No-Go条件:

- 接続先をloopbackへ強制できない。
- 鍵またはpairing codeが平文永続化される。
- 任意command入力を実装しないとライブラリを利用できない。
- 依存ライセンス条件を満たせない。
- 実機でread-backが安定して一致しない。

## 12. 参考資料

- [Android Developers: Wireless debugging](https://developer.android.com/studio/run/device.html#wireless)
- [AOSP: Architecture of ADB Wifi](https://android.googlesource.com/platform/packages/modules/adb/+/refs/heads/main/docs/dev/adb_wifi.md)
- [AOSP: pairing_connection](https://android.googlesource.com/platform/packages/modules/adb/+/refs/heads/main/pairing_connection/)
- [Samsung: Galaxy S26シリーズ発表](https://news.samsung.com/global/samsung-unveils-galaxy-s26-series-the-most-intuitive-galaxy-ai-phone-yet)
- [Samsung: One UI 9 beta for Galaxy S26](https://news.samsung.com/global/samsung-launches-one-ui-9-beta-for-galaxy-s26-series-users)
- [LADB repository](https://github.com/tytydraco/LADB)
- [LADB license](https://github.com/tytydraco/LADB/blob/main/LICENSE)
- [libadb-android repository](https://github.com/MuntashirAkon/libadb-android)
- [libadb-android license/dependency notice](https://github.com/MuntashirAkon/libadb-android/blob/master/COPYING)
- [SPAKE2-Java repository and LGPL-3.0 notice](https://github.com/MuntashirAkon/spake2-java)
- [dadb repository](https://github.com/mobile-dev-inc/dadb)
- [Android Developers: Android Keystore](https://developer.android.com/privacy-and-security/keystore)
- [Android Developers: Auto Backup](https://developer.android.com/identity/data/autobackup)
- [Android Developers: Local network permission](https://developer.android.com/privacy-and-security/local-network-permission)
- [Android Developers: Settings.System.canWrite](https://developer.android.com/reference/android/provider/Settings.System#canWrite(android.content.Context))
