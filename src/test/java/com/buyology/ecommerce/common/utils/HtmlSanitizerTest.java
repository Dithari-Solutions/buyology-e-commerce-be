package com.buyology.ecommerce.common.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class HtmlSanitizerTest {

    @Test
    void stripsScriptTagsAndContent() {
        String out = HtmlSanitizer.stripHtml("hello<script>alert('xss')</script>world");
        assertFalse(out.toLowerCase().contains("<script"));
        assertFalse(out.contains("</script>"));
    }

    @Test
    void stripsEventHandlerMarkup() {
        String out = HtmlSanitizer.stripHtml("<img src=x onerror=alert(1)>caption");
        assertFalse(out.contains("<img"));
        assertFalse(out.toLowerCase().contains("onerror"));
        assertEquals("caption", out);
    }

    @Test
    void preservesPlainTextAndDecodesEntities() {
        assertEquals("Tom & Jerry", HtmlSanitizer.stripHtml("Tom & Jerry"));
        assertEquals("Great product, 5/5!", HtmlSanitizer.stripHtml("Great product, 5/5!"));
    }

    @Test
    void isNullAndEmptySafe() {
        assertNull(HtmlSanitizer.stripHtml(null));
        assertEquals("", HtmlSanitizer.stripHtml(""));
    }

    @Test
    void neutralizesJavascriptUriAnchor() {
        String out = HtmlSanitizer.stripHtml("<a href=\"javascript:alert(1)\">click</a>");
        assertFalse(out.toLowerCase().contains("<a"));
        assertEquals("click", out);
    }
}
