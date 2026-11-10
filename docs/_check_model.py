# -*- coding: utf-8 -*-
"""Проверка модели BAC (первый порядок всасывания + Михаэлис-Ментен выведение).
Считаем эталонные значения для промпта/тестов.
"""
RHO = 0.789          # г/мл этанола
KA = 1.5             # 1/ч
VMAX = 0.15          # г/л/ч
KM = 0.02            # г/л
DT = 1.0 / 60.0      # 1 минута в часах


def simulate(drinks, weight_kg, r, ka=KA, vmax=VMAX, km=KM, hours=24.0):
    """drinks: [(t_minutes, volume_ml, abv_percent)]"""
    vd = r * weight_kg
    n = int(hours * 60)
    # невсосавшаяся масса по порциям, г
    pend = [0.0] * len(drinks)
    grams = [v * (a / 100.0) * RHO for (_, v, a) in drinks]
    c = 0.0
    curve = [0.0] * (n + 1)
    for step in range(n + 1):
        t = step * DT
        for i, (tm, _, _) in enumerate(drinks):
            if tm / 60.0 <= t and pend[i] == 0.0 and grams[i] > 0:
                pend[i] = grams[i]
        absorbed_rate = ka * sum(pend)          # г/ч
        elim = vmax * c / (km + c) if c > 0 else 0.0
        dc = (absorbed_rate / vd - elim) * DT
        c = max(0.0, c + dc)
        for i in range(len(pend)):
            pend[i] = max(0.0, pend[i] - ka * pend[i] * DT)
        curve[step] = c
    return curve


def report(title, drinks, weight, r, ka=KA, hours=24.0):
    curve = simulate(drinks, weight, r, ka=ka, hours=hours)
    peak = max(curve)
    tpeak = curve.index(peak) / 60.0
    def at(minutes):
        return curve[int(minutes)]
    def time_to(thr):
        for i in range(len(curve) - 1, -1, -1):
            if curve[i] > thr:
                return (i + 1) / 60.0
        return 0.0
    print(f"--- {title}")
    print(f"    Vd={r*weight:.1f} л, порций={len(drinks)}")
    print(f"    пик {peak:.3f} ‰ в {tpeak*60:.0f} мин")
    for m in (15, 30, 45, 60, 90, 120):
        if m <= hours * 60:
            print(f"    C({m:>3} мин) = {at(m):.3f} ‰")
    print(f"    до 0.30 ‰: {time_to(0.30):.2f} ч; до 0.00: {time_to(0.001):.2f} ч")
    print()


report("A. М 80 кг, 1 стопка 50 мл 40%", [(0, 50, 40)], 80, 0.71)
report("B. Ж 70 кг, 1 кружка 500 мл 5%", [(0, 500, 5)], 70, 0.58)
report("C. М 90 кг, 3 стопки по 50 мл 40% за 30 мин", [(0, 50, 40), (15, 50, 40), (30, 50, 40)], 90, 0.71)
report("D. Ж 60 кг, 2 бокала 150 мл 12% (0 и 20 мин)", [(0, 150, 12), (20, 150, 12)], 60, 0.58)
report("E. М 80 кг, 500 мл 5% + 50 мл 40% (натощак ka=2.0)", [(0, 500, 5), (10, 50, 40)], 80, 0.71, ka=2.0)

