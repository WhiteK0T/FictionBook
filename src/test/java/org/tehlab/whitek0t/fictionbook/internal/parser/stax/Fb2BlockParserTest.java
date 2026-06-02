package org.tehlab.whitek0t.fictionbook.internal.parser.stax;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.tehlab.whitek0t.fictionbook.dto.block.*;
import org.tehlab.whitek0t.fictionbook.dto.inline.*;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import java.io.StringReader;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive tests for {@link Fb2BlockParser}.
 *
 * <p>Covers all block elements: Paragraph, EmptyLine, Subtitle, Poem, Table, Cite, Epigraph.
 * Tests include basic cases, edge cases, nested structures, and forgiving mode (unknown tags).</p>
 */
@DisplayName("Fb2BlockParser Tests")
class Fb2BlockParserTest {

    private Fb2BlockParser parser;
    private XMLInputFactory factory;

    @BeforeEach
    void setUp() {
        factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, false);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        parser = new Fb2BlockParser(factory);
    }

    // ========================================================================
    // PARAGRAPH TESTS
    // ========================================================================

    @Nested
    @DisplayName("Paragraph Parsing")
    class ParagraphTests {

        @Test
        @DisplayName("should parse simple paragraph with text")
        void shouldParseSimpleParagraph() throws Exception {
            String xml = "<p>Простой текст</p>";
            Paragraph p = (Paragraph) parseFirstBlock(xml, "p");

            assertThat(p.elements()).hasSize(1);
            assertThat(p.elements().get(0)).isInstanceOf(Text.class);
            assertThat(((Text) p.elements().get(0)).value()).isEqualTo("Простой текст");
        }

        @Test
        @DisplayName("should parse paragraph with bold text")
        void shouldParseParagraphWithBold() throws Exception {
            String xml = "<p>Обычный <strong>жирный</strong> текст</p>";
            Paragraph p = (Paragraph) parseFirstBlock(xml, "p");

            assertThat(p.elements()).hasSize(3);
            assertThat(p.elements().get(0)).isInstanceOf(Text.class);
            assertThat(p.elements().get(1)).isInstanceOf(Strong.class);
            assertThat(p.elements().get(2)).isInstanceOf(Text.class);

            Strong strong = (Strong) p.elements().get(1);
            assertThat(strong.elements()).hasSize(1);
            assertThat(((Text) strong.elements().get(0)).value()).isEqualTo("жирный");
        }

        @Test
        @DisplayName("should parse paragraph with italic text")
        void shouldParseParagraphWithItalic() throws Exception {
            String xml = "<p>Обычный <emphasis>курсив</emphasis> текст</p>";
            Paragraph p = (Paragraph) parseFirstBlock(xml, "p");

            assertThat(p.elements()).hasSize(3);
            assertThat(p.elements().get(1)).isInstanceOf(Emphasis.class);
        }

        @Test
        @DisplayName("should parse paragraph with nested formatting")
        void shouldParseParagraphWithNestedFormatting() throws Exception {
            String xml = "<p>Текст <strong>жирный <emphasis>и курсив</emphasis></strong></p>";
            Paragraph p = (Paragraph) parseFirstBlock(xml, "p");

            assertThat(p.elements()).hasSize(2);
            Strong strong = (Strong) p.elements().get(1);
            assertThat(strong.elements()).hasSize(2);
            assertThat(strong.elements().get(1)).isInstanceOf(Emphasis.class);
        }

        @Test
        @DisplayName("should parse paragraph with link")
        void shouldParseParagraphWithLink() throws Exception {
            String xml = "<p>Ссылка <a l:href=\"#note1\" type=\"note\">[1]</a></p>";
            Paragraph p = (Paragraph) parseFirstBlock(xml, "p");

            assertThat(p.elements()).hasSize(2);
            Link link = (Link) p.elements().get(1);
            assertThat(link.href()).isEqualTo("#note1");
            assertThat(link.type()).isEqualTo("note");
            assertThat(link.elements()).hasSize(1);
        }

        @Test
        @DisplayName("should parse paragraph with image")
        void shouldParseParagraphWithImage() throws Exception {
            String xml = "<p>Текст <image l:href=\"#img1\" alt=\"Картинка\"/></p>";
            Paragraph p = (Paragraph) parseFirstBlock(xml, "p");

            assertThat(p.elements()).hasSize(2);
            ImageRef img = (ImageRef) p.elements().get(1);
            assertThat(img.href()).isEqualTo("#img1");
            assertThat(img.alt()).isEqualTo("Картинка");
        }

        @Test
        @DisplayName("should parse paragraph with sub and sup")
        void shouldParseParagraphWithSubSup() throws Exception {
            String xml = "<p>H<sub>2</sub>O и x<sup>2</sup></p>";
            Paragraph p = (Paragraph) parseFirstBlock(xml, "p");

            // Текст "O и x" склеивается в один Text элемент
            assertThat(p.elements()).hasSize(4);
            assertThat(p.elements().get(0)).isInstanceOf(Text.class);
            assertThat(((Text) p.elements().get(0)).value()).isEqualTo("H");
            assertThat(p.elements().get(1)).isInstanceOf(Sub.class);
            assertThat(p.elements().get(2)).isInstanceOf(Text.class);
            assertThat(((Text) p.elements().get(2)).value()).isEqualTo("O и x");
            assertThat(p.elements().get(3)).isInstanceOf(Sup.class);
        }

        @Test
        @DisplayName("should parse paragraph with strikethrough")
        void shouldParseParagraphWithStrikethrough() throws Exception {
            String xml = "<p>Обычный <strikethrough>зачёркнутый</strikethrough> текст</p>";
            Paragraph p = (Paragraph) parseFirstBlock(xml, "p");

            assertThat(p.elements()).hasSize(3);
            assertThat(p.elements().get(1)).isInstanceOf(Strikethrough.class);
        }

        @Test
        @DisplayName("should parse empty paragraph")
        void shouldParseEmptyParagraph() throws Exception {
            String xml = "<p></p>";
            Paragraph p = (Paragraph) parseFirstBlock(xml, "p");

            assertThat(p.elements()).isEmpty();
        }

        @Test
        @DisplayName("should parse paragraph with whitespace only")
        void shouldParseParagraphWithWhitespace() throws Exception {
            String xml = "<p>   </p>";
            Paragraph p = (Paragraph) parseFirstBlock(xml, "p");

            assertThat(p.elements()).hasSize(1);
            assertThat(((Text) p.elements().get(0)).value()).isEqualTo("   ");
        }

        @Test
        @DisplayName("should handle multiple inline elements")
        void shouldHandleMultipleInlineElements() throws Exception {
            String xml = "<p><strong>Жирный</strong> и <emphasis>курсив</emphasis> и <strikethrough>зачёркнутый</strikethrough></p>";
            Paragraph p = (Paragraph) parseFirstBlock(xml, "p");

            assertThat(p.elements()).hasSize(5);
            assertThat(p.elements().get(0)).isInstanceOf(Strong.class);
            assertThat(p.elements().get(2)).isInstanceOf(Emphasis.class);
            assertThat(p.elements().get(4)).isInstanceOf(Strikethrough.class);
        }
    }

    // ========================================================================
    // EMPTY LINE TESTS
    // ========================================================================

    @Nested
    @DisplayName("EmptyLine Parsing")
    class EmptyLineTests {

        @Test
        @DisplayName("should parse empty-line element")
        void shouldParseEmptyLine() throws Exception {
            String xml = "<empty-line/>";
            BlockElement block = parseFirstBlock(xml, "empty-line");

            assertThat(block).isInstanceOf(EmptyLine.class);
        }

        @Test
        @DisplayName("should parse empty-line with closing tag")
        void shouldParseEmptyLineWithClosingTag() throws Exception {
            String xml = "<empty-line></empty-line>";
            BlockElement block = parseFirstBlock(xml, "empty-line");

            assertThat(block).isInstanceOf(EmptyLine.class);
        }
    }

    // ========================================================================
    // SUBTITLE TESTS
    // ========================================================================

    @Nested
    @DisplayName("Subtitle Parsing")
    class SubtitleTests {

        @Test
        @DisplayName("should parse subtitle as paragraph")
        void shouldParseSubtitle() throws Exception {
            String xml = "<subtitle>Подзаголовок</subtitle>";
            BlockElement block = parseFirstBlock(xml, "subtitle");

            assertThat(block).isInstanceOf(Paragraph.class);
            Paragraph p = (Paragraph) block;
            assertThat(((Text) p.elements().get(0)).value()).isEqualTo("Подзаголовок");
        }

        @Test
        @DisplayName("should parse subtitle with formatting")
        void shouldParseSubtitleWithFormatting() throws Exception {
            String xml = "<subtitle><strong>Жирный</strong> подзаголовок</subtitle>";
            BlockElement block = parseFirstBlock(xml, "subtitle");

            assertThat(block).isInstanceOf(Paragraph.class);
            Paragraph p = (Paragraph) block;
            assertThat(p.elements()).hasSize(2);
            assertThat(p.elements().get(0)).isInstanceOf(Strong.class);
        }
    }

    // ========================================================================
    // POEM TESTS
    // ========================================================================

    @Nested
    @DisplayName("Poem Parsing")
    class PoemTests {

        @Test
        @DisplayName("should parse simple poem with one stanza")
        void shouldParseSimplePoem() throws Exception {
            String xml = """
                    <poem id="poem1">
                        <stanza>
                            <v>Я помню чудное мгновенье</v>
                            <v>Передо мной явилась ты</v>
                        </stanza>
                        <text-author>А.С. Пушкин</text-author>
                    </poem>
                    """;

            Poem poem = (Poem) parseFirstBlock(xml, "poem");

            assertThat(poem.id()).isEqualTo("poem1");
            assertThat(poem.stanzas()).hasSize(1);
            assertThat(poem.stanzas().get(0).verses()).hasSize(2);
            assertThat(poem.author()).isEqualTo("А.С. Пушкин");
        }

        @Test
        @DisplayName("should parse poem with title")
        void shouldParsePoemWithTitle() throws Exception {
            String xml = """
                    <poem>
                        <title><p>Название стиха</p></title>
                        <stanza>
                            <v>Строка 1</v>
                        </stanza>
                    </poem>
                    """;

            Poem poem = (Poem) parseFirstBlock(xml, "poem");

            assertThat(poem.title()).hasSize(1);
            assertThat(poem.title().get(0)).isInstanceOf(Paragraph.class);
        }

        @Test
        @DisplayName("should parse poem with epigraph")
        void shouldParsePoemWithEpigraph() throws Exception {
            String xml = """
                    <poem>
                        <epigraph>
                            <p>Эпиграф к стиху</p>
                            <text-author>Автор эпиграфа</text-author>
                        </epigraph>
                        <stanza>
                            <v>Строка 1</v>
                        </stanza>
                    </poem>
                    """;

            Poem poem = (Poem) parseFirstBlock(xml, "poem");

            assertThat(poem.epigraph()).hasSize(1);
            Epigraph epi = (Epigraph) poem.epigraph().get(0);
            assertThat(epi.author()).isEqualTo("Автор эпиграфа");
        }

        @Test
        @DisplayName("should parse poem with multiple stanzas")
        void shouldParsePoemWithMultipleStanzas() throws Exception {
            String xml = """
                    <poem>
                        <stanza>
                            <v>Строфа 1, строка 1</v>
                            <v>Строфа 1, строка 2</v>
                        </stanza>
                        <stanza>
                            <v>Строфа 2, строка 1</v>
                        </stanza>
                        <stanza>
                            <v>Строфа 3, строка 1</v>
                            <v>Строфа 3, строка 2</v>
                            <v>Строфа 3, строка 3</v>
                        </stanza>
                    </poem>
                    """;

            Poem poem = (Poem) parseFirstBlock(xml, "poem");

            assertThat(poem.stanzas()).hasSize(3);
            assertThat(poem.stanzas().get(0).verses()).hasSize(2);
            assertThat(poem.stanzas().get(1).verses()).hasSize(1);
            assertThat(poem.stanzas().get(2).verses()).hasSize(3);
        }

        @Test
        @DisplayName("should parse verse with inline formatting")
        void shouldParseVerseWithInlineFormatting() throws Exception {
            String xml = """
                    <poem>
                        <stanza>
                            <v>Обычный <strong>жирный</strong> текст</v>
                        </stanza>
                    </poem>
                    """;

            Poem poem = (Poem) parseFirstBlock(xml, "poem");
            Verse verse = poem.stanzas().get(0).verses().get(0);

            assertThat(verse.elements()).hasSize(3);
            assertThat(verse.elements().get(0)).isInstanceOf(Text.class);
            assertThat(verse.elements().get(1)).isInstanceOf(Strong.class);
            assertThat(verse.elements().get(2)).isInstanceOf(Text.class);
        }

        @Test
        @DisplayName("should parse poem without author")
        void shouldParsePoemWithoutAuthor() throws Exception {
            String xml = """
                    <poem>
                        <stanza>
                            <v>Строка</v>
                        </stanza>
                    </poem>
                    """;

            Poem poem = (Poem) parseFirstBlock(xml, "poem");
            assertThat(poem.author()).isNull();
        }

        @Test
        @DisplayName("should parse empty poem")
        void shouldParseEmptyPoem() throws Exception {
            String xml = "<poem></poem>";
            Poem poem = (Poem) parseFirstBlock(xml, "poem");

            assertThat(poem.stanzas()).isEmpty();
            assertThat(poem.title()).isEmpty();
            assertThat(poem.epigraph()).isEmpty();
            assertThat(poem.author()).isNull();
        }
    }

    // ========================================================================
    // TABLE TESTS
    // ========================================================================

    @Nested
    @DisplayName("Table Parsing")
    class TableTests {

        @Test
        @DisplayName("should parse simple table")
        void shouldParseSimpleTable() throws Exception {
            String xml = """
                    <table>
                        <tr>
                            <td><p>Ячейка 1</p></td>
                            <td><p>Ячейка 2</p></td>
                        </tr>
                        <tr>
                            <td><p>Ячейка 3</p></td>
                            <td><p>Ячейка 4</p></td>
                        </tr>
                    </table>
                    """;

            Table table = (Table) parseFirstBlock(xml, "table");

            assertThat(table.rows()).hasSize(2);
            assertThat(table.rows().get(0).cells()).hasSize(2);

            TableCell cell = table.rows().get(0).cells().get(0);
            assertThat(cell.content()).hasSize(1);
            assertThat(cell.content().get(0)).isInstanceOf(Paragraph.class);
        }

        @Test
        @DisplayName("should auto-wrap orphan text in table cell")
        void shouldAutoWrapOrphanTextInTableCell() throws Exception {
            String xml = """
                    <table>
                        <tr>
                            <td>Грязный текст</td>
                        </tr>
                    </table>
                    """;

            Table table = (Table) parseFirstBlock(xml, "table");
            TableCell cell = table.rows().get(0).cells().get(0);

            assertThat(cell.content()).hasSize(1);
            assertThat(cell.content().get(0)).isInstanceOf(Paragraph.class);

            Paragraph p = (Paragraph) cell.content().get(0);
            assertThat(((Text) p.elements().get(0)).value()).isEqualTo("Грязный текст");
        }

        @Test
        @DisplayName("should handle mixed content in table cell")
        void shouldHandleMixedContentInTableCell() throws Exception {
            String xml = """
                    <table>
                        <tr>
                            <td>
                                <p>Первый параграф</p>
                                <p>Второй параграф</p>
                            </td>
                        </tr>
                    </table>
                    """;

            Table table = (Table) parseFirstBlock(xml, "table");
            TableCell cell = table.rows().get(0).cells().get(0);

            // Пустые параграфы (whitespace) фильтруются
            assertThat(cell.content()).hasSize(2);
            assertThat(cell.content().get(0)).isInstanceOf(Paragraph.class);
            assertThat(cell.content().get(1)).isInstanceOf(Paragraph.class);
        }

        @Test
        @DisplayName("should support th elements")
        void shouldSupportThElements() throws Exception {
            String xml = """
                    <table>
                        <tr>
                            <th><p>Заголовок</p></th>
                            <td><p>Данные</p></td>
                        </tr>
                    </table>
                    """;

            Table table = (Table) parseFirstBlock(xml, "table");
            assertThat(table.rows().get(0).cells()).hasSize(2);
        }

        @Test
        @DisplayName("should handle empty table")
        void shouldHandleEmptyTable() throws Exception {
            String xml = "<table></table>";
            Table table = (Table) parseFirstBlock(xml, "table");
            assertThat(table.rows()).isEmpty();
        }

        @Test
        @DisplayName("should handle table with empty row")
        void shouldHandleTableWithEmptyRow() throws Exception {
            String xml = """
                    <table>
                        <tr></tr>
                    </table>
                    """;

            Table table = (Table) parseFirstBlock(xml, "table");
            assertThat(table.rows()).hasSize(1);
            assertThat(table.rows().get(0).cells()).isEmpty();
        }

        @Test
        @DisplayName("should handle table cell with inline elements")
        void shouldHandleTableCellWithInlineElements() throws Exception {
            String xml = """
                    <table>
                        <tr>
                            <td><strong>Жирный</strong> текст</td>
                        </tr>
                    </table>
                    """;

            Table table = (Table) parseFirstBlock(xml, "table");
            TableCell cell = table.rows().get(0).cells().get(0);

            assertThat(cell.content()).hasSize(1);
            Paragraph p = (Paragraph) cell.content().get(0);
            assertThat(p.elements()).hasSize(2);
            assertThat(p.elements().get(0)).isInstanceOf(Strong.class);
        }
    }

    // ========================================================================
    // CITE TESTS
    // ========================================================================

    @Nested
    @DisplayName("Cite Parsing")
    class CiteTests {

        @Test
        @DisplayName("should parse cite with author")
        void shouldParseCiteWithAuthor() throws Exception {
            String xml = """
                    <cite id="cite1">
                        <p>Текст цитаты</p>
                        <text-author>Автор цитаты</text-author>
                    </cite>
                    """;

            Cite cite = (Cite) parseFirstBlock(xml, "cite");

            assertThat(cite.id()).isEqualTo("cite1");
            assertThat(cite.content()).hasSize(1);
            assertThat(cite.content().get(0)).isInstanceOf(Paragraph.class);
            assertThat(cite.author()).isEqualTo("Автор цитаты");
        }

        @Test
        @DisplayName("should parse cite with formatted author")
        void shouldParseCiteWithFormattedAuthor() throws Exception {
            String xml = """
                    <cite>
                        <p>Цитата</p>
                        <text-author><p><strong>Жирный</strong> автор</p></text-author>
                    </cite>
                    """;

            Cite cite = (Cite) parseFirstBlock(xml, "cite");
            assertThat(cite.author()).isEqualTo("Жирный автор");
        }

        @Test
        @DisplayName("should parse cite with multiple paragraphs")
        void shouldParseCiteWithMultipleParagraphs() throws Exception {
            String xml = """
                    <cite>
                        <p>Первый параграф</p>
                        <p>Второй параграф</p>
                        <p>Третий параграф</p>
                    </cite>
                    """;

            Cite cite = (Cite) parseFirstBlock(xml, "cite");
            assertThat(cite.content()).hasSize(3);
        }

        @Test
        @DisplayName("should parse cite without author")
        void shouldParseCiteWithoutAuthor() throws Exception {
            String xml = """
                    <cite>
                        <p>Цитата без автора</p>
                    </cite>
                    """;

            Cite cite = (Cite) parseFirstBlock(xml, "cite");
            assertThat(cite.author()).isNull();
        }

        @Test
        @DisplayName("should parse empty cite")
        void shouldParseEmptyCite() throws Exception {
            String xml = "<cite></cite>";
            Cite cite = (Cite) parseFirstBlock(xml, "cite");

            assertThat(cite.content()).isEmpty();
            assertThat(cite.author()).isNull();
        }
    }

    // ========================================================================
    // EPIGRAPH TESTS
    // ========================================================================

    @Nested
    @DisplayName("Epigraph Parsing")
    class EpigraphTests {

        @Test
        @DisplayName("should parse epigraph with author")
        void shouldParseEpigraphWithAuthor() throws Exception {
            String xml = """
                    <epigraph>
                        <p>Текст эпиграфа</p>
                        <text-author>Автор</text-author>
                    </epigraph>
                    """;

            Epigraph epi = (Epigraph) parseFirstBlock(xml, "epigraph");

            assertThat(epi.content()).hasSize(1);
            assertThat(epi.author()).isEqualTo("Автор");
        }

        @Test
        @DisplayName("should parse epigraph without author")
        void shouldParseEpigraphWithoutAuthor() throws Exception {
            String xml = """
                    <epigraph>
                        <p>Текст эпиграфа без автора</p>
                    </epigraph>
                    """;

            Epigraph epi = (Epigraph) parseFirstBlock(xml, "epigraph");

            assertThat(epi.content()).hasSize(1);
            assertThat(epi.author()).isNull();
        }

        @Test
        @DisplayName("should parse epigraph with id")
        void shouldParseEpigraphWithId() throws Exception {
            String xml = """
                    <epigraph id="epi1">
                        <p>Текст</p>
                    </epigraph>
                    """;

            Epigraph epi = (Epigraph) parseFirstBlock(xml, "epigraph");
            assertThat(epi.id()).isEqualTo("epi1");
        }

        @Test
        @DisplayName("should parse empty epigraph")
        void shouldParseEmptyEpigraph() throws Exception {
            String xml = "<epigraph></epigraph>";
            Epigraph epi = (Epigraph) parseFirstBlock(xml, "epigraph");

            assertThat(epi.content()).isEmpty();
            assertThat(epi.author()).isNull();
        }
    }

    // ========================================================================
    // BLOCK CONTAINER TESTS
    // ========================================================================

    @Nested
    @DisplayName("Block Container Parsing")
    class BlockContainerTests {

        @Test
        @DisplayName("should parse block container with multiple blocks")
        void shouldParseBlockContainer() throws Exception {
            String xml = """
                    <title>
                        <p>Первый параграф</p>
                        <p>Второй параграф</p>
                        <empty-line/>
                    </title>
                    """;

            XMLStreamReader reader = factory.createXMLStreamReader(new StringReader(xml));
            while (reader.hasNext()) {
                if (reader.next() == javax.xml.stream.XMLStreamConstants.START_ELEMENT) {
                    break;
                }
            }

            List<BlockElement> blocks = parser.parseBlockContainer(reader, "title", "<test>");

            assertThat(blocks).hasSize(3);
            assertThat(blocks.get(0)).isInstanceOf(Paragraph.class);
            assertThat(blocks.get(1)).isInstanceOf(Paragraph.class);
            assertThat(blocks.get(2)).isInstanceOf(EmptyLine.class);
        }

        @Test
        @DisplayName("should parse empty block container")
        void shouldParseEmptyBlockContainer() throws Exception {
            String xml = "<title></title>";

            XMLStreamReader reader = factory.createXMLStreamReader(new StringReader(xml));
            while (reader.hasNext()) {
                if (reader.next() == javax.xml.stream.XMLStreamConstants.START_ELEMENT) {
                    break;
                }
            }

            List<BlockElement> blocks = parser.parseBlockContainer(reader, "title", "<test>");
            assertThat(blocks).isEmpty();
        }
    }

    // ========================================================================
    // XML FRAGMENT TESTS
    // ========================================================================

    @Nested
    @DisplayName("XML Fragment Parsing")
    class XmlFragmentTests {

        @Test
        @DisplayName("should parse XML fragment with multiple blocks")
        void shouldParseXmlFragment() throws Exception {
            String xmlFragment = "<p>Первый</p><p>Второй</p><empty-line/>";

            List<BlockElement> blocks = parser.parseXmlFragment(xmlFragment);

            assertThat(blocks).hasSize(3);
            assertThat(blocks.get(0)).isInstanceOf(Paragraph.class);
            assertThat(blocks.get(1)).isInstanceOf(Paragraph.class);
            assertThat(blocks.get(2)).isInstanceOf(EmptyLine.class);
        }

        @Test
        @DisplayName("should return empty list for null fragment")
        void shouldReturnEmptyListForNullFragment() throws Exception {
            List<BlockElement> blocks = parser.parseXmlFragment(null);
            assertThat(blocks).isEmpty();
        }

        @Test
        @DisplayName("should return empty list for blank fragment")
        void shouldReturnEmptyListForBlankFragment() throws Exception {
            List<BlockElement> blocks = parser.parseXmlFragment("   ");
            assertThat(blocks).isEmpty();
        }

        @Test
        @DisplayName("should parse complex XML fragment")
        void shouldParseComplexXmlFragment() throws Exception {
            String xmlFragment = """
                    <p>Текст с <strong>форматированием</strong></p>
                    <empty-line/>
                    <p>Второй параграф</p>
                    """;

            List<BlockElement> blocks = parser.parseXmlFragment(xmlFragment);

            assertThat(blocks).hasSize(3);
            Paragraph p = (Paragraph) blocks.get(0);
            assertThat(p.elements()).hasSize(2);
            assertThat(p.elements().get(1)).isInstanceOf(Strong.class);
        }
    }

    // ========================================================================
    // FORGIVING MODE TESTS
    // ========================================================================

    @Nested
    @DisplayName("Forgiving Mode (Unknown Tags)")
    class ForgivingModeTests {

        @Test
        @DisplayName("should skip unknown block tags")
        void shouldSkipUnknownBlockTags() throws Exception {
            String xml = "<unknown-tag>Какой-то текст</unknown-tag>";
            BlockElement block = parseFirstBlock(xml, "unknown-tag");

            assertThat(block).isNull();
        }

        @Test
        @DisplayName("should skip unknown inline tags in paragraph")
        void shouldSkipUnknownInlineTagsInParagraph() throws Exception {
            String xml = "<p>Текст <unknown-inline>игнорируется</unknown-inline> продолжение</p>";
            Paragraph p = (Paragraph) parseFirstBlock(xml, "p");

            // Текст до и после неизвестного тега склеивается в один Text элемент
            assertThat(p.elements()).hasSize(1);
            assertThat(((Text) p.elements().get(0)).value()).isEqualTo("Текст  продолжение");
        }

        @Test
        @DisplayName("should skip unknown tags in poem")
        void shouldSkipUnknownTagsInPoem() throws Exception {
            String xml = """
                    <poem>
                        <stanza>
                            <v>Строка</v>
                        </stanza>
                        <unknown-tag>Игнорируется</unknown-tag>
                    </poem>
                    """;

            Poem poem = (Poem) parseFirstBlock(xml, "poem");
            assertThat(poem.stanzas()).hasSize(1);
        }

        @Test
        @DisplayName("should skip unknown tags in table")
        void shouldSkipUnknownTagsInTable() throws Exception {
            String xml = """
                    <table>
                        <tr>
                            <td><p>Ячейка</p></td>
                        </tr>
                        <unknown-row>Игнорируется</unknown-row>
                    </table>
                    """;

            Table table = (Table) parseFirstBlock(xml, "table");
            assertThat(table.rows()).hasSize(1);
        }
    }

    // ========================================================================
    // EDGE CASES
    // ========================================================================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("should handle CDATA sections")
        void shouldHandleCdataSections() throws Exception {
            String xml = "<p><![CDATA[Текст в CDATA]]></p>";
            Paragraph p = (Paragraph) parseFirstBlock(xml, "p");

            assertThat(p.elements()).hasSize(1);
            assertThat(((Text) p.elements().get(0)).value()).isEqualTo("Текст в CDATA");
        }

        @Test
        @DisplayName("should handle XML entities")
        void shouldHandleXmlEntities() throws Exception {
            String xml = "<p>Спецсимволы: &lt; &gt; &amp; &quot;</p>";
            Paragraph p = (Paragraph) parseFirstBlock(xml, "p");

            assertThat(p.elements()).hasSize(1);
            String text = ((Text) p.elements().get(0)).value();
            assertThat(text).contains("<");
            assertThat(text).contains(">");
            assertThat(text).contains("&");
            assertThat(text).contains("\"");
        }

        @Test
        @DisplayName("should handle deeply nested structures")
        void shouldHandleDeeplyNestedStructures() throws Exception {
            String xml = """
                    <poem>
                        <title>
                            <p><strong><emphasis>Глубокая вложенность</emphasis></strong></p>
                        </title>
                        <stanza>
                            <v>Строка</v>
                        </stanza>
                    </poem>
                    """;

            Poem poem = (Poem) parseFirstBlock(xml, "poem");
            assertThat(poem.title()).hasSize(1);

            Paragraph p = (Paragraph) poem.title().get(0);
            Strong strong = (Strong) p.elements().get(0);
            Emphasis em = (Emphasis) strong.elements().get(0);
            assertThat(((Text) em.elements().get(0)).value()).isEqualTo("Глубокая вложенность");
        }

        @Test
        @DisplayName("should handle link without href")
        void shouldHandleLinkWithoutHref() throws Exception {
            String xml = "<p><a>Ссылка без href</a></p>";
            Paragraph p = (Paragraph) parseFirstBlock(xml, "p");

            Link link = (Link) p.elements().get(0);
            assertThat(link.href()).isNull();
        }

        @Test
        @DisplayName("should handle image without href")
        void shouldHandleImageWithoutHref() throws Exception {
            String xml = "<p><image alt=\"Картинка без href\"/></p>";
            Paragraph p = (Paragraph) parseFirstBlock(xml, "p");

            ImageRef img = (ImageRef) p.elements().get(0);
            assertThat(img.href()).isNull();
            assertThat(img.alt()).isEqualTo("Картинка без href");
        }
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    private BlockElement parseFirstBlock(String xml, String tag) throws Exception {
        XMLStreamReader reader = factory.createXMLStreamReader(new StringReader(xml));

        while (reader.hasNext()) {
            if (reader.next() == javax.xml.stream.XMLStreamConstants.START_ELEMENT) {
                break;
            }
        }

        return parser.parseBlock(reader, tag, "<test>");
    }
}