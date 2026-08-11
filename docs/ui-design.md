# UI設計

## 設計原則

- 画面上部のナビゲーションと、本文の操作を混在させない。
- 同じ目的の導線を1画面に重複配置しない。
- 状態表示、通常操作、補助操作、危険操作の優先度を視覚的に分ける。
- ホームは機能を増やせる一覧とし、接続設定を個別機能と同格に扱わない。
- 表示名は「ホーム」「接続設定」「カメラ設定」に統一する。本文に「メイン画面」は使用しない。
- 状態の違いは文言だけでなく、色とアイコンでも区別する。文言だけの区別に頼らない。
- 進行中の操作は必ず視覚的に表示し、操作の無効化だけで代替しない。

## テーマ

`MaterialTheme`をデフォルトのまま呼ばず、`ui/theme/`にテーマを定義する。

- `Color.kt`: light/darkの`ColorScheme`を定義する。Android 16のみを対象とするため、`dynamicLightColorScheme` / `dynamicDarkColorScheme`を既定とし、取得できない場合のフォールバックとして静的な`ColorScheme`を持つ。
- `Type.kt`: Material 3の`Typography`を基準にし、変更する場合のみ上書きする。日本語のため`lineHeight`を既定より広げる。
- `Theme.kt`: `LocalTweaksTheme`を公開し、`isSystemInDarkTheme()`でlight/darkを切り替える。`MainActivity`は`LocalTweaksTheme { ... }`だけを呼ぶ。

色の役割は次のとおり固定する。個別画面で`Color`リテラルを直接書かない。

| 用途 | ロール |
| --- | --- |
| 画面背景 | `background` |
| セクションカード | `surfaceContainerHigh` |
| 無効なカード | `surfaceContainer` + `onSurfaceVariant`の文字 |
| 成功状態 | `primary` |
| 失敗・入力エラー | `error` |
| 要再起動の警告・危険操作カード | `errorContainer` / `onErrorContainer` |
| 補助説明 | `onSurfaceVariant` |

カードの階調は「画面背景 < 無効なカード < セクションカード」の順とし、light/darkの両方でこの順序が崩れないこと。無効なカードは有効なカードより沈ませるが、**背景と同色にはしない**。dynamic colorのpaletteによっては`surfaceContainerLow`が`background`と同値になり、カードの境界が消えて「ただの文字」に見える。

`errorContainer`を背景に使う面では、前景に`error`を直接指定しない。paletteが保証するのはcontainerとon-containerの対応であり、`error`を上書きするとその保証を外れる。面の文字は`onErrorContainer`とし、`error`は危険操作のボタン自身の強調にだけ使う。

ボタンは強調度の三段階（主要・補助・危険）を種別で区別し、色の上書きで表現しない。3種とも「押せる要素」と分かる見た目（塗り、薄い塗り、枠線のいずれか）を保つこと。文字だけのボタン（`TextButton`）は、ページ内リンクのようなナビゲーション操作（例: ホームの「接続する」）にだけ使い、実際の操作を伴う主要・補助・危険操作には使わない。

| 強調度 | コンポーネント | 色 |
| --- | --- | --- |
| 主要操作 | `Button` | 既定（`primary` / `onPrimary`の塗り） |
| 補助操作 | `FilledTonalButton` | 既定（`secondaryContainer` / `onSecondaryContainer`の薄い塗り、色の上書き不要） |
| 危険操作 | `OutlinedButton` | `error`色の枠線と文字。塗りにはしない（主要操作と同じ重みになり、破壊的操作を誘うため）。ボタンを載せるカード面は`errorContainer` / `onErrorContainer`のままとし、`error`はボタンだけに使う |

主要操作・補助操作は色を上書きしないため、`enabled = false`時も`ButtonDefaults`の既定disabled配色がそのまま効く。危険操作は`contentColor`と`border`を`error`色に上書きしているため、`disabledContentColor`と、無効時の`border`色（`error.copy(alpha = 0.38f)`など）も必ず明示すること。`Card`や`TextButton`で`containerColor`/`contentColor`だけを渡して`disabled*`を渡し忘れた過去の不具合と同じ性質なので、色を上書きするボタンでは常に無効時の見た目を確認する。

