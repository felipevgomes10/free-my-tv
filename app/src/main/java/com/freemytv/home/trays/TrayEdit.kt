package com.freemytv.home.trays

/**
 * Edit Mode for any Tray: one item is lifted and moves along the tray until it is
 * dropped (keeping [order]) or the edit is cancelled (keeping [original]).
 * Fixed items, such as "Todos os apps", are simply not part of [order].
 */
data class TrayEdit<K>(
    val original: List<K>,
    val order: List<K>,
    val lifted: K,
) {
    /** Moves the lifted item [steps] places to the right (negative: left), stopping at either end. */
    fun move(steps: Int): TrayEdit<K> {
        val from = order.indexOf(lifted)
        val to = (from + steps).coerceIn(0, order.lastIndex)
        if (from == to) return this
        val moved = order.toMutableList().apply {
            removeAt(from)
            add(to, lifted)
        }
        return copy(order = moved)
    }

    companion object {
        fun <K> start(order: List<K>, lifted: K): TrayEdit<K>? =
            if (lifted in order) TrayEdit(order, order, lifted) else null
    }
}
