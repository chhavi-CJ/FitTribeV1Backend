package com.fittribe.api.service;

import com.fittribe.api.entity.GroupMember;
import com.fittribe.api.entity.User;
import com.fittribe.api.repository.GroupMemberRepository;
import com.fittribe.api.repository.UserRepository;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class UserDeletionService {

    private static final Logger log = LoggerFactory.getLogger(UserDeletionService.class);

    private final UserRepository         userRepository;
    private final GroupMemberRepository  groupMemberRepository;
    private final GroupMembershipService groupMembershipService;

    public UserDeletionService(UserRepository userRepository,
                               GroupMemberRepository groupMemberRepository,
                               GroupMembershipService groupMembershipService) {
        this.userRepository         = userRepository;
        this.groupMemberRepository  = groupMemberRepository;
        this.groupMembershipService = groupMembershipService;
    }

    /**
     * Hard-deletes all Postgres state for the user in one transaction.
     * MUST commit before any Firebase deletion is attempted — the caller
     * invokes deleteFirebaseUser() only after this method returns.
     */
    @Transactional
    public void deletePostgresData(User user) {
        UUID userId = user.getId();
        try {
            // Exit every group first: removeMemberFromGroup handles admin
            // transfer, sole-member group deletion, and weekly-progress
            // aggregate adjustment in one place.
            List<GroupMember> memberships = groupMemberRepository.findByUserId(userId);
            for (GroupMember gm : memberships) {
                groupMembershipService.removeMemberFromGroup(gm.getGroupId(), userId);
            }

            // Hard delete. V69 FK cascades remove all remaining child rows
            // (workout_sessions, set_logs, pr_events, user_session_feedback,
            // saved_routines, reactions, notifications, coin_transactions,
            // device_tokens, ai_insights, user_plans, etc).
            userRepository.delete(user);
        } catch (Exception e) {
            log.error("Hard delete failed for user={}: {}", userId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Deletes the Firebase Auth user. Soft-fail: never throws. Called
     * AFTER deletePostgresData() has committed, never inside its tx, so
     * a Firebase outage cannot roll back (or be rolled back by) Postgres.
     */
    public void deleteFirebaseUser(UUID userId, String firebaseUid) {
        if (firebaseUid == null || firebaseUid.isBlank()) {
            log.info("Skipping Firebase delete for user={}: no firebase_uid", userId);
            return;
        }
        if (FirebaseApp.getApps().isEmpty()) {
            log.info("Skipping Firebase delete for user={}: Firebase not configured", userId);
            return;
        }
        try {
            FirebaseAuth.getInstance().deleteUser(firebaseUid);
            log.info("event=firebase_delete_ok user_id={} firebase_uid={}", userId, firebaseUid);
        } catch (Exception e) {
            log.error("event=firebase_delete_failed user_id={} firebase_uid={} reason={}",
                    userId, firebaseUid, e.getMessage(), e);
            // Do NOT re-throw: Postgres already committed; lingering
            // Firebase user is an ops cleanup task, not a 5xx to the user.
        }
    }
}
