package com.contentria.publishing.adapter.required.markdown

import com.contentria.publishing.application.required.MarkdownImages
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.Image
import org.commonmark.parser.Parser
import org.springframework.stereotype.Component

/**
 * The image nodes of the commonmark syntax tree: `![alt](url)` and reference images, wherever they
 * sit, and nothing that only looks like one inside code. Raw HTML `<img>` is not an image here.
 */
@Component
class CommonmarkImages : MarkdownImages {

    private val parser = Parser.builder().extensions(listOf(TablesExtension.create(), StrikethroughExtension.create())).build()

    override fun urlsIn(markdown: String): List<String> {
        val urls = mutableListOf<String>()
        parser.parse(markdown).accept(object : AbstractVisitor() {
            override fun visit(image: Image) {
                image.destination?.takeIf { it.isNotBlank() }?.let(urls::add)
                visitChildren(image)
            }
        })
        return urls
    }
}
