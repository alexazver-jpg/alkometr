package ru.alkometr.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.alkometr.app.core.engine.BacEngine
import ru.alkometr.app.core.engine.TimeToDrive
import ru.alkometr.app.core.format.Formatters
import ru.alkometr.app.core.model.Drink
import ru.alkometr.app.core.model.DrinkKind
import ru.alkometr.app.core.model.IntoxicationLevel
import ru.alkometr.app.core.model.ModelParams
import ru.alkometr.app.core.model.Sex
import ru.alkometr.app.core.model.Units

/**
 * Тесты расчётного ядра: именно здесь выпитое превращается в промилле, поэтому ошибка в
 * формуле означает неверный совет пользователю («можно ехать» вместо «нельзя»).
 *
 * Как устроен тест на JUnit — коротко, чтобы дальше не путаться:
 *  - класс ниже обычный: JUnit создаёт его новый экземпляр перед каждым тестом, поэтому
 *    поля [t0] и [minute] каждый раз инициализируются заново и тесты не делят состояние;
 *  - метод, помеченный `@Test`, — это отдельный тест. JUnit сам находит такие методы и
 *    запускает их по очереди, без ручного вызова из `main`;
 *  - упавшим считается тест, в котором не прошла хотя бы одна проверка: `assertEquals`,
 *    `assertTrue`, `assertNotNull` выбрасывают исключение и печатают сообщение;
 *  - `assertEquals(ожидание, факт, delta)` — сравнение дробных чисел с допуском. Два `Double`
 *    после делений и умножений почти никогда не равны бит в бит, поэтому «равно» здесь
 *    означает «отличается не больше чем на delta».
 *
 * Контрольные значения взяты из эталонной реализации модели (docs/_check_model.py).
 * Допуски по ТЗ: ±0,02 ‰ и ±10 мин. Подгонять константы модели под тесты запрещено.
 */
class BacEngineTest {

    // Фиксированное «начало сессии»: 1_700_000_000_000 мс от эпохи Unix (ноябрь 2023).
    // Время в тестах обязано быть константой, а не System.currentTimeMillis(): иначе результат
    // зависел бы от момента запуска и тест перестал бы быть воспроизводимым.
    private val t0 = 1_700_000_000_000L

    // Минута в миллисекундах, чтобы записывать моменты как «t0 + 30 * minute» без лишних нулей.
    private val minute = 60_000L

    // Хелпер собирает параметры модели: вес, пол и, если нужно, нестандартные константы
    // всасывания ka и выведения vmax. Значения по умолчанию совпадают с боевыми, поэтому
    // тест переопределяет их только когда проверяет другую скорость метаболизма.
    private fun params(
        weightKg: Double,
        sex: Sex,
        ka: Double = 1.5,
        vmax: Double = 0.15,
    ) = ModelParams(weightKg = weightKg, r = sex.r, kaPerHour = ka, vmaxGPerLH = vmax)

    // Хелпер создаёт порцию, выпитую через minutesFromStart минут после t0.
    // По умолчанию это стопка 50 мл 40 % — самый частый случай в тестах. id собирается из
    // параметров, чтобы у порций в одном списке были разные ключи (иначе их нельзя различить).
    private fun drink(
        minutesFromStart: Long,
        volumeMl: Double = 50.0,
        abv: Double = 40.0,
        kind: DrinkKind = DrinkKind.SHOT,
        id: String = "d$minutesFromStart-$volumeMl-$abv",
    ) = Drink(
        id = id,
        atMillis = t0 + minutesFromStart * minute,
        volumeMl = volumeMl,
        abvPercent = abv,
        kind = kind,
    )

    // Короткая обёртка над расчётом: спрашиваем уровень через minutes минут после старта.
    private fun bac(drinks: List<Drink>, p: ModelParams, minutes: Long): Double =
        BacEngine.bacAt(drinks, p, t0 + minutes * minute)

