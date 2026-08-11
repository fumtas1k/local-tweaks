# Galaxy S26 Ultra Local ADB Utility — 要件定義

技術方式、ライブラリ評価、鍵管理、実装フェーズの詳細は
[`technical-design.md`](technical-design.md) を参照する。

## 1. 目的

Galaxy S26 Ultra 上で、PCや第三者製の汎用ADBツールに依存せず、端末自身からLocal ADB経由で限定的なシステム設定を変更できるAndroidアプリを作成する。

初期ユースケースは、Samsung Galaxyのカメラシャッター音強制設定の確認・変更。

対象キー:

```text
csc_pref_camera_forced_shuttersound_key
```

実行したい操作:

```bash
settings get system csc_pref_camera_forced_shuttersound_key

settings put system csc_pref_camera_forced_shuttersound_key 0

settings put system csc_pref_camera_forced_shuttersound_key 1
```

想定:

- `1`: シャッター音強制あり
- `0`: シャッター音強制解除

ただし値の意味・実際のカメラ挙動はSamsungのファームウェア依存なので、アプリでは「現在値」を正確に表示し、0/1以外を勝手に解釈しないこと。

---

## 2. 背景

Galaxy S26 Ultraでシャッター音の強制設定を変更したい。

MacからGoogle公式ADBを使えば以下で変更可能。

```bash
adb shell settings put system csc_pref_camera_forced_shuttersound_key 0
```

しかし毎回Macを接続するのではなく、Galaxy単体で完結させたい。

第三者製ADBアプリにshell権限を与えることにはセキュリティ上の懸念がある。

そのため、自分でソースを管理・監査・ビルド・署名した最小構成のADBクライアントアプリを作成する。

---

## 3. 重要な環境制約

### 対象端末

```text
Samsung Galaxy S26 Ultra
```

比較的新しいAndroid / One UI環境を対象とする。

---

### Shizukuは使用しない

Galaxy S26 Ultraでは、現在の環境でShizukuがバージョン互換性の問題によりインストール自体できない。

したがって以下の構成は禁止。

```text
App
 ↓
Shizuku
 ↓
shell
```

Shizukuを依存関係、オプション機能、フォールバックとしても前提にしないこと。

---

## 4. 基本アーキテクチャ

端末自身のWireless DebuggingにLocal ADBクライアントとして接続する。

```text
┌───────────────────────────────┐
│ Galaxy S26 Ultra             │
│                               │
│  自作Android App              │
│       │                       │
│       │ ADB protocol          │
│       │ TLS / pairing         │
│       ▼                       │
│  localhost / Wireless ADB     │
│       │                       │
│       ▼                       │
│     adbd                      │
│       │                       │
│       ▼                       │
│   shell UID 2000              │
│       │                       │
│       ▼                       │
│   SettingsProvider            │
└───────────────────────────────┘
```

rootは使用しない。

---

## 5. 最重要セキュリティ要件

このアプリは「汎用ADBターミナル」にしない。

任意のshellコマンドをユーザーが入力・実行できる機能は実装しない。

たとえば以下は禁止。

```text
$ ______________________
[ Execute ]
```

releaseビルドで実行可能な処理は、アプリコードに定義された固定操作だけにする。

初期バージョンでは以下の3種類だけ。

```bash
settings get system csc_pref_camera_forced_shuttersound_key
```

```bash
settings put system csc_pref_camera_forced_shuttersound_key 0
```

```bash
settings put system csc_pref_camera_forced_shuttersound_key 1
```

---

## 6. 攻撃面を最小化する

以下は実装しない。

- 任意shell command実行
- APKインストール
- APKアンインストール
- package disable / enable
- ファイルpush
- ファイルpull
- ファイル削除
- logcat viewer
- screenshot
- screencap
- screenrecord
- port forwarding
- TCP接続先を自由に指定する機能
- 他端末へのADB接続
- LAN内ADB探索
- Analytics
- Crash reporting SDK
- 広告SDK
- 外部API通信
- クラウド同期

Local ADB以外のネットワーク通信は原則行わない。

---

## 7. 接続先制限

ADB接続は原則として「自端末」に限定する。

任意IPアドレスを入力してADB接続する機能は作らない。

