package md.lab.currency

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.text.InputType
import android.view.View
import android.widget.*
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var repository: Repository
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private lateinit var tabs: LinearLayout
    private val executor = Executors.newSingleThreadExecutor()
    private val histories = mutableMapOf<Int, History>()
    private val pendingHistory = mutableSetOf<Int>()
    private var loaded: Loaded? = null
    private var page = 0
    private var from = "EUR"
    private var to = "MDL"
    private var entered = "100"
    private var fetching = false
    private var latestError = ""
    private val ink = Color.rgb(27, 51, 37)
    private val green = Color.rgb(49, 88, 63)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        page = savedInstanceState?.getInt("page") ?: 0
        from = savedInstanceState?.getString("from") ?: "EUR"
        to = savedInstanceState?.getString("to") ?: "MDL"
        entered = savedInstanceState?.getString("amount") ?: "100"
        repository = Repository(File(filesDir, "bnm-rates"))
        loaded = repository.latestCached()?.let { Loaded(it, true, "Показан сохранённый курс. Обновляем данные…") }
        val scroll = ScrollView(this)
        scroll.setBackgroundColor(Color.rgb(244, 246, 242))
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(30), dp(22), dp(24)) }
        root.setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(dp(22), insets.systemWindowInsetTop + dp(18), dp(22), insets.systemWindowInsetBottom + dp(22))
            insets
        }
        scroll.addView(root)
        root.addView(label("↗ Курс Mobile", 30, true))
        root.addView(label("Официальные данные BNM", 13))
        tabs = LinearLayout(this)
        root.addView(tabs, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(24); bottomMargin = dp(18) })
        status = label("Загрузка курсов…", 13)
        root.addView(status)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(content)
        setContentView(scroll)
        render()
        refresh()
        if (page > 0) requestHistory(if(page == 1) 7 else 30)
    }

    private fun label(value: String, size: Int = 15, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size.toFloat()
            setTextColor(ink)
            setPadding(0, dp(7), 0, dp(7))
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun button(value: String, action: () -> Unit) = Button(this).apply {
        text = value
        isAllCaps = false
        setTextColor(Color.WHITE)
        backgroundTintList = android.content.res.ColorStateList.valueOf(green)
        setOnClickListener { action() }
    }

    private fun panel(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(16), dp(20), dp(18))
        background = GradientDrawable().apply { setColor(Color.rgb(225,234,216)); cornerRadius = dp(18).toFloat() }
    }

    private fun render() {
        tabs.removeAllViews()
        listOf("Конвертер", "7 дней", "30 дней").forEachIndexed { index, title ->
            val tab = button(title) {
                page = index
                render()
                if (page > 0) requestHistory(if(page == 1) 7 else 30)
            }
            tab.alpha = if(index == page) 1f else .55f
            tabs.addView(tab, LinearLayout.LayoutParams(0, -2, if(index == 0) 1.5f else 1f))
        }
        status.text = when {
            fetching -> "Получаем курс BNM…"
            latestError.isNotEmpty() -> latestError
            loaded != null -> loaded!!.notice + "\nДата: " + loaded!!.quote.day + if(loaded!!.cached) " · Кэш" else ""
            else -> "Данные пока недоступны."
        }
        content.removeAllViews()
        if (page == 0) converter() else history(if(page == 1) 7 else 30)
    }

    private fun converter() {
        content.addView(label("Одна сумма. Любая валюта.", 23, true))
        content.addView(label("Сумма", 12))
        val amount = EditText(this).apply {
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
            textSize = 28f
            setText(entered)
            contentDescription = "Сумма для конвертации"
        }
        content.addView(amount)
        val options = loaded?.quote?.mdlPerUnit?.keys?.sorted() ?: listOf("EUR", "MDL", "USD", "RON")
        val pair = LinearLayout(this)
        val error = label("", 12)
        val result = label("—", 32, true)
        val calculate = button("Конвертировать") {
            try {
                val quote = loaded?.quote ?: throw IllegalArgumentException("Сначала загрузите курс.")
                result.text = Domain.convert(entered, from, to, quote).toPlainString() + " " + to
                error.text = ""
            } catch (problem: Exception) { error.text = problem.message }
        }
        fun validate() {
            val valid = runCatching { Domain.amount(entered) }.isSuccess
            calculate.isEnabled = valid && loaded != null && !fetching
            calculate.alpha = if(calculate.isEnabled) 1f else .45f
            error.text = if(valid || entered.isEmpty()) "" else "Введите положительную сумму числом."
        }
        fun selector(selected: String, change: (String) -> Unit): Spinner {
            return Spinner(this).apply {
                adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, options)
                setSelection(options.indexOf(selected).coerceAtLeast(0))
                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        change(options[position]); result.text = "—"; validate()
                    }
                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                }
            }
        }
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; addView(label("Из валюты", 12)); addView(selector(from) { from = it }) }
        val right = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; addView(label("В валюту", 12)); addView(selector(to) { to = it }) }
        pair.addView(left, LinearLayout.LayoutParams(0, -2, 1f))
        pair.addView(right, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(pair)
        amount.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                entered = s.toString(); result.text = "—"; validate()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        content.addView(calculate)
        content.addView(error)
        val resultPanel = panel()
        resultPanel.addView(label("РЕЗУЛЬТАТ", 11))
        resultPanel.addView(result)
        resultPanel.addView(label("Курс на " + (loaded?.quote?.day ?: "—") + " · BNM", 12))
        content.addView(resultPanel)
        content.addView(button("Обновить курс") { refresh() })
        content.addView(label("История выбранной пары доступна на экранах «7 дней» и «30 дней».", 12))
        validate()
    }

    private fun history(days: Int) {
        content.addView(label("$from → $to", 26, true))
        content.addView(label("Динамика за последние $days дней", 14))
        val data = histories[days]
        if (data == null) {
            content.addView(label(if(pendingHistory.contains(days)) "Загружаем историю…" else "История ещё не загружена."))
        } else {
            val points = data.quotes.mapNotNull { quote ->
                runCatching { Point(quote.day, Domain.pair(quote, from, to)) }.getOrNull()
            }
            content.addView(label("Данных: ${points.size}/$days дней. Источник: BNM.", 12))
            content.addView(label(if(data.cached) "Часть данных прочитана из постоянного кэша." else "Данные получены из источника.", 12))
            if (points.isEmpty()) {
                content.addView(label("Нет данных для выбранной пары. Проверьте сеть и повторите загрузку."))
            } else {
                val stats = Domain.statistics(points)
                content.addView(label("Актуальность: ${points.last().day}", 12))
                content.addView(ChartView(this, points, days), LinearLayout.LayoutParams(-1, dp(240)))
                val card = panel()
                fun number(value: BigDecimal) = value.setScale(6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
                card.addView(label(stats.trend, 24, true))
                card.addView(label("Минимум: ${number(stats.minimum)} $to", 15))
                card.addView(label("Максимум: ${number(stats.maximum)} $to", 15))
                card.addView(label("Среднее: ${number(stats.average)} $to", 15))
                card.addView(label("Изменение: ${number(stats.change)} $to", 15))
                card.addView(label("Изменение: ${stats.percent.setScale(2, RoundingMode.HALF_UP)} %", 15))
                content.addView(card)
                content.addView(label("Показатель — стоимость 1 $from в $to. Линия соединяет доступные наблюдения; отсутствующие дни не выдумываются.", 12))
            }
        }
        content.addView(button("Загрузить недостающие дни") { histories.remove(days); requestHistory(days) })
    }

    private fun refresh() {
        if(fetching) return
        fetching = true
        latestError = ""
        render()
        executor.execute {
            val value = runCatching { repository.current() }
            runOnUiThread {
                if(isDestroyed || isFinishing) return@runOnUiThread
                fetching = false
                value.onSuccess { loaded = it }.onFailure { latestError = it.message ?: "Не удалось получить данные." }
                render()
            }
        }
    }

    private fun requestHistory(days: Int) {
        if(histories.containsKey(days) || !pendingHistory.add(days)) return
        render()
        executor.execute {
            val value = runCatching { repository.history(days) }
            runOnUiThread {
                if(isDestroyed || isFinishing) return@runOnUiThread
                pendingHistory.remove(days)
                value.onSuccess { histories[days] = it }.onFailure { latestError = it.message ?: "Ошибка истории." }
                render()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("page", page)
        outState.putString("from", from)
        outState.putString("to", to)
        outState.putString("amount", entered)
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy() { executor.shutdownNow(); super.onDestroy() }
}
