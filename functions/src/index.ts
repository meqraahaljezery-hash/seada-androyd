import * as functions from "firebase-functions";
import * as admin from "firebase-admin";

admin.initializeApp();
const db = admin.firestore();

export const approvePaymentRequest = functions.https.onCall(async (data, context) => {
  if (!context.auth || (!context.auth.token.admin && context.auth.token.role !== 'admin' && context.auth.token.role !== 'super_admin')) {
    throw new functions.https.HttpsError('permission-denied', 'Only administrators can approve payment requests.');
  }

  const requestId = data.requestId;
  if (!requestId) {
    throw new functions.https.HttpsError('invalid-argument', 'requestId is required.');
  }

  const requestRef = db.collection('paymentRequests').doc(requestId);
  
  return db.runTransaction(async (transaction) => {
    const requestDoc = await transaction.get(requestRef);
    if (!requestDoc.exists) {
      throw new functions.https.HttpsError('not-found', 'Payment request not found.');
    }

    const reqData = requestDoc.data()!;
    if (reqData.status === 'approved') {
      throw new functions.https.HttpsError('already-exists', 'Payment request already approved.');
    }

    const uid = reqData.uid;
    const packageId = reqData.packageId;

    const packageDoc = await transaction.get(db.collection('packages').doc(packageId));
    const minutesToAdd = packageDoc.exists ? (packageDoc.data()!.minutes || 0) : 100;

    const userRef = db.collection('users').doc(uid);
    const userDoc = await transaction.get(userRef);
    const currentBalance = userDoc.exists ? (userDoc.data()!.minuteBalance || 0) : 0;
    const newBalance = currentBalance + minutesToAdd;

    transaction.update(userRef, {
      minuteBalance: newBalance,
      updatedAt: admin.firestore.FieldValue.serverTimestamp()
    });

    transaction.update(requestRef, {
      status: 'approved',
      reviewer: context.auth.uid,
      reviewedAt: admin.firestore.FieldValue.serverTimestamp()
    });

    const ledgerRef = db.collection('creditLedger').doc();
    transaction.set(ledgerRef, {
      id: ledgerRef.id,
      uid: uid,
      type: 'top_up',
      amount: minutesToAdd,
      balanceBefore: currentBalance,
      balanceAfter: newBalance,
      reason: `Approved payment request ${requestId}`,
      actorUid: context.auth.uid,
      createdAt: admin.firestore.FieldValue.serverTimestamp()
    });

    const auditRef = db.collection('auditLogs').doc();
    transaction.set(auditRef, {
      id: auditRef.id,
      actorUid: context.auth.uid,
      action: 'APPROVE_PAYMENT',
      targetType: 'paymentRequests',
      targetId: requestId,
      reason: `Added ${minutesToAdd} minutes to user ${uid}`,
      timestamp: admin.firestore.FieldValue.serverTimestamp()
    });

    return { success: true, newBalance };
  });
});

export const setUserRole = functions.https.onCall(async (data, context) => {
  if (!context.auth || context.auth.token.role !== 'super_admin') {
    throw new functions.https.HttpsError('permission-denied', 'Only Super Admin can assign roles.');
  }

  const targetUid = data.uid;
  const newRole = data.role;

  if (!targetUid || !['student', 'teacher', 'supervisor', 'admin'].includes(newRole)) {
    throw new functions.https.HttpsError('invalid-argument', 'Invalid targetUid or role.');
  }

  await admin.auth().setCustomUserClaims(targetUid, { role: newRole, admin: newRole === 'admin' });
  await db.collection('users').doc(targetUid).update({
    role: newRole,
    updatedAt: admin.firestore.FieldValue.serverTimestamp()
  });

  return { success: true };
});
