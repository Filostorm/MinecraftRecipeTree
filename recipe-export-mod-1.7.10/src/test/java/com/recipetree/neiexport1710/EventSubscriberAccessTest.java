package com.recipetree.neiexport1710;

import cpw.mods.fml.common.gameevent.TickEvent;
import codechicken.nei.event.NEIConfigsLoadedEvent;
import org.junit.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.Assert.assertTrue;

public class EventSubscriberAccessTest {
    @Test
    public void forgeGeneratedEventTrampolineCanAccessSubscriber() throws Exception {
        assertTrue(
                "Forge's ASM event trampoline runs in another runtime package and class loader, "
                        + "so the subscriber class must be public",
                Modifier.isPublic(ExportCoordinator.class.getModifiers()));

        Method method = ExportCoordinator.class.getMethod(
                "onClientTick", TickEvent.ClientTickEvent.class);
        assertTrue("subscribed event method must be public", Modifier.isPublic(method.getModifiers()));

        Method neiMethod = ExportCoordinator.class.getMethod(
                "onNeiConfigsLoaded", NEIConfigsLoadedEvent.class);
        assertTrue("NEI event method must be public", Modifier.isPublic(neiMethod.getModifiers()));
    }
}
