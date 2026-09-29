package com.example.autodial.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.autodial.data.*
import com.example.autodial.util.EngageBotTrigger
import com.example.autodial.util.PreferenceManager
import com.example.autodial.util.TransactionQueueManager
import com.example.autodial.util.UssdSessionManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock

/**
 * Sequential Advanced USSD Interaction Engine
 * Hardened for 100% reliability in multi-step sessions.
 */
class UssdAccessibilityService : AccessibilityService() {

    companion object {
        private var instance: UssdAccessibilityService? = null
        
        fun startSuppression() {
            instance?.hideKeyboard()
        }

        fun stopSuppression() {
            instance?.showKeyboard()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        
        // Optimize service persistence and interaction
        val info = serviceInfo
        info.flags = info.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                     android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                     android.accessibilityservice.AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        serviceInfo = info
        
        Log.i("AdvancedEngine", "Service connected. Triggering queue process.")
        TransactionQueueManager.processNext(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        showKeyboard()
        serviceJob.cancel()
        instance = null
    }

    override fun onUnbind(intent: Intent?): Boolean {
        showKeyboard()
        instance = null
        UssdSessionManager.endSession(false)
        TransactionQueueManager.resetProcessingLock()
        Log.w("AdvancedEngine", "Service unbound. Returning true for rebinding.")
        return true
    }

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e("AdvancedEngine", "Critical coroutine crash: ${throwable.message}", throwable)
    }

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob + exceptionHandler)
    
    // De-bounce timer to prevent double-replies
    private var lastActionTime = 0L
    private val DEBOUNCE_DELAY = 0L // Ultra-fast responsive delay
    private val recordingMutex = kotlinx.coroutines.sync.Mutex()