    // Общая проверка уровня: допуск 0,02 ‰ задан ТЗ. Третий параметр по умолчанию позволяет
    // в отдельных тестах сузить допуск, не переписывая вызов.
    private fun assertBac(expected: Double, actual: Double, tolerance: Double = 0.02) {
        assertEquals("BAC отличается больше допуска", expected, actual, tolerance)
    }

    // Проверка момента времени: движок возвращает миллисекунды или null (если события нет),
    // поэтому сначала убеждаемся, что значение вообще есть, а потом переводим его в минуты
    // от старта и сравниваем с ожиданием в минутах — так числа в тестах читаются понятнее.
    private fun assertMinutes(expected: Double, actualMillis: Long?, toleranceMinutes: Double = 10.0) {
        assertNotNull("Ожидалось время, получено null", actualMillis)
        val actualMinutes = (actualMillis!! - t0) / minute.toDouble()
        assertEquals("Время отличается больше допуска", expected, actualMinutes, toleranceMinutes)
    }

    // Пустая сессия: порций нет — уровень должен быть строго нулевым в любой момент, а не
    // «почти нулевым». Это точка отсчёта для остальных тестов: если модель «фонит» на пустом
    // входе, сравнивать её с контрольными значениями бессмысленно.
    @Test
    fun emptySession_returnsZero() {
        // Параметры конкретного человека (мужчина 80 кг), но на пустом списке они не влияют.
        val p = params(80.0, Sex.MALE)
        // Два разных момента: сразу на старте и через два часа — ноль должен быть везде.
        assertBac(0.0, bac(emptyList(), p, 0))
        assertBac(0.0, bac(emptyList(), p, 120))
        // Кривая за час должна существовать (точки для графика) и целиком лежать на нуле:
        // иначе график окажется пустым или начнёт «прыгать» без выпитого.
        val points = BacEngine.curve(emptyList(), p, t0, t0 + 60 * minute)
        assertTrue(points.isNotEmpty())
        assertTrue(points.all { it.bacGPerL == 0.0 })
    }

    // Сценарий A: одна стопка. Проверяем весь жизненный цикл — нарастание, пик, выведение.
    // Числа взяты из эталонной реализации, а не сняты с текущего кода: иначе тест просто
    // подтверждал бы любое поведение, включая ошибочное.
    @Test
    fun singleShot_matchesGolden() {
        // A. Мужчина 80 кг, 1 стопка 50 мл 40 % (15,8 г) в t=0
        val p = params(80.0, Sex.MALE)
        // minutesFromStart = 0 — порция выпита в момент старта сессии.
        val drinks = listOf(drink(0))

        // Через 30 минут 0,097 ‰, через 60 — 0,102 ‰: всасывание ещё продолжается, уровень растёт.
        assertBac(0.097, bac(drinks, p, 30))
        assertBac(0.102, bac(drinks, p, 60))

        // Пик ищем на окне 6 часов: ожидается около 0,106 ‰ примерно на 47-й минуте.
        val peak = BacEngine.peak(drinks, p, t0, t0 + 6 * 60 * minute)
        assertBac(0.106, peak.bacGPerL)
        assertMinutes(47.0, peak.atMillis)

        // 2,98 ч от первой порции — столько нужно, чтобы уровень вернулся к нулю.
        assertMinutes(2.98 * 60, BacEngine.timeToZero(drinks, p, t0))
    }

    // Сценарий B: то же самое для женщины и пива. Здесь другой пол (меньше доля воды в теле)
    // и другой напиток, поэтому проверяем, что формула учитывает и то, и другое.
    @Test
    fun beerFemale_matchesGolden() {
        // B. Женщина 70 кг, 1 кружка 500 мл 5 % (19,7 г)
        val p = params(70.0, Sex.FEMALE)
        // Кружка: объём и крепость заданы явно, вид напитка — MUG (влияет на подачу в UI).
        val drinks = listOf(drink(0, volumeMl = 500.0, abv = 5.0, kind = DrinkKind.MUG))

        // Через час 0,252 ‰, через два — 0,195 ‰: после пика уровень уже снижается.
        assertBac(0.252, bac(drinks, p, 60))
        assertBac(0.195, bac(drinks, p, 120))

        // Пик около 0,252 ‰ на 65-й минуте: у пива всасывание медленнее, чем у стопки.
        val peak = BacEngine.peak(drinks, p, t0, t0 + 8 * 60 * minute)
        assertBac(0.252, peak.bacGPerL)
        assertMinutes(65.0, peak.atMillis)

        // Полное выведение — примерно 4,18 ч.
        assertMinutes(4.18 * 60, BacEngine.timeToZero(drinks, p, t0))
    }

