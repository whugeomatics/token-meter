package local.agent.dashboard.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class Json {
    private Json() {
    }

    public static Optional<String> firstString(String json, String key) {
        List<String> values = stringOccurrences(json, key);
        return values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }

    public static List<String> stringOccurrences(String json, String key) {
        List<String> values = new ArrayList<>();
        String needle = "\"" + key + "\"";
        int index = 0;
        while ((index = json.indexOf(needle, index)) >= 0) {
            int colon = json.indexOf(':', index + needle.length());
            if (colon < 0) {
                break;
            }
            int quote = nextNonWhitespace(json, colon + 1);
            if (quote >= json.length() || json.charAt(quote) != '"') {
                index = colon + 1;
                continue;
            }
            int end = stringEnd(json, quote + 1);
            if (end < 0) {
                break;
            }
            values.add(unescape(json.substring(quote + 1, end)));
            index = end + 1;
        }
        return values;
    }

    public static Optional<Long> longValue(String json, String key) {
        String needle = "\"" + key + "\"";
        int index = json.indexOf(needle);
        if (index < 0) {
            return Optional.empty();
        }
        int colon = json.indexOf(':', index + needle.length());
        if (colon < 0) {
            return Optional.empty();
        }
        int start = nextNonWhitespace(json, colon + 1);
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
            end++;
        }
        if (end == start) {
            return Optional.empty();
        }
        return Optional.of(Long.parseLong(json.substring(start, end)));
    }

    public static Optional<String> objectSection(String json, String key) {
        String needle = "\"" + key + "\"";
        int keyIndex = json.indexOf(needle);
        if (keyIndex < 0) {
            return Optional.empty();
        }
        int colon = json.indexOf(':', keyIndex + needle.length());
        if (colon < 0) {
            return Optional.empty();
        }
        int start = nextNonWhitespace(json, colon + 1);
        if (start >= json.length() || json.charAt(start) != '{') {
            return Optional.empty();
        }
        int end = matchingBrace(json, start);
        return end < 0 ? Optional.empty() : Optional.of(json.substring(start, end + 1));
    }

    public static Optional<String> arraySection(String json, String key) {
        String needle = "\"" + key + "\"";
        int keyIndex = json.indexOf(needle);
        if (keyIndex < 0) {
            return Optional.empty();
        }
        int colon = json.indexOf(':', keyIndex + needle.length());
        if (colon < 0) {
            return Optional.empty();
        }
        int start = nextNonWhitespace(json, colon + 1);
        if (start >= json.length() || json.charAt(start) != '[') {
            return Optional.empty();
        }
        int end = matchingBracket(json, start);
        return end < 0 ? Optional.empty() : Optional.of(json.substring(start, end + 1));
    }

    public static List<String> objectElements(String arrayJson) {
        List<String> objects = new ArrayList<>();
        int index = 0;
        while (index < arrayJson.length()) {
            int start = arrayJson.indexOf('{', index);
            if (start < 0) {
                break;
            }
            int end = matchingBrace(arrayJson, start);
            if (end < 0) {
                break;
            }
            objects.add(arrayJson.substring(start, end + 1));
            index = end + 1;
        }
        return objects;
    }

    public static String stringArray(List<String> values) {
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append('"').append(escape(values.get(i))).append('"');
        }
        return out.append(']').toString();
    }

    public static <T> String array(List<T> values, JsonMapper<T> mapper) {
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(mapper.map(values.get(i)));
        }
        return out.append(']').toString();
    }

    public static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static int nextNonWhitespace(String value, int start) {
        int index = start;
        while (index < value.length() && Character.isWhitespace(value.charAt(index))) {
            index++;
        }
        return index;
    }

    private static int stringEnd(String value, int start) {
        boolean escaped = false;
        for (int i = start; i < value.length(); i++) {
            char current = value.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (current == '\\') {
                escaped = true;
                continue;
            }
            if (current == '"') {
                return i;
            }
        }
        return -1;
    }

    private static int matchingBrace(String value, int start) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < value.length(); i++) {
            char current = value.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }
            if (current == '"') {
                inString = true;
            } else if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static int matchingBracket(String value, int start) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < value.length(); i++) {
            char current = value.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }
            if (current == '"') {
                inString = true;
            } else if (current == '[') {
                depth++;
            } else if (current == ']') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static String unescape(String value) {
        return value.replace("\\\"", "\"").replace("\\\\", "\\");
    }
}
