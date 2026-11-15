package com.sharksmp.clans.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/** Conversao de textos com codigos de cor (&amp;a, &amp;#rrggbb) para Components. */
public final class Text {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .build();

    private Text() {
    }

    public static Component color(String text) {
        return LEGACY.deserialize(text == null ? "" : text);
    }

    /** Igual a color(), mas sem italico (padrao do Minecraft para nomes/lore de itens). */
    public static Component item(String text) {
        return color(text).decoration(TextDecoration.ITALIC, false);
    }

    public static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