    // Сценарий C: три порции подряд. Проверяем, что вклады нескольких порций складываются,
    // а пик наступает позже, чем от одной порции.
    @Test
    fun threeShots_matchesGolden() {
        // C. Мужчина 90 кг, 3 стопки 50 мл 40 % в 0, 15, 30 мин
        val p = params(90.0, Sex.MALE)
        // Три порции в разное время: список упорядочен по времени, но модель обязана
        // работать и с любым порядком (это проверяется отдельным тестом ниже).
        val drinks = listOf(drink(0), drink(15), drink(30))

        // Через час после первой порции — 0,374 ‰.
        assertBac(0.374, bac(drinks, p, 60))

        // Пик 0,434 ‰ примерно на 97-й минуте — позже и выше, чем от одной порции.
        val peak = BacEngine.peak(drinks, p, t0, t0 + 10 * 60 * minute)
        assertBac(0.434, peak.bacGPerL)
        assertMinutes(97.0, peak.atMillis)

        // До порога 0,3 ‰ (условная «норма» из настроек) — около 3,17 ч, до нуля — около 5,97 ч.
        assertMinutes(3.17 * 60, BacEngine.timeToThreshold(drinks, p, t0, 0.3))
        assertMinutes(5.97 * 60, BacEngine.timeToZero(drinks, p, t0))
    }

    // Сценарий D: два бокала вина с интервалом. Проверяем случай, когда вторая порция
    // приходит на спад первой — модель должна корректно сложить остаток и новое поступление.
    @Test
    fun twoWineGlasses_matchesGolden() {
        // D. Женщина 60 кг, 2 бокала 150 мл 12 % в 0 и 20 мин
        val p = params(60.0, Sex.FEMALE)
        // Два одинаковых бокала: второй через 20 минут после первого.
        val drinks = listOf(
            drink(0, volumeMl = 150.0, abv = 12.0, kind = DrinkKind.WINE_GLASS),
            drink(20, volumeMl = 150.0, abv = 12.0, kind = DrinkKind.WINE_GLASS),
        )

        // Через час — 0,454 ‰.
        assertBac(0.454, bac(drinks, p, 60))

        // Пик 0,506 ‰ около 95-й минуты: суммарная доза больше, поэтому и пик выше.
        val peak = BacEngine.peak(drinks, p, t0, t0 + 10 * 60 * minute)
        assertBac(0.506, peak.bacGPerL)
        assertMinutes(95.0, peak.atMillis)

        // До полного нуля — примерно 6,43 ч.
        assertMinutes(6.43 * 60, BacEngine.timeToZero(drinks, p, t0))
    }

    // Сценарий E: пиво и стопка вместе, с ускоренным всасыванием (ka = 2,0 вместо 1,5).
    // Так проверяется, что параметр ka действительно влияет на форму кривой, а не игнорируется.
    @Test
    fun beerPlusShot_ka2_matchesGolden() {
        // E. Мужчина 80 кг, 500 мл 5 % в 0 + 50 мл 40 % на 10-й мин, ka = 2,0
        val p = params(80.0, Sex.MALE, ka = 2.0)
        // Первая порция — кружка пива, вторая — стопка через 10 минут.
        val drinks = listOf(
            drink(0, volumeMl = 500.0, abv = 5.0, kind = DrinkKind.MUG),
            drink(10),
        )

        // Через час — 0,399 ‰.
        assertBac(0.399, bac(drinks, p, 60))

        // При быстром всасывании пик наступает раньше (68-я минута) и почти совпадает по высоте
        // с уровнем на 60-й минуте — кривая выходит на плато.
        val peak = BacEngine.peak(drinks, p, t0, t0 + 8 * 60 * minute)
        assertBac(0.402, peak.bacGPerL)
        assertMinutes(68.0, peak.atMillis)
    }

