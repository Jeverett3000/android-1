package mega.privacy.android.app.meeting.fragments

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.android.synthetic.main.in_meeting_fragment.*
import kotlinx.android.synthetic.main.in_meeting_fragment.view.*
import mega.privacy.android.app.BaseActivity
import mega.privacy.android.app.MegaApplication
import mega.privacy.android.app.R
import mega.privacy.android.app.databinding.InMeetingFragmentBinding
import mega.privacy.android.app.lollipop.AddContactActivityLollipop
import mega.privacy.android.app.lollipop.megachat.AppRTCAudioManager
import mega.privacy.android.app.lollipop.megachat.calls.OnDragTouchListener
import mega.privacy.android.app.meeting.AnimationTool.fadeInOut
import mega.privacy.android.app.meeting.AnimationTool.moveY
import mega.privacy.android.app.meeting.BottomFloatingPanelListener
import mega.privacy.android.app.meeting.BottomFloatingPanelViewHolder
import mega.privacy.android.app.meeting.activity.HEAD_PHONE_EVENT
import mega.privacy.android.app.meeting.adapter.Participant
import mega.privacy.android.app.utils.Constants
import mega.privacy.android.app.utils.LogUtil.logDebug
import mega.privacy.android.app.utils.StringResourcesUtils
import kotlin.random.Random

@AndroidEntryPoint
class InMeetingFragment : MeetingBaseFragment(), BottomFloatingPanelListener {

    private lateinit var gridViewMenuItem: MenuItem
    private lateinit var speakerViewMenuItem: MenuItem

    private lateinit var individualCallFragment: IndividualCallFragment
    private lateinit var floatingWindowFragment: IndividualCallFragment
    private lateinit var gridViewCallFragment: GridViewCallFragment
    private lateinit var speakerViewCallFragment: SpeakerViewCallFragment

    private var lastTouch: Long = 0

    private var previousY = -1f

    val inMeetingViewModel by viewModels<InMeetingViewModel>()

    lateinit var bottomFloatingPanelViewHolder: BottomFloatingPanelViewHolder

    /**
     * Should get the value from somewhere
     */
    private var isGuest = false
    private var isModerator = true

    private lateinit var binding: InMeetingFragmentBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        meetingActivity.hideActionBar()

