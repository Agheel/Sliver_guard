package com.angae.phishingdefender.ui.main

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.angae.phishingdefender.databinding.ActivityMainBinding
import com.angae.phishingdefender.ui.alert.AlertActivity
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging

/**
 * [SRP] 앱의 메인 진입점으로서 권한 관리, 역할 선택 및 시연용 데이터 주입을 담당함.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, "알림 권한이 허용되었습니다.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "알림 권한이 거부되었습니다.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRoleSelection()
        checkNotificationPermission()
        syncFcmToken()
        setupTestButton()
    }

    /**
     * [SRP] 사용자의 역할을 선택하고 SharedPreferences에 저장함.
     */
    private fun setupRoleSelection() {
        val prefs = getSharedPreferences("prefs", Context.MODE_PRIVATE)
        val currentRole = prefs.getString("role", "elderly") ?: "elderly"

        if (currentRole == "elderly") {
            binding.rbElderly.isChecked = true
        } else {
            binding.rbGuardian.isChecked = true
        }

        binding.rgRole.setOnCheckedChangeListener { _, checkedId ->
            val role = if (checkedId == binding.rbElderly.id) "elderly" else "guardian"
            prefs.edit().putString("role", role).apply()
            
            // [SRP] 역할 변경 시 Firestore의 기기 정보도 즉시 동기화
            syncFcmToken()
            Toast.makeText(this, "설정이 저장되었습니다: $role", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    /**
     * [SRP] 현재 기기의 FCM 토큰을 가져와 Firestore에 업데이트함.
     */
    private fun syncFcmToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result ?: return@addOnCompleteListener
                updateTokenOnFirestore(token)
            }
        }
    }

    private fun updateTokenOnFirestore(token: String) {
        // [SRP] 기기 고유 ID를 문서 ID로 사용
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        val role = getSharedPreferences("prefs", Context.MODE_PRIVATE).getString("role", "elderly") ?: "elderly"

        val deviceData = hashMapOf(
            "fcmToken" to token,
            "role" to role,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        // [SRP] Firestore의 devices 컬렉션에 토큰 정보 업데이트 (Upsert)
        FirebaseFirestore.getInstance().collection("devices").document(deviceId)
            .set(deviceData, SetOptions.merge())
            .addOnSuccessListener {
                Log.d("MainActivity", "FCM 토큰 서버 등록 성공: $token")
            }
            .addOnFailureListener { e ->
                // [Safety] 실패해도 앱이 죽지 않도록 예외 처리
                Log.e("MainActivity", "FCM 토큰 서버 등록 실패", e)
            }
    }

    private fun setupTestButton() {
        binding.btnTestPhishing.setOnClickListener {
            val intent = Intent(this, AlertActivity::class.java).apply {
                putExtra("sender", "010-1234-5678")
                putExtra("body", "[국제발신] 해외결제 980,000원 승인완료. 본인 아닐 시 즉시 확인: http://bit.ly/fake-link")
                putExtra("reason", "피싱 의심 단어 및 단축 URL 포함")
                putStringArrayListExtra("matched", arrayListOf("국제발신", "http://bit.ly/fake-link"))
            }
            startActivity(intent)
        }
    }
}