    // Инвариант неотрицательности: концентрация — физическая величина, её не может быть
    // меньше нуля. Если бы модель просто продолжала линейное выведение, кривая ушла бы
    // в минус — такой график и «отрицательное опьянение» в интерфейсе были бы багом.
    @Test
    fun curveNeverNegative() {
        val p = params(80.0, Sex.MALE)
        // Сразу две порции: пиво и стопка в один момент, чтобы кривая была повыше и подольше.
        val drinks = listOf(drink(0), drink(0, volumeMl = 500.0, abv = 5.0, kind = DrinkKind.MUG))
        // Смотрим сутки: за это время всё гарантированно выведется, включая «хвост» около нуля.
        val points = BacEngine.curve(drinks, p, t0, t0 + 24 * 60 * minute)
        assertTrue(points.all { it.bacGPerL >= 0.0 })
    }

    // Инвариант формы кривой: после пика уровень обязан только убывать — выпитое больше
    // не поступает, работает только выведение. Это защита от «второй волны», которой
    // физически быть не может.
    @Test
    fun monotonicDecreaseAfterPeak() {
        val p = params(90.0, Sex.MALE)
        val drinks = listOf(drink(0), drink(15), drink(30))
        // Кривая за 12 часов: пик и весь спад точно попадают в это окно.
        val points = BacEngine.curve(drinks, p, t0, t0 + 12 * 60 * minute)
        // Индекс максимума: maxByOrNull вернёт null только для пустого списка, поэтому `!!` безопасен.
        val peakIndex = points.indices.maxByOrNull { points[it].bacGPerL }!!
        // Идём от пика до конца и сравниваем каждую точку с предыдущей. Допуск 1e-9 нужен
        // из-за погрешности сложения Double: плато «ноль-в-ноль» иначе считалось бы ростом.
        for (i in peakIndex + 1 until points.size) {
            assertTrue(
                "После пика кривая должна убывать (${points[i - 1].bacGPerL} → ${points[i].bacGPerL})",
                points[i].bacGPerL <= points[i - 1].bacGPerL + 1e-9,
            )
        }
    }

    // Скорость выведения: у модели она постоянная (кинетика нулевого порядка), около 0,15 ‰/ч.
    // Проверяем это там, где примеси всасывания уже нет, иначе наклон был бы искажён.
    @Test
    fun eliminationSlope_atHighBac_isAbout015PerHour() {
        // Большая доза натощак (ka = 2,0): через 4 часа всасывание закончено,
        // концентрация 1,5–2,5 ‰ — здесь работает нулевой порядок выведения.
        val p = params(80.0, Sex.MALE, ka = 2.0)
        // 500 мл 40 % залпом — искусственно большая доза, нужная только для этого теста.
        val drinks = listOf(drink(0, volumeMl = 500.0, abv = 40.0, kind = DrinkKind.CUSTOM))
        val startMinutes = 4 * 60L
        val points = BacEngine.curve(drinks, p, t0, t0 + 12 * 60 * minute)

        // Берём точку ровно на 4-м часу. Сначала убеждаемся, что она попала в нужный диапазон
        // 1,5–2,5 ‰: если нет, тест проверяет не тот режим и его результат ничего не значит.
        val start = points.first { it.atMillis == t0 + startMinutes * minute }
        assertTrue("Ожидался уровень 1,5–2,5 ‰, получено ${start.bacGPerL}", start.bacGPerL in 1.5..2.5)

        // Разница уровней за час и есть скорость выведения в ‰/ч.
        val afterHour = points.first { it.atMillis == start.atMillis + 60 * minute }
        val slope = start.bacGPerL - afterHour.bacGPerL
        assertEquals("Скорость выведения должна быть около 0,15 ‰/ч", 0.15, slope, 0.02)
    }

