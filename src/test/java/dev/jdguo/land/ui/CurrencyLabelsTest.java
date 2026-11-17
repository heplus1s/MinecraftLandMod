package dev.jdguo.land.ui;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CurrencyLabelsTest {
    @Test void stableIdUsesDisplayNameAndType() {
        var data = JsonParser.parseString("{\"currencies\":[{\"id\":\"personal:123\",\"label\":\"测试币\"},{\"id\":\"note\",\"label\":\"银行券\"}]}").getAsJsonObject();
        assertEquals("测试币（个人币）", CurrencyLabels.label("personal:123",data));
        assertEquals("央行券（央行）", CurrencyLabels.label("note",data));
    }
    @Test void customCentralNamePreservedAndMissingPersonalIdHidden() {
        var data = JsonParser.parseString("{\"currencies\":[{\"id\":\"note\",\"label\":\"中央元\"}]}").getAsJsonObject();
        assertEquals("中央元（央行）",CurrencyLabels.label("note",data));
        assertEquals("个人币（名称暂不可用）",CurrencyLabels.label("personal:123",new JsonObject()));
    }
}
