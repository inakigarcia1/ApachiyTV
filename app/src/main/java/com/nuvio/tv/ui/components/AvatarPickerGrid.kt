package com.nuvio.tv.ui.components

import com.nuvio.tv.ui.theme.NuvioTheme

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlin.math.floor
import kotlin.math.max
import androidx.compose.animation.animateColorAsState
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.nuvio.tv.R
import com.nuvio.tv.data.remote.supabase.AvatarCatalogItem
import com.nuvio.tv.ui.screens.detail.requestFocusAfterFrames
import kotlinx.coroutines.delay

private val PinnedAvatarCategories = listOf("anime", "animation", "tv", "movie", "gaming")

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AvatarPickerGrid(
    avatars: List<AvatarCatalogItem>,
    selectedAvatarId: String?,
    onAvatarSelected: (AvatarCatalogItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val categories = remember(avatars) {
        buildList {
            add("all")

            val normalizedCategories = avatars
                .mapNotNull { avatar -> avatar.category.trim().takeIf { it.isNotEmpty() } }
                .distinct()

            PinnedAvatarCategories.forEach { category ->
                if (normalizedCategories.any { it.equals(category, ignoreCase = true) }) {
                    add(category)
                }
            }

            normalizedCategories
                .filterNot { category ->
                    PinnedAvatarCategories.any { it.equals(category, ignoreCase = true) }
                }
                .sortedBy { it.lowercase() }
                .forEach(::add)
        }
    }
    var selectedCategory by remember { mutableStateOf("all") }
    val categoryFocusRequester = remember { FocusRequester() }

    LaunchedEffect(categories) {
        if (selectedCategory !in categories) {
            selectedCategory = "all"
        }
    }

    val filteredAvatars = remember(avatars, selectedCategory) {
        if (selectedCategory == "all") avatars
        else avatars.filter { it.category.equals(selectedCategory, ignoreCase = true) }
    }
    val avatarRequesters = remember(filteredAvatars) {
        filteredAvatars.associate { it.id to FocusRequester() }
    }
    val firstAvatarRequester = filteredAvatars.firstOrNull()?.let { avatarRequesters[it.id] }
    val gridState = rememberLazyGridState()

    Column(modifier = modifier.fillMaxHeight()) {
        AvatarCategoryDropdown(
            categories = categories,
            selectedCategory = selectedCategory,
            onCategorySelected = { selectedCategory = it },
            focusRequester = categoryFocusRequester,
            downFocusRequester = firstAvatarRequester,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = NuvioTheme.spacing.md)
        )

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            val minCellWidth = 88.dp
            val horizontalSpacing = NuvioTheme.spacing.md
            val horizontalPadding = NuvioTheme.spacing.lg
            val availableWidth = maxWidth - horizontalPadding
            val columnCount = max(
                1,
                floor(
                    (availableWidth.value + horizontalSpacing.value) /
                        (minCellWidth.value + horizontalSpacing.value)
                ).toInt()
            )

            Box(modifier = Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(minSize = minCellWidth),
                    contentPadding = PaddingValues(
                        horizontal = NuvioTheme.spacing.sm,
                        vertical = NuvioTheme.spacing.xs
                    ),
                    horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
                    verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(end = 14.dp)
                ) {
                    itemsIndexed(filteredAvatars, key = { _, avatar -> avatar.id }) { index, avatar ->
                        AvatarGridItem(
                            avatar = avatar,
                            isSelected = avatar.id == selectedAvatarId,
                            focusRequester = avatarRequesters.getValue(avatar.id),
                            upFocusRequester = if (index < columnCount) categoryFocusRequester else null,
                            onClick = { onAvatarSelected(avatar) }
                        )
                    }
                }

                ApachiyVerticalScrollIndicator(
                    totalItems = gridState.layoutInfo.totalItemsCount,
                    firstVisibleIndex = gridState.firstVisibleItemIndex,
                    visibleCount = gridState.layoutInfo.visibleItemsInfo.size,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(vertical = NuvioTheme.spacing.xs)
                )
            }
        }
    }
}

