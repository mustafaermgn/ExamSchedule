import { initializeApp } from "firebase-admin/app";
import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import { onDocumentWritten } from "firebase-functions/v2/firestore";

initializeApp();

type Assignment = {
  roomId?: string;
  proctorId?: string;
};

type Exam = {
  courseId?: string;
  date?: number;
  slotId?: number;
  assignments?: Assignment[];
};

export const notifyProctorsOnExamWrite = onDocumentWritten("Exams/{examId}", async (event) => {
  const after = event.data?.after;
  if (!after?.exists) return;

  const exam = after.data() as Exam;
  const proctorIds = Array.from(new Set((exam.assignments ?? []).map((item) => item.proctorId).filter(Boolean))) as string[];
  if (proctorIds.length === 0) return;

  const db = getFirestore();
  const userDocs = await Promise.all(proctorIds.map((id) => db.collection("Users").doc(id).get()));
  const tokens = userDocs
    .map((doc) => doc.get("fcmToken") as string | undefined)
    .filter((token): token is string => Boolean(token));

  if (tokens.length > 0) {
    await getMessaging().sendEachForMulticast({
      tokens,
      notification: {
        title: "Sınav görevi güncellendi",
        body: `Oturum ${exam.slotId ?? "-"} için görevlendirme değişti.`
      },
      data: {
        examId: event.params.examId,
        courseId: exam.courseId ?? "",
        date: String(exam.date ?? "")
      }
    });
  }

  await db.collection("Logs").add({
    action: "exam_write",
    oldValue: event.data?.before.exists ? JSON.stringify(event.data.before.data()) : "",
    newValue: JSON.stringify(exam),
    editorId: "cloud-function",
    timestamp: FieldValue.serverTimestamp()
  });
});