MVPの接続先hostは、

```text
127.0.0.1
```

だけを対象とする。名前解決を避けるため、`localhost`文字列も実装内部では使用しない。

AndroidのWireless Debuggingでは接続ポートが固定ではないため、MVPでは設定画面に表示されるポート番号をユーザーが入力する。

MVPではmDNS discoveryを実装しない。接続先hostはコードで数値loopback `127.0.0.1` に固定し、host、IPアドレス、hostname、URIを入力できるUIやpublic APIを作らない。

ユーザーが入力できる接続情報は次だけとする。

- ペアリング用ポート
- 6桁のペアリングコード
- ペアリング後の接続用ポート

各ポートはASCII数字のみを受け付け、`1..65535` の範囲に制限する。ペアリング用ポートと接続用ポートを同一視しない。

---

## 8. ADBペアリング

PC不要でGalaxy単体からADBペアリングできる必要がある。

想定フロー:

```text
アプリ起動
 ↓
Wireless Debuggingが利用可能か確認
 ↓
未ペアリング
 ↓
ユーザーにAndroid設定画面を開いてもらう
 ↓
「ペア設定コードによるデバイスのペア設定」
 ↓
ペアリング用ポートと6桁コード入力
 ↓
ADB pairing
 ↓
接続用ポート入力
 ↓
接続
 ↓
shell command実行
```

Android標準のWireless Debuggingを利用する。

独自のroot処理や権限昇格は行わない。

---

## 9. UI案

Jetpack Composeを使用する。

初期画面は極力小さくする。

例:

```text
Galaxy Utility

Local ADB
──────────────

Status
Connected

Camera shutter sound
Current value: 1

[ Disable forced shutter sound ]

[ Set forced shutter sound setting to 1 ]

──────────────

Wireless debugging:
Enabled

ADB pairing:
Paired
```

接続されていない場合:

```text
Local ADB

Not connected

[ Pair with Wireless Debugging ]
```

---

## 10. 表現上の注意

「シャッター音 OFF」と断定しない。

実際には変更しているのはSamsung独自の設定値。

そのためUI上は、

```text
Disable forced shutter sound
```

または日本語なら、

```text
シャッター音の強制設定を解除
```

程度が望ましい。

値を`1`にする操作は、変更前の値が`1`だと確認できない場合に「復元」と表現しない。基本ラベルは値への操作を正確に示す。

```text
強制設定を0に変更
強制設定を1に変更
```

`0` にしてもカメラアプリ、地域、CSC、One UIバージョン等によって実際の動作が異なる可能性がある。

---

## 11. 推奨プロジェクト構成

```text
app/
├── adb/
│   ├── AdbClient.kt
│   ├── AdbConnection.kt
│   ├── AdbPairingManager.kt
│   ├── AdbKeyManager.kt
│   └── AdbDiscovery.kt
│
├── feature/
│   └── shutter/
│       ├── ShutterSoundRepository.kt
│       ├── ShutterSoundState.kt
│       └── ShutterSoundViewModel.kt
│
├── ui/
│   ├── MainScreen.kt
│   └── PairingScreen.kt
│
└── MainActivity.kt
```

---

## 12. Repository API

UIやViewModelから生のshell文字列を扱わせない。

悪い例:

```kotlin
repository.execute("settings put system ...")
```

推奨:

```kotlin
sealed interface ForcedSettingValue {
    data object NotSet : ForcedSettingValue

    data class Present(val raw: String) : ForcedSettingValue
}

interface ShutterSoundRepository {
    suspend fun getForcedShutterSoundSetting(): Result<ForcedSettingValue>

    suspend fun setForcedShutterSoundSettingToZero(): Result<Unit>

    suspend fun setForcedShutterSoundSettingToOne(): Result<Unit>
}
```

内部だけでコマンドを固定する。

例:

```kotlin
private const val KEY =
    "csc_pref_camera_forced_shuttersound_key"

private const val GET_COMMAND =
    "settings get system csc_pref_camera_forced_shuttersound_key"

suspend fun getForcedShutterSoundSetting() =
    adb.executeFixed(GET_COMMAND)
```

