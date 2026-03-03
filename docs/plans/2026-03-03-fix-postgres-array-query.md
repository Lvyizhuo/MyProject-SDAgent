# Fix PostgreSQL Array Query in KnowledgeDocumentRepository

**Date:** 2026-03-03
**Status:** Approved

## Problem Summary

The application fails to start with a Hibernate query validation error:

```
org.hibernate.query.SemanticException: Operand of 'member of' operator must be a plural path
[SELECT d FROM KnowledgeDocument d WHERE :tag MEMBER OF d.tags]
```

### Root Cause
- `KnowledgeDocument.tags` field uses PostgreSQL native array type: `@Column(columnDefinition = "VARCHAR(500)[]")`
- Hibernate 6.x's `MEMBER OF` syntax doesn't work with PostgreSQL array columns
- `MEMBER OF` expects JPA collection mappings like `@ElementCollection`, not native arrays

## Solution

### Approach
Use **PostgreSQL native SQL query** with `ANY()` function instead of HQL `MEMBER OF`.

### Changes Required

**File:** `backend/src/main/java/com/shandong/policyagent/repository/KnowledgeDocumentRepository.java`

**Before:**
```java
@Query("SELECT d FROM KnowledgeDocument d WHERE :tag MEMBER OF d.tags")
Page<KnowledgeDocument> findByTag(@Param("tag") String tag, Pageable pageable);
```

**After:**
```java
@Query(value = "SELECT * FROM knowledge_documents WHERE ?1 = ANY(tags)",
       nativeQuery = true)
Page<KnowledgeDocument> findByTag(@Param("tag") String tag, Pageable pageable);
```

## Design Considerations

### Pros
- ✅ Simple and direct fix
- ✅ Leverages PostgreSQL native array performance
- ✅ Maintains Pageable pagination support
- ✅ No changes needed to KnowledgeService or other callers
- ✅ Keeps the existing PostgreSQL array column design

### Cons
- ⚠️ Uses native SQL (not pure JPA) - but acceptable since we're already using PostgreSQL-specific column type

## Impact Analysis

- **Affected Files:** 1 file (KnowledgeDocumentRepository.java)
- **Backward Compatibility:** Fully compatible - same method signature
- **Performance:** No degradation - uses native PostgreSQL array operations
- **Testing:** Should verify tag query functionality works correctly

## Implementation Steps

1. Modify `KnowledgeDocumentRepository.findByTag()` to use native query
2. Test application startup
3. Verify tag query functionality (if tests exist)
