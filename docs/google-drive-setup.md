# Google Drive GPX Export Setup

このアプリの「今日のGPXをDriveへ書き出す」機能を実機で使うには、Google Cloud 側の設定が必要です。

## 必要な設定

1. Google Cloud Console でプロジェクトを作成する
2. Google Drive API を有効化する
3. OAuth 同意画面を設定する
4. Android 用 OAuth クライアントを作成する
   - package name: `com.elevation`
   - SHA-1: Android Studio の debug keystore またはリリース署名の SHA-1
5. テスト中は OAuth 同意画面のテストユーザーに自分の Google アカウントを追加する

## 使用するスコープ

```text
https://www.googleapis.com/auth/drive.file
```

このスコープは、アプリが作成・選択したファイルへのアクセスに限定されるため、Drive 全体を読む権限より小さくできます。

## 実機確認の流れ

1. Pixel Watch / Fitbit でGPSルート付きのウォーキングを記録する
2. Fitbit から Health Connect へ同期する
3. スマホアプリで Health Connect 権限を許可する
4. 「今日のGPXをDriveへ書き出す」を押す
5. ルート読み取り同意画面が出たら許可する
6. Google Drive の認可画面が出たら許可する
7. Google Drive のマイドライブ直下に `walking-YYYY-MM-DD.gpx` が作成される

## 毎日の自動バックアップ

スマホアプリの「毎日のGPX自動バックアップをONにする」を押すと、Googleアカウント選択とDrive権限確認のあと、毎日 02:30 ごろに前日分のGPXバックアップを試行します。

Android の省電力制御により、実行時刻は数分〜数十分ずれることがあります。端末再起動後も JobScheduler によりスケジュールは維持されます。

### 自動化の制限

Health Connect の仕様上、他アプリが作成した運動ルートはバックグラウンドで無断取得できません。Pixel Watch / Fitbit が作成したルートの場合、自動バックアップ時に `ConsentRequired` になることがあります。その場合は通知を出し、アプリを開いて手動の「今日のGPXをDriveへ書き出す」操作でルート読み取りを許可してください。

## 注意

Health Connect の仕様上、他アプリが作成した運動ルートは、バックグラウンドで無断取得できません。ユーザーがアプリ画面上でボタンを押したタイミングで、必要に応じて一回ごとの同意画面が表示されます。