ADB shell serviceはargvではなくcommand文字列を受けるため、`List<String>` 化だけをコマンドインジェクション対策とは見なさない。完全なcommand定数を操作ごとに選択し、外部入力の展開、文字列連結、汎用execute APIの公開を行わない。

---

## 13. コマンドインジェクション対策

現在のキーは固定値なので、外部入力をコマンド文字列へ展開しない。

禁止例:

```kotlin
execute("settings put system $userInput $value")
```

初期バージョンでは以下を完全固定する。

```kotlin
object SamsungSettings {
    const val FORCED_SHUTTER_SOUND =
        "csc_pref_camera_forced_shuttersound_key"
}
```

---

## 14. ADB秘密鍵

ADBペアリング用秘密鍵を適切に保護する。

要件:

- アプリprivate storage外へ平文保存しない
- ログ出力しない
- clipboardへコピーしない
- backupおよびdevice-to-device transferの対象にしない
- Git repositoryに秘密鍵を入れない

第一案として、Android Keystore内にnon-exportableなRSA key pairを生成し、ADBライブラリから直接署名に利用する。

ただしADBライブラリ側の鍵フォーマットとの互換性を確認すること。

Keystoreで直接ADB signingが難しい場合は、

```text
Android Keystore
 ↓
暗号化キー
 ↓
encrypted ADB private key
 ↓
app private storage
```

のような構成をfallbackとする。fallbackの暗号文は`noBackupFilesDir`へ保存する。

manifestでは`android:allowBackup="false"`を明示し、`dataExtractionRules`でもcloud backupとdevice transferからADB資格情報を除外する。

アプリ内に「ADB資格情報を削除」操作を用意し、Keystore aliasとfallback暗号文を削除できるようにする。

---

## 15. ログ方針

デバッグログに以下を出さない。

- ADB private key
- pairing secret
- pairing code
- authentication token
- shell command結果に含まれる機密情報

今回の固定コマンド自体は秘密ではないが、原則としてproductionでは必要最低限のログにする。

---

## 16. ネットワーク権限

必要最低限にする。

Wireless ADB通信のために、

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

を使用する。

ただしこの権限があるからといって外部Internet通信を実装しない。

MVPではmDNSとWi-Fi scanを行わないため、次の権限は宣言しない。

- `ACCESS_FINE_LOCATION`
- `ACCESS_COARSE_LOCATION`
- `ACCESS_WIFI_STATE`
- `CHANGE_WIFI_STATE`
- `NEARBY_WIFI_DEVICES`
- `ACCESS_LOCAL_NETWORK`

Network Security Configは任意TCP socketの接続先制限には使用できない。外部通信防止は、接続先をnumeric loopback定数へ固定すること、network dependencyをADB実装以外に追加しないこと、merged manifestとdependency graphを検査することで担保する。

---

## 17. ADBライブラリ調査

調査結果と採用判断は[`technical-design.md`](technical-design.md)へ記録する。

### 参考実装

LADB:

```text
https://github.com/tytydraco/LADB
```

LADBは端末自身のWireless DebuggingへLocal ADB接続する実績のあるsource-available実装なので、アーキテクチャやADB通信部分の参考にする。

現行LICENSEには原作者以外のGoogle Play Store公開を禁止する追加条項があり、プリビルド`libadb.so`の由来も本プロジェクト側で再現しにくい。このため、LADBのコードとバイナリは取り込まず、動作方式の参考に限定する。

以下を確認:

- プロジェクト全体のライセンス
- ADBライブラリ部分のライセンス
- 使用しているADB Java/Kotlinライブラリ
- Android最新版との互換性
- TLS pairingの実装
- mDNS discovery
- ADB key generation
- shell stream handling

---

## 18. ライブラリ選定基準

ADBライブラリを採用する場合、以下を優先する。

1. OSS
2. ライセンスが明確
3. 最近のAndroid Wireless Debuggingに対応
4. TLS pairing対応
5. root不要
6. 外部サーバー不要
7. Analytics等の不要なSDKを含まない
8. 依存関係が少ない
9. ソースコード監査が容易
10. メンテナンス状況を確認できる

古い、

```text
adb tcpip 5555
```

前提のライブラリは避ける。

---

