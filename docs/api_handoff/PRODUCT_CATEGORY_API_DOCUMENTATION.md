# Product Category API Documentation

Base URL: `/api/category`

All responses follow this envelope:

```json
{
  "statusCode": 200,
  "message": "...",
  "data": { ... }
}
```

---

## Endpoints

### 1. Get All Categories (localized)

```
GET /api/category?lang={language}
```

Returns all categories translated into the requested language.

**Query Parameters**

| Parameter | Type   | Required | Values         |
|-----------|--------|----------|----------------|
| `lang`    | string | Yes      | `AZ`, `EN`, `AR` |

**Response `200`**

```json
{
  "statusCode": 200,
  "message": "Categories fetched successfully",
  "data": [
    {
      "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "parentId": null,
      "status": "ACTIVE",
      "name": "Electronics",
      "description": "All electronic products",
      "slug": "electronics",
      "createdAt": "2024-01-01T10:00:00Z",
      "updatedAt": "2024-01-01T10:00:00Z"
    }
  ]
}
```

---

### 2. Get Category by ID

```
GET /api/category/{id}
```

Returns a single category with all three language translations.

**Path Parameters**

| Parameter | Type   | Required |
|-----------|--------|----------|
| `id`      | UUID   | Yes      |

**Response `200`**

```json
{
  "statusCode": 200,
  "message": "Category fetched successfully",
  "data": {
    "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "parentId": null,
    "status": "ACTIVE",
    "createdAt": "2024-01-01T10:00:00Z",
    "updatedAt": "2024-01-01T10:00:00Z",
    "translations": [
      { "language": "AZ", "name": "Elektronika", "description": "Bütün elektron məhsullar", "slug": "elektronika" },
      { "language": "EN", "name": "Electronics", "description": "All electronic products", "slug": "electronics" },
      { "language": "AR", "name": "إلكترونيات", "description": "جميع المنتجات الإلكترونية", "slug": "electronics-ar" }
    ]
  }
}
```

**Response `404`**

```json
{
  "statusCode": 404,
  "message": "Category not found with id: 3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "data": null
}
```

---

### 3. Create Category

```
POST /api/category
Content-Type: application/json
```

Creates a root category (`parentId` omitted) or a subcategory (`parentId` provided). All three language translations are required.

**Request Body**

```json
{
  "parentId": null,
  "status": "ACTIVE",
  "translations": {
    "nameAz": "Elektronika",
    "descriptionAz": "Bütün elektron məhsullar",
    "slugAz": "elektronika",
    "nameEn": "Electronics",
    "descriptionEn": "All electronic products",
    "slugEn": "electronics",
    "nameAr": "إلكترونيات",
    "descriptionAr": "جميع المنتجات الإلكترونية",
    "slugAr": "electronics-ar"
  }
}
```

| Field                      | Type   | Required | Notes                                    |
|----------------------------|--------|----------|------------------------------------------|
| `parentId`                 | UUID   | No       | Omit or null for root category           |
| `status`                   | string | No       | Defaults to `ACTIVE`                     |
| `translations.nameAz`      | string | Yes      | Max 255 chars                            |
| `translations.descriptionAz` | string | Yes    |                                          |
| `translations.slugAz`      | string | Yes      | Lowercase alphanumeric with hyphens only |
| `translations.nameEn`      | string | Yes      | Max 255 chars                            |
| `translations.descriptionEn` | string | Yes    |                                          |
| `translations.slugEn`      | string | Yes      | Lowercase alphanumeric with hyphens only |
| `translations.nameAr`      | string | Yes      | Max 255 chars                            |
| `translations.descriptionAr` | string | Yes    |                                          |
| `translations.slugAr`      | string | Yes      | Lowercase alphanumeric with hyphens only |

**Response `201`**

```json
{
  "statusCode": 201,
  "message": "Category created successfully",
  "data": {
    "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "parentId": null,
    "status": "ACTIVE",
    "createdAt": "2024-01-01T10:00:00Z",
    "updatedAt": "2024-01-01T10:00:00Z",
    "translations": [
      { "language": "AZ", "name": "Elektronika", "description": "Bütün elektron məhsullar", "slug": "elektronika" },
      { "language": "EN", "name": "Electronics", "description": "All electronic products", "slug": "electronics" },
      { "language": "AR", "name": "إلكترونيات", "description": "جميع المنتجات الإلكترونية", "slug": "electronics-ar" }
    ]
  }
}
```

