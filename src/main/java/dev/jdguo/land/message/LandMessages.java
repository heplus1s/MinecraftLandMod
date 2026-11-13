package dev.jdguo.land.message;

import net.minecraft.text.Text;

public final class LandMessages {
    public static final String PREFIX = "[领地] ";
    public static final String USAGE =
            "Usage: /land claim [join [parcel]] | abandon | info | list | trust <player> | untrust <player> | transfer <player> | mark [clear|list] | split [name] | split transfer <player> | parcel list | parcel info [parcel] | parcel rename <name> | parcel merge <parcel> | parcel transfer <player> | sell parcel | sell chunks | sell cancel | buy [parcel|chunks] | area [parcel]";
    public static final String PARCEL_USAGE =
            "Usage: /land parcel list | info [parcel] | rename <name> | merge <parcel> | transfer <player>";
    public static final String SELL_USAGE = "Usage: /land sell parcel | chunks | cancel";
    public static final String BUY_USAGE = "Usage: /land buy [parcel|chunks]";

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
