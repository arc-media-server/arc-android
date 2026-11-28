package org.arcmediaserver.android.ui.mobile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.arcmediaserver.android.data.MediaCard
import org.arcmediaserver.android.ui.components.Artwork
import org.arcmediaserver.android.ui.components.EpisodeArtwork
import org.arcmediaserver.android.ui.components.ProgressLine
import org.arcmediaserver.android.ui.components.WatchBadge
import org.arcmediaserver.android.ui.components.cardSubtitle
import org.arcmediaserver.android.ui.components.cardTitle
import org.arcmediaserver.android.ui.theme.ArcColors

/** The page's side margin: Material's 16dp on a phone, 24dp from a small tablet up. */
@Composable
internal fun pageMargin(): Dp = if (LocalConfiguration.current.screenWidthDp >= 600) 24.dp else 16.dp

/** Card widths: a phone shows three posters and a half, so the row reads as scrollable. */
@Composable
internal fun posterWidth(): Dp = if (LocalConfiguration.current.screenWidthDp >= 600) 140.dp else 112.dp

@Composable
internal fun thumbWidth(): Dp = if (LocalConfiguration.current.screenWidthDp >= 600) 280.dp else 240.dp

private val Shape = RoundedCornerShape(8.dp)

/**
 * A title's card for touch: the same artwork, watched badge and progress line as the TV's card,
 * with its name and the line under it (an episode's "S1 · E4", a film's year).
 */
@Composable
fun MobileCard(card: MediaCard, landscape: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, width: Dp? = null) {
    val cardWidth = width ?: if (landscape) thumbWidth() else posterWidth()
    Column(modifier.width(cardWidth).clip(Shape).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(if (landscape) 16f / 9f else 2f / 3f).clip(Shape)) {
            val title = cardTitle(card)
            if (landscape && card.item.isEpisode) {
                // An episode's still keeps spoilers back while the profile hides them.
                EpisodeArtwork(card.item, card.thumb, label = title, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            } else {
                Artwork(if (landscape) card.thumb else card.poster, label = title, modifier = Modifier.fillMaxWidth().aspectRatio(if (landscape) 16f / 9f else 2f / 3f))
            }
            WatchBadge(card.item, newEpisodes = card.newEpisodes.takeIf { card.seasonNumber != null })
            ProgressLine(card.progress)
        }
        Spacer(Modifier.height(6.dp))
        Text(cardTitle(card), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        cardSubtitle(card)?.takeIf { it.isNotEmpty() }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = ArcColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(4.dp))
    }
}

/** A titled row of cards that scrolls sideways, lined up with the page's margin. */
@Composable
fun MobileShelf(title: String, cards: List<MediaCard>, landscape: Boolean, onClick: (MediaCard) -> Unit, modifier: Modifier = Modifier) {
    val margin = pageMargin()
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = margin, end = margin, bottom = 8.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = margin), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(cards, key = { it.key }) { card -> MobileCard(card, landscape, onClick = { onClick(card) }) }
        }
    }
}

/** A person's round photo (or initials) with their name and a line under it: cast and search. */
@Composable
fun MobilePersonCard(name: String, image: String?, sub: String?, onClick: () -> Unit) {
    Column(Modifier.width(88.dp).clip(Shape).clickable(onClick = onClick).padding(vertical = 4.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
        Artwork(image, label = org.arcmediaserver.android.ui.person.initials(name), modifier = Modifier.size(72.dp).clip(CircleShape))
        Spacer(Modifier.height(6.dp))
        Text(name, style = MaterialTheme.typography.labelMedium, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
        if (!sub.isNullOrBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = ArcColors.Muted, maxLines = 1, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
    }
}

/** A row of people under a heading. */
@Composable
internal fun PeopleRow(title: String, people: List<Triple<String, String?, String?>>, onClick: (Int) -> Unit) {
    val margin = pageMargin()
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = margin, end = margin, bottom = 8.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = margin), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(people.size) { i -> val (n, img, sub) = people[i]; MobilePersonCard(n, img, sub) { onClick(i) } }
        }
    }
}

