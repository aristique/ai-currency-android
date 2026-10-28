package md.lab.currency

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import java.time.temporal.ChronoUnit

class ChartView(context: Context, private val points: List<Point>, private val days: Int) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density
    init { contentDescription = "График выбранной валютной пары за $days дней, ${points.size} наблюдений" }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if(points.isEmpty()) return
        val left = 16f * density
        val right = width - 16f * density
        val top = 36f * density
        val bottom = height - 38f * density
        val values = points.map { it.value.toDouble() }
        val low = values.minOrNull()!!
        val high = values.maxOrNull()!!
        val start = today().minusDays(days.toLong() - 1)
        paint.strokeWidth = density
        paint.color = Color.rgb(210, 221, 206)
        for(i in 0..2) {
            val y = top + (bottom-top)*i/2
            canvas.drawLine(left, y, right, y, paint)
        }
        paint.textSize = 11f*density
        paint.color = Color.rgb(92, 112, 88)
        canvas.drawText(points.first().day.toString().substring(5), left, height-12*density, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(points.last().day.toString().substring(5), right, height-12*density, paint)
        canvas.drawText("1 ${points.first().value.setScale(4, java.math.RoundingMode.HALF_UP)}", right, 20*density, paint)
        paint.textAlign = Paint.Align.LEFT
        val path = Path()
        points.forEachIndexed { index, point ->
            val x = left + (right-left)*ChronoUnit.DAYS.between(start,point.day).toFloat()/(days-1)
            val y = if(high == low) (top+bottom)/2 else (bottom - (bottom-top)*(point.value.toDouble()-low)/(high-low)).toFloat()
            if(index == 0) path.moveTo(x,y) else path.lineTo(x,y)
            paint.color = Color.rgb(49,88,63)
            paint.style = Paint.Style.FILL
            canvas.drawCircle(x,y,3*density,paint)
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f*density
        paint.color = Color.rgb(49,88,63)
        canvas.drawPath(path,paint)
        paint.style = Paint.Style.FILL
    }
}
