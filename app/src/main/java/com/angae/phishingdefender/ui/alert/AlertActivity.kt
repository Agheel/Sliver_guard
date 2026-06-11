package com.angae.phishingdefender.ui.alert

import android.app.AlertDialog
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.angae.phishingdefender.databinding.ActivityAlertBinding
import com.example.sliver_guard.domain.notifier.GuardianNotifier
import com.example.sliver_guard.data.notifier.FirestoreGuardianNotifier
import com.example.sliver_guard.domain.model.SmsMessage
import com.google.firebase.firestore.FirebaseFirestore
import android.provider.Settings
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * [SRP] 피싱 위험을 어르신에게 경고하고, 사용자의 최종 선택에 따라 후속 조치를 실행하는 화면.
 */
class AlertActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlertBinding

    // [DIP] 인터페이스에 의존하며, 구체적인 구현체(Firestore)를 주입받아 사용함.
    private val guardianNotifier: GuardianNotifier by lazy { 
        FirestoreGuardianNotifier(FirebaseFirestore.getInstance()) 
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAlertBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // [SRP] 잠금화면 위에서도 뜨도록 설정 (API 27 O_MR1 이상 대응)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        setupDisplay()
        setupListeners()
    }

    private fun setupDisplay() {
        val sender = intent.getStringExtra("sender") ?: "알 수 없는 번호"
        val body = intent.getStringExtra("body") ?: ""
        val reason = intent.getStringExtra("reason") ?: "의심스러운 패턴 발견"
        val matched = intent.getStringArrayListExtra("matched") ?: arrayListOf<String>()

        binding.tvSenderInfo.text = getString(com.angae.phishingdefender.R.string.label_sender, sender)
        binding.tvAlertReason.text = getString(com.angae.phishingdefender.R.string.label_reason, reason)
        binding.tvMessageContent.text = getString(com.angae.phishingdefender.R.string.label_content, body)
        
        if (matched.isNotEmpty()) {
            binding.tvAlertReason.append("\n(위험 단어: ${matched.joinToString(", ")})")
        }
    }

    private fun setupListeners() {
        // 가장 안전한 동작(닫기)
        binding.btnSafeClose.setOnClickListener {
            finish()
        }

        // 위험한 동작(열기) 시 시니어 맞춤형 되묻기 다이얼로그
        binding.btnOpen.setOnClickListener {
            showDoubleCheckDialog()
        }
    }

    private fun showDoubleCheckDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(com.angae.phishingdefender.R.string.confirm_title))
            .setMessage(getString(com.angae.phishingdefender.R.string.confirm_msg))
            .setPositiveButton(getString(com.angae.phishingdefender.R.string.confirm_yes)) { _, _ ->
                handleFinalOpen()
            }
            .setNegativeButton(getString(com.angae.phishingdefender.R.string.confirm_no)) { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    private fun handleFinalOpen() {
        val sender = intent.getStringExtra("sender") ?: "Unknown"
        val body = intent.getStringExtra("body") ?: ""
        val reason = intent.getStringExtra("reason") ?: "Unknown"
        val elderId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

        // [SRP] 알림 발송은 비동기로 처리하여 UI 흐름을 방해하지 않음
        lifecycleScope.launch {
            // [DIP] 인터페이스를 통해 알림 발송 호출 (세부 저장 로직은 몰라도 됨)
            val smsMessage = SmsMessage(sender, body)
            guardianNotifier.notifyGuardian(smsMessage, reason, elderId)
        }
        
        finish()
    }
}
