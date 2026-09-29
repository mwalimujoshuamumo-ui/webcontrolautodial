package com.example.autodial.util

import java.util.regex.Pattern

data class MpesaDetails(
    val transactionCode: String,
    val amount: Double,
    val sender: String,
    val recipient: String = "",
    val customerNumber: String,
    val rawMessage: String,
    val isPaybill: Boolean = false,
    val isMasked: Boolean = false,
    val isReceived: Boolean = false,
    val accountNumberHadSpaces: Boolean = false
)

object MpesaParser {
    // Matches 10 Alphanumeric + Confirmed or just the code (handles forwarded prefixes)
    private val CODE_PATTERN = Pattern.compile("([A-Z0-9]{10})[\\s.]?Confirmed", Pattern.CASE_INSENSITIVE)
    
    // Matches KSH/Ksh/ksh + amount
    private val AMOUNT_PATTERN = Pattern.compile("(?:KSH|Ksh|ksh)\\s?([0-9,.]+)", Pattern.CASE_INSENSITIVE)
    
    // Format 1: Name BEFORE Number (e.g. from PETER SAMUEL 07... or 071 122 333)
    private val NAME_BEFORE_NUMBER_PATTERN = Pattern.compile("(?:from|to|by|paid to|sent to|received from)\\s+([^0-9*.]{2,50})\\s+((?:254|0)?[0-9*\\s]{9,20})", Pattern.CASE_INSENSITIVE)
    
    // Format 2: Number BEFORE Name (e.g. from 254... ruth mwedwa)
    private val NUMBER_BEFORE_NAME_PATTERN = Pattern.compile("(?:from|to|by|paid to|sent to|received from)\\s+((?:254|0)?[0-9*\\s]{9,20})\\s+([^0-9*.]{2,50})(\\s|\\.|New|on|$)", Pattern.CASE_INSENSITIVE)

    // Explicit search for names in message body if they appear between keywords and a number
    private val BODY_NAME_PATTERN = Pattern.compile("(?:sent to|paid to|received from)\\s+([^0-9*.]{2,50})\\s?(?:254|0)?[0-9*\\s]{9,20}", Pattern.CASE_INSENSITIVE)
    
    // Explicit Name Search if above fails
    private val SENDER_NAME_ONLY_PATTERN = Pattern.compile("(?:from|to|by|paid to|sent to|received from)\\s+([^0-9*.]{3,50})(?:\\s|\\.|New|on|$)", Pattern.CASE_INSENSITIVE)
    
    private val ACCOUNT_PATTERN = Pattern.compile("(?:Account\\sNumber|Acc(?:[. ](?:No.?)?)?|Account(?!\\s+balance))\\s?[:\\-]?\\s?([A-Z0-9*\\s-]{4,25})", Pattern.CASE_INSENSITIVE)

    fun isLikelyMpesa(message: String): Boolean {
        val msg = message.lowercase()
        val hasKeyword = msg.contains("received") || msg.contains("paid") || msg.contains("sent") || 
                         msg.contains("confirmed") || msg.contains("m-pesa") || msg.contains("mpesa")
        
        // Forwarded messages often contain the keyword but maybe not at the start.
        // We look for a 10-char alphanumeric code anywhere if keywords are present.
        return hasKeyword && (
            CODE_PATTERN.matcher(message).find()
        )
    }

