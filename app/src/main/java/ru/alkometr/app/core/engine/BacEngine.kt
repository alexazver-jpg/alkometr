package ru.alkometr.app.core.engine

// Импорты: типы данных модели (порция напитка, параметры расчёта) и функции max/min из стандартной библиотеки Kotlin.
import ru.alkometr.app.core.model.Drink
import ru.alkometr.app.core.model.ModelParams
import kotlin.math.max
import kotlin.math.min

/**
 * Точка кривой: момент времени (epoch millis) и концентрация в г/л (== ‰).
 *
 * `data class` — «класс данных»: компилятор сам генерирует `equals`, `hashCode`, `toString`
 * и `copy`, поэтому такой класс удобно использовать как простой контейнер двух значений.
 *
 * @param atMillis момент времени в миллисекундах от 01.01.1970 UTC — так Android хранит время.
 * @param bacGPerL концентрация этанола в крови, грамм на литр; 1 г/л численно равен 1 ‰ (промилле).
 */
data class CurvePoint(val atMillis: Long, val bacGPerL: Double)

/**
 * Информация о пике концентрации: когда он наступил и насколько высоким был.
 * Поля те же, что у [CurvePoint], но смысл другой — это «максимум», а не точка графика.
 *
 * @param atMillis время пика, epoch millis.
 * @param bacGPerL концентрация в пике, г/л (‰).
 */
data class PeakInfo(val atMillis: Long, val bacGPerL: Double)

/**
 * Чистая математика модели:
 *  - всасывание первого порядка: da/dt = −ka·a, a(t_drink) = D;
 *  - поступление в кровь: input(t) = ka·Σa;
 *  - выведение по Михаэлису–Ментену: dC/dt = input/Vd − Vmax·C/(Km + C).
 *
 * Никакого состояния внутри: всё определяется списком порций, параметрами и временем.
 * Класс не импортирует ничего из android.*, поэтому работает в JVM-юнит-тестах.
 *
 * Расшифровка обозначений (единицы должны быть согласованы: граммы, литры, часы):
 *  - a — ещё не всосавшийся алкоголь в желудке и кишечнике, граммы;
 *  - D — доза одной порции в граммах чистого этанола;
 *  - ka — константа скорости всасывания, 1/час: чем больше, тем быстрее «накрывает»;
 *  - input(t) — сколько граммов этанола в час поступает в кровь;
 *  - Vd — объём распределения, литры (масса тела × коэффициент r);
 *  - Vmax — максимальная скорость выведения печенью, г/(л·ч);
 *  - Km — константа Михаэлиса, г/л: концентрация, при которой скорость выведения равна Vmax/2;
 *  - C — текущая концентрация в крови, г/л (то есть ‰).
 *
 * `object` в Kotlin — синглтон: единственный экземпляр на всё приложение, методы вызываются
 * как `BacEngine.curve(...)`. Своего состояния объект не хранит, поэтому такие вызовы безопасны.
 */
object BacEngine {

    /**
     * Шаг интегрирования — 1 минута.
     *
     * Модель решается численно: время «нарезается» на маленькие шаги, и на каждом шаге
     * считается приращение концентрации. 60 000 мс = 60 секунд. Чем шаг меньше, тем точнее
     * решение, но тем больше точек и вычислений; минута — разумный компромисс.
     */
    const val STEP_MILLIS: Long = 60_000L

    /**
     * Горизонт прогноза от первой порции — 24 часа.
     *
     * Через сутки концентрация практически нулевая, поэтому считать дальше нет смысла:
     * ограничение защищает от очень длинных массивов и долгого расчёта.
     * Запись `24L * 60L * 60L * 1000L` — это часы × минуты × секунды × миллисекунды.
     */
    const val HORIZON_MILLIS: Long = 24L * 60L * 60L * 1000L