    // Инвариант детерминизма: один и тот же вход обязан давать один и тот же выход.
    // Без этого нельзя ни сохранить прогноз, ни сравнить два запуска, ни воспроизвести баг.
    @Test
    fun deterministic_sameInputSameOutput() {
        val p = params(70.0, Sex.FEMALE)
        // Первый вызов — пиво, через 20 минут вино.
        val drinks = listOf(drink(0, 500.0, 5.0, DrinkKind.MUG), drink(20, 150.0, 12.0, DrinkKind.WINE_GLASS))
        // Два одинаковых расчёта подряд. Списки точек — data-классы, поэтому assertEquals
        // сравнивает их по значению полей, а не по ссылке: содержимое должно совпасть целиком.
        val first = BacEngine.curve(drinks, p, t0, t0 + 6 * 60 * minute)
        val second = BacEngine.curve(drinks, p, t0, t0 + 6 * 60 * minute)
        assertEquals(first, second)
    }

    // Инвариант независимости от порядка: вклады порций складываются, поэтому перестановка
    // элементов списка не должна менять кривую. Тест ловит скрытую зависимость от порядка
    // (например, если бы движок считал только «последнюю» порцию).
    @Test
    fun orderOfDrinksDoesNotMatter() {
        val p = params(90.0, Sex.MALE)
        val drinks = listOf(drink(0), drink(15), drink(30))
        // Тот же набор, но перемешанный: третья порция стала первой.
        val shuffled = listOf(drinks[2], drinks[0], drinks[1])
        val a = BacEngine.curve(drinks, p, t0, t0 + 8 * 60 * minute)
        val b = BacEngine.curve(shuffled, p, t0, t0 + 8 * 60 * minute)
        // Сначала сравниваем размер: если сетка времени разошлась, дальше сравнивать нечего.
        assertEquals(a.size, b.size)
        // Затем точки попарно: одинаковые моменты времени и одинаковый уровень с точностью 1e-9.
        for (i in a.indices) {
            assertEquals(a[i].atMillis, b[i].atMillis)
            assertEquals(a[i].bacGPerL, b[i].bacGPerL, 1e-9)
        }
    }

    // Обратный отсчёт до порога: сколько времени осталось, пока уровень не опустится
    // до заданного значения. Плюс важный крайний случай — порог уже не превышен.
    @Test
    fun timeToThreshold_correct() {
        val p = params(90.0, Sex.MALE)
        val drinks = listOf(drink(0), drink(15), drink(30))

        // До порога 0,3 ‰ — около 3,17 ч от первой порции
        assertMinutes(3.17 * 60, BacEngine.timeToThreshold(drinks, p, t0, 0.3))

        // Если порог уже не превышен — возвращается текущий момент
        // (ждать нечего, поэтому функция не выдумывает будущее время).
        val late = t0 + 12 * 60 * minute
        assertEquals(late, BacEngine.timeToThreshold(drinks, p, late, 0.3))
    }

    // Удаление порции: история пересчитывается заново. Проверяем и текущий уровень,
    // и «прошлое» — приложение показывает график за весь вечер, и он тоже обязан меняться.
    @Test
    fun deleteDrink_recomputesHistory() {
        val p = params(90.0, Sex.MALE)
        val all = listOf(drink(0), drink(15), drink(30))
        // Тот же набор без третьей порции — как будто пользователь удалил запись.
        val less = listOf(drink(0), drink(15))

        val before = BacEngine.bacAt(all, p, t0 + 60 * minute)
        val after = BacEngine.bacAt(less, p, t0 + 60 * minute)
        assertTrue("После удаления порции уровень должен упасть", after < before)

        // Прошлое тоже пересчитывается: значение после удалённой порции меньше
        // (в момент 40 минут третья порция уже всосалась и добавляла свой вклад).
        val beforeAt40 = BacEngine.bacAt(all, p, t0 + 40 * minute)
        val afterAt40 = BacEngine.bacAt(less, p, t0 + 40 * minute)
        assertTrue(afterAt40 < beforeAt40)
    }

