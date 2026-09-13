package dev.zeli.cleanbill;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.HashMap;
import java.util.Map;

public final class TextUtil {
    public static final int LIGHT_GRAY = 0xD0D0D0;
    public static final int PASTEL_GREEN = 0x91C788;
    public static final int PASTEL_RED = 0xD98C8C;
    public static final int PALE_PURPLE = 0xA27BB5;
    private static final Map<Character, ChatFormatting> COLORS = new HashMap<>();
    static {
        String codes = "0123456789abcdefklmnor";
        ChatFormatting[] values = {
                ChatFormatting.BLACK, ChatFormatting.DARK_BLUE, ChatFormatting.DARK_GREEN, ChatFormatting.DARK_AQUA,
                ChatFormatting.DARK_RED, ChatFormatting.DARK_PURPLE, ChatFormatting.GOLD, ChatFormatting.GRAY,
                ChatFormatting.DARK_GRAY, ChatFormatting.BLUE, ChatFormatting.GREEN, ChatFormatting.AQUA,
                ChatFormatting.RED, ChatFormatting.LIGHT_PURPLE, ChatFormatting.YELLOW, ChatFormatting.WHITE,
                ChatFormatting.OBFUSCATED, ChatFormatting.BOLD, ChatFormatting.STRIKETHROUGH,
                ChatFormatting.UNDERLINE, ChatFormatting.ITALIC, ChatFormatting.RESET
        };
        for (int i = 0; i < codes.length(); i++) COLORS.put(codes.charAt(i), values[i]);
    }

    private TextUtil() {}

    public static MutableComponent colored(String input) {
        MutableComponent result = Component.empty();
        Style active = Style.EMPTY.withColor(LIGHT_GRAY);
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            if (input.charAt(i) == '<' && i + 8 < input.length() && input.charAt(i + 1) == '#'
                    && input.charAt(i + 8) == '>') {
                try {
                    int rgb = Integer.parseInt(input.substring(i + 2, i + 8), 16);
                    if (!text.isEmpty()) result.append(Component.literal(text.toString()).withStyle(active));
                    text.setLength(0);
                    active = active.withColor(TextColor.fromRgb(rgb));
                    i += 8;
                    continue;
                } catch (NumberFormatException ignored) { }
            }
            if (input.charAt(i) == '&' && i + 1 < input.length()) {
                ChatFormatting next = COLORS.get(Character.toLowerCase(input.charAt(i + 1)));
                if (next != null) {
                    if (!text.isEmpty()) result.append(Component.literal(text.toString()).withStyle(active));
                    text.setLength(0);
                    if (next == ChatFormatting.RESET) active = Style.EMPTY.withColor(LIGHT_GRAY);
                    else if (next.isColor()) active = active.withColor(next);
                    else active = active.applyFormat(next);
                    i++;
                    continue;
                }
            }
            text.append(input.charAt(i));
        }
        if (!text.isEmpty()) result.append(Component.literal(text.toString()).withStyle(active));
        return result;
    }

    public static Component message(String template, Map<String, String> replacements) {
        String value = template;
        for (var entry : replacements.entrySet()) value = value.replace("{" + entry.getKey() + "}", entry.getValue());
        return colored(value);
    }

    public static MutableComponent gray(String text) { return Component.literal(text).withStyle(style -> style.withColor(LIGHT_GRAY)); }
    public static MutableComponent green(String text) { return Component.literal(text).withStyle(style -> style.withColor(PASTEL_GREEN)); }
    public static MutableComponent red(String text) { return Component.literal(text).withStyle(style -> style.withColor(PASTEL_RED)); }
    public static MutableComponent purple(String text) { return Component.literal(text).withStyle(style -> style.withColor(PALE_PURPLE)); }
}
