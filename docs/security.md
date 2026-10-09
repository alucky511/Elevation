# Security Notes

## 方針

このアプリは健康データと位置情報を扱うため、以下の方針で実装しています。

- 健康データは端末内で処理する
- Driveアップロード以外の外部送信を行わない
- Drive権限は `drive.file` の最小スコープを使う
- Googleアクセストークンをアプリ内に保存しない
- 自動バックアップ用に保存する情報はGoogleアカウントのメールアドレスと最終実行状態のみ
- HTTP平文通信を禁止する
- Androidの端末バックアップ対象からアプリデータを除外する
- バックグラウンド処理コンポーネントは外部アプリから起動できないようにする

## OSS / 外部依存の扱い

AndroidアプリとしてHealth Connect、Wear OS Data Layer、Google Drive連携を使うため、AndroidX / Google Play services / Health Connect の公式SDKは残しています。

一方で、不要な追加依存は削除しました。

- `androidx.work:work-runtime-ktx` を削除
- `androidx.core:core-ktx` を削除
- `kotlinx-coroutines-play-services` を削除

毎日のバックアップはWorkManagerではなく、Android標準APIの `JobScheduler` / `JobService` で実装しています。

## 制限

Health Connectの仕様上、他アプリが作成した運動ルートはバックグラウンドで自動取得できない場合があります。その場合は通知で知らせ、アプリ画面から手動書き出しを行います。
