package com.vox.sample.webrtc

import android.net.nsd.NsdServiceInfo
import android.view.ViewGroup
import androidx.databinding.ViewDataBinding
import androidx.recyclerview.widget.RecyclerView
import com.vox.sample.webrtc.BR.serviceInfo

/**
 * Created by Kadir Mert Ozcan on 22/05/2019.
 */
class StreamListAdapter(private val serviceInfoList: List<NsdServiceInfo>,
                        private val mainActivity: MainActivity):
        RecyclerView.Adapter<StreamListAdapter.StreamItemViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StreamItemViewHolder {
        val binding = bindingInflate(
                parent,
                R.layout.stream_list_item)
        return StreamItemViewHolder(binding)
    }

    override fun getItemCount(): Int {
        return serviceInfoList.size
    }

    override fun onBindViewHolder(holder: StreamItemViewHolder, position: Int) {
        holder.bind(serviceInfoList[position])
    }

    inner class StreamItemViewHolder(
            private val binding: ViewDataBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(obj: Any) {
            binding.setVariable(serviceInfo, obj)
            binding.setVariable(BR.mainActivity, mainActivity)
            binding.executePendingBindings()
        }
    }
}