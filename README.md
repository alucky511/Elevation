# Elevation

Pixel Watch / Fitbit が Health Connect に保存したウォーキングから、今日の累積上昇標高を表示する Android + Wear OS アプリです。スマートフォン自身でGPS記録を開始するアプリではありません。

## 構成と集計

- `mobile`: 今日のウォーキング区間を結合し、Health Connectの獲得標高を集計。0mの場合は、ユーザーが読み取りを許可した運動ルートの高度から推定します。
- `wear`: スマートフォンからData Layer経由で届いた今日の値を表示。過去日の値は「-- m」と最終記録日を表示し、今日の値と区別します。
- 獲得標高は上りの合計です。下降量や現在地の標高は表示しません。
- ルート補完は5点移動平均と差分フィルター（0.75〜30m）を使う推定値です。高度欠損や垂直精度30m超の点は区間を分割します。実測による精度保証はありません。
- ルート補完時の重複セッションや日付をまたぐルートの扱いは、[レビュー記録](docs/implementation-review.md)の残課題を参照してください。

## 開発・実行

JDK 17、Android SDK 36、Gradle 8.11.1を使用します。スマートフォンはAndroid 8.0以上（Health Connectの提供状況にも依存）、時計はWear OS 3以上が対象です。

1. Android Studioで開き、Gradle Syncを実行します。
2. `mobile`と`wear`を同じ署名でビルドし、スマートフォンとペアリングした時計にそれぞれインストールします。
3. FitbitからHealth Connectへの同期と、アプリの運動・獲得標高読み取り権限を許可します。
4. スマホで更新し、時計で今日の値を確認します。「送信済み」は時計での受信確認を意味しません。

Windowsでの検証:

```powershell
.\gradlew.bat :mobile:testDebugUnitTest :wear:testDebugUnitTest :mobile:assembleDebug :wear:assembleDebug :mobile:lintDebug :wear:lintDebug
```

GitHub Actionsでも同じテスト・ビルド・Lintを実行します。

## GPXとDrive

手動書き出しはAndroidの保存先選択画面を使用し、端末または利用可能なGoogle Driveへ `walking-YYYY-MM-DD.gpx` を保存します。自動バックアップは任意で有効化し、JobSchedulerで毎日02:30以降に前日分をDriveへ保存します。省電力制御、Health Connectのバックグラウンド権限やルート同意によって完了しない場合があります。

標高集計は端末内で処理します。Drive保存を選択・有効化した場合、GPXに含まれる位置・時刻・高度をDriveへ送信します。設定は[Drive設定](docs/google-drive-setup.md)、外部送信方針は[セキュリティ](docs/security.md)を参照してください。

## Pixel Watchアイコン

山と上向き矢印のベクターアイコンを使用します。スマホと時計で共通の図柄とし、円形マスクと単色テーマに対応しています。小さな表示での識別を優先しました。

## スマホ画面と表示検証

ホームで今日の標高とWatch同期を確認し、保存・設定でGPX出力、自動バックアップ、Health Connect権限を操作します。画面表示は `MobileDashboardView` に分離しています。

Pixel 10a / Pixel Watch 4を実機確認の対象としています。Androidエミュレーターで3種類の画面幅と3段階の文字倍率について両ページを検査し、18枚の画像をActionsの `elevation-ui-previews` に保存します。APKは `elevation-debug-apks` から取得できます。[実機確認手順](docs/device-validation.md)を参照してください。実機確認は未実施です。
