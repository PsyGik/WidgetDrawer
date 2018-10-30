package tk.zwander.widgetdrawer.adapters

import android.animation.Animator
import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnticipateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import io.reactivex.subjects.BehaviorSubject
import kotlinx.android.synthetic.main.widget_holder.view.*
import tk.zwander.widgetdrawer.R
import tk.zwander.widgetdrawer.misc.DrawerHost
import tk.zwander.widgetdrawer.misc.OverrideWidgetInfo
import tk.zwander.widgetdrawer.utils.SimpleAnimatorListener
import tk.zwander.widgetdrawer.utils.dpAsPx

class DrawerAdapter(
    private val manager: AppWidgetManager,
    private val appWidgetHost: DrawerHost
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    companion object {
        const val SIZE_MIN = -5
        const val SIZE_DEF = -1
        const val SIZE_MAX = 4

        const val SIZE_STEP_PX = 100

        const val TYPE_HEADER = 1
        const val TYPE_WIDGET = 0
    }

    var isEditing = false
        set(value) {
            field = value
            if (!value) selectedId = -1
            editingObservable.onNext(value)
        }
    var selectedId = -1
        set(value) {
            field = value
            selectedObservable.onNext(value)
        }

    private var editingObservable = BehaviorSubject.create<Boolean>()
    private var selectedObservable = BehaviorSubject.create<Int>()

    val widgets = ArrayList<OverrideWidgetInfo>()
        .apply { add(0, OverrideWidgetInfo(-1, Integer.MIN_VALUE, true)) }

    val selectedWidget: OverrideWidgetInfo?
        get() = widgets.firstOrNull { it.id == selectedId }

    init {
        setHasStableIds(true)
    }

    override fun getItemCount() = widgets.size

    override fun getItemId(position: Int) = widgets[position].id.toLong()

    override fun getItemViewType(position: Int) =
        if (position == 0) TYPE_HEADER
        else TYPE_WIDGET

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        when(viewType) {
            TYPE_HEADER -> HeaderVH(LayoutInflater.from(parent.context).inflate(R.layout.header_layout, parent, false))
            else -> DrawerVH(LayoutInflater.from(parent.context).inflate(R.layout.widget_holder, parent, false))
        }

    @SuppressLint("CheckResult")
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is DrawerVH) {
            val widget = widgets[position]
            val info = manager.getAppWidgetInfo(widget.id)

            updateSelectionCheck(holder, widget)

            holder.itemView.selection.setOnClickListener { if (isEditing) selectedId = widget.id }
            holder.itemView.widget_frame.apply {
                removeAllViews()

                val view = appWidgetHost.createView(
                    holder.itemView.context,
                    widget.id,
                    info
                )

                addView(view)
                view.setOnClickListener {
                    holder.itemView.selection.isChecked = true
                    if (isEditing) selectedId = widget.id
                }
            }
            holder.apply {
                editingObservable
                    .subscribe {
                        updateSelectionVisibility(this)
                    }

                selectedObservable
                    .subscribe {
                        if (adapterPosition != -1) updateSelectionCheck(this,
                            widgets[adapterPosition])
                    }
            }

            updateDimens(holder, info, widget)
        }
    }

    private fun updateSelectionCheck(holder: DrawerVH, widget: OverrideWidgetInfo) {
        holder.itemView.selection.isChecked = widget.id == selectedId
    }

    private fun updateSelectionVisibility(holder: DrawerVH) {
        holder.itemView.selection.apply {
            if (isEditing) {
                visibility = View.VISIBLE
                animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(500L)
                    .setInterpolator(OvershootInterpolator())
                    .setListener(object : SimpleAnimatorListener() {
                        override fun onAnimationEnd(animation: Animator?) {
                            scaleX = 1f
                            scaleY = 1f
                        }
                    })
            } else {
                animate()
                    .scaleX(0f)
                    .scaleY(0f)
                    .setDuration(500L)
                    .setInterpolator(AnticipateInterpolator())
                    .setListener(object : SimpleAnimatorListener() {
                        override fun onAnimationEnd(animation: Animator?) {
                            visibility = View.GONE
                            scaleX = 0f
                            scaleY = 0f
                        }
                    })
            }
        }
    }

    private fun updateDimens(holder: DrawerVH, info: AppWidgetProviderInfo, widget: OverrideWidgetInfo) {
        holder.itemView.apply {
            layoutParams = (layoutParams as StaggeredGridLayoutManager.LayoutParams).apply {
                width = ViewGroup.LayoutParams.MATCH_PARENT
                height = computeHeight(context.dpAsPx(info.minHeight), widget.forcedHeight)

                isFullSpan = widget.isFullWidth
            }
        }
    }

    fun addItem(widget: OverrideWidgetInfo) {
        widgets.add(widget)
        notifyItemInserted(widgets.lastIndex)
    }

    fun addAll(widgets: List<OverrideWidgetInfo>) {
        widgets.forEach {
            addItem(it)
        }
    }

    fun removeItem(widget: OverrideWidgetInfo) {
        val index = widgets.indexOf(widget)
        widgets.remove(widget)
        notifyItemRemoved(index)
    }

    fun removeAt(position: Int): OverrideWidgetInfo {
        val removed = widgets.removeAt(position)
        notifyItemRemoved(position)
        return removed
    }

    private fun computeHeight(currentHeight: Int, expand: Int): Int {
        return when (expand) {
            SIZE_DEF -> currentHeight
            else -> currentHeight + ((expand + 1) * SIZE_STEP_PX)
        }
    }

    class DrawerVH(view: View) : RecyclerView.ViewHolder(view)
    class HeaderVH(view: View) : RecyclerView.ViewHolder(view) {
        init {
            (view.layoutParams as StaggeredGridLayoutManager.LayoutParams).isFullSpan = true
        }
    }
}