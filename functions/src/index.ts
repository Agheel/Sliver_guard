import {onDocumentCreated} from "firebase-functions/v2/firestore";
import * as admin from "firebase-admin";

admin.initializeApp();

/**
 * [SRP] Firestore에 피싱 알림(Alert)이 생성되면 해당 어르신의 보호자들에게 FCM 푸시를 보내는 중계 함수.
 */
export const onPhishingAlertCreated = onDocumentCreated("alerts/{alertId}", async (event) => {
    const snapshot = event.data;
    if (!snapshot) {
        console.log("No data found in the event.");
        return;
    }

    const alertData = snapshot.data();
    const { elderId, sender, body, reason } = alertData;

    try {
        // 1. 어르신-보호자 연결 정보 조회 (links/{elderId})
        const linkDoc = await admin.firestore().collection("links").doc(elderId).get();
        if (!linkDoc.exists) {
            console.log(`No guardian link found for elder: ${elderId}`);
            return;
        }

        const guardianIds: string[] = linkDoc.data()?.guardianIds || [];
        if (guardianIds.length === 0) {
            console.log(`Guardian list is empty for elder: ${elderId}`);
            return;
        }

        // 2. 보호자들의 기기 토큰 수집 (devices/{guardianId})
        const tokens: string[] = [];
        for (const guardianId of guardianIds) {
            const deviceDoc = await admin.firestore().collection("devices").doc(guardianId).get();
            const token = deviceDoc.data()?.fcmToken;
            if (token) {
                tokens.push(token);
            }
        }

        if (tokens.length === 0) {
            console.log("No valid FCM tokens found for guardians.");
            return;
        }

        // 3. FCM 메시지 구성 및 전송
        const message: admin.messaging.MulticastMessage = {
            tokens: tokens,
            notification: {
                title: "⚠️ 어르신이 피싱 문자를 받았어요!",
                body: `${reason} (${sender})`,
            },
            data: {
                sender: sender,
                body: body,
                reason: reason,
                type: "PHISHING_ALERT"
            },
            android: {
                priority: "high",
                notification: {
                    channelId: "phishing_alert_channel",
                    priority: "high",
                    sound: "default",
                }
            }
        };

        const response = await admin.messaging().sendEachForMulticast(message);

        // 4. 전송 결과 확인 및 실패 토큰 로그 처리
        if (response.failureCount > 0) {
            const failedTokens: string[] = [];
            response.responses.forEach((resp, idx) => {
                if (!resp.success) {
                    failedTokens.push(tokens[idx]);
                    console.error(`FCM Delivery Failed for token [${tokens[idx]}]:`, resp.error);
                }
            });
            console.log(`Failed tokens summary: ${failedTokens.join(", ")}`);
        }

        console.log(`Successfully sent alert to ${response.successCount} guardians.`);

    } catch (error) {
        // [Safety] 함수 실행 중 에러가 발생해도 로깅 후 종료하여 비정상적인 재시도 방지
        console.error("Error in onPhishingAlertCreated function:", error);
    }
});
