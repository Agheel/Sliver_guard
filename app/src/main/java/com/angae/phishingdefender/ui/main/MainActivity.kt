package com.angae.phishingdefender.ui.main

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.angae.phishingdefender.databinding.ActivityMainBinding
import com.angae.phishingdefender.databinding.DialogTestModeBinding
import com.angae.phishingdefender.domain.detector.PhishingProcessor
import com.angae.phishingdefender.domain.model.SmsMessage
import com.angae.phishingdefender.ui.alert.AlertActivity
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * [SRP] 앱의 메인 진입점으로서 Flavor에 따른 UI 초기화 및 권한 관리를 담당함.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val processor = PhishingProcessor.createDefault()
    private val scope = CoroutineScope(Dispatchers.Main)
    private var alertListener: ListenerRegistration? = null
    private var simulationListener: ListenerRegistration? = null
    private var linkStatusListener: ListenerRegistration? = null
    private val historyAdapter = AlertHistoryAdapter()
    private var isFirstLoad = true

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val smsGranted = permissions[Manifest.permission.RECEIVE_SMS] ?: false
        val notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.POST_NOTIFICATIONS] ?: false
        } else true

        if (!smsGranted) {
            Toast.makeText(this, "문자 수신 권한이 필요합니다. 설정에서 허용해주세요.", Toast.LENGTH_LONG).show()
        }
        if (!notificationGranted) {
            Toast.makeText(this, "알림 권한이 거부되어 긴급 알림을 받을 수 없습니다.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUIByFlavor()
        checkNotificationPermission()
        checkOverlayPermission()
        checkBatteryOptimization()
        syncFcmToken()
        handleFcmIntent(intent)
        startPulseAnimation()
    }

    private fun startPulseAnimation() {
        binding.viewPulse.animate()
            .scaleX(1.5f)
            .scaleY(1.5f)
            .alpha(0f)
            .setDuration(2000)
            .withEndAction {
                binding.viewPulse.scaleX = 1f
                binding.viewPulse.scaleY = 1f
                binding.viewPulse.alpha = 0.3f
                startPulseAnimation()
            }
            .start()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleFcmIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        alertListener?.remove()
        simulationListener?.remove()
        linkStatusListener?.remove()
    }

    private fun handleFcmIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("from_fcm", false) == true) {
            val title = intent.getStringExtra("fcm_title") ?: "긴급 알림"
            val message = intent.getStringExtra("fcm_message") ?: "위험이 감지되었습니다."
            
            AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("확인", null)
                .show()
        }
    }

    /**
     * [OCP] Gradle Flavor(elderly/guardian)에 따라 서로 다른 대시보드 기능을 활성화함.
     */
    private fun setupUIByFlavor() {
        when (com.angae.phishingdefender.BuildConfig.FLAVOR) {
            "elderly" -> {
                setupElderlyUI()
                setupElderlyTestButton()
                startObservingSimulations()
            }
            "guardian" -> {
                setupGuardianUI()
                setupGuardianTestButton()
            }
        }
    }

    private fun getNumericElderCode(): String {
        val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        // ANDROID_ID를 해시코드로 변환 후 양수 6자리 숫자로 만듦
        val hash = Math.abs(androidId.hashCode())
        return (hash % 1000000).toString().padStart(6, '0')
    }

    private fun setupElderlyUI() {
        val myId = getNumericElderCode()
        
        // [추가] 앱 재설치 후 첫 실행 시, 서버에 남아있는 이전 연결 정보 강제 삭제
        val prefs = getSharedPreferences("prefs", MODE_PRIVATE)
        val isFirstLaunch = prefs.getBoolean("is_first_launch_after_install_$myId", true)
        if (isFirstLaunch) {
            FirebaseFirestore.getInstance().collection("links").document(myId).delete()
                .addOnSuccessListener { Log.d("MainActivity", "First launch: Cleared old links for $myId") }
            prefs.edit().putBoolean("is_first_launch_after_install_$myId", false).apply()
        }

        binding.tvStatus.text = "🛡️ 보호 번호: $myId"
        binding.tvStatus.setTextColor(ContextCompat.getColor(this, com.angae.phishingdefender.R.color.guard_gold))
        binding.btnTestPhishing.visibility = View.VISIBLE
        binding.tvGuidance.visibility = View.VISIBLE
        binding.tvGuidance.text = "보호자 폰에 위 번호를 입력하면 연결됩니다."
        
        // [수정] 화면에 코드가 뜨는 즉시 서버에 강제 등록 (연결 지연 방지)
        updateTokenOnFirestore(null)
        // [추가] 보호자가 연결했는지 감시 시작
        startObservingLinkStatus(myId)
    }

    private fun startObservingLinkStatus(myId: String) {
        linkStatusListener?.remove()
        linkStatusListener = FirebaseFirestore.getInstance().collection("links").document(myId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("MainActivity", "Link status listen failed", e)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val guardianIds = snapshot.get("guardianIds") as? List<*>
                    Log.d("MainActivity", "Link snapshot update: guardianIds size = ${guardianIds?.size}")

                    if (!guardianIds.isNullOrEmpty()) {
                        val firstGuardianId = guardianIds.firstOrNull() as? String
                        val relation = snapshot.getString("relationship_$firstGuardianId") ?: "가족"
                        
                        binding.tvStatus.text = "🛡️ [$relation]님이 보호 중"
                        binding.tvStatus.setTextColor(ContextCompat.getColor(this, com.angae.phishingdefender.R.color.guard_emerald))
                        binding.tvGuidance.text = "현재 보호자와 연결되어 안전하게 보호받고 있습니다."
                    } else {
                        // 보호자 목록이 비어있는 경우 (연결 해제됨)
                        Log.d("MainActivity", "No guardians connected, resetting UI")
                        resetElderlyUI(myId)
                    }
                } else {
                    // 문서가 존재하지 않는 경우 (초기 상태)
                    Log.d("MainActivity", "Link document not exists, resetting UI")
                    resetElderlyUI(myId)
                }
            }
    }

    private fun resetElderlyUI(myId: String) {
        binding.tvStatus.text = "🛡️ 보호 번호: $myId"
        binding.tvStatus.setTextColor(ContextCompat.getColor(this, com.angae.phishingdefender.R.color.guard_gold))
        binding.tvGuidance.text = "보호자 폰에 위 번호를 입력하면 연결됩니다."
    }

    private fun setupGuardianUI() {
        val savedCode = getSharedPreferences("prefs", MODE_PRIVATE).getString("linked_elder", null)
        val myRole = getSharedPreferences("prefs", MODE_PRIVATE).getString("my_role", "보호자")
        val targetRole = getSharedPreferences("prefs", MODE_PRIVATE).getString("target_role", "어르신")
        
        updateGuardianUI(savedCode, myRole, targetRole)

        // 드롭다운 설정
        val myRoles = arrayOf("아들", "딸", "손주", "배우자", "조카", "지인")
        val targetRoles = arrayOf("아버지", "어머니", "할아버지", "할머니", "배우자", "삼촌/고모", "지인")

        binding.spinnerMyRole.setAdapter(ArrayAdapter(this, com.angae.phishingdefender.R.layout.item_dropdown, myRoles))
        binding.spinnerTargetRole.setAdapter(ArrayAdapter(this, com.angae.phishingdefender.R.layout.item_dropdown, targetRoles))

        binding.btnLink.setOnClickListener {
            val code = binding.etElderCode.text.toString()
            val myRoleInput = binding.spinnerMyRole.text.toString()
            val targetRoleInput = binding.spinnerTargetRole.text.toString()
            
            if (myRoleInput.isEmpty() || targetRoleInput.isEmpty()) {
                Toast.makeText(this, "호칭을 모두 선택해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (code.length == 6) {
                validateAndLink(code, myRoleInput, targetRoleInput)
            } else {
                Toast.makeText(this, "6자리 번호를 정확히 입력해주세요.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnDisconnect.setOnClickListener {
            disconnectElder()
        }
    }

    private fun setupGuardianTestButton() {
        binding.btnTestMode.visibility = View.VISIBLE
        binding.btnTestMode.setOnClickListener {
            showTestModeDialog()
        }
    }

    private fun showTestModeDialog() {
        val elderCode = getSharedPreferences("prefs", MODE_PRIVATE).getString("linked_elder", null)
        if (elderCode == null) {
            Toast.makeText(this, "먼저 어르신 기기를 연결해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogBinding = DialogTestModeBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialogBinding.btnSmsCase1.setOnClickListener {
            sendSimulationCommand(elderCode, "00700-123-456", "[국제발신] 해외결제 980,000원 승인완료. 본인 아닐 시 즉시 확인: 02-1234-5678", "해외 결제 유도 스팸")
            dialog.dismiss()
        }
        dialogBinding.btnSmsCase2.setOnClickListener {
            sendSimulationCommand(elderCode, "02-1588-0000", "[검찰청] 귀하의 계좌가 범죄에 연루되었습니다. 확인을 위해 신분증 사본을 보내주세요.", "기관 사칭 피싱")
            dialog.dismiss()
        }
        dialogBinding.btnUrlCase1.setOnClickListener {
            sendSimulationCommand(elderCode, "1588-1111", "[KB국민은행] 보안등급 상향을 위해 링크를 클릭하세요: http://kb-safety-check.com", "은행 사칭 URL 포함")
            dialog.dismiss()
        }
        dialogBinding.btnUrlCase2.setOnClickListener {
            sendSimulationCommand(elderCode, "1544-1234", "[CJ대한통운] 주소지 불명으로 배송 지연. 확인 바랍니다: http://bit.ly/cj-delivery-check", "택배 사칭 단축 URL")
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun sendSimulationCommand(elderCode: String, sender: String, body: String, reason: String) {
        val simulationData = mapOf(
            "elderCode" to elderCode,
            "sender" to sender,
            "body" to body,
            "reason" to reason,
            "createdAt" to FieldValue.serverTimestamp()
        )

        FirebaseFirestore.getInstance().collection("simulations")
            .add(simulationData)
            .addOnSuccessListener {
                Toast.makeText(this, "어르신께 테스트 메시지를 보냈습니다.", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "테스트 전송 실패", Toast.LENGTH_SHORT).show()
            }
    }

    private fun startObservingSimulations() {
        val myId = getNumericElderCode() // 수정된 숫자 코드 사용
        simulationListener?.remove()
        simulationListener = FirebaseFirestore.getInstance().collection("simulations")
            .whereEqualTo("elderCode", myId)
            .addSnapshotListener { snapshots, e ->
                if (e != null || snapshots == null) return@addSnapshotListener

                for (dc in snapshots.documentChanges) {
                    if (dc.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                        if (isFinishing) return@addSnapshotListener // 이미 종료 중이면 무시

                        val sender = dc.document.getString("sender") ?: "Unknown"
                        val body = dc.document.getString("body") ?: ""
                        val reason = dc.document.getString("reason") ?: "테스트 탐지"

                        // 중복 방지를 위해 즉시 문서 삭제
                        dc.document.reference.delete()

                        // 어르신 화면에 경고 띄우기
                        val intent = Intent(this, AlertActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra("sender", sender)
                            putExtra("body", body)
                            putExtra("reason", reason)
                            putExtra("guidance", "이것은 가디언 앱에서 보낸 테스트용 메시지입니다.")
                            putStringArrayListExtra("matched", arrayListOf(sender))
                        }
                        startActivity(intent)
                    }
                }
            }
    }

    private fun updateGuardianUI(elderCode: String?, myRole: String? = "보호자", targetRole: String? = "어르신") {
        if (elderCode != null) {
            binding.tvStatus.text = "🛡️ 보호 연결 완료"
            binding.tvGuidance.text = "어르신($elderCode)과 연결되었습니다.\n현재 [$targetRole]님을 안전하게 보호 중입니다.\n[$myRole]님이 실시간으로 감시하고 있습니다."
            binding.layoutLink.visibility = View.GONE
            binding.btnDisconnect.visibility = View.VISIBLE
            binding.layoutHistory.visibility = View.VISIBLE
            binding.rvHistory.adapter = historyAdapter
            startObservingTargetElder(elderCode)
        } else {
            binding.tvStatus.text = "🔔 보호자 모드 활성화"
            binding.tvGuidance.text = "보호자용 앱입니다.\n어르신이 위험에 빠지면 실시간으로 알려드립니다."
            binding.layoutLink.visibility = View.VISIBLE
            binding.btnDisconnect.visibility = View.GONE
            binding.layoutHistory.visibility = View.GONE
            
            alertListener?.remove()
            alertListener = null
        }
    }

    private fun disconnectElder() {
        val elderCode = getSharedPreferences("prefs", MODE_PRIVATE).getString("linked_elder", null)
        val guardianId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

        if (elderCode != null) {
            // [추가] 서버(Firestore)에서 내 보호자 ID 제거
            FirebaseFirestore.getInstance().collection("links").document(elderCode)
                .update("guardianIds", FieldValue.arrayRemove(guardianId))
                .addOnSuccessListener {
                    Log.d("MainActivity", "Disconnected from elder: $elderCode")
                }
        }

        getSharedPreferences("prefs", MODE_PRIVATE).edit()
            .remove("linked_elder")
            .remove("my_role")
            .remove("target_role")
            .apply()

        updateGuardianUI(null)
        Toast.makeText(this, "연결이 해제되었습니다.", Toast.LENGTH_SHORT).show()
    }

    private fun validateAndLink(code: String, myRole: String, targetRole: String) {
        FirebaseFirestore.getInstance().collection("devices")
            .whereEqualTo("elderCode", code)
            .whereEqualTo("role", "elderly")
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    registerLinkOnFirestore(code, myRole, targetRole)
                    
                    getSharedPreferences("prefs", MODE_PRIVATE).edit()
                        .putString("linked_elder", code)
                        .putString("my_role", myRole)
                        .putString("target_role", targetRole).apply()
                    
                    updateGuardianUI(code, myRole, targetRole)
                    Toast.makeText(this, "연결 성공! 어르신 보호를 시작합니다.", Toast.LENGTH_SHORT).show()
                    binding.etElderCode.text?.clear()
                } else {
                    Toast.makeText(this, "존재하지 않는 보호 번호입니다.\n어르신 폰의 번호를 다시 확인해주세요.", Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "연결 확인 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun registerLinkOnFirestore(elderCode: String, myRole: String, targetRole: String) {
        val guardianId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        val linkData = mapOf(
            "guardianIds" to FieldValue.arrayUnion(guardianId),
            "relationship_$guardianId" to myRole,
            "targetName_$guardianId" to targetRole
        )
        
        FirebaseFirestore.getInstance().collection("links").document(elderCode)
            .set(linkData, SetOptions.merge())
            .addOnSuccessListener { Log.d("MainActivity", "Link registered for code: $elderCode with roles: $myRole / $targetRole") }
    }

    private fun startObservingTargetElder(elderCode: String) {
        alertListener?.remove() // 기존 리스너가 있다면 제거
        isFirstLoad = true
        
        alertListener = FirebaseFirestore.getInstance().collection("alerts")
            .whereEqualTo("elderCode", elderCode)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w("MainActivity", "Listen failed.", e)
                    return@addSnapshotListener
                }

                if (snapshots == null) return@addSnapshotListener

                // 히스토리 리스트 업데이트 (최신순 정렬)
                val historyList = snapshots.documents.mapNotNull { doc ->
                    val timestamp = doc.getTimestamp("createdAt")?.toDate()?.time ?: 0L
                    AlertHistory(
                        sender = doc.getString("sender") ?: "Unknown",
                        body = doc.getString("body") ?: "",
                        reason = doc.getString("reason") ?: "Unknown",
                        createdAt = timestamp
                    )
                }.sortedByDescending { it.createdAt }
                
                historyAdapter.submitList(historyList)

                // 새 알림 처리 (처음 로드 시에는 알림을 띄우지 않음)
                if (!isFirstLoad) {
                    for (dc in snapshots.documentChanges) {
                        if (dc.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                            val sender = dc.document.getString("sender") ?: "Unknown"
                            showLocalNotification("🚨 긴급! 어르신 피싱 위협 감지", "발신: $sender\n피싱 의심 문자가 도착했습니다.")
                            Toast.makeText(this, "🚨 긴급! 어르신 피싱 위협 감지\n발신: $sender", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                isFirstLoad = false
            }
    }

    /**
     * [SRP] 알림 채널 생성 및 로컬 알림 표시 (FCM 서비스와 동일한 디자인)
     */
    private fun showLocalNotification(title: String, message: String) {
        val channelId = "phishing_alert_channel"
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            channelId, "피싱 경고 알림", NotificationManager.IMPORTANCE_HIGH
        )
        notificationManager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                AlertDialog.Builder(this)
                    .setTitle("🛡️ 필수 권한 설정")
                    .setMessage("위험 상황 발생 시 경고 화면을 즉시 보여드리기 위해\n'다른 앱 위에 표시' 권한이 꼭 필요합니다.\n\n[확인]을 눌러 설정을 허용해주세요.")
                    .setPositiveButton("확인") { _, _ ->
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                        startActivity(intent)
                    }
                    .setCancelable(false)
                    .show()
            }
        }
    }

    private fun checkBatteryOptimization() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        val packageName = packageName

        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            AlertDialog.Builder(this)
                .setTitle("⚠️ 백그라운드 보호 설정")
                .setMessage("어르신을 24시간 안전하게 보호하기 위해서는 앱이 백그라운드에서 제한 없이 실행되어야 합니다.\n\n[확인]을 눌러 '배터리 최적화 제외'를 허용해주세요.")
                .setPositiveButton("확인") { _, _ ->
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                }
                .setNegativeButton("나중에", null)
                .show()
        }
    }

    private fun checkNotificationPermission() {
        val permissions = mutableListOf(Manifest.permission.RECEIVE_SMS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val neededPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (neededPermissions.isNotEmpty()) {
            // 시스템 권한 창이 뜨기 전, 어르신을 위한 한글 안내 다이얼로그를 먼저 띄움
            AlertDialog.Builder(this)
                .setTitle("🛡️ 안전 보호 시작하기")
                .setMessage("어르신을 피싱으로부터 안전하게 보호하기 위해\n[문자 읽기]와 [알림 보내기] 권한이 꼭 필요합니다.\n\n다음에 나오는 안내창에서 [허용]을 눌러주세요.")
                .setPositiveButton("확인") { _, _ ->
                    requestPermissionLauncher.launch(neededPermissions.toTypedArray())
                }
                .setCancelable(false)
                .show()
        }
    }

    private fun syncFcmToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                updateTokenOnFirestore(token)
            }
        }
    }

    private fun updateTokenOnFirestore(token: String?) {
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        val role = com.angae.phishingdefender.BuildConfig.FLAVOR
        val elderCode = getNumericElderCode()

        val deviceData = mutableMapOf<String, Any>(
            "role" to role,
            "elderCode" to elderCode,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        // 토큰이 있을 때만 업데이트 (null 허용)
        token?.let { deviceData["fcmToken"] = it }

        // [수정] 문서 ID를 deviceId + role 조합으로 만들어 한 기기에서 여러 역할을 테스트해도 덮어씌워지지 않게 함
        val docId = "${deviceId}_$role"
        FirebaseFirestore.getInstance().collection("devices").document(docId)
            .set(deviceData, SetOptions.merge())
            .addOnSuccessListener { Log.d("MainActivity", "Device Registered: $role / $elderCode") }
            .addOnFailureListener { e -> Log.e("MainActivity", "Registration Failed", e) }
    }

    private fun setupElderlyTestButton() {
        binding.btnTestPhishing.setOnClickListener {
            // 시뮬레이션용 데이터셋에 실시간 탐지 대상 URL 추가
            val extendedPhishingDataset = phishingDataset + listOf(
                "010-0000-0000" to "[경찰청] 미납 과태료 확인 바랍니다. http://phishing-test.kr"
            )

            val isPhishingChance = Random.nextInt(100) < 40
            val selected = if (isPhishingChance) extendedPhishingDataset.random() else safeDataset.random()
            
            val sms = SmsMessage(sender = selected.first, body = selected.second)
            
            scope.launch {
                val result = processor.processAsync(sms)

                if (result.isPhishing) {
                    val intent = Intent(this@MainActivity, AlertActivity::class.java).apply {
                        putExtra("sender", sms.sender)
                        putExtra("body", sms.body)
                        putExtra("reason", result.reason)
                        putExtra("guidance", result.guidance)
                        putStringArrayListExtra("matched", ArrayList(result.matched))
                    }
                    startActivity(intent)
                } else {
                    Toast.makeText(this@MainActivity, "✅ 안전한 메시지 수신: ${sms.sender}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private val phishingDataset = listOf(
        "010-1234-5678" to "[국제발신] 해외결제 980,000원 승인완료. 본인 아닐 시 즉시 확인: http://bit.ly/fake-link",
        "02-1588-0000" to "[검찰청] 귀하의 계좌가 범죄에 연루되었습니다. 확인: http://192.168.1.100/check",
        "1544-1234" to "국세청 환급금 안내. 아래 링크를 통해 신청하세요. http://goo.gl/tax-return",
        "010-9999-8888" to "우체국 본인확인 부탁드립니다. 주소지 불명으로 반송 예정: http://t.co/post-check",
        "02-114-114" to "[카드발급] 카드 발급이 완료되었습니다. 본인 요청 아니면 신고: http://tinyurl.com/card-info"
    )

    private val safeDataset = listOf(
        "1577-7011" to "[쿠팡] 로켓배송 주문하신 상품이 도착했습니다.",
        "010-1111-2222" to "오늘 저녁 7시에 동창회 있는 거 잊지 마세요!",
        "1588-5000" to "[KB국민은행] 06/11 14:20 15,200원 결제 승인됨.",
        "네이버" to "[네이버] 새로운 환경에서 로그인이 감지되었습니다.",
        "010-3333-4444" to "인증번호 [123456]를 입력해 주세요."
    )
}