## 19. Android APIによる直接変更も事前検証する

Local ADB実装に入る前に、releaseとは分離したdebug variantで一度だけ以下の可能性も検証する。

```kotlin
Settings.System.putInt(
    contentResolver,
    "csc_pref_camera_forced_shuttersound_key",
    0
)
```

検証にはmanifestの`WRITE_SETTINGS`宣言に加えて、ユーザーが`ACTION_MANAGE_WRITE_SETTINGS`画面でspecial accessを付与する必要がある。ただしSamsung独自の非公開settingであり、この権限でも変更できない可能性がある。

検証結果:

- 変更できる → ADB自体を不要にできるか、永続的で広いspecial accessとのセキュリティ比較を行って決定する
- SecurityException / 更新不可 → Local ADB方式へ進む

検証用の`WRITE_SETTINGS`はLocal ADB版のrelease manifestへ残さない。

---

## 20. 開発フェーズ

### Phase 0: 実機確認

Galaxy S26 UltraでMacから以下を確認する。

```bash
adb shell settings get system csc_pref_camera_forced_shuttersound_key
```

この出力を元のraw値として記録する。元の値が`0`、`1`、`null`、その他のどれかを確認してから書き込みテストへ進む。

続いて、

```bash
adb shell settings put system csc_pref_camera_forced_shuttersound_key 0
```

実際に変更可能であることを確認。

元の値が`1`だった場合は、その後、

```bash
adb shell settings put system csc_pref_camera_forced_shuttersound_key 1
```

で復元できることを確認する。元の値が`1`以外の場合は、元の状態へ安全に戻す手順を先に決め、`1`への変更を「復元」と扱わない。

---

### Phase 1: 通常Android API検証

releaseとは分離した最小debug APKで、

```kotlin
Settings.System
```

からこのキーを読み書きし、read-backできるか確認。

変更できなければADB方式へ進む。

---

### Phase 2: Local ADB PoC

UIは最低限でよい。このphaseの`echo hello`はdebug source setまたはinstrumented testに限定し、release artifactへ含めない。

以下だけ成立させる。

```text
Wireless Debugging
 ↓
pairing
 ↓
Local ADB connection
 ↓
shell
 ↓
echo test
```

成功条件:

```bash
echo hello
```

相当の固定コマンドをADB shellとして実行し、

```text
hello
```

をアプリ側で取得できる。

この段階では汎用shell UIは作らない。

---

### Phase 3: Settings読み取り

固定コマンド:

```bash
settings get system csc_pref_camera_forced_shuttersound_key
```

結果をCompose UIへ表示する。

想定:

```text
Current value: 1
```

値が存在しない場合:

```text
Current value: not set
```

0/1以外ならそのまま表示する。

値は`Int?`ではなく、`NotSet`またはraw文字列を保持する型で扱う。`settings get`の結果が正確に`null`なら未設定、それ以外は外側のwhitespaceを除いた文字列として保持する。空、複数行、過大出力は異常として扱う。

---

### Phase 4: ON/OFF操作

ボタンを追加。

```text
[ Disable forced shutter sound ]
```

内部:

```bash
settings put system csc_pref_camera_forced_shuttersound_key 0
```

値を1にする操作:

```text
[ Set forced shutter sound setting to 1 ]
```

内部:

```bash
settings put system csc_pref_camera_forced_shuttersound_key 1
```

実行後は必ずGETしてread-back verificationする。

変更前が`1`だと確認できない場合、値を1にする操作を「復元」と表示しない。

---

## 21. 書き込み結果の検証

PUT成功をプロセスのexit codeだけで判断しない。

必ず、

```text
PUT
 ↓
GET
 ↓
期待値と比較
```

する。

例:

```kotlin
set(0)

val actual = get()

if (actual != 0) {
    return Result.failure(...)
}
```

Samsung側が設定値を書き戻すケースも考慮する。

---

## 22. エラーハンドリング

最低限以下を区別する。

```text
Wireless debugging disabled
Pairing required
Pairing failed
ADB connection failed
ADB authentication failed
Command failed
Setting not found
Setting write rejected
Read-back mismatch
Unsupported device / firmware
```

単に、

```text
Something went wrong
```