**Response `400`** — slug already taken

```json
{
  "statusCode": 400,
  "message": "Slug 'electronics' is already taken for language EN",
  "data": null
}
```

---

### 4. Update Category

```
PATCH /api/category/{id}
Content-Type: application/json
```

Partially updates a category. All fields are optional — only provided fields are applied. For translations, only fields that are provided within the `translations` object are updated.

**Path Parameters**

| Parameter | Type | Required |
|-----------|------|----------|
| `id`      | UUID | Yes      |

**Request Body** (all fields optional)

```json
{
  "parentId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
  "status": "INACTIVE",
  "translations": {
    "nameAz": "Yeni Ad",
    "descriptionAz": "Yeni təsvir",
    "slugAz": "yeni-ad",
    "nameEn": "New Name",
    "descriptionEn": "New description",
    "slugEn": "new-name",
    "nameAr": "اسم جديد",
    "descriptionAr": "وصف جديد",
    "slugAr": "new-name-ar"
  }
}
```

| Field               | Type   | Required | Notes                                         |
|---------------------|--------|----------|-----------------------------------------------|
| `parentId`          | UUID   | No       | Must be a valid existing category ID          |
| `status`            | string | No       | `ACTIVE` or `INACTIVE`                        |
| `translations`      | object | No       | If provided, only non-null fields are applied |
| `translations.slug*`| string | No       | Must be globally unique per language          |

**Response `200`**

```json
{
  "statusCode": 200,
  "message": "Category updated successfully",
  "data": {
    "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "parentId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
    "status": "INACTIVE",
    "createdAt": "2024-01-01T10:00:00Z",
    "updatedAt": "2024-06-01T12:00:00Z",
    "translations": [
      { "language": "AZ", "name": "Yeni Ad", "description": "Yeni təsvir", "slug": "yeni-ad" },
      { "language": "EN", "name": "New Name", "description": "New description", "slug": "new-name" },
      { "language": "AR", "name": "اسم جديد", "description": "وصف جديد", "slug": "new-name-ar" }
    ]
  }
}
```

**Response `400`** — self-referencing parent

```json
{
  "statusCode": 400,
  "message": "A category cannot be its own parent",
  "data": null
}
```

**Response `404`**

```json
{
  "statusCode": 404,
  "message": "Category not found with id: 3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "data": null
}
```

---

### 5. Delete Category

```
DELETE /api/category/{id}
```

Soft-deletes the category by setting its status to `INACTIVE`. The category remains in the database and can be reactivated via the update endpoint.

**Path Parameters**

| Parameter | Type | Required |
|-----------|------|----------|
| `id`      | UUID | Yes      |

**Response `200`**

```json
{
  "statusCode": 200,
  "message": "Category deleted successfully",
  "data": null
}
```

**Response `404`**

```json
{
  "statusCode": 404,
  "message": "Category not found with id: 3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "data": null
}
```

---

## Slug Rules

- Must be **lowercase alphanumeric** characters only, separated by hyphens
- Valid: `electronics`, `mobile-phones`, `smart-tv-2024`
- Invalid: `Electronics`, `mobile phones`, `smart_tv`
- Slugs must be **globally unique per language** — the same slug cannot be used by two different categories in the same language

---

## Status Values

| Value      | Meaning                              |
|------------|--------------------------------------|
| `ACTIVE`   | Category is visible and usable       |
| `INACTIVE` | Category is soft-deleted / hidden    |

---

## Language Codes

| Code | Language    |
|------|-------------|
| `AZ` | Azerbaijani |
| `EN` | English     |
| `AR` | Arabic      |

---

## Category Hierarchy

- A **root category** has no parent (`parentId` is `null`)
- A **subcategory** references an existing category via `parentId`
- A category **cannot be its own parent**
- There is no enforced depth limit, but keep nesting reasonable (2–3 levels recommended)
