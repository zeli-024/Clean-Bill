package dev.zeli.cleanbill;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

import java.io.IOException;

@Mod(CleanBill.MOD_ID)
public final class CleanBill {
    public static final String MOD_ID = "cleanbill";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final CleanupManager CLEANUP = new CleanupManager();

    public CleanBill(IEventBus modBus) {
        try {
            CleanConfig.load();
        } catch (IOException exception) {
            LOGGER.error("Could not load Clean Bill's configuration; defaults will be used", exception);
        }
        NeoForge.EVENT_BUS.addListener(CLEANUP::onServerTick);
        NeoForge.EVENT_BUS.addListener(CLEANUP::onEntityJoin);
        NeoForge.EVENT_BUS.addListener(CleanBillCommands::register);
        NeoForge.EVENT_BUS.addListener(CleanBillMenus::onServerChat);
    }
}
