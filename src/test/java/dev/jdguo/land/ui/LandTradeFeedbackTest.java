package dev.jdguo.land.ui;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LandTradeFeedbackTest {
    @Test void invalidDimensionIsActionableWithoutSilentRemapping() {
        String message = LandTradeFeedback.reason("rejected:dimension", "example:archived");
        assertTrue(message.contains("example:archived"));
        assertTrue(message.contains("K 网格"));
        assertTrue(message.contains("不会自动"));
    }
    @Test void disabledSalesExplainsSaveStep() {
        assertTrue(LandTradeFeedback.reason("管理员尚未启用无主地块出售", "minecraft:overworld").contains("开启并保存"));
    }
    @Test void unrelatedErrorsAreNotHidden() {
        assertEquals("余额不足", LandTradeFeedback.reason("余额不足", "minecraft:overworld"));
    }
}
