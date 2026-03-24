# Roles & Permissions — Frontend Integration Guide

This document covers everything the frontend needs to build the admin user-management screens, assign roles, grant/revoke individual permissions, and implement the **"Grant Full Access"** button.

---

## Table of Contents

1. [How It Works](#1-how-it-works)
2. [Predefined Roles](#2-predefined-roles)
3. [All Permission Codes](#3-all-permission-codes)
4. [Authentication](#4-authentication)
5. [API Reference](#5-api-reference)
6. [Integration Walkthrough](#6-integration-walkthrough)
7. [Grant Full Access Button](#7-grant-full-access-button)
8. [Permission Overrides (ALLOW / DENY)](#8-permission-overrides-allow--deny)
9. [UI Access Control (Hiding Elements)](#9-ui-access-control-hiding-elements)
10. [Error Handling](#10-error-handling)

---

## 1. How It Works

The system has **three layers** of access control:

```
User
 ├── UserType (ADMIN / CUSTOMER)            → grants ROLE_ADMIN or ROLE_CUSTOMER
 ├── Assigned Roles  (user_roles table)     → each role carries a set of permissions
 └── Direct Permission Overrides            → ALLOW adds a permission, DENY removes one
```

On every authenticated request the backend:
1. Reads the JWT `Bearer` token from the `Authorization` header.
2. Resolves the user and their assigned roles.
3. Collects all permission codes those roles carry.
4. Applies any direct user-level overrides (ALLOW/DENY).
5. Enforces `@PreAuthorize` checks on each endpoint.

The frontend only needs to **call the role/permission assignment APIs** and **read back the user's current roles** to render the admin UI.

---

## 2. Predefined Roles

These four roles are seeded automatically on every server startup. You do **not** need to create them manually.

| Role name | Purpose | Who uses it |
|---|---|---|
| `CUSTOMER_SUPPORT` | Moderate reviews and product Q&A | Support agents |
| `COURIER_ADMIN` | Manage courier accounts (no delete) | Logistics managers |
| `STORE_ADMIN` | Assign and manage products for a store | Store managers |
| `SUPERADMIN` | Full access to every module | Platform owners / head admins |

> A user can have **multiple roles** at the same time (e.g. `COURIER_ADMIN` + `STORE_ADMIN`).

### Permissions included per role

#### CUSTOMER_SUPPORT
```
review:read          review:moderate       review:reply:create
review:reply:update  review:reply:delete   review:delete
question:read        question:moderate     question:answer:create
question:answer:update  question:answer:toggle  question:answer:delete
question:delete
```

#### COURIER_ADMIN
```
courier:read   courier:create   courier:update
```
> `courier:delete` is intentionally excluded — only `ADMIN` / `SUPERADMIN` can permanently delete a courier.

#### STORE_ADMIN
```
store:read          store:update
store:product:read  store:product:assign  store:product:update  store:product:remove
store:admin:read
```

#### SUPERADMIN
All 27 permissions across every module.

---

## 3. All Permission Codes

These are the exact string values stored in the `permissions` table and used in `@PreAuthorize` expressions.

| Code | Module | What it grants |
|---|---|---|
| `review:read` | Reviews | View all reviews |
| `review:moderate` | Reviews | Approve / reject reviews |
| `review:reply:create` | Reviews | Post an admin reply |
| `review:reply:update` | Reviews | Edit an admin reply |
| `review:reply:delete` | Reviews | Delete an admin reply |
| `review:delete` | Reviews | Soft-delete a review |
| `question:read` | Questions | View all questions |
| `question:moderate` | Questions | Approve / reject questions |
| `question:answer:create` | Questions | Post an admin answer |
| `question:answer:update` | Questions | Edit an admin answer |
| `question:answer:toggle` | Questions | Toggle answer visibility |
| `question:answer:delete` | Questions | Delete an admin answer |
| `question:delete` | Questions | Soft-delete a question |
| `courier:read` | Courier | List and view couriers |
| `courier:create` | Courier | Register a new courier |
| `courier:update` | Courier | Update courier status |
| `courier:delete` | Courier | Permanently delete a courier |
| `store:read` | Store | View store details |
| `store:update` | Store | Edit store settings |
| `store:product:read` | Store | View products assigned to a store |
| `store:product:assign` | Store | Link a global product to a store |
| `store:product:update` | Store | Change price / stock / active flag |
| `store:product:remove` | Store | Unlink a product from a store |
| `store:admin:read` | Store | View store admin assignments |
| `store:admin:assign` | Store | Assign a user as store admin |
| `store:admin:update` | Store | Change a store admin's role |
| `store:admin:remove` | Store | Remove a store admin |

---

## 4. Authentication

Every request to a protected endpoint must carry the access token in the header:

```
Authorization: Bearer <access_token>
```

The access token is returned in the sign-in response body. It expires after **15 minutes**. Use the refresh token (HttpOnly cookie) to rotate it via `POST /auth/refresh`.

---

## 5. API Reference

All endpoints are under `/api/admin/` and require an authenticated `ADMIN` or `SUPERADMIN` token.

### Base URL convention

```
https://<your-api-host>
```

### 5.1 List All Roles

```
GET /api/admin/roles
Authorization: Bearer <token>
```

**Response**
```json
{
  "success": true,
  "data": [
    {
      "id": "uuid",
      "name": "CUSTOMER_SUPPORT",
      "description": "Moderates customer reviews and product questions",
      "isSystem": true,
      "createdAt": "2026-03-24T10:00:00Z",
      "updatedAt": "2026-03-24T10:00:00Z"
    },
    {
      "id": "uuid",
      "name": "COURIER_ADMIN",
      "description": "Manages courier accounts and delivery operations",
      "isSystem": true,
      "createdAt": "2026-03-24T10:00:00Z",
      "updatedAt": "2026-03-24T10:00:00Z"
    }
  ],
  "message": "Roles retrieved successfully"
}
```

> **Cache this list.** It rarely changes. Use the `id` values when assigning roles to users.

---

### 5.2 List All Permissions

```
GET /api/admin/permissions
Authorization: Bearer <token>
```

**Response**
```json
{
  "success": true,
  "data": [
    { "id": "uuid", "code": "review:read",    "description": "Review read",    "createdAt": "..." },
    { "id": "uuid", "code": "courier:create", "description": "Courier create", "createdAt": "..." }
  ],
  "message": "Permissions retrieved successfully"
}
```

> **Cache this list** on page load. You need the `id` values to assign individual permissions to a user.

---

### 5.3 Get a User's Assigned Roles

```
GET /api/admin/user-roles/users/{userId}
Authorization: Bearer <token>
```

**Response**
```json
{
  "success": true,
  "data": [
    {
      "userId": "uuid",
      "roleId": "uuid",
      "roleName": "COURIER_ADMIN",
      "assignedBy": "uuid",
      "assignedAt": "2026-03-24T12:00:00Z"
    }
  ],
  "message": "User roles retrieved successfully"
}
```

---

### 5.4 Assign a Role to a User

```
POST /api/admin/user-roles
Authorization: Bearer <token>
Content-Type: application/json

{
  "userId": "<target-user-uuid>",
  "roleId": "<role-uuid>",
  "assignedBy": "<current-admin-uuid>"   // optional but recommended for audit trail
}
```

**Response** — `201 Created`
```json
{
  "success": true,
  "data": {
    "userId": "uuid",
    "roleId": "uuid",
    "roleName": "COURIER_ADMIN",
    "assignedBy": "uuid",
    "assignedAt": "2026-03-24T12:00:00Z"
  },
  "message": "Role assigned to user successfully"
}
```

---

### 5.5 Remove a Role from a User

```
DELETE /api/admin/user-roles/users/{userId}/roles/{roleId}
Authorization: Bearer <token>
```

**Response** — `200 OK`
```json
{
  "success": true,
  "data": null,
  "message": "Role removed from user successfully"
}
```

---

### 5.6 Get a User's Direct Permission Overrides

```
GET /api/admin/user-permissions/users/{userId}
Authorization: Bearer <token>
```

**Response**
```json
{
  "success": true,
  "data": [
    {
      "userId": "uuid",
      "permissionId": "uuid",
      "permissionCode": "courier:delete",
      "effect": "ALLOW",
      "assignedBy": "uuid",
      "assignedAt": "2026-03-24T12:00:00Z"
    }
  ],
  "message": "User permissions retrieved successfully"
}
```

---

### 5.7 Assign a Direct Permission Override to a User

```
POST /api/admin/user-permissions
Authorization: Bearer <token>
Content-Type: application/json

{
  "userId": "<target-user-uuid>",
  "permissionId": "<permission-uuid>",
  "effect": "ALLOW",         // "ALLOW" to grant, "DENY" to block
  "assignedBy": "<current-admin-uuid>"
}
```

**Response** — `201 Created`
```json
{
  "success": true,
  "data": {
    "userId": "uuid",
    "permissionId": "uuid",
    "permissionCode": "courier:delete",
    "effect": "ALLOW",
    "assignedBy": "uuid",
    "assignedAt": "2026-03-24T12:00:00Z"
  },
  "message": "Permission assigned to user successfully"
}
```

---

### 5.8 Remove a Direct Permission Override

```
DELETE /api/admin/user-permissions/users/{userId}/permissions/{permissionId}
Authorization: Bearer <token>
```

---

## 6. Integration Walkthrough

This section describes the exact flow to build the **Admin User Detail → Roles & Permissions** page.

### Step 1 — Load reference data (once, on app init)

```js
const [roles, setRoles]             = useState([])
const [permissions, setPermissions] = useState([])

useEffect(() => {
  Promise.all([
    api.get('/api/admin/roles'),
    api.get('/api/admin/permissions'),
  ]).then(([rolesRes, permsRes]) => {
    setRoles(rolesRes.data.data)
    setPermissions(permsRes.data.data)
  })
}, [])
```

Store these globally (React context, Zustand, Redux — your choice). You reference them by `id` everywhere.

### Step 2 — Load a specific admin's current assignments

```js
const loadUserAccess = async (userId) => {
  const [rolesRes, permsRes] = await Promise.all([
    api.get(`/api/admin/user-roles/users/${userId}`),
    api.get(`/api/admin/user-permissions/users/${userId}`),
  ])
  setUserRoles(rolesRes.data.data)           // roleName, roleId
  setUserPermissions(permsRes.data.data)     // permissionCode, effect, permissionId
}
```

### Step 3 — Derive which role checkboxes are checked

```js
const assignedRoleIds = new Set(userRoles.map(r => r.roleId))

// In your JSX:
roles.map(role => (
  <RoleCheckbox
    key={role.id}
    label={role.name}
    checked={assignedRoleIds.has(role.id)}
    onChange={(checked) => checked
      ? assignRole(userId, role.id)
      : removeRole(userId, role.id)
    }
  />
))
```

### Step 4 — Assign / remove a role

```js
const assignRole = async (userId, roleId) => {
  await api.post('/api/admin/user-roles', {
    userId,
    roleId,
    assignedBy: currentAdminId,
  })
  await loadUserAccess(userId)   // refresh
}

const removeRole = async (userId, roleId) => {
  await api.delete(`/api/admin/user-roles/users/${userId}/roles/${roleId}`)
  await loadUserAccess(userId)
}
```

### Step 5 — Show the effective permission list

Compute effective permissions client-side so users can see what access the combination of roles + overrides produces:

```js
const effectivePermissions = useMemo(() => {
  // 1. Collect all codes that come from assigned roles
  const fromRoles = new Set(
    roles
      .filter(r => assignedRoleIds.has(r.id))
      .flatMap(r => ROLE_PERMISSIONS[r.name] ?? [])  // see section 2 above
  )

  // 2. Apply direct overrides
  userPermissions.forEach(({ permissionCode, effect }) => {
    if (effect === 'DENY')  fromRoles.delete(permissionCode)
    if (effect === 'ALLOW') fromRoles.add(permissionCode)
  })

  return fromRoles
}, [assignedRoleIds, userPermissions])
```

---

## 7. Grant Full Access Button

The **"Grant Full Access"** button assigns the `SUPERADMIN` role to a user. This gives them every permission on the platform.

### UI recommendation

```
┌─────────────────────────────────────────────┐
│  Roles & Permissions                        │
│                                             │
│  [ ] CUSTOMER_SUPPORT                       │
│  [ ] COURIER_ADMIN                          │
│  [ ] STORE_ADMIN                            │
│  [ ] SUPERADMIN                             │
│                                             │
│  ┌──────────────────────────────────┐       │
│  │  ⚡ Grant Full Access            │       │
│  └──────────────────────────────────┘       │
│                                             │
│  (!) This gives the user SUPERADMIN access  │
│      to all modules. Use with caution.      │
└─────────────────────────────────────────────┘
```

### Implementation

```js
// 1. Find the SUPERADMIN role ID from the cached list
const superadminRole = roles.find(r => r.name === 'SUPERADMIN')

// 2. Grant Full Access handler
const grantFullAccess = async () => {
  const confirmed = await showConfirmDialog(
    'Grant Full Access',
    'This will assign SUPERADMIN to this user, giving them unrestricted access to all modules. Are you sure?'
  )
  if (!confirmed) return

  if (!assignedRoleIds.has(superadminRole.id)) {
    await api.post('/api/admin/user-roles', {
      userId: targetUser.id,
      roleId: superadminRole.id,
      assignedBy: currentAdminId,
    })
    await loadUserAccess(targetUser.id)
    toast.success('Full access granted.')
  }
}

// 3. Revoke Full Access handler
const revokeFullAccess = async () => {
  await api.delete(
    `/api/admin/user-roles/users/${targetUser.id}/roles/${superadminRole.id}`
  )
  await loadUserAccess(targetUser.id)
  toast.success('SUPERADMIN role removed.')
}
```

### Toggle pattern

Show either "Grant Full Access" or "Revoke Full Access" depending on current state:

```jsx
const hasSuperadmin = assignedRoleIds.has(superadminRole?.id)

{hasSuperadmin ? (
  <button className="btn btn-danger" onClick={revokeFullAccess}>
    Revoke Full Access
  </button>
) : (
  <button className="btn btn-warning" onClick={grantFullAccess}>
    ⚡ Grant Full Access
  </button>
)}
```

---

## 8. Permission Overrides (ALLOW / DENY)

Beyond role assignment you can grant or block **individual permissions** directly on a user, regardless of their roles. This is useful for edge cases like:

- Giving a `COURIER_ADMIN` the ability to also delete couriers (`courier:delete` ALLOW).
- Blocking a `CUSTOMER_SUPPORT` agent from deleting reviews (`review:delete` DENY).

### Render the per-permission override panel

```js
// Map permission IDs to their overrides for quick lookup
const overrideMap = Object.fromEntries(
  userPermissions.map(p => [p.permissionId, p])
)

permissions.map(perm => {
  const override = overrideMap[perm.id]
  return (
    <PermissionRow
      key={perm.id}
      code={perm.code}
      override={override?.effect ?? null}   // null | 'ALLOW' | 'DENY'
      onAllow={() => setOverride(userId, perm.id, 'ALLOW')}
      onDeny={()  => setOverride(userId, perm.id, 'DENY')}
      onClear={() => clearOverride(userId, perm.id)}
    />
  )
})
```

### API calls for overrides

```js
const setOverride = async (userId, permissionId, effect) => {
  await api.post('/api/admin/user-permissions', {
    userId,
    permissionId,
    effect,                   // 'ALLOW' or 'DENY'
    assignedBy: currentAdminId,
  })
  await loadUserAccess(userId)
}

const clearOverride = async (userId, permissionId) => {
  await api.delete(
    `/api/admin/user-permissions/users/${userId}/permissions/${permissionId}`
  )
  await loadUserAccess(userId)
}
```

---

## 9. UI Access Control (Hiding Elements)

Decode the current user's own role list (returned from `GET /api/admin/user-roles/users/{currentUserId}`) to conditionally render navigation items and action buttons.

### Helper

```js
// Store in auth context after login
const currentUserRoles = new Set(userRoles.map(r => r.roleName))

export const can = {
  manageReviews:   () => currentUserRoles.has('SUPERADMIN') || currentUserRoles.has('CUSTOMER_SUPPORT'),
  manageQuestions: () => currentUserRoles.has('SUPERADMIN') || currentUserRoles.has('CUSTOMER_SUPPORT'),
  manageCouriers:  () => currentUserRoles.has('SUPERADMIN') || currentUserRoles.has('COURIER_ADMIN') || currentUserRoles.has('ADMIN'),
  deleteCourier:   () => currentUserRoles.has('SUPERADMIN') || currentUserRoles.has('ADMIN'),
  manageStore:     () => currentUserRoles.has('SUPERADMIN') || currentUserRoles.has('STORE_ADMIN') || currentUserRoles.has('ADMIN'),
  assignStoreAdmins: () => currentUserRoles.has('SUPERADMIN') || currentUserRoles.has('ADMIN'),
  manageRoles:     () => currentUserRoles.has('SUPERADMIN') || currentUserRoles.has('ADMIN'),
}
```

### Navigation guard example

```jsx
<nav>
  {can.manageReviews()   && <NavLink to="/admin/reviews">Reviews</NavLink>}
  {can.manageQuestions() && <NavLink to="/admin/questions">Questions</NavLink>}
  {can.manageCouriers()  && <NavLink to="/admin/couriers">Couriers</NavLink>}
  {can.manageStore()     && <NavLink to="/admin/stores">Stores</NavLink>}
  {can.manageRoles()     && <NavLink to="/admin/users">User Management</NavLink>}
</nav>
```

### Action button guard example

```jsx
// Only SUPERADMIN / ADMIN can delete a courier
{can.deleteCourier() && (
  <button onClick={() => deleteCourier(courierId)}>Delete Courier</button>
)}
```

> **Important:** UI guards are for UX only. The API enforces real authorization server-side — hiding a button does not replace server security.

---

## 10. Error Handling

| HTTP Status | Meaning | What to do in UI |
|---|---|---|
| `401 Unauthorized` | Token missing or expired | Redirect to login |
| `403 Forbidden` | Valid token but insufficient role/permission | Show "You don't have access to perform this action" |
| `404 Not Found` | Role / permission / user not found | Show inline error |
| `409 Conflict` | Role or permission already assigned | Silently ignore or show "Already assigned" toast |

```js
api.interceptors.response.use(
  res => res,
  err => {
    if (err.response?.status === 401) router.push('/login')
    if (err.response?.status === 403) toast.error("Access denied.")
    return Promise.reject(err)
  }
)
```

---

## Quick Reference — Role to Module Matrix

| Module | CUSTOMER_SUPPORT | COURIER_ADMIN | STORE_ADMIN | SUPERADMIN |
|---|:---:|:---:|:---:|:---:|
| Reviews (read, moderate, reply) | ✅ | — | — | ✅ |
| Questions (read, moderate, answer) | ✅ | — | — | ✅ |
| Couriers (read, create, update) | — | ✅ | — | ✅ |
| Courier delete | — | — | — | ✅ |
| Store (read, update) | — | — | ✅ | ✅ |
| Store products (assign, update, remove) | — | — | ✅ | ✅ |
| Store admin management | — | — | Read only | ✅ |
| Role & permission management | — | — | — | ✅ |
