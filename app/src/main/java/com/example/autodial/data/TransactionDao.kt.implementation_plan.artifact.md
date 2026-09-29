# Fix Room Query Result Conversion Error in TransactionDao

The user is experiencing a compilation error: `[ksp] .../TransactionDao.kt:33: Not sure how to convert the query result to this function's return type (kotlin.Int)`.

## Proposed Changes

### [Component Name]

#### [MODIFY] [TransactionDao.kt](file:///C:/Users/USER/AndroidStudioProjects/AUTODIAL2/app/src/main/java/com/example/autodial/data/TransactionDao.kt)

The function `deleteTransactionsBefore` is currently using a `SELECT *` query but has an `Int` return type. Based on the function name, it should be a `DELETE` query. Room's `@Query` for `DELETE` returns an `Int` representing the number of rows deleted, which matches the function signature.

```diff
-    @Query("SELECT * FROM transactions WHERE date < :timestamp")
+    @Query("DELETE FROM transactions WHERE date < :timestamp")
     suspend fun deleteTransactionsBefore(timestamp: Long): Int
```

## Verification Plan

### Automated Tests
- Run `./gradlew :app:kspDebugKotlin` to verify that the KSP error is resolved.
- Run `./gradlew :app:assembleDebug` to ensure the project builds successfully.