@Composable
private fun ApachiyVerticalScrollIndicator(
    totalItems: Int,
    firstVisibleIndex: Int,
    visibleCount: Int,
    modifier: Modifier = Modifier
) {
    if (totalItems == 0) return

    val scrollFraction = if (totalItems <= 1) {
        0f
    } else {
        firstVisibleIndex.toFloat() / (totalItems - 1).toFloat()
    }
    val thumbFraction = (visibleCount.toFloat() / totalItems.toFloat()).coerceIn(0.15f, 1f)
    val trackColor = NuvioTheme.colors.Secondary.copy(alpha = 0.35f)
    val thumbColor = NuvioTheme.colors.Secondary

    BoxWithConstraints(
        modifier = modifier.width(10.dp)
    ) {
        val trackHeight = maxHeight
        val thumbHeight = maxOf(trackHeight * thumbFraction, 32.dp)
        val thumbOffset = (trackHeight - thumbHeight) * scrollFraction

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(5.dp))
                .background(trackColor)
        )
        Box(
            modifier = Modifier
                .width(10.dp)
                .height(thumbHeight)
                .align(Alignment.TopCenter)
                .offset(y = thumbOffset)
                .clip(RoundedCornerShape(5.dp))
                .background(thumbColor)
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun AvatarCategoryDropdown(
    categories: List<String>,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    focusRequester: FocusRequester,
    downFocusRequester: FocusRequester?,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }
    var anchorSize by remember { mutableStateOf(IntSize.Zero) }
    val selectedItemFocusRequester = remember { FocusRequester() }
    val selectedBringIntoViewRequester = remember { BringIntoViewRequester() }

    LaunchedEffect(expanded, selectedCategory) {
        if (!expanded) return@LaunchedEffect
        var focused = selectedItemFocusRequester.requestFocusAfterFrames(frames = 3)
        var attempt = 0
        while (!focused && attempt < 6) {
            delay(32)
            focused = runCatching { selectedItemFocusRequester.requestFocus() }.getOrDefault(false)
            attempt++
        }
        if (!focused) return@LaunchedEffect
        runCatching { selectedBringIntoViewRequester.bringIntoView() }
        delay(48)
        if (runCatching { selectedItemFocusRequester.requestFocus() }.getOrDefault(false)) {
            runCatching { selectedBringIntoViewRequester.bringIntoView() }
        }
    }

    Box(modifier = modifier) {
        Card(
            onClick = { expanded = !expanded },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .then(
                    if (downFocusRequester != null) {
                        Modifier.focusProperties { down = downFocusRequester }
                    } else {
                        Modifier
                    }
                )
                .onSizeChanged { anchorSize = it }
                .onFocusChanged { isFocused = it.isFocused },
            shape = CardDefaults.shape(shape = RoundedCornerShape(14.dp)),
            colors = CardDefaults.colors(
                containerColor = NuvioTheme.colors.BackgroundCard,
                focusedContainerColor = NuvioTheme.colors.FocusBackground
            ),
            border = CardDefaults.border(
                border = Border(
                    border = BorderStroke(NuvioTheme.spacing.hairline, NuvioTheme.colors.Border),
                    shape = RoundedCornerShape(14.dp)
                ),
                focusedBorder = Border(
                    border = BorderStroke(NuvioTheme.spacing.xxs, NuvioTheme.colors.FocusRing),
                    shape = RoundedCornerShape(14.dp)
                )
            ),
            scale = CardDefaults.scale(focusedScale = 1f, pressedScale = 1f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.xxs)
                ) {
                    Text(
                        text = stringResource(R.string.profile_avatar_category_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = NuvioTheme.colors.TextTertiary
                    )
                    Text(
                        text = categoryLabel(selectedCategory),
                        style = MaterialTheme.typography.titleMedium,
                        color = NuvioTheme.colors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) {
                        stringResource(R.string.cd_collapse, stringResource(R.string.profile_avatar_category_label))
                    } else {
                        stringResource(R.string.cd_expand, stringResource(R.string.profile_avatar_category_label))
                    },
                    tint = if (isFocused) NuvioTheme.colors.FocusRing else NuvioTheme.colors.TextSecondary
                )
            }
        }

        if (expanded) {
            val categoryListState = rememberLazyListState()
            val categoryLayoutInfo by remember { derivedStateOf { categoryListState.layoutInfo } }
            val menuHeight = minOf(52.dp * categories.size + 8.dp, 320.dp)

            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(0, anchorSize.height),
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true)
            ) {
            Box(
                modifier = Modifier
                    .width(with(LocalDensity.current) { anchorSize.width.toDp() })
                    .height(menuHeight)
                    .clip(RoundedCornerShape(14.dp))
                    .background(NuvioTheme.colors.BackgroundCard)
                    .border(
                        BorderStroke(NuvioTheme.spacing.hairline, NuvioTheme.colors.Border),
                        RoundedCornerShape(14.dp)
                    )
            ) {
                LazyColumn(
                    state = categoryListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(end = 14.dp, top = 4.dp, bottom = 4.dp)
                ) {
                    items(categories, key = { it }) { category ->
                        val isSelected = category == selectedCategory
                        var optionFocused by remember { mutableStateOf(false) }
                        val itemBackgroundColor = when {
                            optionFocused -> NuvioTheme.colors.Secondary
                            isSelected -> NuvioTheme.colors.FocusBackground
                            else -> Color.Transparent
                        }

                        DropdownMenuItem(
                            modifier = Modifier
                                .then(
                                    if (isSelected) {
                                        Modifier
                                            .focusRequester(selectedItemFocusRequester)
                                            .bringIntoViewRequester(selectedBringIntoViewRequester)
                                    } else {
                                        Modifier
                                    }
                                )
                                .padding(horizontal = 6.dp, vertical = NuvioTheme.spacing.xxs)
                                .background(itemBackgroundColor, RoundedCornerShape(10.dp))
                                .onFocusChanged { optionFocused = it.isFocused || it.hasFocus },
                            text = {
                                Text(
                                    text = categoryLabel(category),
                                    color = if (optionFocused) {
                                        NuvioTheme.colors.OnSecondary
                                    } else {
                                        NuvioTheme.colors.TextPrimary
                                    },
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                                )
                            },
                            onClick = {
                                onCategorySelected(category)
                                expanded = false
                            }
                        )
                    }
                }

                ApachiyVerticalScrollIndicator(
                    totalItems = categoryLayoutInfo.totalItemsCount,
                    firstVisibleIndex = categoryListState.firstVisibleItemIndex,
                    visibleCount = categoryLayoutInfo.visibleItemsInfo.size,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(vertical = 6.dp, horizontal = 2.dp)
                )
            }
            }
        }
    }
}

