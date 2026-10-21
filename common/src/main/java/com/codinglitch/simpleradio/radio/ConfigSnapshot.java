package com.codinglitch.simpleradio.radio;

import com.codinglitch.lexiconfig.classes.LexiconPageData;
import com.codinglitch.simpleradio.core.registry.blocks.AntennaBlock;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Copies the simple values (booleans, numbers, strings) of a config between server and client, so screens,
 * tooltips and the HUD on a client show the server's rules (battery, band plan, power...) and not the client's
 * own config file.
 */
public final class ConfigSnapshot {
    private static final int MAX_DEPTH = 4;

    private ConfigSnapshot() {}

    public static Map<String, String> capture(Object root) {
        Map<String, String> values = new LinkedHashMap<>();
        capture(root, "", values, 0);
        return values;
    }

    private static void capture(Object object, String prefix, Map<String, String> values, int depth) {
        if (object == null || depth > MAX_DEPTH) return;
        for (Field field : object.getClass().getFields()) {
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) continue;
            try {
                Object value = field.get(object);
                String key = prefix + field.getName();
                if (isSimple(field.getType())) {
                    if (value != null) values.put(key, value.toString());
                } else if (value instanceof LexiconPageData) {
                    capture(value, key + ".", values, depth + 1);
                }
            } catch (IllegalAccessException ignored) {
            }
        }
    }

    /**
     * Writes values into a config. Unknown keys and unparsable values are skipped.
     * @return how many values were applied
     */
    public static int apply(Object root, Map<String, String> values) {
        int applied = 0;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (apply(root, entry.getKey(), entry.getValue())) applied++;
        }
        FrequenciesImpl.onLexiconRevision();
        AntennaBlock.onLexiconRevision();
        return applied;
    }

    private static boolean apply(Object root, String key, String text) {
        String[] path = key.split("\\.");
        if (path.length > MAX_DEPTH + 1) return false;
        try {
            Object target = root;
            for (int i = 0; i < path.length - 1; i++) {
                Field page = target.getClass().getField(path[i]);
                target = page.get(target);
                if (!(target instanceof LexiconPageData)) return false;
            }
            Field field = target.getClass().getField(path[path.length - 1]);
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers()) || !isSimple(field.getType())) return false;
            Object value = parse(field.getType(), text);
            if (value == null) return false;
            field.set(target, value);
            return true;
        } catch (ReflectiveOperationException | IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean isSimple(Class<?> type) {
        return type == Boolean.class || type == boolean.class || type == Integer.class || type == int.class
                || type == Double.class || type == double.class || type == Float.class || type == float.class
                || type == Long.class || type == long.class || type == String.class;
    }

    private static Object parse(Class<?> type, String text) {
        try {
            if (type == String.class) return text;
            if (type == Boolean.class || type == boolean.class) return Boolean.parseBoolean(text);
            if (type == Integer.class || type == int.class) return Integer.parseInt(text);
            if (type == Double.class || type == double.class) return Double.parseDouble(text);
            if (type == Float.class || type == float.class) return Float.parseFloat(text);
            if (type == Long.class || type == long.class) return Long.parseLong(text);
        } catch (NumberFormatException ignored) {
        }
        return null;
    }
}
