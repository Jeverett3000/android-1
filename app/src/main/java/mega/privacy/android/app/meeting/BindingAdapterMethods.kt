package mega.privacy.android.app.meeting

import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.databinding.BindingAdapter
import mega.privacy.android.app.R

/**
 * Binding Adapter method for Meeting Participant Bottom Sheet page
 */
object BindingAdapterMethods {

    /**
     * Determine if the item should show moderator icon on the participant item
     */
    @JvmStatic
    @BindingAdapter("android:showModeratorIcon")
    fun showModeratorIcon(view: TextView, moderator: Boolean) {
        if (moderator) {
            view.setCompoundDrawablesWithIntrinsicBounds(
                null,
                null,
                ContextCompat.getDrawable(view.context, R.drawable.ic_moderator),
                null
            )
        } else {
            view.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null)
        }
    }

    /**
     * Determine if the item should visible
     */
    @JvmStatic
    @BindingAdapter("android:show")
    fun isVisible(view: View, show: Boolean) {
        view.visibility = if (show) View.VISIBLE else View.GONE
    }
}