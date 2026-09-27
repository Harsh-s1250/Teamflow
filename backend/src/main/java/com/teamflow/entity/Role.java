package com.teamflow.entity;

/** Application-wide user roles. Authorization decisions are always made
 * server-side against this role, never against client-supplied claims. */
public enum Role {
    PROJECT_MANAGER,
    TEAM_MEMBER
}