    private fun hideKeyboard() {
        try {
            val prefManager = PreferenceManager(this)
            if (!prefManager.isKeyboardSuppressionEnabled()) return

            // 1. CONTEXT AWARE: Only suppress if we are actually processing a transaction
            if (!TransactionQueueManager.isProcessing()) return

            // 2. WHITELIST: Only hide keyboard if we are in our app or a system USSD window across OEMs
            val activeRoot = try { rootInActiveWindow } catch (e: Exception) { null }
            val currentPkg = try { activeRoot?.packageName?.toString() ?: "" } catch (e: Exception) { "" }
            val isWhitelisted = currentPkg == packageName || 
                               currentPkg == "com.android.phone" || 
                               currentPkg == "com.android.server.telecom" ||
                               currentPkg == "com.android.systemui" ||
                               currentPkg == "android" ||
                               currentPkg.contains("stk") ||
                               currentPkg.contains("incallui") ||
                               currentPkg.contains("dialer") ||
                               currentPkg.contains("phone") ||
                               currentPkg.contains("transsion") ||
                               currentPkg.contains("mediatek") ||
                               currentPkg.contains("qualcomm") ||
                               currentPkg.contains("samsung") ||
                               currentPkg.contains("sec.") ||
                               currentPkg.contains("xiaomi") ||
                               currentPkg.contains("miui") ||
                               currentPkg.contains("hios") ||
                               currentPkg.contains("xos") ||
                               currentPkg.contains("coloros") ||
                               currentPkg.contains("oppo") ||
                               currentPkg.contains("vivo")

            if (!isWhitelisted) return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                softKeyboardController.showMode = AccessibilityService.SHOW_MODE_HIDDEN
            }
        } catch (e: Exception) {
            Log.e("AdvancedEngine", "hideKeyboard failed", e)
        }
    }

    private fun showKeyboard() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                softKeyboardController.showMode = AccessibilityService.SHOW_MODE_AUTO
            }
        } catch (e: Exception) {
            Log.e("AdvancedEngine", "showKeyboard failed", e)
        }
    }

    private val repository by lazy {
        AppDatabase.getDatabase(this).createRepository()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        try {
            if (!UssdSessionManager.isActive && !TransactionQueueManager.isWaitingForDismissal) {
                return
            }

            // PERF: Ignore non-relevant event types to save CPU
            if (event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) return

            // 1. Hunt for the USSD dialog
            val rootNode = try { findUssdRoot(event) } catch (e: Exception) { null } ?: return

            // Suppress soft keyboard only while active USSD dialog is present
            try {
                hideKeyboard()
            } catch (e: Exception) {
                Log.e("AdvancedEngine", "Keyboard suppression error", e)
            }
            
            // 3. Extract and Normalize Content
            val contentText = getAllText(rootNode)
            if (contentText.isBlank()) return
            val normalizedBody = contentText.lowercase().replace(Regex("[^a-z0-9]"), "")
            
            // 4. Skip progress popups ("Code Running", "Sending...", "Please wait...") if they lack an input field
            val isProgressPopup = (normalizedBody.contains("ussd") || normalizedBody.contains("code") || normalizedBody.contains("request")) && 
                                  (normalizedBody.contains("running") || normalizedBody.contains("sending") || 
                                   normalizedBody.contains("pleasewait") || normalizedBody.contains("loading") || 
                                   normalizedBody.contains("connecting"))
            if (isProgressPopup && !hasInputField(rootNode)) return

            // 5. Unique Screen Detection (Fingerprinting)
            val fingerprint = "${extractScreenInstruction(contentText)}|${normalizedBody}"
            
            if (fingerprint == UssdSessionManager.lastScreenFingerprint) return

            serviceScope.launch {
                try {
                    // STRICT INTERACTION LOCK: Ensure one screen processed at a time
                    recordingMutex.withLock {
                        // Double-check fingerprint under lock
                        if (fingerprint == UssdSessionManager.lastScreenFingerprint) return@launch

                        // SNAPPY PRO RHYTHM: Minimal stabilization wait
                        val screenDelay = PreferenceManager(this@UssdAccessibilityService).getUssdResponseDelay()
                        if (screenDelay > 0) delay(screenDelay)
                        val activeRoot = try { findUssdRoot(event) } catch (e: Throwable) { null } ?: return@launch
                        val freshText = getAllText(activeRoot)
                        
                        processScreen(root = activeRoot, text = freshText, fingerprint = fingerprint)
                    }
                } catch (e: Throwable) {
                    Log.e("AdvancedEngine", "Error in processScreen coroutine", e)
                }
            }
        } catch (e: Throwable) {
            Log.e("AdvancedEngine", "Fatal error in onAccessibilityEvent", e)
        }
    }

    private suspend fun processScreen(root: AccessibilityNodeInfo, text: String, fingerprint: String) {
        // A. Terminal Response Handling
        if (isTerminalResponse(text, root)) {
            Log.i("MapRecorder", "Terminal Screen Detected: $fingerprint")
            val finalMsg = extractPopUpMessage(root)
            
            // Smart Failure Handling: Check for negative responses with positive overrides
            val lowerMsg = text.lowercase()
            val isPositiveOverride = lowerMsg.contains("success") || 
                                     lowerMsg.contains("confirmed") || 
                                     lowerMsg.contains("bought") || 
                                     lowerMsg.contains("purchased") || 
                                     lowerMsg.contains("completed") || 
                                     lowerMsg.contains("credited")

            val isNegative = !isPositiveOverride && (
                             lowerMsg.contains("insufficient") || 
                             lowerMsg.contains("not have enough") || 
                             lowerMsg.contains("sorry") || 
                             lowerMsg.contains("invalid") ||
                             lowerMsg.contains("failed") || 
                             EngageBotTrigger.isAlreadyRecommendedToday(text)
                             )

            UssdSessionManager.addFinalResponse(finalMsg, if (isNegative) "FAILURE" else "SUCCESS")
            
            if (isNegative) {
                Log.w("AdvancedEngine", "FAILURE detected in final screen. Dismissing...")
                dismissUssd(root)
                completeSession(finalMsg, false)
                return
            }

            // SUCCESS Case: Auto-dismissal as requested
            Log.i("AdvancedEngine", "SUCCESS detected. Auto-dismissing instantly.")
            
            if (UssdSessionManager.isTrainingMode) {
                val currentStepNum = UssdSessionManager.getCurrentIndex() + 1
                serviceScope.launch(Dispatchers.IO) {
                    saveTrainingStepSynchronously(text, "FINAL", "SUCCESS", stepNumber = currentStepNum)
                }
            }
            
            UssdSessionManager.lastScreenFingerprint = fingerprint
            dismissUssd(root)
            completeSession(finalMsg, true)
            return
        }

        // B. Training Mode (Map Recorder) - CRITICAL: Record ONLY when screen appears and BEFORE sending
        if (UssdSessionManager.isTrainingMode) {
            // Drop event if we are already busy recording/sending the previous step
            if (fingerprint == UssdSessionManager.lastScreenFingerprint) return

            val nextInput = UssdSessionManager.peekNextStep()
            if (nextInput != null) {
                val currentStepNum = UssdSessionManager.getCurrentIndex() + 1
                Log.i("MapRecorder", "RECORDING Step $currentStepNum")
                
                val isBfStep = nextInput == UssdSessionManager.trainingPhoneNumber || nextInput == "0700000000"
                val isLast = UssdSessionManager.isLastStep()
                val selectionType = if (isBfStep) "CUSTOMER_NUMBER" else "AUTOMATIC"
                
                // 1. Capture and Save to Database in background (Non-Blocking)
                serviceScope.launch(Dispatchers.IO) {
                    saveTrainingStepSynchronously(
                        response = text,
                        selected = if (isBfStep) "BF" else nextInput,
                        type = selectionType,
                        variable = if (isBfStep) "BF" else "",
                        trainingVal = if (isBfStep) nextInput else "",
                        stepNumber = currentStepNum
                    )
                }
                
                // 2. Safety Mode: If it's the LAST step, record it but DO NOT send it.
                if (isLast) {
                    Log.i("MapRecorder", "SAFETY MODE: Final step captured. Tapping Cancel.")
                    delay(100) 
                    cancelUssd(root)
                    completeSession("Training Complete (Safety Mode)", true)
                } else {
                    // Regular step: Execute automation normally (Pro rhythm sync)
                    if (executeAutomaticReply(root, nextInput)) {
                        UssdSessionManager.consumeStep()
                        Log.i("MapRecorder", "Step ${UssdSessionManager.getCurrentIndex()} Sent Success.")
                    }
                }
            }
            return
        }

        // C. Map-Based Execution (Intelligent Auto-Pilot)
        val activeMap = UssdSessionManager.activeMap
        if (activeMap != null) {
            handleMapBasedExecution(root, text, fingerprint, activeMap)
            return
        }

        // D. Sequential Interaction (Standard/Template Mode)
        val now = System.currentTimeMillis()
        if (now - lastActionTime < DEBOUNCE_DELAY) return

        val nextInput = UssdSessionManager.peekNextStep()
        if (nextInput != null) {
            Log.i("AdvancedEngine", "STEP: Inputting '$nextInput' into menu...")
            
            if (executeAutomaticReply(root, nextInput)) {
                lastActionTime = System.currentTimeMillis()
                
                // Smart Trail Label Extraction
                val options = parseOptions(text)
                val label = options.find { it.first == nextInput }?.second ?: (if (nextInput.length >= 10) "Phone Number" else "Selection $nextInput")
                
                UssdSessionManager.addToTrail(UssdSessionManager.getCurrentIndex() + 1, nextInput, label, extractScreenInstruction(text))
                UssdSessionManager.consumeStep()
                UssdSessionManager.lastScreenFingerprint = fingerprint
                Log.d("AdvancedEngine", "STEP COMPLETE: Sequence advanced.")
            } else {
                Log.e("AdvancedEngine", "STEP FAILED: Could not interact with menu.")
            }
        } else {
            // E. Auto-Confirm Fallback
            if (text.lowercase().contains(Regex("confirm|accept|yes|reply with 1"))) {
                Log.i("AdvancedEngine", "AUTO-CONFIRM: Sending '1'")
                executeAutomaticReply(root, "1")
            }
        }
    }

    private suspend fun handleMapBasedExecution(root: AccessibilityNodeInfo, text: String, fingerprint: String, map: UssdMapWithSteps) {
        val currentIndex = UssdSessionManager.getCurrentIndex()
        val sortedSteps = map.steps.sortedBy { it.step.stepNumber }
        val mappedStep = sortedSteps.getOrNull(currentIndex) ?: run {
            Log.w("AdvancedEngine", "No mapped step for index $currentIndex. Falling back to template.")
            return
        }

        Log.i("AdvancedEngine", "MAP ENGINE: Interaction Step ${currentIndex + 1}")

        val optionsOnScreen = parseOptions(text)
        var labelForTrail: String
        var statusForTrail = "SUCCESS"
        
        val rawInputToUse = when (mappedStep.step.selectionType) {
            "CUSTOMER_NUMBER" -> {
                labelForTrail = "Phone Number"
                UssdSessionManager.resolvePlaceholders("BF")
            }
            "FIXED_VALUE" -> {
                labelForTrail = mappedStep.step.selectedText.ifEmpty { "Fixed Value" }
                UssdSessionManager.resolvePlaceholders(mappedStep.step.selectedOption)
            }
            else -> {
                // Intelligent Auto-Switch logic: Find label even if number moved
                val trainedOption = mappedStep.options.find { it.isSelected }
                if (trainedOption != null) {
                    val normalizedTrainedLabel = normalizeLabel(trainedOption.optionText)
                    
                    val matchingOptionOnScreen = optionsOnScreen.find { 
                        normalizeLabel(it.second) == normalizedTrainedLabel
                    }
                    
                    if (matchingOptionOnScreen != null) {
                        val currentNum = matchingOptionOnScreen.first
                        if (currentNum != trainedOption.optionNumber) {
                            labelForTrail = "Ex ${trainedOption.optionNumber} Found $currentNum: ${trainedOption.optionText}"
                            statusForTrail = "SWITCHED"
                            Log.i("AdvancedEngine", "AUTO-CORRECT: $labelForTrail")
                        } else {
                            labelForTrail = trainedOption.optionText
                        }
                        currentNum
                    } else {
                        // FIX: Abort and fail immediately on label mismatch to prevent dangerous incorrect choices
                        Log.w("AdvancedEngine", "LABEL MATCH FAILED: Option '${trainedOption.optionText}' not found on screen. Aborting transaction safely.")
                        UssdSessionManager.addToTrail(currentIndex + 1, "MISMATCH", "Mismatch Abort", extractScreenInstruction(text), "FAILED")
                        cancelUssd(root)
                        completeSession("Menu Mismatch: '${trainedOption.optionText}' not found", false)
                        return
                    }
                } else {
                    labelForTrail = mappedStep.step.selectedText.ifEmpty { "Selection ${mappedStep.step.selectedOption}" }
                    mappedStep.step.selectedOption
                }
            }
        }
        val inputToUse = UssdSessionManager.resolvePlaceholders(rawInputToUse)

        if (executeAutomaticReply(root, inputToUse)) {
            UssdSessionManager.addToTrail(currentIndex + 1, inputToUse, labelForTrail, extractScreenInstruction(text), statusForTrail)
            UssdSessionManager.consumeStep()
            UssdSessionManager.lastScreenFingerprint = fingerprint
            Log.d("AdvancedEngine", "MAP STEP COMPLETE: Sent $inputToUse")
        }
    }

    private suspend fun saveTrainingStepSynchronously(
        response: String, 
        selected: String, 
        type: String, 
        variable: String = "", 
        trainingVal: String = "",
        stepNumber: Int
    ) {
        val mapId = UssdSessionManager.trainingMapId
        if (mapId == -1) return

        withContext(Dispatchers.IO) {
            try {
                val prefManager = PreferenceManager(this@UssdAccessibilityService)
                val isHidden = prefManager.isDataHidden()
                
                // Enhanced Masking
                val safeResponse = if (isHidden) {
                    response.replace(Regex("(?i)(balance|is|ksh|balance\\s?is|amount|ksh\\.)\\s?[0-9,.]+"), "$1 ****")
                } else response

                // Check if this step was already recorded to prevent duplicates
                val existingStep = repository.ussdMapDao.getStepByNumber(mapId, stepNumber)

                val options = parseOptions(safeResponse)
                
                // Resolve descriptive text for the badge (e.g. "GIFT SMS" instead of "8")
                val selectedOptionText = if (type == "CUSTOMER_NUMBER") {
                    "Phone Number"
                } else {
                    options.find { it.first == selected }?.second ?: selected
                }

                val step = UssdMapStep(
                    id = existingStep?.id ?: 0, // USE EXISTING ID TO UPDATE
                    mapId = mapId,
                    stepNumber = stepNumber,
                    ussdRequest = "", 
                    fullResponse = safeResponse,
                    selectedOption = selected,
                    selectedText = selectedOptionText, 
                    selectedBy = "MAP",
                    selectionType = type,
                    inputType = if (selected.matches(Regex("\\d+"))) "MENU_OPTION" else "TEXT_INPUT",
                    variableName = variable,
                    trainingValue = trainingVal
                )
                
                val stepId = repository.ussdMapDao.insertStep(step)
                
                // Sync options
                repository.ussdMapDao.deleteOptionsForStep(stepId.toInt())
                options.forEach { (num, label) ->
                    repository.ussdMapDao.insertOption(UssdMapOption(
                        stepId = stepId.toInt(),
                        optionNumber = num,
                        optionText = label,
                        isSelected = num == selected
                    ))
                }
                Log.i("MapRecorder", "DATABASE SAVE SUCCESS: Step $stepNumber ${if (existingStep != null) "UPDATED" else "RECORDED"}.")
            } catch (e: Exception) {
                Log.e("MapRecorder", "DATABASE SAVE FAILED", e)
            }
        }
    }

    private fun parseOptions(text: String): List<Pair<String, String>> {
        val lines = text.split("\n")
        val options = mutableListOf<Pair<String, String>>()
        // Pro Regex: Handles 8., 8:, 8), [8], 8- etc.
        val optionRegex = Regex("^[\\[({]?(\\d+)[.:)]?\\s*(.*)$")
        
        lines.forEach { line ->
            val match = optionRegex.find(line.trim())
            if (match != null) {
                options.add(match.groupValues[1] to match.groupValues[2])
            }
        }
        return options
    }

    /**
     * Normalizes text for comparison (removes dots, dashes, and extra spaces)
     */
    private fun normalizeLabel(text: String): String {
        return text.lowercase()
            .replace(Regex("[^a-z0-9]"), "") // Remove all symbols
            .replace("\\s+".toRegex(), "")   // Remove all spaces
            .trim()
    }

    private fun findAndClickByResourceIdOrLabel(node: AccessibilityNodeInfo, labels: List<String>, depth: Int = 0): Boolean {
        if (depth > 25) return false
        try {
            val viewId = try { node.viewIdResourceName?.lowercase() ?: "" } catch (e: Exception) { "" }
            if (viewId.contains("button1") || viewId.contains("button_send") || viewId.contains("send_button") || viewId.contains("btn_send") || viewId.contains("ok_button")) {
                val clickable = try { node.isClickable } catch (e: Exception) { false }
                if (clickable) {
                    try {
                        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    } catch (e: Exception) {
                        Log.e("AdvancedEngine", "ID Click failed", e)
                    }
                }
            }
            val count = try { node.childCount } catch (e: Exception) { 0 }
            for (i in 0 until count) {
                val child = try { node.getChild(i) } catch (e: Exception) { null } ?: continue
                if (findAndClickByResourceIdOrLabel(child, labels, depth + 1)) return true
            }
        } catch (e: Exception) {
            Log.e("AdvancedEngine", "findAndClickByResourceIdOrLabel error", e)
        }
        return false
    }

    private suspend fun executeAutomaticReply(root: AccessibilityNodeInfo, text: String): Boolean {
        // 0. Force Keyboard Hidden before interaction
        val prefManager = PreferenceManager(this)
        if (prefManager.isKeyboardSuppressionEnabled()) {
            hideKeyboard()
        }

        // 1. Find and Focus Input
        val inputNode = findInputNode(root)
        if (inputNode != null) {
            try {
                // Focus and type instantly
                inputNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                
                val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
                var success = inputNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                
                if (!success) {
                    inputNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    success = inputNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                }

                if (success) {
                    val selectionArgs = Bundle().apply {
                        putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, text.length)
                        putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, text.length)
                    }
                    inputNode.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selectionArgs)
                    
                    // Click Send instantly
                    val sendButtons = listOf("Send", "OK", "Reply", "Submit", "Accept", "Next", "Continue")
                    var clicked = findAndClickByResourceIdOrLabel(root, sendButtons) || findAndClick(root, sendButtons) || clickFirstActionButton(root)
                    
                    var retries = 0
                    while (!clicked && retries < 3) {
                        delay(5)
                        clicked = findAndClickByResourceIdOrLabel(root, sendButtons) || findAndClick(root, sendButtons) || clickFirstActionButton(root)
                        retries++
                    }

                    if (clicked) {
                        hideKeyboard() 
                    }
                    return clicked
                }
            } catch (e: Exception) {
                Log.e("AdvancedEngine", "Action execution failed", e)
            }
        } else {
            // Direct Choice Menu
            val directLabels = listOf(text, "Send", "OK")
            var directClicked = findAndClick(root, directLabels)
            var directRetries = 0
            while (!directClicked && directRetries < 2) {
                delay(5)
                directClicked = findAndClick(root, directLabels)
                directRetries++
            }
            return directClicked
        }
        return false
    }

    private var lastWindowsCheckTime = 0L

    private fun findUssdRoot(event: AccessibilityEvent? = null): AccessibilityNodeInfo? {
        try {
            val activeRoot = try { rootInActiveWindow } catch (e: Throwable) { null }
            if (activeRoot != null && isUssdWindow(activeRoot)) return activeRoot
            
            val eventSource = try { event?.source } catch (e: Throwable) { null }
            if (eventSource != null && isUssdWindow(eventSource)) return eventSource
            
            val now = System.currentTimeMillis()
            if (now - lastWindowsCheckTime > 800) {
                lastWindowsCheckTime = now
                val windowRoots = try { windows.mapNotNull { try { it.root } catch (e: Throwable) { null } } } catch (e: Throwable) { emptyList() }
                for (winRoot in windowRoots) {
                    if (isUssdWindow(winRoot)) return winRoot
                }
            }
        } catch (e: Throwable) {
            Log.e("AdvancedEngine", "Error finding USSD root", e)
        }
        return null
    }

    private fun isUssdWindow(node: AccessibilityNodeInfo): Boolean {
        try {
            val pkg = try { node.packageName?.toString() ?: "" } catch (e: Exception) { "" }
            if (pkg == "com.example.autodial") return false
            
            // Exclude social media, games, or non-USSD media apps
            if (pkg.contains("facebook") || pkg.contains("tiktok") || pkg.contains("musically") || pkg.contains("instagram") || pkg.contains("whatsapp")) return false
            
            // Whitelist all system and OEM telephony/STK packages across all Android vendors
            val isKnownTelephonyPkg = pkg == "com.android.phone" || 
                              pkg == "com.android.server.telecom" || 
                              pkg == "com.android.systemui" ||
                              pkg == "android" ||
                              pkg.contains("telephony") ||
                              pkg.contains("telecom") ||
                              pkg.contains("settings") ||
                              pkg.contains("stk") ||
                              pkg.contains("incallui") ||
                              pkg.contains("dialer") ||
                              pkg.contains("phone") ||
                              pkg.contains("sec.phone") ||
                              pkg.contains("transsion") ||
                              pkg.contains("mediatek") ||
                              pkg.contains("qualcomm") ||
                              pkg.contains("shannon") ||
                              pkg.contains("hios") ||
                              pkg.contains("xos") ||
                              pkg.contains("miui") ||
                              pkg.contains("xiaomi") ||
                              pkg.contains("samsung") ||
                              pkg.contains("coloros") ||
                              pkg.contains("oppo") ||
                              pkg.contains("vivo") ||
                              pkg.contains("realme") ||
                              pkg.contains("oneplus") ||
                              pkg.contains("huawei") ||
                              pkg.contains("honor") ||
                              pkg.contains("spreadtrum") ||
                              pkg.contains("sprd") ||
                              pkg.contains("unisoc") ||
                              pkg.contains("zte") ||
                              pkg.contains("motorola") ||
                              pkg.contains("moto")
            
            val hasUssdIndicators = hasButton(node, "Send") || 
                                   hasButton(node, "Cancel") || 
                                   hasButton(node, "OK") || 
                                   hasButton(node, "Close") || 
                                   hasButton(node, "Dismiss") || 
                                   hasButton(node, "Reply") ||
                                   hasButton(node, "Submit") ||
                                   hasInputField(node)

            // Accept if it has USSD indicators and is either a known telephony package OR has an input field
            return hasUssdIndicators && (isKnownTelephonyPkg || hasInputField(node))
        } catch (e: Throwable) {
            Log.e("AdvancedEngine", "isUssdWindow error", e)
            return false
        }
    }

    // --- Helper Methods ---

    private fun getAllText(node: AccessibilityNodeInfo): String {
        val sb = StringBuilder()
        fun traverse(n: AccessibilityNodeInfo, depth: Int) {
            if (depth > 25) return // Recursion guard
            try {
                val t = try { n.text?.toString()?.trim() } catch (e: Throwable) { null }
                if (!t.isNullOrBlank()) sb.append(t).append("\n")
                val count = try { n.childCount } catch (e: Throwable) { 0 }
                for (i in 0 until count) {
                    val child = try { n.getChild(i) } catch (e: Throwable) { null }
                    if (child != null) traverse(child, depth + 1)
                }
            } catch (e: Throwable) {
                // Safeguard against recycled node exception
            }
        }
        try {
            traverse(node, 0)
        } catch (e: Throwable) {
            Log.e("AdvancedEngine", "getAllText error", e)
        }
        return sb.toString().trim()
    }

    private fun findInputNode(node: AccessibilityNodeInfo, depth: Int = 0): AccessibilityNodeInfo? {
        if (depth > 25) return null // Recursion guard
        try {
            val className = try { node.className?.toString() } catch (e: Throwable) { null }
            val isEditable = try { node.isEditable } catch (e: Throwable) { false }
            if (className?.contains("EditText") == true || isEditable) return node
            val count = try { node.childCount } catch (e: Throwable) { 0 }
            for (i in 0 until count) {
                val child = try { node.getChild(i) } catch (e: Throwable) { null } ?: continue
                findInputNode(child, depth + 1)?.let { return it }
            }
        } catch (e: Throwable) {
            Log.e("AdvancedEngine", "findInputNode error", e)
        }
        return null
    }

    private fun hasInputField(node: AccessibilityNodeInfo): Boolean = findInputNode(node) != null

    private fun findAndClick(node: AccessibilityNodeInfo, labels: List<String>, depth: Int = 0): Boolean {
        if (depth > 25) return false // Recursion guard
        try {
            val t = try { node.text?.toString() ?: "" } catch (e: Throwable) { "" }
            val d = try { node.contentDescription?.toString() ?: "" } catch (e: Throwable) { "" }
            
            if ((t.isNotEmpty() || d.isNotEmpty()) && labels.any { t.contains(it, true) || d.contains(it, true) }) {
                var curr: AccessibilityNodeInfo? = node
                while (curr != null) {
                    val clickable = try { curr.isClickable } catch (e: Throwable) { false }
                    if (clickable) {
                        try {
                            return curr.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        } catch (e: Throwable) {
                            Log.e("AdvancedEngine", "Click action failed", e)
                            return false
                        }
                    }
                    curr = try { curr.parent } catch (e: Throwable) { null }
                }
            }
            val count = try { node.childCount } catch (e: Throwable) { 0 }
            for (i in 0 until count) {
                val child = try { node.getChild(i) } catch (e: Throwable) { null } ?: continue
                if (findAndClick(child, labels, depth + 1)) return true
            }
        } catch (e: Throwable) {
            Log.e("AdvancedEngine", "findAndClick error", e)
        }
        return false
    }

    private fun clickFirstActionButton(node: AccessibilityNodeInfo, depth: Int = 0): Boolean {
        if (depth > 25) return false // Recursion guard
        try {
            val className = try { node.className?.toString() ?: "" } catch (e: Throwable) { "" }
            val clickable = try { node.isClickable } catch (e: Throwable) { false }
            if (className.contains("Button") && clickable) {
                val text = try { node.text?.toString()?.lowercase() ?: "" } catch (e: Throwable) { "" }
                if (!text.contains("cancel") && !text.contains("back")) {
                    return try {
                        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    } catch (e: Throwable) {
                        false
                    }
                }
            }
            val count = try { node.childCount } catch (e: Throwable) { 0 }
            for (i in 0 until count) {
                val child = try { node.getChild(i) } catch (e: Throwable) { null } ?: continue
                if (clickFirstActionButton(child, depth + 1)) return true
            }
        } catch (e: Throwable) {
            Log.e("AdvancedEngine", "clickFirstActionButton error", e)
        }
        return false
    }

    private fun hasButton(node: AccessibilityNodeInfo, text: String, depth: Int = 0): Boolean {
        if (depth > 25) return false // Recursion guard
        try {
            val nodeText = try { node.text?.toString() ?: "" } catch (e: Throwable) { "" }
            val nodeDesc = try { node.contentDescription?.toString() ?: "" } catch (e: Throwable) { "" }
            val clickable = try { node.isClickable } catch (e: Throwable) { false }
            val className = try { node.className?.toString() ?: "" } catch (e: Throwable) { "" }
            
            if ((nodeText.contains(text, true) || nodeDesc.contains(text, true)) && 
                (clickable || className.contains("Button", true))) {
                return true
            }
            
            val count = try { node.childCount } catch (e: Throwable) { 0 }
            for (i in 0 until count) {
                val child = try { node.getChild(i) } catch (e: Throwable) { null } ?: continue
                if (hasButton(child, text, depth + 1)) return true
            }
        } catch (e: Throwable) {
            Log.e("AdvancedEngine", "hasButton error", e)
        }
        return false
    }

    private fun isTerminalResponse(text: String, root: AccessibilityNodeInfo): Boolean {
        // 0. Sequential Safety: If there are still steps defining the USSD sequence, this can't be terminal
        if (UssdSessionManager.peekNextStep() != null) return false

        // 1. If there is an input field, it's definitely NOT a terminal screen
        if (hasInputField(root)) return false

        // 2. Proactive "OK" Detection: If no input field exists, check for any dismissal button
        val dismissButtons = listOf("OK", "Close", "Dismiss", "Exit", "Accept", "Confirm", "Done")
        if (dismissButtons.any { hasButton(root, it) }) return true

        val lower = text.lowercase()
        val terminalPhrases = listOf(
            "successful", "completed", "done", "thank you", "thankyou", "confirmed", "success",
            "failed", "error", "insufficient", "sorry", "top-up", "invalid",
            "received", "balance is", "credited", "bought", "purchased", "sent to",
            "recommendation submitted", "successfully purchased", "kindly wait as we submit",
            "request processed", "will be processed", "wait for confirmation", "processed successfully",
            "already recommended", "already been recommended", "for"
        )
        return terminalPhrases.any { lower.contains(it) } || EngageBotTrigger.isAlreadyRecommendedToday(text)
    }

    private fun extractScreenInstruction(content: String): String = content.split("\n").firstOrNull { it.isNotBlank() }?.take(40) ?: "Menu"

    private fun completeSession(msg: String, success: Boolean) {
        if (com.example.autodial.util.UssdSessionManager.isTokenPurchase) {
            com.example.autodial.util.TokenAwardHelper.processResponse(this@UssdAccessibilityService, msg, com.example.autodial.util.UssdSessionManager.tokenAmount)
        }
        serviceScope.launch(Dispatchers.IO) {
            var fallbackInitiated = false
            try {
                val prefManager = PreferenceManager(this@UssdAccessibilityService)
                val transId = UssdSessionManager.activeTransactionId
                repository.getTransactionById(transId)?.let { trans ->
                    var commission = trans.commissionEarned
                    var safPrice = trans.safPrice
                    var shouldDeductToken = false
                    
                    val isAutomated = trans.senderName != "MANUAL RUN" && trans.senderName != "QUICK DIAL"
                    val isAlternativeOrFallback = trans.isFallbackExecuted || trans.isSwitched || trans.originalOfferId != null
                    val isNegativeMsg = msg.lowercase().let { 
                        it.contains("insufficient") || it.contains("sorry") || it.contains("invalid") || it.contains("failed") || EngageBotTrigger.isAlreadyRecommendedToday(it) 
                    }
                    val alreadySucceeded = trans.status == Transaction.STATUS_SUCCESS || trans.isVerified
                    val effectiveSuccess = alreadySucceeded || success || (isAlternativeOrFallback && !isNegativeMsg)

                    if (effectiveSuccess && trans.offerId != 0) {
                        val offer = repository.allOffers.first().find { it.id == trans.offerId }
                        commission = offer?.commission ?: 0.0
                        safPrice = offer?.safPrice ?: 0.0
                        
                        // TOKEN DEDUCTION FIX: Deduct token for successful accessibility USSD
                        if (isAutomated && !trans.tokenDeducted) {
                            if (prefManager.useToken()) {
                                shouldDeductToken = true
                            }
                        }
                    } else if (!effectiveSuccess && isAutomated && !trans.isFallbackExecuted && trans.offerId != 0) {
                        // TARGETED FALLBACK LOGIC: Fallback/Alternative offer runs ONLY when initial offer was "Already Recommended Today"
                        val isAlreadyRec = com.example.autodial.util.EngageBotTrigger.isAlreadyRecommendedToday(msg)
                        val offer = repository.allOffers.first().find { it.id == trans.offerId }
                        
                        if (isAlreadyRec && offer?.isFallbackEnabled == true && offer.fallbackOfferId != null) {
                            val fallbackOffer = repository.allOffers.first().find { it.id == offer.fallbackOfferId }
                            if (fallbackOffer != null) {
                                repository.updateTransaction(trans.copy(
                                    offerId = fallbackOffer.id,
                                    offerName = fallbackOffer.name,
                                    status = Transaction.STATUS_WAITING,
                                    isFallbackExecuted = true,
                                    simUsed = fallbackOffer.simSelection,
                                    finalResponse = msg,
                                    notes = (trans.notes + "\n[FALLBACK] Initial offer '${offer.name}' already recommended today ($msg). Retrying with alternative '${fallbackOffer.name}'.").trim()
                                ))
                                fallbackInitiated = true
                                Log.i("AdvancedEngine", "Fallback initiated for transaction ${trans.id}")
                            }
                        }
                    }
                    
                    if (!fallbackInitiated) {
                        val newTrail = UssdSessionManager.getTrail()
                        
                        val oldResponse = trans.finalResponse
                        val fallbackNote = if (trans.isFallbackExecuted) {
                            "\n[FALLBACK EXECUTION] Final Status: ${if (effectiveSuccess) Transaction.STATUS_SUCCESS else Transaction.STATUS_FAILED} | Last Response: $msg"
                        } else if (trans.isSwitched || trans.originalOfferId != null) {
                            "\n[ALTERNATIVE EXECUTION] Final Status: ${if (effectiveSuccess) Transaction.STATUS_SUCCESS else Transaction.STATUS_FAILED} | Last Response: $msg"
                        } else ""

                        val newNotes = if (oldResponse.isNotBlank() && oldResponse != msg) {
                            (trans.notes + "\n[HISTORY] Initial Response: $oldResponse" + fallbackNote).trim()
                        } else {
                            (trans.notes + fallbackNote).trim()
                        }

                        val updatedTrans = trans.copy(
                            status = if (effectiveSuccess) Transaction.STATUS_SUCCESS else Transaction.STATUS_FAILED,
                            finalResponse = if (alreadySucceeded && isNegativeMsg) trans.finalResponse else msg,
                            notes = newNotes,
                            commissionEarned = commission,
                            safPrice = safPrice,
                            isVerified = trans.isVerified,
                            verificationSms = trans.verificationSms,
                            tokenDeducted = trans.tokenDeducted || shouldDeductToken,
                            executionTrail = newTrail
                        )
                        repository.updateTransaction(updatedTrans)
                        
                        // CONNECT Reporting
                        if (updatedTrans.senderName.startsWith("RELAY:")) {
                            com.example.autodial.util.RelayClient.reportResult(updatedTrans.transactionCode, updatedTrans.status, msg)
                        }

                        // Engage Bot: specific "already recommended today" OR result of Engage-spawned offers
                        val isEngageTxn = updatedTrans.senderName.startsWith(com.example.autodial.util.EngageBotManager.SENDER_PREFIX) ||
                            updatedTrans.rawMessage.contains(com.example.autodial.util.EngageBotManager.SESSION_MARKER)

                        val engageStarted = if (!success && !isEngageTxn) {
                            com.example.autodial.util.EngageBotManager.tryStartFromRecommendationFailure(
                                this@UssdAccessibilityService, updatedTrans, msg
                            )
                        } else false

                        val engageHandled = if (!engageStarted) {
                            com.example.autodial.util.EngageBotManager.onOfferExecutionResult(
                                this@UssdAccessibilityService, updatedTrans, success, msg
                            )
                        } else true

                        // Trigger Auto-Notification (skip when Engage Bot took over)
                        if (!engageStarted && !engageHandled && !updatedTrans.isNotified) {
                            val category = if (success) NotificationTemplate.CAT_COMPLETE else NotificationTemplate.CAT_FAILED
                            repository.getTemplateByCategory(category)?.let { template ->
                                com.example.autodial.util.SmsSender.sendAutoNotification(this@UssdAccessibilityService, updatedTrans, template)
                                repository.updateTransaction(updatedTrans.copy(isNotified = true))
                            }
                        }
                    }
                }
            } catch (e: Throwable) {
                Log.e("AdvancedEngine", "Error updating transaction during session completion", e)
            }

            withContext(Dispatchers.Main) {
                try {
                    showKeyboard()
                    UssdSessionManager.endSession(success)
                    TransactionQueueManager.markAsDismissed(UssdSessionManager.lastCustomerNumber) 
                    TransactionQueueManager.resetProcessingLock()
                    
                    // Stop foreground service if no longer processing
                    if (!TransactionQueueManager.isProcessing()) {
                        TransactionQueueManager.stopForeground(this@UssdAccessibilityService)
                    }
                    
                    // Give telecom framework and window manager time to finish window dismissal
                    delay(1200)
                    TransactionQueueManager.processNext(this@UssdAccessibilityService)
                } catch (e: Throwable) {
                    Log.e("AdvancedEngine", "Error during main thread session cleanup", e)
                }
            }
        }
    }

    private fun dismissUssd(root: AccessibilityNodeInfo) {
        try {
            val dismissLabels = listOf("OK", "Dismiss", "Close", "Exit", "Accept", "Confirm", "Back", "End", "Done")
            if (findAndClick(root, dismissLabels)) {
                TransactionQueueManager.markAsDismissed()
            } else {
                clickFirstActionButton(root)
                TransactionQueueManager.markAsDismissed()
            }
        } catch (e: Exception) {
            Log.e("AdvancedEngine", "dismissUssd error", e)
            TransactionQueueManager.markAsDismissed()
        }
    }

    private fun cancelUssd(root: AccessibilityNodeInfo) {
        try {
            val cancelLabels = listOf("Cancel", "Back", "Exit", "Close", "Dismiss", "OK", "Reject")
            if (findAndClick(root, cancelLabels)) {
                TransactionQueueManager.markAsDismissed()
            } else {
                TransactionQueueManager.markAsDismissed()
            }
        } catch (e: Exception) {
            Log.e("AdvancedEngine", "cancelUssd error", e)
            TransactionQueueManager.markAsDismissed()
        }
    }

    private fun extractPopUpMessage(node: AccessibilityNodeInfo): String {
        val sb = StringBuilder()
        fun traverse(n: AccessibilityNodeInfo, depth: Int) {
            if (depth > 25) return // Recursion guard
            try {
                val t = try { n.text?.toString()?.trim() } catch (e: Exception) { null }
                val className = try { n.className?.toString() ?: "" } catch (e: Exception) { "" }
                if (!t.isNullOrBlank() && !className.contains("Button")) {
                    sb.append(t).append("\n")
                }
                val count = try { n.childCount } catch (e: Exception) { 0 }
                for (i in 0 until count) {
                    val child = try { n.getChild(i) } catch (e: Exception) { null }
                    if (child != null) traverse(child, depth + 1)
                }
            } catch (e: Exception) {
                // Safeguard against recycled node exception
            }
        }
        try {
            traverse(node, 0)
        } catch (e: Exception) {
            Log.e("AdvancedEngine", "extractPopUpMessage error", e)
        }
        return sb.toString().trim()
    }

    override fun onInterrupt() {
        Log.w("AdvancedEngine", "Accessibility service interrupted.")
        showKeyboard()
        instance = null
        UssdSessionManager.endSession(false)
        TransactionQueueManager.resetProcessingLock()
    }
}
