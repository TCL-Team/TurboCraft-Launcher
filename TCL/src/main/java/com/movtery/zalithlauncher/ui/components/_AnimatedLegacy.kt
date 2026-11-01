/*
 * Zalith Launcher 2
 * Copyright (C) 2025-2026 MovTery <movtery228@qq.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package com.movtery.zalithlauncher.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * TurboCraft 旧版连锁动画布局 API 的兼容层。
 *
 * 新版 [AnimatedColumn] / [AnimatedRow] / [AnimatedLazyColumn] 通过 scope 声明子项，
 * 而 TurboCraft 既有页面仍以 `AnimatedItem(scope)` 的形式书写。这里保留旧签名，
 * 让两套写法共用同一套动画实现。
 */
@Composable
fun AnimatedColumn(
    modifier: Modifier = Modifier,
    isVisible: Boolean,
    baseDelay: Int = 0,
    delayIncrement: Int = 50,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(12.dp),
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable AnimatedColumnScope.(ColumnScope) -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment
    ) {
        // 旧版由调用方自行累加延迟，这里按子项索引模拟
        LegacyColumnScope(this, isVisible, baseDelay, delayIncrement).content(this@Column)
    }
}

private class LegacyColumnScope(
    private val columnScope: ColumnScope,
    private val isVisible: Boolean,
    private val baseDelay: Int,
    private val delayIncrement: Int
) : AnimatedColumnScope, ColumnScope by columnScope {
    private var itemIndex = 0

    @Composable
    override fun AnimatedItem(
        delay: Int?,
        content: @Composable ColumnScope.(ctx: AnimatedContext) -> Unit
    ) {
        val ctx = rememberAnimatedContext(
            isVisible = isVisible,
            index = 0,
            extraDelayMillis = delay ?: (baseDelay + itemIndex++ * delayIncrement),
            defaultDirection = AnimatedDirection.Top
        )
        content(ctx)
    }
}

/**
 * 旧版连锁动画水平容器，见 [AnimatedColumn] 的兼容说明
 */
@Composable
fun AnimatedRow(
    modifier: Modifier = Modifier,
    isVisible: Boolean,
    baseDelay: Int = 0,
    delayIncrement: Int = 50,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(12.dp),
    content: @Composable AnimatedRowScope.(RowScope) -> Unit
) {
    Row(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement
    ) {
        LegacyRowScope(this, isVisible, baseDelay, delayIncrement).content(this@Row)
    }
}

private class LegacyRowScope(
    private val rowScope: RowScope,
    private val isVisible: Boolean,
    private val baseDelay: Int,
    private val delayIncrement: Int
) : AnimatedRowScope, RowScope by rowScope {
    private var itemIndex = 0

    @Composable
    override fun AnimatedItem(
        delay: Int?,
        content: @Composable RowScope.(ctx: AnimatedContext) -> Unit
    ) {
        val ctx = rememberAnimatedContext(
            isVisible = isVisible,
            index = 0,
            extraDelayMillis = delay ?: (baseDelay + itemIndex++ * delayIncrement),
            defaultDirection = AnimatedDirection.Right
        )
        content(ctx)
    }
}

/**
 * 旧版连锁动画懒加载列表，见 [AnimatedColumn] 的兼容说明
 */
@Composable
fun AnimatedLazyColumn(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    isVisible: Boolean,
    baseDelay: Int = 0,
    delayIncrement: Int = 50,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(12.dp),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: AnimatedLazyListScope.(LazyListScope) -> Unit
) {
    LazyColumn(
        modifier = modifier,
        state = state,
        verticalArrangement = verticalArrangement,
        contentPadding = contentPadding
    ) {
        LegacyLazyListScope(this, isVisible, baseDelay, delayIncrement).content(this@LazyColumn)
    }
}

private class LegacyLazyListScope(
    private val lazyListScope: LazyListScope,
    private val isVisible: Boolean,
    private val baseDelay: Int,
    private val delayIncrement: Int
) : AnimatedLazyListScope {
    private var itemIndex = 0

    override fun animatedItem(
        key: Any?,
        delay: Int?,
        content: @Composable androidx.compose.foundation.lazy.LazyItemScope.(ctx: AnimatedContext) -> Unit
    ) {
        val currentIndex = itemIndex++
        lazyListScope.item(key = key) {
            val ctx = rememberAnimatedContext(
                isVisible = isVisible,
                index = 0,
                extraDelayMillis = delay ?: (baseDelay + currentIndex * delayIncrement),
                defaultDirection = AnimatedDirection.Top
            )
            content(ctx)
        }
    }

    override fun <T> animatedItems(
        items: List<T>,
        delay: Int,
        key: ((item: T) -> Any)?,
        content: @Composable androidx.compose.foundation.lazy.LazyItemScope.(index: Int, item: T, ctx: AnimatedContext) -> Unit
    ) {
        lazyListScope.itemsIndexed(
            items = items,
            key = if (key != null) { _, item -> key(item) } else null
        ) { index, item ->
            val ctx = rememberAnimatedContext(
                isVisible = isVisible,
                index = index,
                extraDelayMillis = delay + baseDelay + index * delayIncrement,
                defaultDirection = AnimatedDirection.Top
            )
            content(index, item, ctx)
        }
    }
}
