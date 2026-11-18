package dev.jdguo.land.client;

import dev.jdguo.land.LandMod;
import dev.jdguo.land.client.xaero.XaeroRefresh;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.Files;
import java.util.Properties;

/** This machine's overlay visibility only; never sent to the server. Selection stays visible. */
public final class ClaimDisplayPreferences {
    private static boolean visible=true;
    private static int revision;
    private ClaimDisplayPreferences() {}
    private static java.nio.file.Path path(){return FabricLoader.getInstance().getConfigDir().resolve("landmod-display.properties");}
    public static void load(){
        if(!Files.isRegularFile(path()))return;
        try(var in=Files.newInputStream(path())){var p=new Properties();p.load(in);visible=Boolean.parseBoolean(p.getProperty("showClaims","true"));}
        catch(java.io.IOException error){LandMod.LOGGER.warn("Could not load local land display preference",error);}
    }
    public static boolean visible(){return visible;}
    public static int revision(){return revision;}
    public static void toggle(){
        visible=!visible;revision++;XaeroRefresh.mark();
        try{Files.createDirectories(path().getParent());try(var out=Files.newOutputStream(path())){var p=new Properties();p.setProperty("showClaims",Boolean.toString(visible));p.store(out,"Local land overlay visibility");}}
        catch(java.io.IOException error){LandMod.LOGGER.warn("Could not save local land display preference",error);}
    }
}
