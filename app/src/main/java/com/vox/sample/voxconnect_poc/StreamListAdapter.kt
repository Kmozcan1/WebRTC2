package com.vox.sample.voxconnect_poc

import android.view.ViewGroup
import androidx.databinding.ViewDataBinding
import androidx.recyclerview.widget.RecyclerView

/**
 * Created by Kadir Mert Ozcan on 22/05/2019.
 */
class StreamListAdapter(private val streamList: List<String>, private val activity: MainActivity):
        RecyclerView.Adapter<StreamListAdapter.StreamItemViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StreamItemViewHolder {
        val binding = bindingInflate(
                parent,
                R.layout.stream_list_item)
        return StreamItemViewHolder(binding)
    }

    override fun getItemCount(): Int {
        return streamList.size
    }

    override fun onBindViewHolder(holder: StreamItemViewHolder, position: Int) {
        holder.bind(streamList[position])
    }

    inner class StreamItemViewHolder(
            private val binding: ViewDataBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(streamName: Any) {
            binding.setVariable(BR.streamName, streamName)
            binding.setVariable(BR.activity, activity)
            binding.executePendingBindings()
        }
    }
}