    /**
     * Кривая концентрации на отрезке [fromMillis, toMillis].
     * Возвращает точки с шагом [stepMillis] плюс точное значение на левой границе.
     *
     * Точка на левой границе добавляется отдельно: интегрирование начинается ровно с [fromMillis],
     * но первая «шаговая» точка может оказаться уже правее, а графику нужен честный старт кривой.
     *
     * @param drinks выпитые порции (могут быть и в будущем — тогда кривая ещё поднимется).
     * @param p параметры организма: масса, доля воды r, ka, Vmax, Km.
     * @param fromMillis начало окна, epoch millis.
     * @param toMillis конец окна, epoch millis.
     * @param stepMillis шаг по времени; по умолчанию — одна минута.
     */
    fun curve(
        drinks: List<Drink>,   // список порций; порядок не важен — время берётся из каждой порции
        p: ModelParams,        // параметры модели: от них зависят высота и форма кривой
        fromMillis: Long,      // левая граница окна: с этого момента начинаем считать
        toMillis: Long,        // правая граница окна: до этого момента (включительно) считаем
        stepMillis: Long = STEP_MILLIS,  // шаг интегрирования; аргумент по умолчанию можно не передавать
    ): List<CurvePoint> {
        // Защита от бесконечного цикла: при шаге <= 0 время в цикле while не будет расти.
        require(stepMillis > 0) { "Шаг интегрирования должен быть положительным" }
        // Перевёрнутый или пустой интервал — строить нечего, отдаём пустой список.
        if (toMillis < fromMillis) return emptyList()

        // Считаем «сырые» точки интегрированием — это самая дорогая часть работы.
        val raw = simulate(drinks, p, fromMillis, toMillis, stepMillis)
        // Вырожденный случай: остаёмся на нуле в начале окна.
        if (raw.isEmpty()) return listOf(CurvePoint(fromMillis, 0.0))

        // Готовый список точек; ёмкость задаём заранее, чтобы ArrayList не перевыделял память.
        val result = ArrayList<CurvePoint>(raw.size + 1)
        // Первая точка — ровно на левой границе (значение между узлами берём интерполяцией).
        result.add(CurvePoint(fromMillis, valueAt(raw, fromMillis)))
        // Дальше добавляем все точки строго внутри окна, то есть из интервала (fromMillis; toMillis].
        for (point in raw) {
            // Левая граница уже добавлена выше, поэтому требуем строго больше fromMillis.
            if (point.atMillis > fromMillis && point.atMillis <= toMillis) result.add(point)
        }
        // Кривая возвращается по возрастанию времени — так удобнее и графику, и бинарному поиску.
        return result
    }

    /** Концентрация в конкретный момент, г/л. */
    fun bacAt(drinks: List<Drink>, p: ModelParams, atMillis: Long): Double {
        // Просим проинтегрировать интервал нулевой длины: «от момента до того же момента».
        val raw = simulate(drinks, p, atMillis, atMillis, STEP_MILLIS)
        // Последняя точка — состояние на этот момент; `?.` — безопасный вызов, `?:` — значение по умолчанию.
        return raw.lastOrNull()?.bacGPerL ?: 0.0
    }

    /**
     * Пик концентрации на отрезке.
     *
     * Максимум ищем перебором точек: модель нелинейная (Михаэлис–Ментен), готовой формулы
     * для момента пика нет, поэтому дешевле пройти по уже посчитанной кривой.
     */
    fun peak(
        drinks: List<Drink>,
        p: ModelParams,
        fromMillis: Long,
        toMillis: Long,
    ): PeakInfo {
        // Строим кривую на всём отрезке с шагом по умолчанию — одна минута.
        val points = curve(drinks, p, fromMillis, toMillis)
        // Стартовый кандидат — первая точка; пустая кривая сразу даёт нулевой пик.
        var best = points.firstOrNull() ?: return PeakInfo(fromMillis, 0.0)
        // Линейный поиск максимума: O(n) по числу точек кривой.
        for (point in points) {
            // Сравнение строгое, поэтому при равных значениях остаётся более ранняя точка (первый пик).
            if (point.bacGPerL > best.bacGPerL) best = point
        }
        // Упаковываем найденный максимум: время пика и его высоту.
        return PeakInfo(best.atMillis, best.bacGPerL)
    }

    /**
     * Первый момент после [fromMillis], когда концентрация не выше [threshold] и уже не растёт.
     * null — если за горизонт прогноза этого не произошло.
     *
     * Логика: сначала находим пик, а затем идём только по спаду. Иначе на подъёме кривая
     * может на мгновение оказаться ниже порога, но это не «уже можно», а «ещё не накрыло».
     *
     * @param threshold порог в г/л, то есть в ‰ (например, 0,3 для России).
     */
    fun timeToThreshold(
        drinks: List<Drink>,
        p: ModelParams,
        fromMillis: Long,
        threshold: Double,
    ): Long? {
        // Дальше горизонта прогноза не заглядываем: 24 часа от первой порции или от «сейчас».
        val horizonEnd = horizonEnd(drinks, fromMillis)
        // Кривая на всём горизонте с шагом одна минута — по ней и ищем пересечение порога.
        val points = curve(drinks, p, fromMillis, horizonEnd)
        // Индекс пика; maxByOrNull вернёт null только для пустого списка (тогда ждать нечего).
        val peakIndex = points.indices.maxByOrNull { points[it].bacGPerL } ?: return fromMillis
        // Идём от пика вправо по времени до первого значения не выше порога.
        for (i in peakIndex until points.size) {
            // Порог проверяем включительно: значение «ровно порог» уже разрешено.
            if (points[i].bacGPerL <= threshold) return points[i].atMillis
        }
        // За горизонт так и не спустились ниже порога — честного прогноза нет, возвращаем null.
        return null
    }

