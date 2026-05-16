import { initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import { onDocumentCreated, onDocumentWritten } from "firebase-functions/v2/firestore";
import { onRequest } from "firebase-functions/v2/https";

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

type DateRange = {
  start?: number;
  end?: number;
  isApproved?: boolean;
  isRejected?: boolean;
  note?: string;
};

type User = {
  uid?: string;
  name?: string;
  email?: string;
  password?: string;
  role?: string;
  fcmToken?: string;
  preferences?: Record<string, boolean>;
  excuses?: DateRange[];
};

type TokenRegistrationRequest = {
  email?: string;
  password?: string;
  role?: string;
  token?: string;
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
    .filter((doc) => {
      const prefs = doc.get("preferences") as Record<string, boolean> | undefined;
      return prefs?.notifications !== false; // Default to true if not set
    })
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

export const notifyOnExcuseWrite = onDocumentWritten("Users/{userId}", async (event) => {
  const after = event.data?.after;
  if (!after?.exists) return;

  const beforeUser = event.data?.before.exists ? event.data.before.data() as User : undefined;
  const afterUser = after.data() as User;
  const beforeExcuses = beforeUser?.excuses ?? [];
  const afterExcuses = afterUser.excuses ?? [];

  const newExcuses = afterExcuses.filter((excuse) => !findSameExcuse(beforeExcuses, excuse));
  const approvedExcuses = afterExcuses.filter((excuse) => {
    if (excuse.isApproved !== true) return false;
    const beforeExcuse = findSameExcuse(beforeExcuses, excuse);
    return beforeExcuse !== undefined && beforeExcuse.isApproved !== true;
  });

  if (newExcuses.length === 0 && approvedExcuses.length === 0) return;

  const db = getFirestore();
  const adminsSnapshot = await db.collection("Users").where("role", "==", "ADMIN").get();
  const adminUsers = adminsSnapshot.docs.map((doc) => doc.data() as User);
  const proctorName = afterUser.name?.trim() || "Gözetmen";

  for (const excuse of newExcuses) {
    const data = excuseNotificationData("excuse_requested", event.params.userId, excuse);
    await sendNotificationToUsers(adminUsers, {
      title: "Yeni izin talebi",
      body: `${proctorName} izin talebi oluşturdu${formatExcuseSuffix(excuse)}.`,
      data
    });

    await sendNotificationToUsers([afterUser], {
      title: "İzin talebiniz alındı",
      body: `Talebiniz yönetici onayına gönderildi${formatExcuseSuffix(excuse)}.`,
      data
    });
  }

  for (const excuse of approvedExcuses) {
    const data = excuseNotificationData("excuse_approved", event.params.userId, excuse);
    await sendNotificationToUsers(adminUsers, {
      title: "İzin talebi onaylandı",
      body: `${proctorName} için izin talebi onaylandı${formatExcuseSuffix(excuse)}.`,
      data
    });

    await sendNotificationToUsers([afterUser], {
      title: "İzin talebiniz onaylandı",
      body: `İzin talebiniz onaylandı${formatExcuseSuffix(excuse)}.`,
      data
    });
  }

  await db.collection("Logs").add({
    action: "excuse_notification",
    oldValue: event.data?.before.exists ? JSON.stringify(beforeUser) : "",
    newValue: JSON.stringify({ userId: event.params.userId, newExcuses, approvedExcuses }),
    editorId: "cloud-function",
    timestamp: FieldValue.serverTimestamp()
  });
});

export const registerFcmToken = onRequest(async (request, response) => {
  if (request.method !== "POST") {
    response.status(405).json({ ok: false, error: "Method not allowed." });
    return;
  }

  const body = request.body as TokenRegistrationRequest;
  const email = body.email?.trim().toLowerCase() ?? "";
  const password = body.password ?? "";
  const role = body.role ?? "";
  const token = body.token?.trim() ?? "";

  if (!email || !password || !role || !token) {
    response.status(400).json({ ok: false, error: "Missing registration fields." });
    return;
  }

  const db = getFirestore();
  const usersSnapshot = await db.collection("Users").get();
  const matchedDoc = usersSnapshot.docs.find((doc) => {
    const user = doc.data() as User;
    return user.email?.trim().toLowerCase() === email &&
      user.password === password &&
      user.role === role;
  });

  if (!matchedDoc) {
    response.status(403).json({ ok: false, error: "Invalid user credentials." });
    return;
  }

  await matchedDoc.ref.set({ fcmToken: token }, { merge: true });
  console.log("FCM token registered via HTTPS", {
    userId: matchedDoc.id,
    role
  });

  response.status(200).json({ ok: true, userId: matchedDoc.id });
});

export const loginLocalUser = onRequest(async (request, response) => {
  if (request.method !== "POST") {
    response.status(405).json({ ok: false, error: "Method not allowed." });
    return;
  }

  const body = request.body as TokenRegistrationRequest;
  const email = body.email?.trim().toLowerCase() ?? "";
  const password = body.password ?? "";
  const role = body.role ?? "";

  if (!email || !password || !role) {
    response.status(400).json({ ok: false, error: "Missing login fields." });
    return;
  }

  const db = getFirestore();
  const usersSnapshot = await db.collection("Users").get();
  const matchedDoc = usersSnapshot.docs.find((doc) => {
    const user = doc.data() as User;
    return user.email?.trim().toLowerCase() === email &&
      user.password === password &&
      user.role === role;
  });

  if (!matchedDoc) {
    response.status(403).json({ ok: false, error: "Invalid user credentials." });
    return;
  }

  const user = matchedDoc.data() as User;
  const userId = user.uid || matchedDoc.id;
  const customToken = await getAuth().createCustomToken(userId, { role: user.role ?? role });

  response.status(200).json({
    ok: true,
    userId,
    role: user.role,
    customToken
  });
});

export const resetFirestoreFromAdminCommand = onDocumentCreated("AdminCommands/{commandId}", async (event) => {
  const snapshot = event.data;
  if (!snapshot?.exists) return;

  const data = snapshot.data() as {
    type?: string;
    requestedBy?: string;
  };

  if (data.type !== "RESET_DATABASE") return;

  const db = getFirestore();
  const commandRef = snapshot.ref;

  try {
    const requesterId = data.requestedBy ?? "";
    const requesterDoc = requesterId ? await db.collection("Users").doc(requesterId).get() : null;
    const requesterRole = requesterDoc?.get("role");

    if (requesterRole !== "ADMIN") {
      await commandRef.set({
        status: "failed",
        error: "Only admin can reset database.",
        processedAt: FieldValue.serverTimestamp()
      }, { merge: true });
      return;
    }

    const keepCollections = new Set(["AdminCommands"]);
    const rootCollections = await db.listCollections();
    for (const collection of rootCollections) {
      if (keepCollections.has(collection.id)) continue;
      await db.recursiveDelete(collection);
    }

    await db.collection("Users").doc("admin-root").set({
      uid: "admin-root",
      name: "Sistem Yöneticisi",
      email: "admin@fakulte.edu.tr",
      role: "ADMIN",
      password: "123456",
      deptId: "BIL",
      excuses: [],
      profileImageUrl: "",
      phone: "",
      preferences: {}
    });

    await commandRef.set({
      status: "completed",
      processedAt: FieldValue.serverTimestamp()
    }, { merge: true });
  } catch (error) {
    await commandRef.set({
      status: "failed",
      error: error instanceof Error ? error.message : String(error),
      processedAt: FieldValue.serverTimestamp()
    }, { merge: true });
  }
});

function findSameExcuse(excuses: DateRange[], target: DateRange): DateRange | undefined {
  return excuses.find((excuse) =>
    excuse.start === target.start &&
    excuse.end === target.end &&
    (excuse.note ?? "") === (target.note ?? "")
  );
}

function formatExcuseSuffix(excuse: DateRange): string {
  if (excuse.start === undefined) return "";
  return ` (${new Date(excuse.start).toLocaleDateString("tr-TR")})`;
}

function excuseNotificationData(type: string, userId: string, excuse: DateRange): Record<string, string> {
  return {
    type,
    userId,
    start: String(excuse.start ?? ""),
    end: String(excuse.end ?? "")
  };
}

async function sendNotificationToUsers(
  users: User[],
  message: {
    title: string;
    body: string;
    data: Record<string, string>;
  }
): Promise<void> {
  const tokens = Array.from(new Set(users
    .filter((user) => user.preferences?.notifications !== false)
    .map((user) => user.fcmToken)
    .filter((token): token is string => Boolean(token))));

  if (tokens.length === 0) {
    console.log("No FCM tokens found for notification", {
      title: message.title,
      userCount: users.length
    });
    return;
  }

  for (let index = 0; index < tokens.length; index += 500) {
    const tokenBatch = tokens.slice(index, index + 500);
    await getMessaging().sendEachForMulticast({
      tokens: tokenBatch,
      notification: {
        title: message.title,
        body: message.body
      },
      data: message.data
    }).then((response) => {
      console.log("FCM notification sent", {
        title: message.title,
        successCount: response.successCount,
        failureCount: response.failureCount
      });
    });
  }
}
