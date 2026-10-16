package pro.sketchware.utility;

public class XmlUtil {
    public static String replaceXml(String text) {
        return text.replace("<?xml version=\"1.0\" encoding=\"utf-8\"?>", "")
                .replace("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\" ?>", "")
                .replace("\r", "")
                .replace("\n", "")
                .replace(" ", "")
                .replace("\t", "");
    }

    public static String escapeAttributeValue(String value) {
        if (value == null) return "";
        StringBuilder escaped = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&' -> escaped.append("&amp;");
                case '"' -> escaped.append("&quot;");
                case '<' -> escaped.append("&lt;");
                case '\n' -> escaped.append("&#10;");
                case '\r' -> escaped.append("&#13;");
                case '\t' -> escaped.append("&#9;");
                default -> escaped.append(c);
            }
        }
        return escaped.toString();
    }

    public static String normalizeInjectedAttributes(String inject) {
        if (inject == null || inject.isEmpty()) return "";
        StringBuilder normalized = new StringBuilder(inject.length());
        char quote = 0;
        boolean pendingSpace = false;
        for (int i = 0; i < inject.length(); i++) {
            char c = inject.charAt(i);
            if (quote != 0) {
                normalized.append(c);
                if (c == quote) quote = 0;
                continue;
            }
            if (c == ' ') {
                pendingSpace = true;
                continue;
            }
            if (pendingSpace) {
                pendingSpace = false;
                int last = normalized.length() - 1;
                boolean afterValue = last >= 0 && (normalized.charAt(last) == '"' || normalized.charAt(last) == '\'');
                if (afterValue && c != '\n' && c != '\r' && c != '\t') normalized.append(' ');
            }
            normalized.append(c);
            if (c == '"' || c == '\'') quote = c;
        }
        return normalized.toString();
    }

    public static void saveXml(String path, String xml) {
        FileUtil.writeFile(path, xml);
    }

}
