package com.example.ffdiamond.onboarding

import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.example.ffdiamond.R
import com.example.ffdiamond.databinding.ViewAccessCardBinding

/**
 * Renders one permission card. Called again on every onResume, so it has to fully describe the
 * card's state rather than toggling bits of it.
 *
 * @param actionLabel null hides the CTA, which is how a card that needs nothing further is shown.
 */
fun ViewAccessCardBinding.render(
    @DrawableRes icon: Int,
    @StringRes title: Int,
    @StringRes body: Int,
    granted: Boolean,
    showStatus: Boolean = true,
    @StringRes grantedLabel: Int = R.string.granted,
    @StringRes pendingLabel: Int = R.string.not_granted,
    @StringRes actionLabel: Int? = null,
    onAction: (() -> Unit)? = null
) {
    val context = root.context
    cardIcon.setImageResource(icon)
    cardTitle.setText(title)
    cardBody.setText(body)

    cardStatus.visibility = if (showStatus) View.VISIBLE else View.GONE
    if (showStatus) {
        cardStatus.setText(if (granted) grantedLabel else pendingLabel)
        cardStatus.setTextColor(
            ContextCompat.getColor(
                context,
                if (granted) R.color.status_granted else R.color.status_pending
            )
        )
        cardStatus.setBackgroundResource(
            if (granted) R.drawable.bg_status_chip_granted else R.drawable.bg_status_chip_pending
        )
    }

    if (actionLabel == null || onAction == null) {
        cardAction.visibility = View.GONE
        cardAction.setOnClickListener(null)
    } else {
        cardAction.visibility = View.VISIBLE
        cardAction.setText(actionLabel)
        cardAction.setOnClickListener { onAction() }
    }
}
