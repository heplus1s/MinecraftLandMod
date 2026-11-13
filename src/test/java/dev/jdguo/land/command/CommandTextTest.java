package dev.jdguo.land.command;

import dev.jdguo.land.claim.ClaimKey;
import dev.jdguo.land.claim.ParcelInfo;
import dev.jdguo.land.message.LandMessages;
import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandTextTest {
    @Test
    void usageAndParcelSuggestionsStayInEnglish() {
        assertEnglish(LandMessages.USAGE);
        assertEnglish(LandMessages.PARCEL_USAGE);
        assertEnglish(LandMessages.SELL_USAGE);
        assertEnglish(LandMessages.BUY_USAGE);
        ParcelInfo info = new ParcelInfo(
                UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"),
                "市场",
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "爱丽",
                "minecraft:overworld",
                4,
                1024L,
                List.of(new ClaimKey("minecraft:overworld", -3, 9)),
                new NbtCompound());
        String tooltip = LandCommands.parcelSuggestionTooltip(info);
        assertEnglish(tooltip);
        assertFalse(tooltip.contains("市场"));
        assertFalse(tooltip.contains("爱丽"));
        assertTrue(tooltip.contains("4"));
        assertTrue(tooltip.contains("-3"));
        assertTrue(tooltip.contains("9"));
        assertEquals(8, info.shortId().length());
    }

    private static void assertEnglish(String text) {
        text.codePoints().forEach(codePoint -> assertFalse(
                Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN,
                () -> "unexpected Chinese in command text: " + text));
    }
}
