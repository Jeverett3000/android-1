package mega.privacy.android.app.data.model

import nz.mega.sdk.MegaContactRequest
import nz.mega.sdk.MegaEvent
import nz.mega.sdk.MegaGlobalListenerInterface
import nz.mega.sdk.MegaNode
import nz.mega.sdk.MegaUser
import nz.mega.sdk.MegaUserAlert

/**
 * Global update events corresponding to [MegaGlobalListenerInterface] callbacks
 */
sealed class GlobalUpdate {

    /**
     * On users update
     *
     * @property users
     */
    data class OnUsersUpdate(val users: java.util.ArrayList<MegaUser>?) : GlobalUpdate()

    /**
     * On user alerts update
     *
     * @property userAlerts
     */
    data class OnUserAlertsUpdate(val userAlerts: java.util.ArrayList<MegaUserAlert>?) :
        GlobalUpdate()

    /**
     * On nodes update
     *
     * @property nodeList
     */
    data class OnNodesUpdate(val nodeList: java.util.ArrayList<MegaNode>?) : GlobalUpdate()

    object OnReloadNeeded : GlobalUpdate()

    object OnAccountUpdate : GlobalUpdate()

    /**
     * On contact requests update
     *
     * @property requests
     */
    data class OnContactRequestsUpdate(val requests: java.util.ArrayList<MegaContactRequest>?) :
        GlobalUpdate()

    /**
     * On event
     *
     * @property event
     */
    data class OnEvent(val event: MegaEvent?) : GlobalUpdate()
}
