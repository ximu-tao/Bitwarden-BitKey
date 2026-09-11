package com.x8bit.bitwarden.data.platform.util

/**
 * Returns the value of this nullable integer or zero when it is null.
 */
fun Int?.orZero(): Int = this ?: 0