@Composable
private fun AvatarGridItem(
    avatar: AvatarCatalogItem,
    isSelected: Boolean,
    focusRequester: FocusRequester,
    upFocusRequester: FocusRequester?,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.1f else 1f,
        animationSpec = tween(150),
        label = "avatarScale"
    )
    val borderWidth by animateDpAsState(
        targetValue = when {
            isSelected -> 3.dp
            isFocused -> NuvioTheme.spacing.xxs
            else -> NuvioTheme.spacing.none
        },
        animationSpec = tween(120),
        label = "avatarBorder"
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            isSelected || isFocused -> NuvioTheme.colors.FocusRing
            else -> Color.Transparent
        },
        animationSpec = tween(120),
        label = "avatarBorderColor"
    )

    Box(
        modifier = Modifier
            .requiredSize(80.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .focusRequester(focusRequester)
            .then(
                if (upFocusRequester != null) {
                    Modifier.focusProperties { up = upFocusRequester }
                } else {
                    Modifier
                }
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clip(CircleShape)
            .border(borderWidth, borderColor, CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(avatar.imageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = avatar.displayName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
private fun categoryLabel(category: String): String {
    return when (category) {
        "all" -> stringResource(R.string.profile_avatar_category_all)
        "anime" -> stringResource(R.string.profile_avatar_category_anime)
        "animation" -> stringResource(R.string.profile_avatar_category_animation)
        "movie" -> stringResource(R.string.profile_avatar_category_movie)
        "tv" -> stringResource(R.string.profile_avatar_category_tv)
        "gaming" -> stringResource(R.string.profile_avatar_category_gaming)
        else -> category.replaceFirstChar { it.uppercase() }
    }
}