だけにはしない。

ただしADBの内部情報を過剰にUIへ露出しない。

---

## 23. Samsung以外への対応

初期バージョンはGalaxy S26 Ultra向け。

Samsung以外では設定キーの意味がない可能性が高い。

以下等を確認してガードすることを検討。

```kotlin
Build.MANUFACTURER
```

Samsungでなければ、

```text
This feature is intended for Samsung Galaxy devices.
```

程度の警告を出す。

ただし端末モデルのハードコードで完全拒否する必要はない。

---

## 24. 設定初期値を変更しない

アプリ起動時に自動変更しない。

必ず、

```text
現在値取得
 ↓
表示
 ↓
ユーザー操作
 ↓
変更
```

とする。

アプリ起動だけで設定値を書き換えるのは禁止。

---

## 25. 操作履歴

初期バージョンでは永続的な操作履歴は不要。

必要なら画面内だけ、

```text
Changed: 1 → 0
Verified: 0
```

程度を表示する。

端末識別情報やADB情報を記録しない。

---

## 26. テスト方針

Unit Test:

- pairing port / connection port parser
- pairing code parser
- setting output parser
- `null`
- `0`
- `1`
- whitespace
- unexpected values
- command generation
- repository state handling
- public APIからhostやcommand文字列を渡せないこと

Integration / Instrumented Test:

- ViewModel
- pairing state
- connection state
- error handling
- merged manifestのpermission allowlist
- release artifactにdebug用`echo hello`が含まれないこと

実ADBについてはGalaxy S26 Ultra実機で確認する。

---

## 27. Gradle / dependency security

依存パッケージを増やしすぎない。

追加するdependencyについて、

- purpose
- version
- repository
- license

を把握できるようにする。

不要なSDKは導入しない。

可能ならdependency locking / verificationも検討する。

---

## 28. ビルド・署名

最終APKは自分でビルド・署名する。

第三者が配布したAPKを利用することを前提にしない。

公開配布は現時点では目的ではない。

---

## 29. 完成条件

MVPのDefinition of Done:

1. Galaxy S26 Ultraにインストールできる
2. root不要
3. Shizuku不要
4. PC不要
5. Wireless Debuggingだけで動作
6. 端末自身のadbdへ接続できる
7. ADB pairingできる
8. 現在値を取得できる
9. `1 → 0` へ変更できる
10. `0 → 1` へ変更できる
11. PUT後にread-back verificationを行う
12. 任意shell commandを実行できない
13. 他端末へのADB接続機能を持たない
14. Analyticsなし
15. 広告なし
16. 外部サーバー通信なし
17. ADB秘密鍵を安全に保存する
18. ソースコードから自分でビルドできる
19. ADB接続先hostが`127.0.0.1`へ固定されている
20. ペアリング用ポートと接続用ポートを別の値として扱う
21. mDNS、LAN探索、任意host入力を実装しない
22. ADB資格情報をbackupおよびdevice-to-device transfer対象から除外する
23. ADB資格情報をアプリ内操作で削除できる

---

## 30. 実装開始条件

実装方式、候補ライブラリ、ライセンス、現行Androidとの互換性、TLS pairing、接続先制限、秘密鍵保存、権限、テストおよび実装計画は[`technical-design.md`](technical-design.md)で管理する。

Local ADBライブラリはPoC完了時点で正式採否を決定し、versionまたはcommit、推移的依存、license、checksumを記録する。

いきなりUI全体を実装するのではなく、

```text
Local ADB pairing
     ↓
ADB接続
     ↓
固定shell command実行
```

のPoCを最優先する。PoCの任意shell UIは禁止し、debug限定の固定`echo hello`だけを許可する。

---

## 31. 設計上の最優先事項

優先順位:

```text
1. セキュリティ
2. 最小権限
3. 実装の透明性
4. Galaxy S26 Ultraでの実動作
5. シンプルさ
6. UI
```

機能追加のために汎用ADB shell化しないこと。

このアプリの価値は、

```text
「ADBで何でもできる」
```

ことではなく、

```text
「必要な1つの操作だけを、
自分で監査できる最小限のADBクライアントで安全に実行する」
```

ことにある。
