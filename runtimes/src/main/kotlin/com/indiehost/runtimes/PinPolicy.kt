package com.indiehost.runtimes

/**
 * Pure pin-request rules shared by the pin activity and the pinned-shortcut list.
 *
 * Keeping these decisions in one testable object means a request can never be
 * accepted just because an Intent arrived on an exported component: the system
 * request must still be valid, of the right type and from Butterscotch, and a
 * listed shortcut must still be enabled and pinned.
 */
data class PinCandidate(
    val packageName: String?,
    val shortcutId: String?,
    val isValid: Boolean = true,
    val requestType: Int = REQUEST_TYPE_SHORTCUT,
    val isEnabled: Boolean = true,
    val isPinned: Boolean = true,
) {
    val isGameShortcut: Boolean
        get() = packageName == GameMakerRuntimeAdapter.BUTTERSCOTCH_PACKAGE &&
            shortcutId?.startsWith(ButterscotchShortcuts.GAME_SHORTCUT_PREFIX) == true

    companion object {
        /** Value of LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT. */
        const val REQUEST_TYPE_SHORTCUT = 1
    }
}

object PinPolicy {

    /** True only when we should call accept() on this system request. */
    fun accept(request: PinCandidate): Boolean =
        request.isValid &&
            request.requestType == PinCandidate.REQUEST_TYPE_SHORTCUT &&
            request.isGameShortcut

    /** True only when a shortcut should be shown in the library. */
    fun visible(shortcut: PinCandidate): Boolean =
        shortcut.isGameShortcut && shortcut.isEnabled && shortcut.isPinned
}
