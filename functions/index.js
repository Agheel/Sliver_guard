const {onDocumentCreated} = require("firebase-functions/v2/firestore");
const admin = require("firebase-admin");
const {setGlobalOptions} = require("firebase-functions");

admin.initializeApp();

// 리전 설정 (데이터베이스와 동일한 서울 리전 추천)
setGlobalOptions({region: "asia-northeast3", maxInstances: 10});

/**
 * alerts 컬렉션에 새 문서가 생성되면 실행됨
 */
exports.onPhishingAlert = onDocumentCreated("alerts/{alertId}",
    async (event) => {
      const alertData = event.data.data();
      if (!alertData) return;

      const elderCode = alertData.elderCode;
      const sender = alertData.sender;
      const reason = alertData.reason || "피싱 의심";

      console.log(`알림 감지: elderCode=${elderCode}, sender=${sender}`);

      try {
        // 1. 해당 어르신(elderCode)과 연결된 보호자들 찾기
        const linkDoc = await admin.firestore()
            .collection("links").doc(elderCode).get();
        if (!linkDoc.exists) {
          console.log(`연결된 보호자 없음: ${elderCode}`);
          return;
        }

        const guardianIds = linkDoc.data().guardianIds;
        if (!guardianIds || guardianIds.length === 0) return;

        // 2. 보호자들의 FCM 토큰 가져오기
        const tokens = [];
        for (const guardianId of guardianIds) {
          const deviceDoc = await admin.firestore()
              .collection("devices").doc(guardianId).get();
          if (deviceDoc.exists && deviceDoc.data().fcmToken) {
            tokens.push(deviceDoc.data().fcmToken);
          }
        }

        if (tokens.length === 0) {
          console.log("전송할 FCM 토큰이 없음");
          return;
        }

        // 3. FCM 메시지 구성
        const message = {
          data: {
            title: "🚨 긴급! 어르신 피싱 위협 감지",
            message: `발신: ${sender}\n${reason}`,
            from_fcm: "true",
          },
          tokens: tokens,
        };

        // 4. 알림 전송
        const response = await admin.messaging().sendEachForMulticast(message);
        console.log(`${response.successCount}개의 기기에 알림 전송 성공`);
      } catch (error) {
        console.error("알림 전송 중 오류 발생:", error);
      }
    });
