# Schedule Imager (Android)

Outlook（Microsoft 365 / Outlook.com）カレンダーの予定を、ホーム画面ウィジェットに
時間割イメージで表示するAndroidアプリです。iOS版と同じ「予定はイメージで記憶」という
コンセプトのAndroid移植で、AOD・ロック画面ウィジェット・Live Activity相当の仕組みが
Androidには無いため、出力はホーム画面ウィジェット1本に絞っています。

構成:
- `MainActivity` / `ScheduleImagerApp`（本体アプリ）: Outlookへのサインイン（MSAL）、
  予定の手動同期、ウィジェット更新用WorkManagerジョブの登録を行います。
- `widget/`: Jetpack Glanceで実装したホーム画面ウィジェット。2日分（幅が狭いとき）・
  3日分（幅が広いとき）の時間割を、日ごとの列×時間軸のブロックで表示します。ウィジェット
  自身はネットワーク通信をせず、`data/ScheduleStore`に保存されたキャッシュを読むだけです
  （iOSと異なり、本体アプリとウィジェットは同一プロセス・同一ファイルを読むので
  App Group相当の仕組みは不要です）。
- `data/` / `auth/`: MSAL認証・Microsoft Graph API呼び出し・キャッシュ処理。
- `work/ScheduleRefreshWorker`: 30分ごと（`Config.REFRESH_INTERVAL_MINUTES`）に
  バックグラウンドでOutlookから予定を再取得し、ウィジェットを更新するWorkManagerジョブ。
  サインイン直後・アプリの「更新」ボタンからも同じ処理を即時実行します。

## セットアップ手順

### 1. Azure AD にアプリを登録する