## アイコン

戻る・シェブロン・状態表示を文字リテラル（`←` `›`）で描画しない。`androidx.compose.material:material-icons-core`の`ImageVector`を使う。

- 戻る: `Icons.AutoMirrored.Filled.ArrowBack`
- 機能カードのシェブロン: `Icons.AutoMirrored.Filled.KeyboardArrowRight`
- 成功: `Icons.Filled.CheckCircle`
- 失敗・入力エラー: `Icons.Filled.Error`
- 要再起動: `Icons.Filled.Warning`

`Icons.Filled.Error`のみ`material-icons-core`に含まれないため、`app/src/main/res/drawable/`にvector drawableとして配置し、`painterResource`で参照する。

`contentDescription`は、意味を持つアイコンにだけ設定する。状態表示のアイコンは隣接する文言と重複するため`null`にする。装飾ではない戻るボタンには`R.string.navigate_back`を設定する。

依存追加時は`dependencyLocking`が`LockMode.STRICT`のため、`./gradlew --write-locks test lint check`でロックファイルを更新する。

## 状態表示

`MainStatus`の26種を、表示の分類`MainStatusTone`にまとめる。文言の対応表（`statusText`）とは独立させ、色とアイコンはtoneだけから決める。

| tone | `MainStatus` |
| --- | --- |
| `Neutral` | `NotConnected` |
| `Progress` | `Pairing` / `Connecting` / `Reading` / `SettingZero` / `SettingOne` / `CredentialResetting` |
| `Success` | `Paired` / `Connected` / `ReadComplete` / `SetZeroSuccess` / `SetOneSuccess` |
| `Failure` | `InvalidPairingPort` / `InvalidPairingCode` / `PairingFailed` / `InvalidConnectionPort` / `ConnectionFailed` / `InvalidOutput` / `ReadTimeout` / `ReadTransportFailed` / `WriteReadBackMismatch` / `WriteInvalidOutput` / `WriteTimeout` / `WriteTransportFailed` / `CredentialResetFailed` |
| `Warning` | `RestartRequired` |

表示規則:

- `Neutral`はアイコンなし、`onSurfaceVariant`。
- `Progress`は`CircularProgressIndicator`（16dp、`strokeWidth = 2.dp`）を先頭に置く。
- `Success`は`CheckCircle` + `primary`。
- `Failure`は`Error` + `error`。
- `Warning`は`Warning` + `onErrorContainer`、背景に`errorContainer`を敷く。`restartRequired`の間はこの表示を接続設定の最上部に固定する。
- `state.busy`が`true`の間、実行中のボタンはラベルを保ったまま先頭に16dpの`CircularProgressIndicator`を表示する。ラベルの反対側にも同じ16dpの`Spacer`を置き、表示・非表示の切り替えでボタン幅が変動せず、かつラベルが中央に保たれるようにする。

入力エラー（`InvalidPairingPort` / `InvalidPairingCode` / `InvalidConnectionPort`）は、画面上部の状態行だけでなく、対応する`OutlinedTextField`の`isError`と`supportingText`にも反映する。状態行にしか出さない扱いはしない。

## 共通構造

全画面でMaterial 3の`Scaffold`と`TopAppBar`を使用する。ホームのTop App Barには`Local Tweaks`だけを表示する。接続設定とカメラ設定では、戻るアイコン、画面名の順に表示する。本文中に「ホームに戻る」ボタンは置かない。

Top App BarとScaffoldのcontent paddingでsystem barを避ける。本文は16dpを基本余白とし、狭い画面では縦スクロール可能にする。

`MainActivity`は`enableEdgeToEdge()`を呼び、system barのアイコンの明暗をlight/darkに追従させる。targetSdkが35以上のため`android:statusBarColor`と`android:navigationBarColor`は非推奨のno-opであり、`styles.xml`でsystem barの色を指定しない。window背景がCompose描画前に明るく光らないよう、`values-night/styles.xml`でparentだけdarkに差し替える。

