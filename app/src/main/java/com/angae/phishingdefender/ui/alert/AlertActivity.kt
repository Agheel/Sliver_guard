package com.angae.phishingdefender.ui.alert

import android.app.AlertDialog
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.angae.phishingdefender.databinding.ActivityAlertBinding
import com.angae.phishingdefender.domain.notifier.GuardianNotifier
import com.angae.phishingdefender.data.notifier.FirestoreGuardianNotifier
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
        FirestoreGuardianNotifier(this) 
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
        notifyGuardianImmediately()
    }

    private fun notifyGuardianImmediately() {
        val sender = intent.getStringExtra("sender") ?: "Unknown"
        val body = intent.getStringExtra("body") ?: ""
        val reason = intent.getStringExtra("reason") ?: "Unknown"

        // [SRP] 알림 발송은 비동기로 처리하여 UI 흐름을 방해하지 않음
        // 감시 중인 보호자에게 즉시 Firestore를 통해 알림을 보냄
        lifecycleScope.launch {
            guardianNotifier.notifyGuardian(sender, body, reason)
        }
    }

    private fun setupDisplay() {
        val sender = intent.getStringExtra("sender") ?: "알 수 없는 번호"
        val body = intent.getStringExtra("body") ?: ""
        val reason = intent.getStringExtra("reason") ?: "의심스러운 패턴 발견"
        val guidance = intent.getStringExtra("guidance") ?: "링크를 클릭하지 마시고 삭제하세요."
        val matched = intent.getStringArrayListExtra("matched") ?: arrayListOf<String>()

        binding.tvSenderInfo.text = getString(com.angae.phishingdefender.R.string.label_sender, sender)
        binding.tvAlertReason.text = reason // "의심 사유"를 더 명확히 표시
        binding.tvMessageContent.text = getString(com.angae.phishingdefender.R.string.label_content, body)
        binding.tvGuidance.text = guidance
        
        if (matched.isNotEmpty() && matched[0].contains("사칭")) {
            binding.tvAlertReason.text = "⚠️ $reason"
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

        lifecycleScope.launch {
            // "위험 무시하고 열기 클릭"임을 명시하여 로그를 남김
            guardianNotifier.notifyGuardian(sender, body, "$reason (사용자가 위험 무시하고 열기 클릭함)")
        }
        
        finish()
    }
}
