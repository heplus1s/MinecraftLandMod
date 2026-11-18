package dev.jdguo.land.ui;

import dev.jdguo.land.client.ClientClaims;
import dev.jdguo.land.client.ClaimDisplayPreferences;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Player tools only. Rename/member operations still enforce ownership on the server. */
public final class PersonalLandScreen extends Screen {
    private final Screen parent;
    private ButtonWidget rename, permissions, display;
    private Object connection, world;
    public PersonalLandScreen(Screen parent){super(Text.literal("我的领地"));this.parent=parent;}
    @Override protected void init(){
        connection=client.getNetworkHandler();world=client.world;
        int w=Math.min(280,width-24),x=(width-w)/2,half=(w-6)/2;
        rename=addDrawableChild(ButtonWidget.builder(Text.literal("宗地命名"),b->client.setScreen(new ParcelNameScreen(this))).dimensions(x,56,half,20).build());
        permissions=addDrawableChild(ButtonWidget.builder(Text.literal("区块成员权限"),b->client.setScreen(new ClaimPermissionsScreen(this))).dimensions(x+half+6,56,half,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("我的颜色"),b->client.setScreen(new OwnerColorScreen(this))).dimensions(x,84,half,20).build());
        display=addDrawableChild(ButtonWidget.builder(Text.literal("隐藏领地"),b->ClaimDisplayPreferences.toggle()).dimensions(x+half+6,84,half,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("返回"),b->close()).dimensions(x,height-26,w,20).build());update();
    }
    private boolean ownsSelection(){
        if(client.player==null||LandPick.selected().isEmpty())return false;
        return LandPick.selected().stream().allMatch(k->{var e=ClientClaims.get(k.dimension(),k.chunkX(),k.chunkZ());return e!=null&&client.player.getUuid().equals(e.owner());});
    }
    private void update(){
        boolean own=ownsSelection();permissions.active=own&&LandPick.selected().size()==1;
        rename.active=own&&LandPick.selected().stream().map(k->ClientClaims.get(k.dimension(),k.chunkX(),k.chunkZ()).parcelId()).distinct().count()==1;
        display.setMessage(Text.literal(ClaimDisplayPreferences.visible()?"本机隐藏领地":"本机显示领地"));
    }
    @Override public void tick(){if(client.getNetworkHandler()!=connection||client.world!=world){close();return;}update();}
    @Override public boolean shouldPause(){return false;}
    @Override public void close(){client.setScreen(parent);}
    @Override public void render(DrawContext ctx,int mx,int my,float delta){
        renderBackground(ctx);int w=Math.min(280,width-24),x=(width-w)/2;
        ctx.drawTextWithShadow(textRenderer,title,x,12,0xFFFFFF);
        String status=ownsSelection()?"当前选中自己的 "+LandPick.selected().size()+" 个区块":"请先在地图选中自己的领地";
        ctx.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(status,w),x,34,0xDDDDDD);
        var lines=textRenderer.wrapLines(Text.literal("成员权限仅单区块；命名仅同一宗地。本人颜色会同步，本机显示开关不影响别人。"),w);
        int max=Math.max(0,(height-146)/10);for(int i=0;i<Math.min(max,lines.size());i++)ctx.drawTextWithShadow(textRenderer,lines.get(i),x,114+i*10,0xBBBBBB);
        super.render(ctx,mx,my,delta);
    }
}