        binding = InMeetingFragmentBinding.inflate(inflater)
        return binding.root
    }

    fun onPageClick() {
        // Prevent fast tapping.
        if (System.currentTimeMillis() - lastTouch < 500) return

        in_meeting_toolbar.fadeInOut(toTop = true)
        bottomFloatingPanelInOut()

        if (in_meeting_toolbar.visibility == View.VISIBLE) {
            meetingActivity.window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        } else {
            meetingActivity.window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }

        placeFloatingWindow()

        lastTouch = System.currentTimeMillis()
    }

    private fun placeFloatingWindow() {
        checkRelativePositionWithToolbar()
        checkRelativePositionWithBottomSheet()
    }

    private fun checkRelativePositionWithToolbar() {
        val isIntersect = (in_meeting_toolbar.bottom - self_feed_floating_window_container.y) > 0
        if (in_meeting_toolbar.visibility == View.VISIBLE && isIntersect) {
            self_feed_floating_window_container.moveY(in_meeting_toolbar.bottom.toFloat())
        }

        val isIntersectPreviously = (in_meeting_toolbar.bottom - previousY) > 0
        if (in_meeting_toolbar.visibility == View.GONE && isIntersectPreviously && previousY >= 0) {
            self_feed_floating_window_container.moveY(previousY)
        }
    }

    private fun checkRelativePositionWithBottomSheet() {
        val bottom =
            self_feed_floating_window_container.y + self_feed_floating_window_container.height
        val top = bottom_floating_panel.top
        val margin1 = bottom - top

        val isIntersect = margin1 > 0
        if (bottom_floating_panel.visibility == View.VISIBLE && isIntersect) {
            self_feed_floating_window_container.moveY(self_feed_floating_window_container.y - margin1)
        }

        val margin2 =
            previousY + self_feed_floating_window_container.height - bottom_floating_panel.top
        val isIntersectPreviously = margin2 > 0
        if (bottom_floating_panel.visibility == View.GONE && isIntersectPreviously && previousY >= 0) {
            self_feed_floating_window_container.moveY(previousY)
        }
    }

    private lateinit var dragTouchListener: OnDragTouchListener

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.setOnClickListener {
            onPageClick()
        }

        dragTouchListener = OnDragTouchListener(
            view.self_feed_floating_window_container,
            view,
            object : OnDragTouchListener.OnDragActionListener {

                override fun onDragStart(view: View?) {
                    if (in_meeting_toolbar.visibility == View.VISIBLE) {
                        dragTouchListener.setToolbarHeight(in_meeting_toolbar.bottom)
                        dragTouchListener.setBottomSheetHeight(bottom_floating_panel.top)
                    } else {
                        dragTouchListener.setToolbarHeight(0)
                        dragTouchListener.setBottomSheetHeight(0)
                    }
                }

                override fun onDragEnd(view: View) {
                    // Record the last Y of the floating window after dragging ended.
                    previousY = view.y
                }

            }
        )
        self_feed_floating_window_container.setOnTouchListener(dragTouchListener)

        individualCallFragment = IndividualCallFragment.newInstance(1, 2, false)
        gridViewCallFragment = GridViewCallFragment.newInstance()
        speakerViewCallFragment = SpeakerViewCallFragment.newInstance()

        //TODO test code start
        loadChildFragment(
            R.id.meeting_container,
            gridViewCallFragment,
            GridViewCallFragment.TAG
        )

        floatingWindowFragment = IndividualCallFragment.newInstance(1, 2, true)
        loadChildFragment(
            R.id.self_feed_floating_window_container,
            floatingWindowFragment,
            IndividualCallFragment.TAG
        )
        //TODO test code end

        meetingActivity.setSupportActionBar(view.in_meeting_toolbar)
        val actionBar = meetingActivity.supportActionBar ?: return
        actionBar.setHomeButtonEnabled(true)
        actionBar.setDisplayHomeAsUpEnabled(true)
        actionBar.setHomeAsUpIndicator(R.drawable.ic_arrow_back_white)
        setHasOptionsMenu(true)

        // decor.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        meetingActivity.window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or 0x00000010

        view.setOnApplyWindowInsetsListener { _, insets ->
            insets
        }

        initFloatingPanel()
        initShareViewModel()
    }

    private fun loadChildFragment(containerId: Int, fragment: Fragment, tag: String) {
        childFragmentManager.beginTransaction().replace(
            containerId,
            fragment,
            tag
        ).commit()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.in_meeting_fragment_menu, menu)
        speakerViewMenuItem = menu.findItem(R.id.speaker_view)
        gridViewMenuItem = menu.findItem(R.id.grid_view)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                findNavController().navigate(R.id.createMeetingFragment)
                true
            }
            R.id.swap_camera -> {
                //TODO test code start: add or remove last participants
                inMeetingViewModel.addParticipant(Random.nextBoolean())
//                logDebug("Swap camera.")
//                VideoCaptureUtils.swapCamera(ChatChangeVideoStreamListener(requireContext()))
                //TODO test code end: add or remove last participants
                true
            }
            R.id.grid_view -> {
                logDebug("Change to grid view.")
                gridViewMenuItem.isVisible = false
                speakerViewMenuItem.isVisible = true

                loadChildFragment(
                    R.id.meeting_container,
                    gridViewCallFragment,
                    GridViewCallFragment.TAG
                )
                true
            }
            R.id.speaker_view -> {
                logDebug("Change to speaker view.")
                gridViewMenuItem.isVisible = true
                speakerViewMenuItem.isVisible = false

                loadChildFragment(
                    R.id.meeting_container,
                    speakerViewCallFragment,
                    SpeakerViewCallFragment.TAG
                )
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * Init Share View Model
     */
    private fun initShareViewModel() {
        sharedModel.micLiveData.observe(viewLifecycleOwner) {
            updateAudio(it)
        }
        sharedModel.cameraLiveData.observe(viewLifecycleOwner) {
            updateVideo(it)
        }

        sharedModel.eventLiveData.observe(viewLifecycleOwner) {
            when (it) {
                HEAD_PHONE_EVENT -> {
                    bottomFloatingPanelViewHolder.onHeadphoneConnected(
                        MegaApplication.getInstance().audioManager.isWiredHeadsetConnected,
                        MegaApplication.getInstance().audioManager.isBluetoothConnected
                    )
                }
            }
        }
        /**
         * Will Change after Andy modify the permission structure
         */
        sharedModel.cameraPermissionCheck.observe(viewLifecycleOwner) {
            if (it) {
                checkMeetingPermissions(
                    arrayOf(Manifest.permission.CAMERA),
                ) { showSnackbar() }
            }
        }
        sharedModel.recordAudioPermissionCheck.observe(viewLifecycleOwner) {
            if (it) {
                checkMeetingPermissions(
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                ) { showSnackbar() }
            }
        }
    }

    fun showSnackbar() {
        val warningText =
            StringResourcesUtils.getString(R.string.meeting_required_permissions_warning)
        (activity as BaseActivity).showSnackbar(
            Constants.PERMISSIONS_TYPE,
            binding.root,
            warningText
        )
    }

    /**
     * Init Floating Panel, will move to `inMeetingFragment` later
     */
    private fun initFloatingPanel() {
        MegaApplication.getInstance().createRTCAudioManagerWhenCreatingMeeting()

        bottomFloatingPanelViewHolder =
            BottomFloatingPanelViewHolder(binding, this, isGuest, isModerator).apply {
                // Create a repository get the participants
                onHeadphoneConnected(
                    MegaApplication.getInstance().audioManager.isWiredHeadsetConnected,
                    MegaApplication.getInstance().audioManager.isBluetoothConnected
                )
            }
        bottomFloatingPanelViewHolder.collapse()

        /**
         * Observer the participant List
         */
        inMeetingViewModel.participants.observe(viewLifecycleOwner) { participants ->
            participants?.let {
                bottomFloatingPanelViewHolder.setParticipants(it)
            }
        }
    }

    private fun bottomFloatingPanelInOut() {
        bottom_floating_panel.fadeInOut()
    }

    /**
     * Change Mic State
     */
    override fun onChangeMicState(micOn: Boolean) {
        sharedModel.clickMic(!micOn)
    }

    private fun updateAudio(micOn: Boolean) {
        bottomFloatingPanelViewHolder.updateMicIcon(micOn)
    }

    /**
     * Change Cam State
     */
    override fun onChangeCamState(camOn: Boolean) {
        sharedModel.clickCamera(!camOn)
    }

    private fun updateVideo(camOn: Boolean) {
        bottomFloatingPanelViewHolder.updateCamIcon(camOn)
    }

    /**
     * Change Hold State
     */
    override fun onChangeHoldState(isHold: Boolean) {
        inMeetingViewModel.setCallOnHold(isHold)
    }

    /**
     * Change Speaker state
     */
    override fun onChangeAudioDevice(device: AppRTCAudioManager.AudioDevice) {}

    /**
     * Pop up dialog for end meeting for the user/guest
     *
     * Will show bottom sheet fragment for the moderator
     */
    override fun onEndMeeting() {
        if (isModerator) {
            val endMeetingBottomSheetDialogFragment =
                EndMeetingBottomSheetDialogFragment.newInstance()
            endMeetingBottomSheetDialogFragment.show(
                parentFragmentManager,
                endMeetingBottomSheetDialogFragment.tag
            )
        } else {
            askConfirmationEndMeetingForUser()
        }
    }

    /**
     * Dialog for confirming leave meeting action
     */
    private fun askConfirmationEndMeetingForUser() {
        MaterialAlertDialogBuilder(
            requireContext(),
            R.style.ThemeOverlay_Mega_MaterialAlertDialog
        ).apply {
            setMessage(getString(R.string.title_end_meeting))
            setPositiveButton(R.string.general_ok) { _, _ -> leaveMeeting() }
            setNegativeButton(R.string.general_cancel, null)
            show()
        }
    }

    private fun leaveMeeting() {
        inMeetingViewModel.leaveMeeting()
    }


    /**
     * Send share link
     */
    override fun onShareLink() {
        showShortToast("onShareLink")

        startActivity(Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, getShareLink())
            type = "text/plain"
        })
    }

    /**
     * Get the special link
     */
    private fun getShareLink(): String {
        return "This is the share link"
    }

    /**
     * Open invite participant page
     */
    override fun onInviteParticipants() {
        logDebug("chooseAddContactDialog")

        val inviteParticipantIntent =
            Intent(requireActivity(), AddContactActivityLollipop::class.java).apply {
                putExtra("contactType", Constants.CONTACT_TYPE_MEGA)
                putExtra("chat", true)
                putExtra("chatId", 123L)
                putExtra("aBtitle", getString(R.string.invite_participants))
            }
        startActivityForResult(
            inviteParticipantIntent, Constants.REQUEST_ADD_PARTICIPANTS
        )
    }


    /**
     * Show participant bottom sheet when user click the three dots on participant item
     */
    override fun onParticipantOption(participant: Participant) {
        val participantBottomSheet =
            MeetingParticipantBottomSheetDialogFragment.newInstance(
                isGuest,
                isModerator,
                participant
            )
        participantBottomSheet.show(parentFragmentManager, participantBottomSheet.tag)
    }

    private fun showShortToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}