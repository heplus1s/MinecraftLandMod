package dev.jdguo.land.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Structural regression; Minecraft handler/layout tests are separate from in-game QA. */
class GovernmentScreenWiringTest {
    private String source() throws Exception {
        return Files.readString(Path.of("src/client/java/dev/jdguo/land/ui/GovernmentLandScreen.java"));
    }
    @Test void lifecycleActionsUsePreviewAndDistinctConfirmation() throws Exception {
        String text = source();
        for (String action : new String[]{"previewSplit", "previewMerge", "previewRelease", "previewReleaseParcel", "openListing"})
            assertTrue(text.contains('"' + action + '"'), action);
        assertTrue(text.contains("data.addProperty(\"target\", mergeTarget)"));
        assertTrue(text.contains("data.addProperty(\"source\", parcel.get(\"id\").getAsString())"));
        assertTrue(text.contains("data.addProperty(\"token\", token)"));
        assertTrue(text.contains("submittedToken = token"));
    }
    @Test void authorityAndStaleResponsesRemainGuarded() throws Exception {
        String text = source();
        assertTrue(text.contains("if (waiting || !LandQuotes.canManage()) return"));
        assertTrue(text.contains("generation != requestId || !LandQuotes.canManage()"));
        assertTrue(text.contains("if (!LandQuotes.canManage()) { close(); return; }"));
        assertTrue(text.contains("scope.equals(LandPick.selected())"));
    }
    @Test void retrySameTokenOnlyOnExplicitServerRetryOrUncertainTimeout() throws Exception {
        String text = source();
        assertTrue(text.contains("data.has(\"retry\") && data.get(\"retry\").getAsBoolean()"));
        assertTrue(text.contains("token = submittedToken; retrySave = true"));
        assertTrue(text.contains("if (!retrySave && !token.isBlank()"));
        assertTrue(text.contains("结果未确认；可手动核验同一操作，不会自动重试。"));
    }
    @Test void ownershipModesAndNarrowLayoutAreExplicit() throws Exception {
        String text = source();
        assertTrue(text.contains("button(\"个人用地\""));
        assertTrue(text.contains("button(\"服务器用地 · 管理员\""));
        assertTrue(text.contains("String[] tabs = {\"登记\", \"宗地资料\", \"处置\"}"));
        assertTrue(text.contains("width < 280 || height < 236"));
        assertTrue(text.contains("confirm.setTooltip"));
        assertTrue(text.contains("previewDetails"));
    }
}
