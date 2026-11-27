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

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
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

test("Unauthenticated user: cannot read or write numberNotes or callerTags", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/numberNotes`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/callerTags`).get());
  await assertFails(
    unauthDb.collection(`users/${ALICE_UID}/numberNotes`).doc("note_1").set({
      userId: ALICE_UID,
      phoneNumber: "+1 555-0199",
      normalizedNumber: "15550199",
      title: "Unauth Note",
      body: "Blocked",
      tags: ["VIP"],
      pinned: true,
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Authenticated owner: valid NumberNote create, list, partial update, and delete succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 5000);

  // List empty subcollection succeeds
  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/numberNotes`).get());

  // Create matching Kotlin NumberNote payload (no @DocumentId `id` field)
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/numberNotes`).doc("note_1").set({
      userId: ALICE_UID,
      phoneNumber: "+91 98400 12345",
      normalizedNumber: "919840012345",
      contactName: "Arjun",
      title: "Delivery gate code",
      body: "Call at main gate #402",
      tags: ["Delivery", "VIP"],
      pinned: true,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Partial update preserving createdAt and userId
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/numberNotes`).doc("note_1").update({
      title: "Updated gate code",
      body: "Gate code #408",
      pinned: false,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject ghost `id` field or future timestamp or immutable createdAt mutation
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/numberNotes`).doc("note_2").set({
      id: "note_2",
      userId: ALICE_UID,
      phoneNumber: "+1 555-0100",
      normalizedNumber: "15550100",
      title: "Bad ID",
      body: "",
      tags: [],
      pinned: false,
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/numberNotes`).doc("note_1").update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/numberNotes`).doc("note_3").set({
      userId: ALICE_UID,
      phoneNumber: "+1 555-0100",
      normalizedNumber: "15550100",
      title: "Future time",
      body: "",
      tags: [],
      pinned: false,
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/numberNotes`).doc("note_1").delete());
});

test("Authenticated owner: valid CallerTag create, update, and enum enforcement", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/callerTags`).doc("tag_1").set({
      userId: ALICE_UID,
      phoneNumber: "+1 800-555-0199",
      normalizedNumber: "18005550199",
      displayName: "Express Courier",
      category: "Delivery",
      blocked: false,
      aiSummary: "Toll-free logistics line",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/callerTags`).doc("tag_1").update({
      blocked: true,
      category: "Spam",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Invalid category rejected
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/callerTags`).doc("tag_2").set({
      userId: ALICE_UID,
      phoneNumber: "+1 800-555-0198",
      normalizedNumber: "18005550198",
      displayName: "Invalid Cat",
      category: "INVALID_CAT",
      blocked: false,
      createdAt: created,
      updatedAt: created,
    })
  );
});

test("Cross-user isolation: Alice cannot read or write Bob's notes or caller tags", async () => {
  const created = new Date(Date.now() - 5000);
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection(`users/${BOB_UID}/numberNotes`).doc("bob_note").set({
      userId: BOB_UID,
      phoneNumber: "+1 555-0200",
      normalizedNumber: "15550200",
      title: "Bob Private",
      body: "Secret",
      tags: ["Work"],
      pinned: true,
      createdAt: created,
      updatedAt: created,
    });
    await db.collection(`users/${BOB_UID}/callerTags`).doc("bob_tag").set({
      userId: BOB_UID,
      phoneNumber: "+1 555-0200",
      normalizedNumber: "15550200",
      displayName: "Bob Boss",
      category: "Work",
      blocked: false,
      createdAt: created,
      updatedAt: created,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection(`users/${BOB_UID}/numberNotes`).get());
  await assertFails(aliceDb.collection(`users/${BOB_UID}/numberNotes`).doc("bob_note").get());
  await assertFails(
    aliceDb.collection(`users/${BOB_UID}/numberNotes`).doc("bob_note").update({ title: "Hijacked" })
  );
  await assertFails(aliceDb.collection(`users/${BOB_UID}/callerTags`).doc("bob_tag").get());
  await assertFails(
    aliceDb.collection(`users/${BOB_UID}/callerTags`).doc("alice_spoof").set({
      userId: ALICE_UID,
      phoneNumber: "+1 555-0999",
      normalizedNumber: "15550999",
      displayName: "Spoof",
      category: "Other",
      blocked: false,
      createdAt: created,
      updatedAt: created,
    })
  );
});
