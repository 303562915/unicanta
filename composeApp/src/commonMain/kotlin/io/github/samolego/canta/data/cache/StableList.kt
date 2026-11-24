package io.github.samolego.canta.data.cache

/**
 * 列表的「就地合并」：新一份里和旧一份完全一样的元素，直接沿用**旧的那个实例**。
 *
 * 为什么需要它：Compose 的列表项要不要重组，看的是那个 item 的取值有没有变。网络回来一份新列表时，
 * 如果整份换成一个新 `List`（里面每个元素都是新对象），即使内容一个字节都没变，整张表也会被当成
 * 「全变了」重画一遍——滚动位置、正在展开的那一行、行的入场动画全跟着抖一下。而现实里一次刷新往往
 * 只有一两行变了。
 *
 * 用法：拿新列表之前先留一份旧的，赋值时过一遍这个函数。
 *
 * ```
 * val merged = stableMerge(posts, fresh) { it.id }
 * if (merged !== posts) posts = merged
 * ```
 *
 * 返回值的语义：
 *
 *  * 新旧完全一致（元素相等、顺序一致、而且旧的那一份本来就是同一批实例）→ 返回 [old] 本身，
 *    调用方一次状态写入都不会发生（`===` 判断能提前拦住重组）。
 *  * 否则返回一个新列表，里面没变的元素是旧实例、变了的是新对象。
 *
 * @param key 用来判断「这是不是同一行」的键（通常是 id 或包名）。
 */
inline fun <T, K> stableMerge(old: List<T>, new: List<T>, key: (T) -> K): List<T> {
    if (old.isEmpty()) return new
    if (new.isEmpty()) return new
    val index = HashMap<K, T>(old.size)
    for (item in old) index[key(item)] = item
    var changed = old.size != new.size
    val merged = ArrayList<T>(new.size)
    for (item in new) {
        val previous = index[key(item)]
        if (previous != null && previous == item) {
            merged += previous
        } else {
            merged += item
            changed = true
        }
    }
    // 元素都相等但顺序换了（列表重新排序）：那也是一次变化，得换一份。
    if (!changed) {
        for (i in merged.indices) {
            if (merged[i] !== old[i]) {
                changed = true
                break
            }
        }
    }
    return if (changed) merged else old
}