    /**
     * Время до практически нулевого уровня (0,001 ‰).
     *
     * Это не ноль в математическом смысле: модель выведения асимптотическая, поэтому
     * «трезв» определяем по очень маленькому значению — 0,001 г/л.
     */
    fun timeToZero(drinks: List<Drink>, p: ModelParams, fromMillis: Long): Long? =
        // Переиспользуем поиск порога, подставив вместо юридического порога почти ноль.
        timeToThreshold(drinks, p, fromMillis, ZERO_THRESHOLD)

    /** Горизонт прогноза: 24 часа от первой порции (или от текущего момента). */
    fun horizonEnd(drinks: List<Drink>, fromMillis: Long): Long {
        // Самая ранняя порция; если порций нет — отсчитываем сутки от текущего момента.
        val firstDrink = drinks.minOfOrNull { it.atMillis } ?: return fromMillis + HORIZON_MILLIS
        // Если первая порция уже в прошлом, берём её время, иначе — текущий момент.
        return max(firstDrink, fromMillis) + HORIZON_MILLIS
    }

    /**
     * Уровень, который считается нулевым.
     *
     * 0,001 г/л = 0,001 ‰ — это ниже точности любого бытового алкотестера, то есть
     * физиологический ноль. Константа объявлена ниже места использования: в `object`
     * это нормально, порядок объявлений не важен.
     */
    const val ZERO_THRESHOLD = 0.001

    // --- внутреннее ---------------------------------------------------------

