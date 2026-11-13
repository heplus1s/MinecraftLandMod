package dev.jdguo.land.message;

import net.minecraft.text.Text;

public final class LandMessages {
    public static final String PREFIX = "[领地] ";
    public static final String USAGE =
            "Usage: /land claim | abandon | info | trust <player> | untrust <player> | transfer <player> | list";

    private LandMessages() {
    }

    public static Text chat(String body) {
        return Text.literal(PREFIX + body);
    }

    public static String dimension(String id) {
        return switch (id) {
            case "minecraft:overworld" -> "主世界";
            case "minecraft:the_nether" -> "下界";
            case "minecraft:the_end" -> "末地";
            default -> id;
        };
    }

    public static String who(String name) {
        return name == null || name.isBlank() ? "未知" : name;
    }
}