本文は`HorizontalDivider`の羅列で区切らず、セクション単位の`Card`（`surfaceContainer`）にまとめる。セクションは「見出し（`titleMedium`）→ 本文 → 操作」の順で構成し、セクション間は16dp、セクション内の要素間は8dpとする。`HorizontalDivider`は同一カード内の並列項目を分けるときにだけ使う。

## ホーム

ホーム本文には「ホーム」という重複タイトルを置かない。

```text
┌─────────────────────────┐
│ Local Tweaks             │
├─────────────────────────┤
│ ┌─────────────────────┐ │
│ │ ADB接続              │ │
│ │ 未接続      [接続する]│ │
│ └─────────────────────┘ │
│                         │
│ 機能                     │
│ ┌─────────────────────┐ │
│ │ カメラ設定           ＞│ │
│ │ 接続後に利用できます  │ │
│ └─────────────────────┘ │
└─────────────────────────┘
```

- 接続導線は接続状態領域の1か所だけに置く。
- 接続状態はセクションカードに入れ、状態表示の規則に従ってアイコンと色を付ける。
- 接続済みの場合、操作名は「接続設定」にする。
- 機能カード全体を選択対象にする。
- 未接続の機能カードは無効化し、「接続が必要です」と表示する。カード内に接続ボタンは置かない。
- 将来の機能は同じ形式のカードとして縦に追加する。機能が4件を超えたら`Column` + `verticalScroll`から`LazyColumn`に切り替える。

## 接続設定

既定で認証情報が既にある場合（畳んだ状態）:

```text
┌─────────────────────────┐
│ ←  接続設定              │
├─────────────────────────┤
│ 状態: 未接続             │
│                         │
│ ┌─────────────────────┐ │
│ │ ワイヤレスデバッグ    │ │
│ │ [開発者向けオプション]│ │
│ └─────────────────────┘ │
│ ┌─────────────────────┐ │
│ │ 接続                 │ │
│ │ 接続ポート           │ │
│ │           [ 接続 ]   │ │
│ └─────────────────────┘ │
│ ┌─────────────────────┐ │
│ │ 初回・再設定時の    ⌄│ │
│ │ ペアリング           │ │
│ └─────────────────────┘ │
│ ┌─────────────────────┐ │
│ │ 危険な操作           │ │
│ │ ADB認証情報をリセット │ │
│ └─────────────────────┘ │
└─────────────────────────┘
```

タップして展開すると、`⌄`が`⌃`に変わり、既存どおりペアリングポート・6桁のコード・`[ペアリング]`が現れる（認証情報が無い初回はこの展開状態が既定）。

- 接続状態は情報表示だけとし、戻る操作を入れない。状態行はカードの外に置き、画面全体の状態であることを示す。
- 接続を主要操作（`Button`）、ペアリングを初回・復旧時の補助操作（`FilledTonalButton`）として扱う。
- 主要操作ボタンはセクションカード内で右寄せにし、入力欄との縦位置関係を固定する。
- 認証情報リセットは画面末尾の独立したカードに置き、`errorContainer`を背景、`error`を文字色とする。ボタンは`OutlinedButton`に`error`色の枠線と文字を与えた危険操作として表現し、塗りボタン（`Button`/`FilledTonalButton`）にはしない。通常の主要ボタンと同じ表現にしない。
- 接続成功時はホームへ戻る。ペアリング成功時は接続設定に留まる。
- `restartRequired`のときは、画面最上部に`Warning`の状態表示を固定し、すべての入力とボタンを無効化する。
- 「初回・再設定時のペアリング」セクションは見出し行自体をタップ領域にし、右端に開閉を示すシェブロン（`Icons.Filled.KeyboardArrowDown` / `Icons.Filled.KeyboardArrowUp`）を置く。畳んだ状態でも見出しとシェブロンだけは常に表示し、「ここを開けばペアリングできる」と分かる見た目にする。
- 既定の開閉状態は`LocalAdbSession.hasStoredCredentials()`（`MainViewModel.hasStoredCredentialsAtStartup`）から決める。認証情報が無ければ既定で展開、既にあれば既定で折りたたむ。
- `hasStoredCredentials()`は「ペアリング済み」を意味しない。認証情報は最初のペアリング/接続試行時に遅延生成されるため、失敗した試行の後でも`true`になり得る。この値は既定の開閉状態を決めるためだけに使い、ユーザーは畳んだ状態からもいつでも開けるようにする。到達性を折りたたみで奪わない。
- 開閉状態はCompose側の`rememberSaveable`一時状態として保持し、画面回転で失われないようにする。永続化はしない。折りたたみ・展開の切り替えは、ペアリングコードを接続設定を離れたときにだけ破棄する既存の挙動に影響しない。

