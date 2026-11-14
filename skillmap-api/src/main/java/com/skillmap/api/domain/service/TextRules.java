package com.skillmap.api.domain.service;

/**
 * Reglas comunes para el texto libre que escribe el usuario. Un "carácter de control" es cualquiera de las categorías
 * Unicode Cc (NUL, ESC, DEL...), Cf (formato invisible: marcas de dirección, espacios de ancho cero...) y los
 * separadores de línea/párrafo U+2028 y U+2029: no se ven y sirven para disfrazar texto.
 */
public final class TextRules {

    private TextRules() {
    }

    /** true si hay algún carácter de control. Con allowLineBreaks se aceptan \n, \r y \t (textos de varias líneas). */
    public static boolean hasControlChars(String text, boolean allowLineBreaks) {
        if (text == null) {
            return false;
        }
        return text.codePoints().anyMatch(cp -> isControl(cp, allowLineBreaks));
    }

    private static boolean isControl(int cp, boolean allowLineBreaks) {
        if (allowLineBreaks && (cp == '\n' || cp == '\r' || cp == '\t')) {
            return false;
        }
        int type = Character.getType(cp);
        return type == Character.CONTROL || type == Character.FORMAT
                || type == Character.LINE_SEPARATOR || type == Character.PARAGRAPH_SEPARATOR;
    }

    /** Sin espacios al inicio ni al final y con los espacios seguidos reducidos a uno. */
    public static String collapseSpaces(String text) {
        return text == null ? null : text.strip().replaceAll("\\s{2,}", " ");
    }

    public static int length(String text) {
        return text == null ? 0 : text.codePointCount(0, text.length());
    }
}