    // Добавление порции: уровень должен подняться. Проверяем именно рост, а не конкретное
    // число, потому что нас интересует реакция модели на новую запись.
    @Test
    fun addDrink_raisesLevelImmediately() {
        val p = params(80.0, Sex.MALE)
        val now = t0 + 30 * minute
        val before = BacEngine.bacAt(listOf(drink(0)), p, now)
        // Порция, добавленная «сейчас», начинает всасываться с текущей минуты,
        // поэтому рост проверяем через минуту после добавления.
        val after = BacEngine.bacAt(listOf(drink(0), drink(30)), p, now + minute)
        assertTrue("Добавленная порция должна поднять уровень", after > before)
    }

    // Границы уровней опьянения: проверяем ровно те значения, где категория меняется.
    // Ошибка на единицу в условии (`<` вместо `<=`) видна только на границе, поэтому
    // каждая граница проверяется дважды — чуть ниже и ровно в точке перехода.
    @Test
    fun levelBoundaries_exact() {
        assertEquals(IntoxicationLevel.SOBER, IntoxicationLevel.forBac(0.0))
        assertEquals(IntoxicationLevel.SOBER, IntoxicationLevel.forBac(0.199))
        assertEquals(IntoxicationLevel.MILD, IntoxicationLevel.forBac(0.20))
        assertEquals(IntoxicationLevel.MILD, IntoxicationLevel.forBac(0.499))
        assertEquals(IntoxicationLevel.MODERATE, IntoxicationLevel.forBac(0.50))
        assertEquals(IntoxicationLevel.MODERATE, IntoxicationLevel.forBac(1.499))
        assertEquals(IntoxicationLevel.STRONG, IntoxicationLevel.forBac(1.50))
        assertEquals(IntoxicationLevel.STRONG, IntoxicationLevel.forBac(2.499))
        // 2,5 ‰ — уже опасная зона, поэтому верхняя граница тоже проверяется.
        assertEquals(IntoxicationLevel.DANGEROUS, IntoxicationLevel.forBac(2.50))
        assertEquals(IntoxicationLevel.DANGEROUS, IntoxicationLevel.forBac(5.0))
    }

    // Пересчёт единиц измерения: пользователь может показывать промилле, проценты,
    // мг/100 мл или мг/л. Главное свойство — «туда и обратно» без потерь: любое
    // преобразование обязано быть обратимым, иначе цифры на экране «поплывут».
    @Test
    fun unitsConversion_roundTrip() {
        val bac = 0.42
        // Проходим по всем вариантам единиц из enum, а не по одному: новый вариант
        // в Units автоматически попадёт под проверку.
        Units.entries.forEach { units ->
            val shown = units.fromBac(bac)
            assertEquals(bac, units.toBac(shown), 1e-9)
        }
        // И фиксируем конкретные коэффициенты, чтобы тест ловил случайную правку формулы.
        assertEquals(0.42, Units.PERMILLE.fromBac(0.42), 1e-9)
        assertEquals(0.042, Units.PERCENT.fromBac(0.42), 1e-9)
        assertEquals(42.0, Units.MG_PER_100ML.fromBac(0.42), 1e-9)
        assertEquals(420.0, Units.MG_PER_LITER.fromBac(0.42), 1e-9)
    }

