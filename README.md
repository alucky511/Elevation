# Elevation

Pixel Watch / Fitbit が Health Connect に保存したウォーキング記録から、日ごとの累積標高を集計する Android + Wear OS アプリです。

## 構成

- `mobile`: Health Connect から過去7日間のウォーキングと獲得標高を読み、日別に表示
- `wear`: スマホから Wear OS Data Layer で同期された本日の累積標高を表示

端末内で完結し、サーバーへ健康データを送信しません。

## 必要環境

- Android Studio（JDK 17同梱版）
- Android SDK 35
- Android 8.0以上のスマートフォン
- Wear OS 3以上の Pixel Watch または Wear OS エミュレーター

## 実行

1. Android Studioでこのフォルダーを開いてGradle Syncを実行します。
2. `mobile` をスマートフォンへ、`wear` をペアリング済みPixel Watchへインストールします。
3. スマートフォンで「Health Connectを許可」を押し、運動と獲得標高の読み取りを許可します。
4. Fitbit側で「FitbitとHealth Connect」を有効にし、ウォーキング記録を同期します。
5. 「最新の結果に更新」を押すと、今日の値が時計にも同期されます。
6. GPXを書き出す場合は「今日のGPXをDriveへ書き出す」または「昨日のGPXをDriveへ書き出す」を押します。
7. 毎日のバックアップを使う場合は「毎日のGPX自動バックアップをONにする」を押し、GoogleアカウントとDrive権限を許可します。

## 集計仕様

- Health Connectの `ExerciseSessionRecord` のうち `EXERCISE_TYPE_WALKING` のみ対象
- 重複したウォーキング時間帯は結合し、二重集計を防止
- 各時間帯の `ElevationGainedRecord.ELEVATION_GAINED_TOTAL` を合計
- 日境界は端末の現在のタイムゾーンを使用

Health Connectに獲得標高が書き込まれていないウォーキングは0mになります。

## GPX / Driveバックアップ

- GPXファイル名は `walking-YYYY-MM-DD.gpx`
- 自動バックアップはAndroid標準APIのJobSchedulerで毎日 02:30 ごろに前日分をGoogle Driveへ保存
- Androidの省電力制御により、実行時刻は多少ずれることがあります
- Drive連携にはGoogle Cloud ConsoleでDrive APIとAndroid OAuthクライアントの設定が必要です

Health Connectの仕様上、他アプリが作成した運動ルートはバックグラウンドで読めない場合があります。Pixel Watch / Fitbitが作成したルートで同意が必要になった場合、自動バックアップは通知を出し、アプリ上の手動書き出しでルート読み取り同意を求めます。

## セキュリティ

セキュリティ方針と外部依存の扱いは [docs/security.md](docs/security.md) にまとめています。
