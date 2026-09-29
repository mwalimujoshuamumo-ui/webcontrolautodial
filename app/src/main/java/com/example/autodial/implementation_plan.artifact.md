# Fix: Skipping Transactions and Search Functionality

The user reported that the app is skipping M-PESA transactions or not reading them at all. Additionally, searching for transactions by phone number is not working effectively.

## User Review Required

> [!IMPORTANT]
> To ensure **all** messages are recorded as requested, I will implement a "Record First" strategy. This means even if a message fails to parse correctly, a transaction record will still be created with the status `UNMATCHED` so it's visible to the user.

## Analysis

### 1. Skipping Transactions (Not Reading/Recording)
The primary reasons M-PESA messages are not recorded appear to be:
- **Missing "MPESA" in Whitelist**: `SmsReceiver` ignores messages from senders not in the "Verified Senders" list. "MPESA" is not currently in the default code-level whitelist.
- **Strict Parsing**: `MpesaParser` requires the word "Confirmed" to be present and specifically formatted near the transaction code.
- **Async Processing**: If the background task fails before recording, the message is lost.

### 2. Search Functionality
- **Date Filter Restriction**: Search only applies to the visible list (e.g., "Today").
- **Search Scope**: Only checks name and phone number.
- **Normalization**: Phone number searches don't handle spaces or the `254` prefix.

## Proposed Changes

### [app]

#### [MODIFY] [SmsReceiver.kt](file:///C:/Users/USER/AndroidStudioProjects/AUTODIAL2/app/src/main/java/com/example/autodial/service/SmsReceiver.kt)
- **Whitelist "MPESA"**: Explicitly include "MPESA" and "M-PESA" as verified senders by default.
- **Guaranteed Recording**:
    - Move the initial transaction recording *before* strict parsing.
    - If parsing fails but the sender is verified, create a record with `status = UNMATCHED` and the full raw message.
- **Improved Logging**: Add breadcrumbs for why an SMS is processed or skipped.

#### [MODIFY] [MpesaParser.kt](file:///C:/Users/USER/AndroidStudioProjects/AUTODIAL2/app/src/main/java/com/example/autodial/util/MpesaParser.kt)
- **Robustness**:
    - Update regex to handle variations in "Confirmed" text.
    - Add a fallback to extract the 10-character code even if the full regex fails.

#### [MODIFY] [TransactionsScreen.kt](file:///C:/Users/USER/AndroidStudioProjects/AUTODIAL2/app/src/main/java/com/example/autodial/ui/transactions/TransactionsScreen.kt)
- **Enhanced Search**:
    - Normalize `searchQuery` (remove spaces, handle `254`).
    - Search across `senderName`, `customerNumber`, `transactionCode`, and `id`.
- **Global Search**: Automatically ignore the date filter (switch to "All") when a search query is active.

## Verification Plan

### Manual Verification
- **Search Test**: Enter phone with `254` or spaces and verify it finds transactions regardless of date filter.
- **Reading Test**: Send a non-standard M-PESA message and verify it appears in the list as `UNMATCHED` instead of being ignored.