1. [Azure Portal](https://portal.azure.com/) → 「Microsoft Entra ID」→「アプリの登録」→「新規登録」。
   iOS版と同じアプリ登録に「プラットフォームを追加」で Android を足しても、
   新規に登録しても構いません。
2. 「サポートされているアカウントの種類」は個人のMicrosoftアカウントも使う場合は
   「個人のMicrosoftアカウントと、任意の組織のディレクトリ内のアカウント」を選択。
3. 登録後、「認証」→「プラットフォームを追加」→「Android」を選択し、
   以下を入力します。
   - **パッケージ名**: `net.ustyle.app.scheduleimager`（変更した場合はそちらを入力）
   - **署名ハッシュ**: 下記手順2で生成した値
4. 「APIのアクセス許可」→「アクセス許可の追加」→「Microsoft Graph」→
   「委任されたアクセス許可」→ `Calendars.Read` を追加。
5. 「概要」ページの **アプリケーション (クライアント) ID** をコピーしておく。

### 2. 署名ハッシュ（signature hash）を生成する

MSAL Androidはアプリの署名証明書のハッシュ値をリダイレクトURIの一部に使います。
デバッグ用の鍵（`~/.android/debug.keystore`、パスワードは`android`）でよければ:

```bash
keytool -exportcert -alias androiddebugkey -keystore ~/.android/debug.keystore \
  -storepass android | openssl sha1 -binary | openssl base64
```

出力された文字列（例: `Ev5J5VTq2xw5vRtMg5BPG00SNPo=`）を、Azure Portalの
「署名ハッシュ」欄にそのまま貼り付けます。リリース用に自分の鍵で署名する場合は、
その鍵で同じコマンドを実行し直し、Azure Portal・`res/raw/msal_config.json`の
両方を更新してください（デバッグビルドとリリースビルドで鍵が違う場合、
両方のハッシュをAzure Portal側に登録する必要があります）。

### 3. パッケージ名を決める（他人と衝突しない値に変更推奨）

現状は仮の識別子 `net.ustyle.app.scheduleimager` を使っています。
実機で動かす・Google Playに出す場合は、一意な値に変更してください。変更箇所:

- `app/build.gradle.kts`
  - `namespace` / `defaultConfig.applicationId`
- `app/src/main/AndroidManifest.xml`
  - `BrowserTabActivity`の`intent-filter`内の`android:host`
- `app/src/main/res/raw/msal_config.json`
  - `redirect_uri`（`msauth://<パッケージ名>/<URLエンコードした署名ハッシュ>`）

### 4. MSAL設定を書き込む

`app/src/main/res/raw/msal_config.json` を編集します。

```json
{
  "client_id": "手順1でコピーしたクライアントID",
  "redirect_uri": "msauth://net.ustyle.app.scheduleimager/<URLエンコードした署名ハッシュ>",
  ...
}
```

`redirect_uri`の署名ハッシュ部分は、手順2で生成した値を**URLエンコードしたもの**
（`=`が`%3D`になる等）を使います。Azure Portal側（署名ハッシュ欄）は
**エンコードしていない生の値**を使う点に注意してください。

また `app/build.gradle.kts` の `manifestPlaceholders["msalRedirectUriPath"]` を、
エンコードしていない生の署名ハッシュに `/` を先頭につけた値
（例: `/Ev5J5VTq2xw5vRtMg5BPG00SNPo=`）に書き換えてください。これは
`AndroidManifest.xml`内の`BrowserTabActivity`のintent-filterが参照しています。

### 5. ビルド・実行

```bash
./gradlew :app:assembleDebug
```

Android Studioで開く場合はそのままAVDまたは実機で実行してください。
初回起動後、アプリ内で「Outlookでサインイン」をタップしてMicrosoftアカウントで
サインインすると、予定が同期され、ホーム画面にウィジェットを追加できるように
なります（ホーム画面を長押し→「ウィジェット」→「Schedule Imager」を検索）。

## 動作の仕組み

- サインインはMSAL（Microsoft Authentication Library for Android）を使い、
  本体アプリから起動したシステムブラウザ／カスタムタブでMicrosoftの認証画面を
  表示します（シングルアカウントモード）。
- 取得したトークンはMSALが管理し、アプリ内でサイレント取得（再ログイン不要の
  自動トークン更新）します。
- ウィジェットは「次の予定」ではなく「時間割イメージ」の1種類のみです。幅に応じて
  2日分・3日分の列数が自動で切り替わり（`ScheduleWidget`の`SizeMode.Exact`により、
  ホーム画面グリッドの実サイズに合わせて連続的にレイアウトし直します）、ユーザーが
  ウィジェットをリサイズするとその都度再計算されます。
- `ScheduleRefreshWorker`が30分ごとにOutlookから直近7日分の予定を取得して
  `ScheduleStore`（アプリ専用ストレージ内のJSONファイル）に保存し、ウィジェットを
  更新します。ネットワークに失敗した場合は前回分のキャッシュを表示し続けます。
- 本体アプリの「更新」ボタン、またはサインイン直後にも同じ更新処理が走り、
  即座にウィジェットへ反映されます。
- ウィジェットをタップすると、Outlookの日表示ページ（`outlook.office.com`）を
  開きます。Outlookアプリが入っていればそちらに、無ければブラウザまたは
  Googleカレンダーなどの既定のハンドラーに遷移します。

## 既知の制限・カスタマイズ余地

- 現状はOutlookの既定カレンダーの予定のみ取得します（`/me/calendarview`）。
  複数カレンダーの選択機能は未実装です。
- 同じ日に時間が重なる予定がある場合、ウィジェットは時系列順に積み上げて表示します
  （Jetpack Glanceでは、絶対位置で複数の矩形を重ねて描くレイアウトが安定して
  描画されなかったため、iOS版のような真の重なり表示ではなく、上から順に
  並べる方式にしています）。個人カレンダー用途では通常問題になりません。
- バックグラウンド更新はWorkManagerの`PeriodicWorkRequest`（最短15分間隔）を
  使っており、実際の発火間隔はAndroidのバッテリー最適化状況に依存します。
- サインアウトはアプリ内からのみ可能です。