    // Форматирование времени: тест фиксирует тексты, которые видит пользователь.
    // Сравниваем строки целиком — так проверяются и единицы, и пробелы, и знак минус.
    @Test
    fun timeFormatter_ru() {
        // Часы и минуты вместе, потом только минуты и ровно один час.
        assertEquals("2 ч 14 мин", Formatters.formatDuration((2 * 60 + 14) * minute))
        assertEquals("14 мин", Formatters.formatDuration(14 * minute))
        assertEquals("1 ч", Formatters.formatDuration(60 * minute))
        // Меньше минуты — не «0 мин», а понятная человеку формулировка.
        assertEquals("меньше минуты", Formatters.formatDuration(30_000L))
        // Возраст записи: в прошлом со знаком минус, в будущем — с плюсом.
        assertEquals("−1 ч 20 мин", Formatters.formatAge(t0, t0 + 80 * minute))
        assertEquals("+15 мин", Formatters.formatAge(t0 + 15 * minute, t0))
    }

    // Далеко за пределами вечера: через двое суток весь алкоголь выведен, уровень — ноль.
    // Допуск 1e-6, а не строгий ноль: сумма маленьких остатков Double может дать 1e-12.
    @Test
    fun fortyEightHoursLater_isZero() {
        val p = params(80.0, Sex.MALE)
        val drinks = listOf(drink(0), drink(15), drink(30))
        val value = BacEngine.bacAt(drinks, p, t0 + 48 * 60 * minute)
        assertEquals(0.0, value, 1e-6)
    }

    // Прогноз «через сколько можно за руль»: два противоположных случая.
    // Сначала сессия с тремя порциями — ехать нельзя, остаток времени положительный.
    // Затем пустая сессия — ехать можно, и остаток равен нулю, а не null.
    @Test
    fun forecast_thresholdLogic() {
        val p = params(90.0, Sex.MALE)
        val drinks = listOf(drink(0), drink(15), drink(30))
        val now = t0 + 60 * minute

        val forecast = TimeToDrive.forecast(drinks, p, now, 0.3)
        // assertFalse здесь не импортирован, поэтому отрицание записано через assertTrue.
        assertTrue(!forecast.canDriveNow)
        // Сначала проверяем, что значение вообще известно (не null), затем — что оно больше нуля.
        assertTrue(forecast.remainingMillis != null && forecast.remainingMillis!! > 0)

        val sober = TimeToDrive.forecast(emptyList(), p, now, 0.3)
        assertTrue(sober.canDriveNow)
        assertEquals(0L, sober.remainingMillis)
    }

    // Старые записи не должны влиять на текущий уровень: через 30 часов алкоголь уже
    // полностью вывелся. Без такой отсечки большая старая доза портила бы все прогнозы.
    @Test
    fun drinksOlderThan24HoursDoNotAffectLevel() {
        // Порция 30 часов назад уже всосалась и вывелась: на текущий уровень она не влияет,
        // даже если доза была очень большой.
        val p = params(80.0, Sex.MALE)
        // 1 литр 40 % — намеренно огромная доза, чтобы «хвост» точно был заметен, если он есть.
        val old = Drink(
            id = "old",
            atMillis = t0 - 30 * 60 * minute,
            volumeMl = 1000.0,
            abvPercent = 40.0,
            kind = DrinkKind.CUSTOM,
        )
        // Ноль с очень узким допуском: остаток должен быть ровно нулевым, а не «почти».
        assertEquals(0.0, BacEngine.bacAt(listOf(old), p, t0), 1e-9)
    }

    // Порция из будущего (пользователь запланировал её заранее) не должна поднимать уровень
    // «сейчас»: спиртное ещё не выпито. Но когда время наступит, вклад обязан появиться.
    @Test
    fun futureDrinkDoesNotRaiseCurrentLevel() {
        val p = params(80.0, Sex.MALE)
        val future = Drink(
            id = "future",
            atMillis = t0 + 60 * minute,
            volumeMl = 500.0,
            abvPercent = 40.0,
            kind = DrinkKind.CUSTOM,
        )
        // В момент t0 порция ещё в будущем — уровень нулевой.
        assertEquals(0.0, BacEngine.bacAt(listOf(future), p, t0), 1e-9)
        // Через полтора часа она уже выпита и всосалась — уровень больше нуля.
        assertTrue(BacEngine.bacAt(listOf(future), p, t0 + 90 * minute) > 0.0)
    }
}
