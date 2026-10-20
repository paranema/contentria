package com.contentria.publishing.adapter.required.markdown

import com.contentria.publishing.application.required.MarkdownPlainText
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.Code
import org.commonmark.node.CustomBlock
import org.commonmark.node.CustomNode
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.Text
import org.commonmark.parser.Parser
import org.springframework.stereotype.Component

/**
 * The words a reader sees, collected from the commonmark syntax tree.
 *
 * Not commonmark's `TextContentRenderer`, which the legacy summary used: it writes a link as
 * `"text" (url)` and strikethrough as `/text/`, so addresses and marks leaked into summaries.
 * Link addresses, images, code blocks and raw HTML are left out; the text of links is kept.
 */
@Component
class CommonmarkPlainText : MarkdownPlainText {

    private val parser = Parser.builder().extensions(listOf(TablesExtension.create(), StrikethroughExtension.create())).build()

    /** Line breaks and runs of spaces become single spaces: a summary is one line of text. */
    override fun of(markdown: String): String =
        TextCollector().also { parser.parse(markdown).accept(it) }.text.replace(WHITESPACE, " ").trim()

    private class TextCollector : AbstractVisitor() {
        private val out = StringBuilder()
        val text: String get() = out.toString()

        override fun visit(text: Text) { out.append(text.literal) }
        override fun visit(code: Code) { out.append(code.literal) }
        override fun visit(softLineBreak: SoftLineBreak) { out.append(' ') }
        override fun visit(hardLineBreak: HardLineBreak) { out.append(' ') }

        // Blocks end in a space so the last word of one does not run into the next.
        override fun visit(paragraph: Paragraph) { visitChildren(paragraph); out.append(' ') }
        override fun visit(heading: Heading) { visitChildren(heading); out.append(' ') }
        override fun visit(customBlock: CustomBlock) { visitChildren(customBlock); out.append(' ') }
        override fun visit(customNode: CustomNode) { visitChildren(customNode); out.append(' ') }

        // Not prose: an image's alt text, code, and raw HTML.
        override fun visit(image: Image) = Unit
        override fun visit(fencedCodeBlock: FencedCodeBlock) = Unit
        override fun visit(indentedCodeBlock: IndentedCodeBlock) = Unit
        override fun visit(htmlBlock: HtmlBlock) = Unit
        override fun visit(htmlInline: HtmlInline) = Unit
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}
