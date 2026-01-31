/**
 * MongoDB Schema Upgrade Script
 * Updates CollabPods collection to support roles, bans, and cooldowns
 * 
 * Execution Environment: MongoDB CLI, Compass, or MongoDB Atlas
 * Date: January 31, 2026
 */

// ============================================================================
// STEP 1: Update CollabPods Collection Schema
// ============================================================================
// Add new role-based fields to existing documents

db.collabPods.updateMany(
    {},
    [
        {
            $set: {
                // Convert creatorId to ownerId (immutable)
                ownerId: "$creatorId",

                // Convert moderatorIds to adminIds
                adminIds: {
                    $cond: [
                        { $eq: ["$moderatorIds", null] },
                        [],
                        "$moderatorIds"
                    ]
                },

                // Initialize empty bannedIds list
                bannedIds: []
            }
        }
    ]
);

// Create index on ownerId for quick lookups
db.collabPods.createIndex({ ownerId: 1 });

// Create index on adminIds for filtering
db.collabPods.createIndex({ adminIds: 1 });

// Create index on bannedIds for quick lookups
db.collabPods.createIndex({ bannedIds: 1 });

// Create compound index for role queries
db.collabPods.createIndex({ podId: 1, ownerId: 1, adminIds: 1, memberIds: 1 });

// ============================================================================
// STEP 2: Create PodCooldowns Collection with TTL Index
// ============================================================================
// Create collection with auto-delete after 15 minutes

// Create the PodCooldowns collection
db.createCollection("podCooldowns");

// Create TTL Index - MongoDB will auto-delete documents 15 minutes after expiryDate
// The second parameter '0' means the TTL index will check immediately when expiryDate is reached
db.podCooldowns.createIndex(
    { expiryDate: 1 },
    { expireAfterSeconds: 0 }
);

// Create index for quick lookup by userId and podId
db.podCooldowns.createIndex({ userId: 1, podId: 1 }, { unique: true });

// Create index for finding cooldowns by userId
db.podCooldowns.createIndex({ userId: 1 });

// Create index for finding cooldowns by podId
db.podCooldowns.createIndex({ podId: 1 });

// ============================================================================
// STEP 3: Update Messages Collection Schema
// ============================================================================
// Modify messageType to use enum instead of string

db.messages.updateMany(
    { messageType: { $exists: true } },
    [
        {
            $set: {
                // Store old messageType as messageTypeString for reference
                messageTypeString: "$messageType",

                // Set messageType to CHAT by default (existing messages are chat messages)
                messageType: "CHAT"
            }
        }
    ]
);

// For new documents without messageType, ensure CHAT is default
db.messages.updateMany(
    { messageType: { $exists: false } },
    { $set: { messageType: "CHAT" } }
);

// Create index for message type queries
db.messages.createIndex({ messageType: 1 });

// Create compound index for pod and message type queries
db.messages.createIndex({ podId: 1, messageType: 1, sentAt: -1 });

// ============================================================================
// STEP 4: Verify Schema Updates
// ============================================================================

// Check sample CollabPod document structure
db.collabPods.findOne();

// Check sample PodCooldown document
db.podCooldowns.findOne();

// Check sample Message document with new enum type
db.messages.findOne({ messageType: "CHAT" });

// Verify indexes were created
db.collabPods.getIndexes();
db.podCooldowns.getIndexes();
db.messages.getIndexes();

// ============================================================================
// STEP 5: Useful Queries for Testing
// ============================================================================

// Find all cooldowns for a user
// db.podCooldowns.find({ userId: "USER_ID" });

// Find if user is on cooldown for a pod
// db.podCooldowns.findOne({ userId: "USER_ID", podId: "POD_ID" });

// Find all pods owned by a user
// db.collabPods.find({ ownerId: "USER_ID" });

// Find all pods where user is admin
// db.collabPods.find({ adminIds: "USER_ID" });

// Find all pods where user is banned
// db.collabPods.find({ bannedIds: "USER_ID" });

// Find all SYSTEM messages in a pod
// db.messages.find({ podId: "POD_ID", messageType: "SYSTEM" });

// Delete a user from a pod (remove from memberIds and adminIds)
// db.collabPods.updateOne(
//     { _id: ObjectId("POD_ID") },
//     {
//         $pull: { memberIds: "USER_ID", adminIds: "USER_ID" },
//         $addToSet: { bannedIds: "USER_ID" }
//     }
// );

// ============================================================================
// NOTE: TTL Index Behavior
// ============================================================================
// The TTL index with expireAfterSeconds: 0 means MongoDB will delete documents
// at the exact time specified in the expiryDate field.
//
// Example: If a cooldown is created at 10:00 with expiryDate = 10:15,
// MongoDB will automatically delete the document at 10:15.
//
// MongoDB checks for expired documents every 60 seconds (default),
// so there may be a delay of up to 60 seconds before deletion.
// ============================================================================