    /**
     * Явный метод Эйлера с шагом [stepMillis]. Модель стартует не раньше чем за 24 ч
     * до [fromMillis]: порции старше горизонта уже не влияют на результат.
     *
     * Как работает метод Эйлера: производная — это скорость изменения, то есть dC/dt ≈ ΔC/Δt,
     * значит за малый шаг Δt концентрация меняется на dC/dt · Δt:
     *     C_новое = C_старое + (input/Vd − выведение) · Δt.
     * Шаг в одну минуту мал по сравнению с часами всасывания и выведения, поэтому погрешность
     * приемлема, а расчёт остаётся простым и быстрым.
     */
    private fun simulate(
        drinks: List<Drink>,
        p: ModelParams,
        fromMillis: Long,
        toMillis: Long,
        stepMillis: Long,
    ): List<CurvePoint> {
        // Время самой ранней порции; null означает, что порций нет вообще.
        val firstDrink = drinks.minOfOrNull { it.atMillis }
        // Точка старта интегрирования: раньше неё считать бессмысленно.
        val simStart = when {
            // Порций нет — стартуем ровно с запрошенного момента.
            firstDrink == null -> fromMillis
            // Порции были раньше: начинаем максимум за 24 ч до fromMillis, чтобы учесть «хвост».
            firstDrink < fromMillis -> max(firstDrink, fromMillis - HORIZON_MILLIS)
            // Ближайшая порция ещё в будущем — достаточно начать с fromMillis.
            else -> fromMillis
        }
        // Конец окна раньше старта — вырожденный случай: одна нулевая точка, без цикла.
        if (toMillis < simStart) return listOf(CurvePoint(toMillis, 0.0))

        // Отбрасываем «пустые» порции (нулевой объём или крепость) и всё, что раньше старта.
        val validDrinks = drinks.filter { it.volumeMl > 0 && it.abvPercent > 0 && it.atMillis >= simStart }
        // Дозы в граммах чистого этанола — по одному числу на порцию, чтобы не считать дважды.
        val grams = DoubleArray(validDrinks.size) { validDrinks[it].ethanolGrams }
        // Сколько граммов ещё не всосалось (образно — «в желудке») для каждой порции.
        val pending = DoubleArray(validDrinks.size)
        // Флаги «порция уже включена в модель»: каждая порция учитывается ровно один раз.
        val added = BooleanArray(validDrinks.size)

        // Текущая концентрация в крови, г/л; в начале расчёта человек считается трезвым.
        var concentration = 0.0
        // Текущее время интегрирования, миллисекунды.
        var time = simStart
        // Накопитель результата: точки в порядке возрастания времени.
        val points = ArrayList<CurvePoint>()
        // Первая точка — состояние в момент старта: алкоголя в крови ещё ноль.
        points.add(CurvePoint(simStart, 0.0))

        // Главный цикл: шагаем по времени вперёд, пока не дойдём до toMillis.
        while (time < toMillis) {
            // Последний шаг обрезаем, чтобы точно попасть в toMillis, а не перескочить его.
            val dtMillis = min(stepMillis, toMillis - time)
            // Переводим шаг в часы: ka задана в 1/час, Vmax — в г/(л·ч), единицы должны совпадать.
            val dtHours = dtMillis / 3_600_000.0

            // «Включаем» порции, время которых уже наступило.
            for (i in validDrinks.indices) {
                if (!added[i] && validDrinks[i].atMillis <= time) {
                    // Вся доза порции попадает в желудок в момент употребления.
                    added[i] = true
                    pending[i] = grams[i]
                }
            }

            // Суммарное поступление этанола в кровь, граммы в час: input(t) = ka · Σa.
            var inputGramsPerHour = 0.0
            // Складываем весь ещё не всосавшийся алкоголь по всем порциям.
            for (i in pending.indices) inputGramsPerHour += pending[i]
            // Умножаем на ka: скорость всасывания пропорциональна остатку в желудке.
            inputGramsPerHour *= p.kaPerHour

            // Скорость выведения печенью по Михаэлису–Ментену: Vmax · C / (Km + C), г/(л·ч).
            val elimination = if (concentration > 0.0) {
                // При C >> Km дробь стремится к 1, и скорость выходит на потолок Vmax.
                p.vmaxGPerLH * concentration / (p.kmGPerL + concentration)
            } else {
                // При нулевой концентрации выводить нечего — скорость равна нулю.
                0.0
            }

            // Шаг Эйлера: (приход в кровь минус выведение), умноженное на длину шага в часах.
            // max(0.0, ...) не даёт концентрации уйти в минус из-за погрешности округления.
            concentration = max(0.0, concentration + (inputGramsPerHour / p.vdLiters - elimination) * dtHours)
            // Уменьшаем остаток в желудке по той же кинетике первого порядка: da = −ka·a·dt.
            for (i in pending.indices) {
                pending[i] = max(0.0, pending[i] - p.kaPerHour * pending[i] * dtHours)
            }

            // Сдвигаем время на шаг и запоминаем новую точку кривой.
            time += dtMillis
            points.add(CurvePoint(time, concentration))
        }
        // Готовый массив точек от simStart до toMillis.
        return points
    }

    /**
     * Линейная интерполяция по точкам кривой.
     *
     * Между соседними точками (t1, C1) и (t2, C2) считаем, что концентрация меняется линейно:
     *     C(t) = C1 + (C2 − C1) · (t − t1) / (t2 − t1).
     * Ошибка мала, потому что шаг кривой — всего одна минута.
     */
    private fun valueAt(points: List<CurvePoint>, atMillis: Long): Double {
        // Нет данных — концентрация считается нулевой.
        if (points.isEmpty()) return 0.0
        // Левее первой точки — берём крайнее левое значение (кривая «прилипает» к краю).
        if (atMillis <= points.first().atMillis) return points.first().bacGPerL
        // Правее последней — крайнее правое значение.
        if (atMillis >= points.last().atMillis) return points.last().bacGPerL
        // Ищем первый отрезок, правая граница которого не левее запрошенного времени.
        for (i in 1 until points.size) {
            val right = points[i]
            if (right.atMillis >= atMillis) {
                val left = points[i - 1]
                // Длина отрезка в миллисекундах; toDouble() нужен для дробного деления.
                val span = (right.atMillis - left.atMillis).toDouble()
                // Защита от деления на ноль, если две точки попали в одно и то же время.
                if (span <= 0.0) return right.bacGPerL
                // Доля пути k от 0 до 1 внутри отрезка: 0 — левая точка, 1 — правая.
                val k = (atMillis - left.atMillis) / span
                // Линейная интерполяция значения между левой и правой точками.
                return left.bacGPerL + (right.bacGPerL - left.bacGPerL) * k
            }
        }
        // Сюда дойти невозможно, но компилятору нужен возврат — отдаём последнее значение.
        return points.last().bacGPerL
    }
}
