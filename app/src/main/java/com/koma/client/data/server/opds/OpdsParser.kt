package com.koma.client.data.server.opds

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.io.InputStream

// ─── Data models ──────────────────────────────────────────────────────────────

data class OpdsFeed(
    val title: String,
    val entries: List<OpdsEntry>,
    val nextUrl: String? = null,
    val searchUrl: String? = null,
)

data class OpdsEntry(
    val id: String,
    val title: String,
    val author: String? = null,
    val summary: String? = null,
    val updated: String? = null,
    val thumbnailUrl: String? = null,
    val coverUrl: String? = null,
    val acquisitionLinks: List<OpdsLink> = emptyList(),
    val navigationLink: String? = null,
    val categories: List<String> = emptyList(),
) {
    /** True when the entry represents a downloadable book (has at least one acquisition link). */
    val isAcquisition: Boolean get() = acquisitionLinks.isNotEmpty()

    /** True when the entry represents a navigation node (sub-feed / folder). */
    val isNavigation: Boolean get() = navigationLink != null
}

data class OpdsLink(
    val href: String,
    val type: String,
    val rel: String = "",
)

// ─── Relation constants ────────────────────────────────────────────────────────

private const val REL_ACQUISITION = "http://opds-spec.org/acquisition"
private const val REL_IMAGE = "http://opds-spec.org/image"
private const val REL_THUMBNAIL = "http://opds-spec.org/image/thumbnail"
private const val REL_SUBSECTION = "subsection"
private const val REL_NEXT = "next"
private const val REL_SEARCH = "search"

private const val TYPE_ATOM_PROFILE = "profile=opds-catalog"
private const val TYPE_OPENSEARCH = "application/opensearchdescription+xml"

// ─── Parser ───────────────────────────────────────────────────────────────────

/**
 * Parses an OPDS Atom feed into an [OpdsFeed] using Android's built-in [XmlPullParser].
 *
 * Feed-level `<link>` elements (next page, search template) are extracted first.
 * Each `<entry>` is then parsed into an [OpdsEntry] with its associated links.
 *
 * Relative URLs are resolved against [baseUrl] if provided.
 */
object OpdsParser {

    /**
     * Parses the given [inputStream] as an OPDS Atom feed.
     *
     * @param inputStream  Raw response body (caller is responsible for closing it).
     * @param baseUrl      The request URL; used to resolve relative `href` values.
     * @throws XmlPullParserException on malformed XML.
     * @throws IOException on read errors.
     */
    @Throws(XmlPullParserException::class, IOException::class)
    fun parse(inputStream: InputStream, baseUrl: String = ""): OpdsFeed {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(inputStream, null)
        }

        var feedTitle = ""
        var nextUrl: String? = null
        var searchUrl: String? = null
        val entries = mutableListOf<OpdsEntry>()

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "title" -> {
                        // Only capture the feed-level title (depth 1), not entry titles
                        if (parser.depth == 2) {
                            feedTitle = parser.nextText()
                        }
                    }
                    "link" -> {
                        // Feed-level links (not inside an <entry>)
                        if (parser.depth == 2) {
                            val rel = parser.getAttributeValue(null, "rel") ?: ""
                            val href = parser.getAttributeValue(null, "href") ?: ""
                            val type = parser.getAttributeValue(null, "type") ?: ""
                            val resolved = resolveUrl(href, baseUrl)
                            when {
                                rel == REL_NEXT -> nextUrl = resolved
                                rel == REL_SEARCH && (type == TYPE_OPENSEARCH || type.isBlank()) ->
                                    searchUrl = resolved
                            }
                        }
                    }
                    "entry" -> {
                        entries.add(parseEntry(parser, baseUrl))
                    }
                }
            }
            eventType = parser.next()
        }

        return OpdsFeed(
            title = feedTitle,
            entries = entries,
            nextUrl = nextUrl,
            searchUrl = searchUrl,
        )
    }

    // ─── Entry parsing ────────────────────────────────────────────────────────

    private fun parseEntry(parser: XmlPullParser, baseUrl: String): OpdsEntry {
        var id = ""
        var title = ""
        var author: String? = null
        var summary: String? = null
        var updated: String? = null
        var thumbnailUrl: String? = null
        var coverUrl: String? = null
        var navigationLink: String? = null
        val acquisitionLinks = mutableListOf<OpdsLink>()
        val categories = mutableListOf<String>()

        val entryDepth = parser.depth

        var eventType = parser.next()
        while (!(eventType == XmlPullParser.END_TAG && parser.depth == entryDepth)) {
            if (eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "id" -> id = parser.nextText()
                    "title" -> title = parser.nextText()
                    "updated" -> updated = parser.nextText()
                    "summary", "content" -> {
                        // Prefer summary; fall back to content if summary is absent
                        val text = parser.nextText()
                        if (summary == null) summary = text.trim().ifBlank { null }
                    }
                    "author" -> author = parseAuthorName(parser)
                    "category" -> {
                        val term = parser.getAttributeValue(null, "term")
                            ?: parser.getAttributeValue(null, "label")
                        if (!term.isNullOrBlank()) categories.add(term)
                    }
                    "link" -> {
                        val rel = parser.getAttributeValue(null, "rel") ?: ""
                        val href = parser.getAttributeValue(null, "href") ?: ""
                        val type = parser.getAttributeValue(null, "type") ?: ""
                        val resolved = resolveUrl(href, baseUrl)

                        when {
                            rel == REL_THUMBNAIL -> thumbnailUrl = resolved
                            rel == REL_IMAGE && thumbnailUrl == null -> coverUrl = resolved
                            rel == REL_IMAGE -> coverUrl = resolved
                            rel.startsWith(REL_ACQUISITION) ->
                                acquisitionLinks.add(OpdsLink(resolved, type, rel))
                            (rel == REL_SUBSECTION || type.contains(TYPE_ATOM_PROFILE)) ->
                                if (navigationLink == null) navigationLink = resolved
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        return OpdsEntry(
            id = id,
            title = title,
            author = author,
            summary = summary,
            updated = updated,
            thumbnailUrl = thumbnailUrl ?: coverUrl,
            coverUrl = coverUrl,
            acquisitionLinks = acquisitionLinks,
            navigationLink = navigationLink,
            categories = categories,
        )
    }

    private fun parseAuthorName(parser: XmlPullParser): String? {
        val depth = parser.depth
        var name: String? = null
        var eventType = parser.next()
        while (!(eventType == XmlPullParser.END_TAG && parser.depth == depth)) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "name") {
                name = parser.nextText().trim().ifBlank { null }
            }
            eventType = parser.next()
        }
        return name
    }

    // ─── URL resolution ───────────────────────────────────────────────────────

    /**
     * Resolves [href] against [base]. If [href] is already absolute (starts with
     * http/https or is blank), returns it as-is.
     */
    internal fun resolveUrl(href: String, base: String): String {
        if (href.isBlank()) return href
        if (href.startsWith("http://") || href.startsWith("https://")) return href
        if (base.isBlank()) return href

        val trimmedBase = base.trimEnd('/')
        val normalizedHref = if (href.startsWith("/")) href else "/$href"
        // Extract origin from base (scheme + host + optional port)
        val origin = run {
            val schemeEnd = trimmedBase.indexOf("://")
            if (schemeEnd < 0) return@run trimmedBase
            val hostStart = schemeEnd + 3
            val pathStart = trimmedBase.indexOf('/', hostStart)
            if (pathStart < 0) trimmedBase else trimmedBase.substring(0, pathStart)
        }
        return "$origin$normalizedHref"
    }
}
