package com.elevation.mobile

import android.Manifest
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.contracts.ExerciseRouteRequestContract
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.readRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.ExerciseRoute
import androidx.health.connect.client.records.ExerciseRouteResult
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val requiredHealthPermissions =
        setOf(
            HealthPermission.getReadPermission(ExerciseSessionRecord::class),
            HealthPermission.getReadPermission(ElevationGainedRecord::class)
        )
    private lateinit var elevationText: TextView
    private lateinit var detailText: TextView
    private lateinit var healthPermissionStatusText: TextView
    private lateinit var watchSyncStatusText: TextView
    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var healthPermissionButton: Button
    private lateinit var refreshButton: Button
    private lateinit var watchSyncButton: Button
    private lateinit var exportGpxButton: Button
    private lateinit var exportYesterdayGpxButton: Button
    private lateinit var autoBackupButton: Button

    private val backupSettings by lazy { GpxBackupSettings(this) }

    private var pendingElevationCalculation: PendingElevationCalculation? = null
    private var pendingRouteExport: PendingRouteExport? = null
    private var pendingGpxDocument: PendingGpxUpload? = null
    private var latestDailyElevation: DailyElevation? = null

    private val requestHealthPermissions =
        registerForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
            updateHealthPermissionUi(granted)
            refreshTodaySummary(requestPermissionIfMissing = false)
        }

    private val requestExerciseRoute =
        registerForActivityResult(ExerciseRouteRequestContract()) { exerciseRoute: ExerciseRoute? ->
            val elevationCalculation = pendingElevationCalculation
            if (elevationCalculation != null) {
                if (exerciseRoute != null) {
                    elevationCalculation.meters += calculateElevationGain(exerciseRoute)
                    elevationCalculation.routeCount += 1
                }
                elevationCalculation.index += 1
                lifecycleScope.launch {
                    try {
                        collectRemainingElevationRoutes()
                    } catch (error: Throwable) {
                        if (error is CancellationException) throw error
                        pendingElevationCalculation = null
                        showIdle(
                            "ルート高度の読み込みに失敗しました: " +
                                (error.message ?: error.javaClass.simpleName)
                        )
                    }
                }
                return@registerForActivityResult
            }

            val pending = pendingRouteExport ?: return@registerForActivityResult
            val session = pending.sessions.getOrNull(pending.index)

            if (exerciseRoute != null && session != null) {
                pending.segments += GpxTrackSegment(session.title ?: "Walking ${pending.index + 1}", exerciseRoute)
            }

            pending.index += 1
            lifecycleScope.launch {
                collectRemainingRoutesAndUpload()
            }
        }

    private val createGpxDocument =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { uri ->
            val pending = pendingGpxDocument
            pendingGpxDocument = null
            if (uri == null || pending == null) {
                showIdle("GPXの保存をキャンセルしました。")
                return@registerForActivityResult
            }
            try {
                val output = contentResolver.openOutputStream(uri, "w")
                    ?: error("保存先を開けませんでした")
                output.use { stream ->
                    stream.write(pending.gpx.toByteArray(Charsets.UTF_8))
                    stream.flush()
                }
                showIdle("GPXを保存しました: ${pending.fileName}")
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                showIdle("GPXの保存に失敗しました: ${error.message ?: error.javaClass.simpleName}")
            }
        }

    private val requestDriveBackupAuthorization =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                finishEnablingAutoBackup()
            } else {
                showIdle("Driveの認可が完了していないため、自動バックアップは有効にしていません。")
            }
        }

    private val requestGoogleSignIn =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            try {
                val account =
                    GoogleSignIn.getSignedInAccountFromIntent(result.data)
                        .getResult(ApiException::class.java)
                val email = account.email
                if (email.isNullOrBlank()) {
                    showIdle("Googleアカウントのメールアドレスを取得できませんでした。")
                    return@registerForActivityResult
                }
                backupSettings.accountName = email
                authorizeDriveForAutoBackup()
            } catch (error: ApiException) {
                showIdle("Googleアカウントの選択に失敗しました: ${error.statusCode}")
            }
        }

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            startGoogleSignInForAutoBackup()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        updateAutoBackupUi()
        refreshTodaySummary(requestPermissionIfMissing = false)
    }

    override fun onResume() {
        super.onResume()
        refreshHealthPermissionStatus()
    }

    private fun buildUi() {
        elevationText =
            TextView(this).apply {
                text = "-- m"
                textSize = 52f
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#F5FFF9"))
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                includeFontPadding = false
            }
        detailText =
            TextView(this).apply {
                text = "今日のウォーキングを読み込みます"
                textSize = 15f
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#9DB7B0"))
                minHeight = dp(28)
            }
        healthPermissionStatusText =
            TextView(this).apply {
                text = "Health Connect権限を確認しています…"
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#79D4B2"))
                minHeight = dp(24)
            }
        watchSyncStatusText =
            TextView(this).apply {
                text = "Pixel Watch: 同期状態を確認します"
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#9DB7B0"))
                minHeight = dp(24)
            }
        statusText =
            TextView(this).apply {
                text = ""
                textSize = 13f
                gravity = Gravity.CENTER
                maxLines = 2
                setTextColor(Color.parseColor("#87A39B"))
            }
        progressBar =
            ProgressBar(this).apply {
                visibility = View.INVISIBLE
                indeterminateTintList = ColorStateList.valueOf(Color.parseColor("#9EF3C9"))
            }
        healthPermissionButton =
            Button(this).apply {
                text = "Health Connectの権限を許可"
                styleButton(this, primary = true)
                setOnClickListener { view ->
                    acknowledgeTap(view, "Health Connectの権限を確認します…")
                    lifecycleScope.launch {
                        val client = healthConnectClientOrNull()
                        if (client == null) {
                            showIdle("Health Connectを利用できません。")
                            return@launch
                        }
                        val granted = client.permissionController.getGrantedPermissions()
                        if (granted.containsAll(requiredHealthPermissions)) {
                            openHealthConnectPermissionSettings()
                        } else {
                            requestHealthPermissions.launch(requiredHealthPermissions)
                        }
                    }
                }
            }
        refreshButton =
            Button(this).apply {
                text = "今日の累積標高を更新"
                styleButton(this, primary = true)
                setOnClickListener { view ->
                    acknowledgeTap(view, "今日のデータを更新します…")
                    refreshTodaySummary()
                }
            }
        watchSyncButton =
            Button(this).apply {
                text = "Pixel Watchへ再同期"
                styleButton(this, primary = false)
                setOnClickListener { view ->
                    acknowledgeTap(view, "Pixel Watchへ再送します…")
                    val latest = latestDailyElevation
                    if (latest == null) {
                        refreshTodaySummary()
                    } else {
                        syncToWatch(latest)
                    }
                }
            }
        exportGpxButton =
            Button(this).apply {
                text = "今日のGPXを保存"
                styleButton(this, primary = false)
                setOnClickListener { view ->
                    acknowledgeTap(view, "今日のGPXを準備します…")
                    startGpxExport(LocalDate.now(ZoneId.systemDefault()))
                }
            }
        exportYesterdayGpxButton =
            Button(this).apply {
                text = "昨日のGPXを保存"
                styleButton(this, primary = false)
                setOnClickListener { view ->
                    acknowledgeTap(view, "昨日のGPXを準備します…")
                    startGpxExport(LocalDate.now(ZoneId.systemDefault()).minusDays(1))
                }
            }
        autoBackupButton =
            Button(this).apply {
                styleButton(this, primary = false)
                setOnClickListener { view ->
                    acknowledgeTap(view, "自動バックアップ設定を確認します…")
                    toggleAutoBackup()
                }
            }

        val card =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(24), dp(28), dp(24), dp(24))
                background =
                    roundedBackground(
                        fillColor = Color.parseColor("#0D222D"),
                        radiusDp = 28,
                        strokeColor = Color.parseColor("#1D3A44")
                    )
                addView(
                    TextView(this@MainActivity).apply {
                        text = "TODAY"
                        textSize = 12f
                        letterSpacing = 0.16f
                        gravity = Gravity.CENTER
                        setTextColor(Color.parseColor("#79D4B2"))
                    },
                    matchWrap()
                )
                addView(
                    TextView(this@MainActivity).apply {
                        text = "今日の累積標高"
                        textSize = 20f
                        gravity = Gravity.CENTER
                        setTextColor(Color.parseColor("#DCECE7"))
                        setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                    },
                    matchWrap()
                )
                addView(elevationText, matchWrap())
                addView(detailText, matchWrap())
                addView(healthPermissionStatusText, matchWrap())
                addView(watchSyncStatusText, matchWrap())
                addView(
                    progressBar,
                    LinearLayout.LayoutParams(dp(32), dp(32)).apply {
                        topMargin = dp(12)
                    }
                )
                addView(healthPermissionButton, matchWrap())
                addView(refreshButton, matchWrap())
                addView(watchSyncButton, matchWrap())
                addView(exportGpxButton, matchWrap())
                addView(exportYesterdayGpxButton, matchWrap())
                addView(autoBackupButton, matchWrap())
                addView(
                    statusText,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(56)
                    ).apply {
                        topMargin = dp(10)
                    }
                )
            }

        val content =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(20), dp(24), dp(20), dp(24))
                addView(
                    card,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
            }

        val root =
            ScrollView(this).apply {
                isFillViewport = true
                setBackgroundColor(Color.parseColor("#06131C"))
                isVerticalScrollBarEnabled = false
                addView(
                    content,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
            }

        setContentView(root)
    }

    private fun styleButton(button: Button, primary: Boolean) {
        val fillColor =
            Color.parseColor(if (primary) "#9EF3C9" else "#142D38")
        val textColor =
            Color.parseColor(if (primary) "#06202A" else "#DCECE7")
        val strokeColor =
            if (primary) null else Color.parseColor("#31515C")
        val rippleColor =
            Color.parseColor(if (primary) "#4DFFFFFF" else "#3379D4B2")

        button.isAllCaps = false
        button.textSize = 14f
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        button.setTextColor(textColor)
        button.minHeight = dp(52)
        button.minimumHeight = dp(52)
        button.stateListAnimator = null
        button.elevation = 0f
        button.setPadding(dp(16), 0, dp(16), 0)
        button.background =
            RippleDrawable(
                ColorStateList.valueOf(rippleColor),
                roundedBackground(fillColor, 16, strokeColor),
                null
            )
        button.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.scaleX = 0.98f
                    view.scaleY = 0.98f
                    view.alpha = 0.82f
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(110L).start()
                }
            }
            false
        }
    }

    private fun roundedBackground(
        fillColor: Int,
        radiusDp: Int,
        strokeColor: Int? = null
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            color = ColorStateList.valueOf(fillColor)
            cornerRadius = dp(radiusDp).toFloat()
            if (strokeColor != null) {
                setStroke(dp(1), strokeColor)
            }
        }
    }

    private fun refreshTodaySummary(requestPermissionIfMissing: Boolean = true) {
        lifecycleScope.launch {
            pendingElevationCalculation = null
            setBusy("Health Connectを確認しています…")
            try {
                val client = healthConnectClientOrNull()
                if (client == null) {
                    healthPermissionStatusText.text = "Health Connect: 利用できません"
                    showIdle("Health Connectを利用できません。端末の設定を確認してください。")
                    return@launch
                }

                if (!hasHealthPermissions(client)) {
                    updateHealthPermissionUi(client.permissionController.getGrantedPermissions())
                    showIdle("Health Connectの権限を許可してください。")
                    if (requestPermissionIfMissing) {
                        requestHealthPermissions.launch(requiredHealthPermissions)
                    }
                    return@launch
                }

                updateHealthPermissionUi(client.permissionController.getGrantedPermissions())
                val today = todayWindow()
                val sessions = readTodayWalkingSessions(client, today)
                val elevationMeters = readWalkingElevationMeters(client, sessions, today)

                if (elevationMeters > 0.0 || sessions.isEmpty()) {
                    showElevationResult(
                        meters = elevationMeters,
                        walkingCount = sessions.size,
                        status = if (sessions.isEmpty()) {
                            "今日のウォーキング記録はまだありません。"
                        } else {
                            "Health Connectの獲得標高から更新しました。"
                        }
                    )
                } else {
                    pendingElevationCalculation =
                        PendingElevationCalculation(
                            sessions = sessions,
                            index = 0,
                            meters = 0.0,
                            routeCount = 0
                        )
                    collectRemainingElevationRoutes()
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                pendingElevationCalculation = null
                showIdle("読み込みに失敗しました: ${error.message ?: error.javaClass.simpleName}")
            }
        }
    }

    private suspend fun collectRemainingElevationRoutes() {
        val client = healthConnectClientOrNull()
        val pending = pendingElevationCalculation
        if (client == null || pending == null) {
            showIdle("Health Connectを利用できません。")
            return
        }

        while (pending.index < pending.sessions.size) {
            val session = pending.sessions[pending.index]
            val recordId = session.metadata.id
            if (recordId.isBlank()) {
                pending.index += 1
                continue
            }

            setBusy("運動ルートの高度を確認しています (${pending.index + 1}/${pending.sessions.size})…")
            val detailedSession = client.readRecord<ExerciseSessionRecord>(recordId).record
            when (val routeResult = detailedSession.exerciseRouteResult) {
                is ExerciseRouteResult.Data -> {
                    pending.meters += calculateElevationGain(routeResult.exerciseRoute)
                    pending.routeCount += 1
                    pending.index += 1
                }

                is ExerciseRouteResult.ConsentRequired -> {
                    statusText.text = "このウォーキングのルート高度を読むには本人の許可が必要です。"
                    requestExerciseRoute.launch(recordId)
                    return
                }

                is ExerciseRouteResult.NoData -> {
                    pending.index += 1
                }
            }
        }

        pendingElevationCalculation = null
        showElevationResult(
            meters = pending.meters,
            walkingCount = pending.sessions.size,
            status =
                if (pending.routeCount > 0) {
                    "運動ルートの高度から更新しました。"
                } else {
                    "今日の記録には標高データが保存されていません。"
                }
        )
    }

    private fun calculateElevationGain(route: ExerciseRoute): Double =
        RouteElevationCalculator.calculate(
            route.route.sortedBy { it.time }.map { point ->
                RouteElevationCalculator.Sample(
                    point.altitude?.inMeters,
                    point.verticalAccuracy?.inMeters,
                )
            }
        )

    private fun showElevationResult(
        meters: Double,
        walkingCount: Int,
        status: String
    ) {
        elevationText.text = "${meters.coerceAtLeast(0.0).toInt()} m"
        detailText.text = "ウォーキング ${walkingCount}件"
        showIdle(status)
        val latest =
            DailyElevation(LocalDate.now(ZoneId.systemDefault()), meters, walkingCount)
        latestDailyElevation = latest
        syncToWatch(latest)
    }

    private fun syncToWatch(value: DailyElevation) {
        watchSyncStatusText.text = "Pixel Watch: 同期中…"
        lifecycleScope.launch {
            try {
                val result = WearSync.send(applicationContext, value)
                watchSyncStatusText.text =
                    if (result.connectedNodeCount > 0) {
                        "Pixel Watch: 送信済み（時計で表示を確認）"
                    } else {
                        "Pixel Watch: データ保存済み・接続待ち"
                    }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                android.util.Log.e("ElevationWearSync", "Sync failed", error)
                watchSyncStatusText.text =
                    "Pixel Watch: 同期失敗 (${error.message ?: error.javaClass.simpleName})"
            }
        }
    }

    private fun startGpxExport(date: LocalDate) {
        lifecycleScope.launch {
            setBusy("${date} のウォーキングルートを読み込んでいます…")
            try {
                val client = healthConnectClientOrNull()
                if (client == null) {
                    showIdle("Health Connectを利用できません。")
                    return@launch
                }

                if (!hasHealthPermissions(client)) {
                    showIdle("Health Connectの権限を許可してください。")
                    requestHealthPermissions.launch(requiredHealthPermissions)
                    return@launch
                }

                val sessions = readTodayWalkingSessions(client, dayWindow(date))
                if (sessions.isEmpty()) {
                    showIdle("${date} のウォーキング記録がありません。")
                    return@launch
                }

                pendingRouteExport =
                    PendingRouteExport(
                        date = date,
                        sessions = sessions,
                        index = 0,
                        segments = mutableListOf()
                    )
                collectRemainingRoutesAndUpload()
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                showIdle("${date} のGPX書き出し準備に失敗しました: ${error.message ?: error.javaClass.simpleName}")
            }
        }
    }

    private suspend fun collectRemainingRoutesAndUpload() {
        val client = healthConnectClientOrNull()
        val pending = pendingRouteExport
        if (client == null || pending == null) {
            showIdle("Health Connectを利用できません。")
            return
        }

        while (pending.index < pending.sessions.size) {
            val session = pending.sessions[pending.index]
            val recordId = session.metadata.id
            if (recordId.isBlank()) {
                pending.index += 1
                continue
            }

            setBusy("ルートを読み込んでいます (${pending.index + 1}/${pending.sessions.size})…")
            val detailedSession = client.readRecord<ExerciseSessionRecord>(recordId).record

            when (val routeResult = detailedSession.exerciseRouteResult) {
                is ExerciseRouteResult.Data -> {
                    pending.segments +=
                        GpxTrackSegment(session.title ?: "Walking ${pending.index + 1}", routeResult.exerciseRoute)
                    pending.index += 1
                }

                is ExerciseRouteResult.ConsentRequired -> {
                    statusText.text = "運動ルートの読み取り許可が必要です。表示される画面で許可してください。"
                    requestExerciseRoute.launch(recordId)
                    return
                }

                is ExerciseRouteResult.NoData -> {
                    pending.index += 1
                }
            }
        }

        val segmentsWithPoints = pending.segments.filter { it.route.route.orEmpty().isNotEmpty() }
        if (segmentsWithPoints.isEmpty()) {
            pendingRouteExport = null
            showIdle("${pending.date} のウォーキングにGPXへ書き出せるルート情報がありません。")
            return
        }

        val fileName = GpxExporter.fileNameFor(pending.date)
        val gpx = GpxExporter.build(pending.date, segmentsWithPoints)
        pendingRouteExport = null
        pendingGpxDocument = PendingGpxUpload(fileName, gpx)
        showIdle("保存先を選んでください。Google Driveも選択できます。")
        createGpxDocument.launch(fileName)
    }

    private fun toggleAutoBackup() {
        if (backupSettings.enabled) {
            backupSettings.enabled = false
            GpxBackupScheduler.cancel(this)
            updateAutoBackupUi()
            showIdle("毎日のGPX自動バックアップをOFFにしました。")
        } else {
            enableAutoBackup()
        }
    }

    private fun enableAutoBackup() {
        setBusy("自動バックアップ用のGoogleアカウントを設定します…")
        lifecycleScope.launch {
            val client = healthConnectClientOrNull()
            if (client == null) {
                showIdle("Health Connectを利用できません。")
                return@launch
            }
            val granted = client.permissionController.getGrantedPermissions()
            updateHealthPermissionUi(granted)
            if (!granted.containsAll(requiredHealthPermissions)) {
                showIdle("先にHealth Connectの基本権限を許可してください。")
                requestHealthPermissions.launch(requiredHealthPermissions)
                return@launch
            }
            if (READ_EXERCISE_ROUTES_PERMISSION !in granted) {
                openHealthConnectPermissionSettings(
                    "自動保存には「運動ルート」の常時許可が必要です。設定で許可後、もう一度ONにしてください。"
                )
                return@launch
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                startGoogleSignInForAutoBackup()
            }
        }
    }

    private fun startGoogleSignInForAutoBackup() {
        val options =
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(Scope(DRIVE_FILE_SCOPE))
                .build()
        val client = GoogleSignIn.getClient(this, options)
        requestGoogleSignIn.launch(client.signInIntent)
    }

    private fun authorizeDriveForAutoBackup() {
        setBusy("Driveへの自動保存権限を確認しています…")
        val request =
            AuthorizationRequest.builder()
                .setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
                .build()

        Identity.getAuthorizationClient(this)
            .authorize(request)
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    val pendingIntent = result.pendingIntent
                    if (pendingIntent == null) {
                        showIdle("Drive認証の確認画面を開けませんでした。")
                    } else {
                        requestDriveBackupAuthorization.launch(
                            IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                        )
                    }
                } else {
                    finishEnablingAutoBackup()
                }
            }
            .addOnFailureListener { error ->
                showIdle("Drive認証に失敗しました: ${error.message ?: error.javaClass.simpleName}")
            }
    }

    private fun finishEnablingAutoBackup() {
        backupSettings.enabled = true
        GpxBackupScheduler.schedule(this)
        updateAutoBackupUi()
        showIdle("毎日のGPX自動バックアップをONにしました。翌日02:30ごろに前日分をDriveへ保存します。")
    }

    private suspend fun healthConnectClientOrNull(): HealthConnectClient? {
        val status = HealthConnectClient.getSdkStatus(this, HEALTH_CONNECT_PROVIDER_PACKAGE)
        return if (status == HealthConnectClient.SDK_AVAILABLE) {
            HealthConnectClient.getOrCreate(this)
        } else {
            null
        }
    }

    private suspend fun hasHealthPermissions(client: HealthConnectClient): Boolean {
        val granted = client.permissionController.getGrantedPermissions()
        return granted.containsAll(requiredHealthPermissions)
    }

    private fun refreshHealthPermissionStatus() {
        if (!::healthPermissionStatusText.isInitialized) return
        lifecycleScope.launch {
            val client = healthConnectClientOrNull()
            if (client == null) {
                healthPermissionStatusText.text = "Health Connect: 利用できません"
                return@launch
            }
            updateHealthPermissionUi(client.permissionController.getGrantedPermissions())
        }
    }

    private fun updateHealthPermissionUi(granted: Set<String>) {
        if (!::healthPermissionStatusText.isInitialized) return
        val baseCount = requiredHealthPermissions.count { it in granted }
        val routeGranted = READ_EXERCISE_ROUTES_PERMISSION in granted
        healthPermissionStatusText.text =
            "Health Connect: $baseCount/${requiredHealthPermissions.size}許可 ・ " +
                if (routeGranted) "ルート常時許可" else "ルートは使用時に確認"
        healthPermissionButton.text =
            if (baseCount == requiredHealthPermissions.size) {
                "Health Connect設定を開く"
            } else {
                "Health Connectの権限を許可"
            }
    }

    private fun openHealthConnectPermissionSettings(
        message: String = "Health Connect設定から権限を変更できます。"
    ) {
        val intent =
            Intent(MANAGE_HEALTH_PERMISSIONS_ACTION)
                .putExtra(Intent.EXTRA_PACKAGE_NAME, packageName)
        try {
            startActivity(intent)
            showIdle(message)
        } catch (_: Throwable) {
            requestHealthPermissions.launch(requiredHealthPermissions)
        }
    }

    private suspend fun readTodayWalkingSessions(
        client: HealthConnectClient,
        window: TimeWindow
    ): List<ExerciseSessionRecord> = WalkingSessionReader(client).read(window)

    private suspend fun readTodayElevationMeters(
        client: HealthConnectClient,
        window: TimeWindow
    ): Double {
        val response =
            client.aggregate(
                AggregateRequest(
                    metrics = setOf(ElevationGainedRecord.ELEVATION_GAINED_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(window.start, window.end)
                )
            )

        return response[ElevationGainedRecord.ELEVATION_GAINED_TOTAL]?.inMeters ?: 0.0
    }

    private suspend fun readWalkingElevationMeters(
        client: HealthConnectClient,
        sessions: List<ExerciseSessionRecord>,
        day: TimeWindow
    ): Double {
        val walkingWindows =
            WalkingWindows.merge(
                sessions.map { session ->
                    TimeWindow(
                        start = maxOf(session.startTime, day.start),
                        end = minOf(session.endTime, day.end)
                    )
                }
            )
        return walkingWindows.sumOf { window ->
            readTodayElevationMeters(client, window)
        }.coerceAtLeast(0.0)
    }

    private fun todayWindow(): TimeWindow {
        return dayWindow(LocalDate.now(ZoneId.systemDefault()), Instant.now())
    }

    private fun dayWindow(
        date: LocalDate,
        endOverride: Instant? = null
    ): TimeWindow {
        val zone = ZoneId.systemDefault()
        val start = date.atStartOfDay(zone).toInstant()
        val end = endOverride ?: date.plusDays(1).atStartOfDay(zone).toInstant()
        return TimeWindow(start = start, end = end)
    }

    private fun setBusy(message: String) {
        progressBar.visibility = View.VISIBLE
        setActionButtonsEnabled(false)
        statusText.text = message
    }

    private fun acknowledgeTap(view: View, message: String) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        statusText.text = message
    }

    private fun showIdle(message: String) {
        progressBar.visibility = View.INVISIBLE
        setActionButtonsEnabled(true)
        statusText.text = message
        updateAutoBackupUi()
    }

    private fun setActionButtonsEnabled(enabled: Boolean) {
        listOf(
            healthPermissionButton,
            refreshButton,
            watchSyncButton,
            exportGpxButton,
            exportYesterdayGpxButton,
            autoBackupButton
        ).forEach { button ->
            button.animate().cancel()
            button.isEnabled = enabled
            button.alpha = if (enabled) 1f else 0.55f
        }
    }

    private fun updateAutoBackupUi() {
        if (!::autoBackupButton.isInitialized) return
        val account = backupSettings.accountName
        autoBackupButton.text =
            if (backupSettings.enabled) {
                "毎日のGPX自動バックアップ: ON\n${account.orEmpty()}"
            } else {
                "毎日のGPX自動バックアップをONにする"
            }
    }

    private fun matchWrap(): ViewGroup.LayoutParams {
        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(10)
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private data class PendingElevationCalculation(
        val sessions: List<ExerciseSessionRecord>,
        var index: Int,
        var meters: Double,
        var routeCount: Int
    )

    private data class PendingRouteExport(
        val date: LocalDate,
        val sessions: List<ExerciseSessionRecord>,
        var index: Int,
        val segments: MutableList<GpxTrackSegment>
    )

    private data class PendingGpxUpload(
        val fileName: String,
        val gpx: String
    )

    companion object {
        private const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
        private const val HEALTH_CONNECT_PROVIDER_PACKAGE = "com.google.android.apps.healthdata"
        private const val READ_EXERCISE_ROUTES_PERMISSION =
            "android.permission.health.READ_EXERCISE_ROUTES"
        private const val MANAGE_HEALTH_PERMISSIONS_ACTION =
            "android.health.connect.action.MANAGE_HEALTH_PERMISSIONS"
    }
}
