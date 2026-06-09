package com.angae.phishingdefender.ui.alert

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.angae.phishingdefender.databinding.ActivityAlertBinding

/**
 * [SRP] 피싱 위협을 어르신들에게 시각적으로 강력하게 경고하는 화면.
 */
class AlertActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAlertBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // ViewBinding 사용 (findViewById 금지 원칙 준수)
        binding = ActivityAlertBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val sender = intent.getStringExtra("sender") ?: "알 수 없는 번호"
        val reason = intent.getStringExtra("reason") ?: "의심스러운 패턴 발견"
        
        binding.tvAlertReason.text = "사유: $reason"
        
        // 어르신들이 실수로 닫지 않도록 확인 버튼만 크게 배치
        binding.btnClose.setOnClickListener {
            finish()
        }
    }
}
