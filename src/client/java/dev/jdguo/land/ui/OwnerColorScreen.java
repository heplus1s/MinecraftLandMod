package dev.jdguo.land.ui;

import dev.jdguo.land.client.ClientOwnerColors;
import dev.jdguo.land.color.OwnerColorPalette;
import dev.jdguo.land.network.OwnerColorSession;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** Changes only the authenticated player's server-owned color. No target player field exists. */
public final class OwnerColorScreen extends Screen {
    private static final int[] PRESETS={0x35D06A,0xE08A2E,0xD66BBD,0x32B8AA,0xA88CE8,0xE07668,0xACBB44,0x779EC9};
    private final Screen parent;
    private TextFieldWidget value;
    private ButtonWidget save, reset;
    private String status="颜色将同步给其他装有领地模组的玩家。";
    private boolean pending, resetting;
    private int expected, sentRevision;
    private long deadline;
    private OwnerColorSession context;
    public OwnerColorScreen(Screen parent){super(Text.literal("我的领地颜色"));this.parent=parent;}
    @Override protected void init(){
        String draft=value==null?null:value.getText();
        int w=Math.max(140,Math.min(280,width-24)),x=(width-w)/2;
        if(context==null)context=new OwnerColorSession(client.getNetworkHandler(),client.world,ClientOwnerColors.sessionId());
        int rgb=client.player==null?0x35D06A:ClientOwnerColors.rgb(client.player.getUuid());
        value=new TextFieldWidget(textRenderer,x,44,w-28,20,Text.literal("六位颜色 HEX"));value.setMaxLength(7);
        value.setText(draft==null?OwnerColorPalette.hex(rgb):draft);addDrawableChild(value);
        int step=w/8;
        for(int i=0;i<PRESETS.length;i++){final int c=PRESETS[i];addDrawableChild(ButtonWidget.builder(Text.literal("■").styled(s->s.withColor(c)),b->value.setText(OwnerColorPalette.hex(c))).dimensions(x+i*step,70,step-2,20).build());}
        save=addDrawableChild(ButtonWidget.builder(Text.literal("保存我的颜色"),b->submit(false)).dimensions(x,96,w/2-2,20).build());
        reset=addDrawableChild(ButtonWidget.builder(Text.literal("恢复自动配色"),b->submit(true)).dimensions(x+w/2+2,96,w/2-2,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("返回"),b->close()).dimensions(x,height-26,w,20).build());
        setInitialFocus(value);updateButtons();
    }
    private void updateButtons(){save.active=reset.active=!pending&&client.player!=null&&ClientOwnerColors.supported()&&ClientOwnerColors.ready();}
    private boolean currentContext(){return client.player!=null && context!=null && context.matches(client.getNetworkHandler(),client.world,ClientOwnerColors.sessionId());}
    private void submit(boolean automatic){
        if(!currentContext()){pending=false;client.setScreen(null);return;}
        if(pending)return;
        try{expected=automatic?OwnerColorPalette.color(client.player.getUuid()):OwnerColorPalette.parseHex(value.getText());}
        catch(IllegalArgumentException invalid){status="请输入六位颜色，例如 #35D06A。";return;}
        sentRevision=ClientOwnerColors.ownerRevision(client.player.getUuid());
        if(!ClientOwnerColors.send(automatic?2:1,expected)){status="服务器不支持颜色设置；自动配色仍可显示。";return;}
        pending=true;resetting=automatic;deadline=System.nanoTime()+10_000_000_000L;status="正在等待服务器确认…";updateButtons();
    }
    @Override public void tick(){
        if(!currentContext()){pending=false;client.setScreen(null);return;}
        value.tick();
        if(pending&&ClientOwnerColors.ownerRevision(client.player.getUuid())!=sentRevision&&(resetting||ClientOwnerColors.rgb(client.player.getUuid())==expected)){
            pending=false;status="已收到服务器颜色："+OwnerColorPalette.hex(ClientOwnerColors.rgb(client.player.getUuid()))+"。";
        }
        if(pending&&System.nanoTime()>=deadline){pending=false;status="未收到保存确认，请核对当前颜色后重试。";}
        updateButtons();
    }
    @Override public boolean shouldPause(){return false;}
    @Override public void close(){pending=false;client.setScreen(currentContext()?parent:null);}
    @Override public void render(DrawContext ctx,int mouseX,int mouseY,float delta){
        renderBackground(ctx);int w=Math.max(140,Math.min(280,width-24)),x=(width-w)/2;
        ctx.drawTextWithShadow(textRenderer,title,x,10,0xFFFFFF);
        int rgb=client.player==null?0x35D06A:ClientOwnerColors.rgb(client.player.getUuid());
        ctx.drawTextWithShadow(textRenderer,"当前："+OwnerColorPalette.hex(rgb)+" · 输入六位 HEX",x,28,0xCCCCCC);
        int preview=rgb;try{preview=OwnerColorPalette.parseHex(value.getText());}catch(IllegalArgumentException ignored){}
        ctx.fill(x+w-22,44,x+w-2,64,0xFF000000|preview);
        String message=ClientOwnerColors.supported()?(ClientOwnerColors.ready()?status:"正在同步服务器颜色，请稍候。"):"服务器未提供颜色设置；当前使用自动配色。";
        var lines=textRenderer.wrapLines(Text.literal(message),w);
        int max=Math.max(0,(height-158)/10);
        for(int i=0;i<Math.min(max,lines.size());i++)ctx.drawTextWithShadow(textRenderer,lines.get(i),x,124+i*10,0xFFFFBB);
        super.render(ctx,mouseX,mouseY,delta);
    }
}