    fun parse(message: String): MpesaDetails? {
        if (!isLikelyMpesa(message)) return null
        
        val codeMatcher = CODE_PATTERN.matcher(message)
        val amountMatcher = AMOUNT_PATTERN.matcher(message)
        
        val code = when {
            codeMatcher.find() -> codeMatcher.group(1) ?: ""
            else -> ""
        }

        if (code.isNotEmpty() && amountMatcher.find()) {
            var amountStr = amountMatcher.group(1)?.replace(",", "") ?: "0.0"
            if (amountStr.endsWith(".")) amountStr = amountStr.dropLast(1)
            val amount = amountStr.toDoubleOrNull() ?: 0.0
            
            var senderName = "UNKNOWN"
            var recipientName = ""
            var extractedPhone = ""
            var accountNumber = ""
            var accountNumberHadSpaces = false
            var isPaybill = false
            var isMasked: Boolean

            // 1. Extract Account Number (Highest Priority for customerNumber)
            val accountMatcher = ACCOUNT_PATTERN.matcher(message)
            if (accountMatcher.find()) {
                val rawAcc = (accountMatcher.group(1) ?: "").trim()
                // Only count as Paybill if it contains at least 4 digits
                if (rawAcc.filter { it.isDigit() }.length >= 4) {
                    accountNumberHadSpaces = rawAcc.contains(" ")
                    accountNumber = rawAcc
                    isPaybill = true
                }
            }

            // 2. Extract Sender Info (Name and Phone)
            val numberBeforeNameMatcher = NUMBER_BEFORE_NAME_PATTERN.matcher(message)
            val nameBeforeNumberMatcher = NAME_BEFORE_NUMBER_PATTERN.matcher(message)
            
            if (numberBeforeNameMatcher.find()) {
                extractedPhone = (numberBeforeNameMatcher.group(1) ?: "").trim()
                senderName = (numberBeforeNameMatcher.group(2) ?: "").trim().uppercase()
            } else if (nameBeforeNumberMatcher.find()) {
                senderName = (nameBeforeNumberMatcher.group(1) ?: "").trim().uppercase()
                extractedPhone = (nameBeforeNumberMatcher.group(2) ?: "").trim()
            } else {
                // Fallback name searches
                val bodyNameMatcher = BODY_NAME_PATTERN.matcher(message)
                if (bodyNameMatcher.find()) {
                    senderName = (bodyNameMatcher.group(1) ?: "UNKNOWN").trim().uppercase()
                } else {
                    val nameOnlyMatcher = SENDER_NAME_ONLY_PATTERN.matcher(message)
                    if (nameOnlyMatcher.find()) {
                        senderName = (nameOnlyMatcher.group(1) ?: "UNKNOWN").trim().uppercase()
                    }
                }
            }

            // 2b. Extract Recipient for "Paid to" messages
            if (message.contains("paid to", true) || message.contains("sent to", true)) {
                val recipientPattern = Pattern.compile("(?:paid to|sent to)\\s+([^0-9*.]+?)(?:\\s+on|\\s+Account|\\s+New|$)", Pattern.CASE_INSENSITIVE)
                val recipientMatcher = recipientPattern.matcher(message)
                if (recipientMatcher.find()) {
                    recipientName = (recipientMatcher.group(1) ?: "").trim().uppercase()
                }
            }

            // 3. Last resort phone search if no phone found in patterns
            if (extractedPhone.isEmpty()) {
                val phonePattern = Pattern.compile("(?:254|0)?([17][0-9*\\s]{8,15})")
                val phoneMatcher = phonePattern.matcher(message)
                if (phoneMatcher.find()) {
                    extractedPhone = (phoneMatcher.group(0) ?: "").trim()
                }
            }

            // 4. Determine Final customerNumber
            val isReceived = message.contains("received", ignoreCase = true)
            
            // IGNORE SPACES: Strip all spaces from extracted values for identity matching
            val noiseWords = listOf("utility", "balance", "mpesa", "ksh", "ksh.", "acc.", "acc", "amount")
            var cleanAcc = accountNumber.replace("\\s".toRegex(), "").lowercase()
            
            // Remove noise words from account number
            noiseWords.forEach { word ->
                cleanAcc = cleanAcc.replace(word, "")
            }
            cleanAcc = cleanAcc.trim().uppercase()
            
            val cleanPhone = extractedPhone.replace("\\s".toRegex(), "")
            
            val formattedAcc = if (cleanAcc.isNotEmpty()) {
                if (cleanAcc.startsWith("U") || cleanAcc.startsWith("L") || cleanAcc.contains("AD-")) {
                    cleanAcc 
                } else {
                    formatPhone(cleanAcc)
                }
            } else ""
            
            val formattedSender = if (cleanPhone.isNotEmpty()) formatPhone(cleanPhone) else ""
            
            val finalCustomerNumber = when {
                // Priority 1: Token/Special patterns (U, L, AD-) - Always extract
                formattedAcc.startsWith("U") || formattedAcc.startsWith("L") || formattedAcc.contains("AD-") -> formattedAcc
                
                // Priority 2: Received Paybill - Account Number is the HIGHEST Priority (Ignored spaces/Alphanumeric)
                isReceived && formattedAcc.isNotEmpty() -> formattedAcc
                
                // Priority 3: Standard Sender Phone
                formattedSender.length == 10 -> formattedSender
                
                // Fallbacks
                formattedSender.isNotEmpty() -> formattedSender
                else -> if (isReceived) "UNKNOWN" else ""
            }
            
            isMasked = finalCustomerNumber.contains("*")
            
            return MpesaDetails(
                transactionCode = code,
                amount = amount,
                sender = senderName,
                recipient = recipientName,
                customerNumber = finalCustomerNumber,
                rawMessage = message,
                isPaybill = isPaybill,
                isMasked = isMasked,
                isReceived = isReceived,
                accountNumberHadSpaces = accountNumberHadSpaces
            )
        }
        return null
    }

    private fun formatPhone(extractedPhone: String): String {
        if (extractedPhone.contains("AD-")) return extractedPhone // Don't format our special IDs
        var formatted = extractedPhone.replace("\\s".toRegex(), "")
        if (formatted.startsWith("254")) formatted = "0" + formatted.drop(3)
        else if (!formatted.startsWith("0") && (formatted.startsWith("7") || formatted.startsWith("1"))) formatted = "0$formatted"
        return formatted
    }
}
