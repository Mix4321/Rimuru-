const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function validProfilePayload(uid, now) {
  return {
    userId: uid,
    displayName: "นักกีฬา MOVE",
    email: "alice@example.com",
    dailyGoalMinutes: 0,
    weightKg: 65.5,
    heightCm: 170.0,
    currentStreak: 0,
    longestStreak: 0,
    lastActiveDate: "",
    onboardingCompleted: true,
    createdAt: now,
    updatedAt: now,
  };
}

function validWorkoutPayload(uid, now) {
  return {
    userId: uid,
    sportType: "RUNNING",
    durationSeconds: 300,
    caloriesBurned: 55,
    steps: 420,
    distanceMeters: 480,
    repsCount: 0,
    trackingMode: "SENSOR_GPS",
    motionActiveSeconds: 290,
    activityDate: "2026-11-17",
    startedAtMillis: 1763350000000,
    endedAtMillis: 1763350300000,
    avgFormScore: 0,
    avgSymmetryPercent: 100,
    routeJson: "[]",
    workoutSubType: "FREE",
    createdAt: now,
    updatedAt: now,
  };
}

test("Unauthenticated user cannot read or write profiles or workouts", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const past = new Date(Date.now() - 2000);

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID, past)));
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("workouts").get());
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("workouts")
      .doc("workout_1")
      .set(validWorkoutPayload(ALICE_UID, past))
  );
});

test("Authenticated owner can create and update own profile, but cannot mutate createdAt or add ghost fields", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID, created))
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      displayName: "โค้ชมูฟ",
      weightKg: 68.0,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject future timestamp
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      displayName: "Future",
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  // Reject immutable createdAt mutation
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject shadow/ghost field injection
  await assertFails(
    aliceDb.collection("users").doc("alice_shadow").set({
      ...validProfilePayload(ALICE_UID, created),
      isAdmin: true,
    })
  );
});

test("Authenticated user cannot read, list, or write another user's profile or workouts", async () => {
  const created = new Date(Date.now() - 10000);
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection("users").doc(BOB_UID).set(validProfilePayload(BOB_UID, created));
    await db
      .collection("users")
      .doc(BOB_UID)
      .collection("workouts")
      .doc("bob_w1")
      .set(validWorkoutPayload(BOB_UID, created));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).update({ displayName: "Hacked", updatedAt: new Date(Date.now() - 500) }));
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("workouts").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("workouts").doc("bob_w1").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("workouts").doc("bob_w1").delete());
});

test("Authenticated owner can create, list, update, and delete own workouts with schema validation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  // Empty subcollection list must succeed (no existing() inspection on list)
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("workouts").get());

  // Valid workout creation (NO 'id' field in payload so @DocumentId works cleanly)
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("workouts")
      .doc("w-101")
      .set(validWorkoutPayload(ALICE_UID, created))
  );

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("workouts").get());

  // Reject redundant 'id' field in create payload
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("workouts")
      .doc("w-102")
      .set({
        id: "w-102",
        ...validWorkoutPayload(ALICE_UID, created),
      })
  );

  // Reject invalid sportType enum
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("workouts")
      .doc("w-103")
      .set({
        ...validWorkoutPayload(ALICE_UID, created),
        sportType: "FLYING",
      })
  );

  // Reject durationSeconds below MIN_WORKOUT_SECONDS (5)
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("workouts")
      .doc("w-104")
      .set({
        ...validWorkoutPayload(ALICE_UID, created),
        durationSeconds: 2,
      })
  );

  // Allow owner delete
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("workouts").doc("w-101").delete()
  );
});
