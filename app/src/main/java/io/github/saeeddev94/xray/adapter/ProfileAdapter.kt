package io.github.saeeddev94.xray.adapter

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import io.github.saeeddev94.xray.R
import io.github.saeeddev94.xray.Settings
import io.github.saeeddev94.xray.database.XrayDatabase
import io.github.saeeddev94.xray.dto.ProfileList
import io.github.saeeddev94.xray.helper.ProfileTouchHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ProfileAdapter(
    private var context: Context,
    private var profiles: ArrayList<ProfileList>,
    private var callback: ProfileClickListener,
) : RecyclerView.Adapter<ProfileAdapter.ViewHolder>(), ProfileTouchHelper.ProfileTouchCallback {

    override fun onCreateViewHolder(container: ViewGroup, type: Int): ViewHolder {
        val linearLayout = LinearLayout(context)
        val item: View = LayoutInflater.from(context).inflate(R.layout.item_recycler_main, linearLayout, false)
        return ViewHolder(item)
    }

    override fun getItemCount(): Int {
        return profiles.size
    }

    override fun onBindViewHolder(holder: ViewHolder, index: Int) {
        val profile = profiles[index]
        profile.index = index
        val color = if (Settings.selectedProfile == profile.id) R.color.primaryColor else R.color.btnColor
        holder.activeIndicator.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, color))
        holder.profileName.text = profile.name
        holder.profileCard.setOnClickListener {
            callback.profileSelect(index, profile)
        }
        holder.profileEdit.setOnClickListener {
            callback.profileEdit(index, profile)
        }
        holder.profileDelete.setOnClickListener {
            callback.profileDelete(index, profile)
        }
    }

    override fun onItemMoved(fromPosition: Int, toPosition: Int): Boolean {
        profiles.add(toPosition, profiles.removeAt(fromPosition))
        notifyItemMoved(fromPosition, toPosition)
        if (toPosition > fromPosition) {
            notifyItemRangeChanged(fromPosition, toPosition - fromPosition + 1)
        } else {
            notifyItemRangeChanged(toPosition, fromPosition - toPosition + 1)
        }
        return true
    }

    override fun onItemMoveCompleted(startPosition: Int, endPosition: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            val id = profiles[endPosition].id
            XrayDatabase.profileDao.updateIndex(endPosition, id)
            if (startPosition > endPosition) {
                XrayDatabase.profileDao.fixMoveUpIndex(endPosition, startPosition, id)
            } else {
                XrayDatabase.profileDao.fixMoveDownIndex(startPosition, endPosition, id)
            }
        }
    }

    class ViewHolder(item: View) : RecyclerView.ViewHolder(item) {
        var activeIndicator: LinearLayout = item.findViewById(R.id.activeIndicator)
        var profileCard: CardView = item.findViewById(R.id.profileCard)
        var profileName: TextView = item.findViewById(R.id.profileName)
        var profileEdit: LinearLayout = item.findViewById(R.id.profileEdit)
        var profileDelete: LinearLayout = item.findViewById(R.id.profileDelete)
    }

    interface ProfileClickListener {
        fun profileSelect(index: Int, profile: ProfileList)
        fun profileEdit(index: Int, profile: ProfileList)
        fun profileDelete(index: Int, profile: ProfileList)
    }
}