## カメラ設定

```text
┌─────────────────────────┐
│ ←  カメラ設定            │
├─────────────────────────┤
│ ┌─────────────────────┐ │
│ │ 現在の設定           │ │
│ │ 0                    │ │
│ │ ✓ 読み取りました     │ │
│ │       [現在値を更新] │ │
│ └─────────────────────┘ │
│ ┌─────────────────────┐ │
│ │ 設定を変更           │ │
│ │ 強制シャッター音設定  │ │
│ │ ON = 1 / OFF = 0 [○]│ │
│ └─────────────────────┘ │
│                         │
│ 注意事項                 │
│ firmware / CSCの注意事項 │
└─────────────────────────┘
```

- 取得値と直近の操作結果を同じカードにまとめて表示する。取得値は`headlineSmall`、操作結果は状態表示の規則に従う。
- 読み取りと書き込みを別カードにする。
- `Present("1")`のときON、`Present("0")`のときOFFとしてSwitchを有効にする。
- null、未設定、0/1以外ではSwitchを無効にし、現在値の取得または確認が必要な理由を`bodySmall` / `onSurfaceVariant`で表示する。
- SwitchのON/OFFはraw設定値との対応だけを示し、実際のシャッター音の状態を断定しない。
- 書き込み中（`SettingZero` / `SettingOne`）はSwitchを無効にし、行の末尾に`CircularProgressIndicator`を出す。楽観的にSwitchの位置を先に動かさない。書き込み結果の読み戻しが確定してから位置を更新する。
- `WriteReadBackMismatch`のときは、Switchの位置を読み戻した実際の値に合わせ、`Failure`の状態表示で不一致を示す。
- 0/1の意味を断定せず、raw設定値への操作として表現する。
- firmwareやCSCによる差異は補助情報として画面末尾に置く。カードに入れず、`bodyMedium` / `onSurfaceVariant`の地の文とする。

## 画面遷移

- 通常起動: ホーム
- ホームの接続操作: 接続設定
- 接続成功: ホーム
- 接続済みのカメラカード: カメラ設定
- 詳細画面の戻るアイコンまたはAndroid Back: ホーム
- `restartRequired`: 接続設定へ強制遷移し、ADB操作を無効化する。この状態ではAndroid Backによるアプリ終了を妨げない。

画面、ポート入力、取得値は`ViewModel`で回転をまたいで保持する。ペアリングコードはCompose内の一時状態に限定し、接続設定を離れた時点で破棄する。

## アクセシビリティ

- タップ対象は48dp以上を確保する。`IconButton`の既定サイズを縮めない。
- Switchは行全体ではなくSwitch自体をタップ対象とし、`contentDescription`に設定名を与える。
- 状態表示のアイコンは`contentDescription = null`とし、意味は隣接テキストで伝える。
- 文字色と背景のコントラストは`ColorScheme`のロール対応（`onX`）を守ることで担保し、独自の組み合わせを作らない。
- light / darkの両方で`@Preview`を用意し、`MainActivity`の各画面Composableを個別に確認できるようにする。
