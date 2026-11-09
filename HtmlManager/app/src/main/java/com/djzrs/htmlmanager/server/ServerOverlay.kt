package com.djzrs.htmlmanager.server

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.view.doOnLayout
import com.djzrs.htmlmanager.MainActivity
import com.djzrs.htmlmanager.R
import kotlin.math.roundToInt

/**
 * 服务器悬浮窗：切后台时挂一层小卡片，显示访问地址，点一下回 APP。
 * 拖到边上松手就收成一颗小球——卡片贴着边也是挡内容，小球只留一个绿点，
 * 点一下再展开看地址。没有悬浮窗权限就静默跳过：服务器由前台服务保着，不依赖它。
 */
class ServerOverlay(private val context: Context) {

    private val windowManager: WindowManager? =
        context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager

    private var view: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var snapAnimator: ValueAnimator? = null

    /** 用户点了 ×：这次后台就别再冒出来，回前台再重置。 */
    private var dismissed = false

    /**
     * 落点与形态记在这里。窗口收起再弹出、服务被系统重建，都从这儿恢复，
     * 否则用户贴到右边的窗子每次都会跳回左边默认位置——看起来就像没吸附。
     */
    private val prefs by lazy { context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE) }

    private var lastX: Int? = prefs.getInt(KEY_X, UNSET).takeUnless { it == UNSET }
    private var lastY: Int? = prefs.getInt(KEY_Y, UNSET).takeUnless { it == UNSET }

    /**
     * 收起成小球。和落点一起落盘：收起过一次，下次切后台还按小球出来，
     * 不用每次都往边上再推一遍。点小球随时能摊开。
     */
    private var collapsed = prefs.getBoolean(KEY_COLLAPSED, false)

    fun show(address: String) {
        val manager = windowManager ?: return
        if (dismissed || !canDrawOverlay()) return
        if (view == null) view = createView()
        val root = view ?: return
        // 沿用上次的形态：收起过就还是小球，没收起过就是卡片。
        applyCollapsed(root, collapsed, relayout = root.parent != null)

        root.findViewById<TextView>(R.id.overlay_address)?.text = address.ifBlank { "未启动" }
        if (root.parent == null) {
            val layoutParams = createParams()
            runCatching { manager.addView(root, layoutParams) }
                .onSuccess {
                    params = layoutParams
                    // 量完宽高才知道该收多少，转屏后上次的落点可能已经出界。
                    root.post { params?.let { clampIntoBounds(root, it) } }
                }
                .onFailure {
                    view = null
                    params = null
                }
        } else {
            // 转屏、分屏后可用区域会变，重新挂出来时把上一次的落点拉回屏幕内。
            params?.let { clampIntoBounds(root, it) }
        }
    }

    /** APP 回到前台时调用，下次切后台重新允许弹出。 */
    fun resetDismiss() {
        dismissed = false
    }

    fun hide() {
        cancelSnap()
        params?.let {
            lastX = it.x
            lastY = it.y
            prefs.edit().putInt(KEY_X, it.x).putInt(KEY_Y, it.y).apply()
        }
        view?.let { root -> runCatching { windowManager?.removeView(root) } }
        view = null
        params = null
    }

    fun canDrawOverlay(): Boolean = canDraw(context)

    @SuppressLint("InflateParams")
    private fun createView(): View {
        val root = LayoutInflater.from(context).inflate(R.layout.overlay_server, null)
        // 卡片态点一下回 APP；小球态点一下先摊开看地址。
        root.setOnClickListener { if (collapsed) expand(root) else openApp() }
        root.findViewById<View>(R.id.overlay_close).setOnClickListener {
            dismissed = true
            hide()
        }
        root.setOnTouchListener(DragToMove())
        return root
    }

    /**
     * 拖动 + 松手吸附：移动时把窗口限制在状态栏与导航栏之间，
     * 抬手后先收成小球，再横向吸到最近的一条竖边（留 [EDGE_MARGIN_DP] 的边距），带动画落位。
     * 手指没动就交给点击事件——OnTouchListener 返回 true 会吃掉 click。
     */
    private inner class DragToMove : View.OnTouchListener {
        private val slopPx = dp(DRAG_SLOP_DP).toFloat()
        private var downX = 0f
        private var downY = 0f
        private var startX = 0
        private var startY = 0
        private var dragging = false

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            val layoutParams = params ?: return false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    cancelSnap()
                    downX = event.rawX
                    downY = event.rawY
                    startX = layoutParams.x
                    startY = layoutParams.y
                    dragging = false
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!dragging && (dx * dx + dy * dy) > slopPx * slopPx) dragging = true
                    if (dragging) moveTo(v, layoutParams, (startX + dx).toInt(), (startY + dy).toInt())
                }

                MotionEvent.ACTION_UP -> {
                    if (dragging) collapseThenSnap(v) else v.performClick()
                }

                // 被上层窗口抢走焦点也会走到这里，同样要落位，别留在半路上。
                MotionEvent.ACTION_CANCEL -> {
                    if (dragging) snapToEdge(v)
                }
            }
            return true
        }
    }

    /** WRAP_CONTENT 的窗口在首帧 layout 之前 width/height 是 0，别拿 0 去做边界计算。 */
    private fun View.realWidth() = if (width > 0) width else measuredWidth

    private fun View.realHeight() = if (height > 0) height else measuredHeight

    /** 把窗口位置收进可用区域，用于转屏 / 分屏后重新摆放。 */
    private fun clampIntoBounds(v: View, layoutParams: WindowManager.LayoutParams) {
        val bounds = usableBounds()
        val x = layoutParams.x.coerceIn(bounds.left, (bounds.right - v.realWidth()).coerceAtLeast(bounds.left))
        val y = layoutParams.y.coerceIn(bounds.top, (bounds.bottom - v.realHeight()).coerceAtLeast(bounds.top))
        if (x == layoutParams.x && y == layoutParams.y) return
        layoutParams.x = x
        layoutParams.y = y
        runCatching { windowManager?.updateViewLayout(v, layoutParams) }
    }

    /** 拖动过程中就贴住可用区域，手指再怎么甩也不会把窗口拖出屏幕。 */
    private fun moveTo(v: View, layoutParams: WindowManager.LayoutParams, x: Int, y: Int) {
        val bounds = usableBounds()
        layoutParams.x = x.coerceIn(bounds.left, (bounds.right - v.realWidth()).coerceAtLeast(bounds.left))
        layoutParams.y = y.coerceIn(bounds.top, (bounds.bottom - v.realHeight()).coerceAtLeast(bounds.top))
        runCatching { windowManager?.updateViewLayout(v, layoutParams) }
    }

    /** 松手统一走这里：卡片拖完先收成小球，量完新尺寸再贴边，不然会按卡片宽度算错落点。 */
    private fun collapseThenSnap(v: View) {
        if (collapsed) {
            snapToEdge(v)
            return
        }
        applyCollapsed(v, true)
        // 等这一帧布局完再量，否则量到的还是卡片时的宽高。
        v.doOnLayout {
            params?.let { clampIntoBounds(v, it) }
            snapToEdge(v)
        }
    }

    /** 点小球：摊回卡片，再贴着原来那一侧落位（宽度变宽，可能要往屏幕里挪）。 */
    private fun expand(v: View) {
        if (!collapsed) return
        cancelSnap()
        applyCollapsed(v, false)
        // 卡片比小球宽，从右边展开会顶出屏幕，先收进可用区域再落位。
        v.doOnLayout {
            params?.let { clampIntoBounds(v, it) }
            snapToEdge(v)
        }
    }

    /**
     * 卡片与小球叠在一层里，切换只翻可见性。
     * WRAP_CONTENT 的窗口内容尺寸变了要主动让它重新布局，否则有些机型上还按旧宽高画。
     */
    private fun applyCollapsed(v: View, value: Boolean, relayout: Boolean = true) {
        collapsed = value
        prefs.edit().putBoolean(KEY_COLLAPSED, value).apply()
        v.findViewById<View>(R.id.overlay_card)?.visibility = if (value) View.GONE else View.VISIBLE
        v.findViewById<View>(R.id.overlay_bubble)?.visibility = if (value) View.VISIBLE else View.GONE
        if (relayout) params?.let { runCatching { windowManager?.updateViewLayout(v, it) } }
    }

    /** 松手：横向吸附到最近的边缘，纵向只做边界收敛，用户可以自己决定停多高。 */
    private fun snapToEdge(v: View) {
        val layoutParams = params ?: return
        val bounds = usableBounds()
        val margin = dp(EDGE_MARGIN_DP)
        val width = v.realWidth()
        val leftX = bounds.left + margin
        val rightX = (bounds.right - width - margin).coerceAtLeast(leftX)
        val targetX = if (layoutParams.x + width / 2 < bounds.centerX()) leftX else rightX
        val targetY = layoutParams.y.coerceIn(
            bounds.top + margin,
            (bounds.bottom - v.realHeight() - margin).coerceAtLeast(bounds.top + margin)
        )
        animateTo(v, layoutParams, targetX, targetY)
    }

    private fun animateTo(
        v: View,
        layoutParams: WindowManager.LayoutParams,
        targetX: Int,
        targetY: Int
    ) {
        cancelSnap()
        val fromX = layoutParams.x
        val fromY = layoutParams.y
        if (fromX == targetX && fromY == targetY) return

        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = SNAP_DURATION_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                val fraction = animation.animatedValue as Float
                layoutParams.x = fromX + ((targetX - fromX) * fraction).roundToInt()
                layoutParams.y = fromY + ((targetY - fromY) * fraction).roundToInt()
                runCatching { windowManager?.updateViewLayout(v, layoutParams) }
            }
        }
        snapAnimator = animator
        runCatching { animator.start() }
    }

    private fun cancelSnap() {
        snapAnimator?.cancel()
        snapAnimator = null
    }

    /**
     * 可用区域：去掉状态栏、导航栏和刘海，悬浮窗只在真能看见的地方待着。
     * API 30 以下拿不到 insets，退回整块屏幕，靠 [moveTo] 的收敛兜底。
     */
    private fun usableBounds(): Rect {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // 服务拿的是非可视化 context，个别机型上取 metric 会抛，抛了就退回整屏估算。
            boundsFromWindowMetrics()?.let { return it }
        }
        val metrics = context.resources.displayMetrics
        return Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun boundsFromWindowMetrics(): Rect? {
        val manager = windowManager ?: return null
        val rect = runCatching {
            val metrics = manager.currentWindowMetrics
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
            )
            val bounds = metrics.bounds
            Rect(
                bounds.left + insets.left,
                bounds.top + insets.top,
                bounds.right - insets.right,
                bounds.bottom - insets.bottom
            )
        }.getOrNull() ?: return null
        return if (rect.width() > 0 && rect.height() > 0) rect else null
    }

    private fun createParams(): WindowManager.LayoutParams {
        val bounds = usableBounds()
        val margin = dp(EDGE_MARGIN_DP)
        val defaultY = bounds.top + (bounds.height() * INITIAL_TOP_RATIO).toInt()
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = lastX ?: (bounds.left + margin)
            y = lastY ?: defaultY
        }
    }

    private fun openApp() {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_SERVER, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        runCatching { context.startActivity(intent) }
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).roundToInt()

    companion object {
        /** 有没有「显示在其他应用上层」权限。 */
        fun canDraw(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

        /** 贴边后离屏幕边缘留的空隙。 */
        private const val EDGE_MARGIN_DP = 8
        private const val INITIAL_TOP_RATIO = 0.35f
        private const val DRAG_SLOP_DP = 8
        private const val SNAP_DURATION_MS = 180L

        private const val PREF_NAME = "overlay_position"
        private const val KEY_X = "x"
        private const val KEY_Y = "y"
        private const val KEY_COLLAPSED = "collapsed"
        private const val UNSET = Int.MIN_VALUE
    }
}
