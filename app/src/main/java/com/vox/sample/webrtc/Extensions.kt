package com.vox.sample.webrtc

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView
import android.graphics.drawable.InsetDrawable
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding


/**
 * Created by Kadir Mert Ozcan on 17/05/2019.
 */

fun ViewGroup.inflate(@LayoutRes layoutRes: Int): View {
    return LayoutInflater.from(context).inflate(layoutRes, this, false)
}

fun bindingInflate(viewGroup: ViewGroup, @LayoutRes layoutRes: Int) : ViewDataBinding {
    return DataBindingUtil.inflate(
            LayoutInflater.from(viewGroup.context),
            layoutRes,
            viewGroup,
            false)
}

fun RecyclerView.setAdapterWithCustomDivider(layoutManager: RecyclerView.LayoutManager,
                                             adapter: RecyclerView.Adapter<out RecyclerView.ViewHolder>?) {
    //Set the adapter and layout manager
    this.layoutManager = layoutManager
    this.adapter = adapter

    //Create the divider with margin
    val attributes = intArrayOf(android.R.attr.listDivider)
    val a = context.obtainStyledAttributes(attributes)
    val divider = a.getDrawable(0)
    val inset = resources.getDimensionPixelSize(R.dimen.recyclerViewDividerMargin)
    val insetDivider = InsetDrawable(divider, inset, 0, inset, 0)
    a.recycle()

    //Add the divider to the recycler view
    val itemDecoration = DividerItemDecoration(context, DividerItemDecoration.VERTICAL)
    itemDecoration.setDrawable(insetDivider)
    if (this.itemDecorationCount < 1) {
        this.addItemDecoration(itemDecoration)
    }
}

fun RecyclerView.setAdapter(layoutManager: RecyclerView.LayoutManager,
                            adapter: RecyclerView.Adapter<out RecyclerView.ViewHolder>?) {
    this.layoutManager = layoutManager
    this.adapter = adapter
}


