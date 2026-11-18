package dev.jdguo.land.client;

import java.util.*;

/** Commit complete snapshots only; partial pages can never become a usable color baseline. */
public final class OwnerColorPages {
    private UUID transfer;
    private int count;
    private final Map<Integer,Map<UUID,Integer>> pages=new HashMap<>();
    private Map<UUID,Integer> colors=Map.of();
    private boolean ready;
    public boolean ready(){return ready;}
    public Map<UUID,Integer> colors(){return colors;}
    public boolean pending(){return transfer!=null;}
    public void reset(){transfer=null;count=0;pages.clear();colors=Map.of();ready=false;}
    public boolean accept(boolean full,UUID id,int index,int total,Map<UUID,Integer> page){
        if(total<1||total>4096||index<0||index>=total||page.size()>256)throw new IllegalArgumentException("color page bounds");
        if(!full){
            if(index!=0||total!=1)throw new IllegalArgumentException("delta bounds");
            if(!ready||pending())return false;
            var next=new HashMap<>(colors);page.forEach((owner,rgb)->{if(rgb==-1)next.remove(owner);else next.put(owner,rgb);});colors=Map.copyOf(next);return true;
        }
        if(!id.equals(transfer)){
            if(index!=0)return false;
            transfer=id;count=total;pages.clear();
        }
        if(count!=total)throw new IllegalArgumentException("inconsistent page count");
        pages.put(index,Map.copyOf(page));
        if(pages.size()!=count)return false;
        var next=new HashMap<UUID,Integer>();
        for(int i=0;i<count;i++)next.putAll(pages.get(i));
        colors=Map.copyOf(next);ready=true;transfer=null;pages.clear();return true;
    }
}
