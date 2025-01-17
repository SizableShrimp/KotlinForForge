package thedarkcolour.kotlinforforge

import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.fml.Logging
import net.minecraftforge.fml.ModContainer
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.loading.moddiscovery.ModAnnotation
import net.minecraftforge.forgespi.language.ModFileScanData
import org.objectweb.asm.Type
import thedarkcolour.kotlinforforge.forge.DIST
import thedarkcolour.kotlinforforge.forge.FORGE_BUS
import thedarkcolour.kotlinforforge.forge.MOD_BUS
import thedarkcolour.kotlinforforge.kotlin.enumSet

/** @since 1.0.0
 * Automatically registers `object` classes to
 * Kotlin for Forge's event buses.
 *
 *  @since 1.12.2
 * Mod.EventBusSubscriber can now be applied as a file annotation.
 * Any functions annotated with @SubscribeEvent in the annotated file will be registered.
 *
 * Example:
 * ```
 * @file:Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
 *
 * package example
 *
 * @SubscribeEvent
 * fun onCommonSetup(event: FMLCommonSetupEvent) {
 *   // registered to mod event bus
 * }
 * ```
 *
 * @see MOD_BUS
 * @see FORGE_BUS
 */
public object AutoKotlinEventBusSubscriber {
    /** The [Mod.EventBusSubscriber] java type. */
    private val EVENT_BUS_SUBSCRIBER: Type = Type.getType(Mod.EventBusSubscriber::class.java)
    /** The default (client & server) list of [Dist] enum holders. */
    private val DIST_ENUM_HOLDERS = listOf(
        ModAnnotation.EnumHolder(null, "CLIENT"),
        ModAnnotation.EnumHolder(null, "DEDICATED_SERVER")
    )

    /**
     * Allows the [Mod.EventBusSubscriber] annotation
     * to target member functions of an `object` class.
     *
     * You **must** be using an `object` class, or the
     * `Mod.EventBusSubscriber` annotation will ignore it.
     *
     * I am against using `Mod.EventBusSubscriber`
     * because it makes it difficult to follow where event
     * listeners are registered. Instead, prefer to directly
     * register event listeners to the [FORGE_BUS] or [MOD_BUS].
     */
    public fun inject(mod: ModContainer, scanData: ModFileScanData, classLoader: ClassLoader) {
        LOGGER.debug(Logging.LOADING, "Attempting to inject @EventBusSubscriber kotlin objects in to the event bus for ${mod.modId}")

        val data = scanData.annotations.filter { annotationData ->
            EVENT_BUS_SUBSCRIBER == annotationData.annotationType
        }

        for (annotationData in data) {
            val sidesValue = annotationData.annotationData.getOrDefault("value", DIST_ENUM_HOLDERS) as List<ModAnnotation.EnumHolder>
            val sides = enumSet<Dist>().plus(sidesValue.map { eh -> Dist.valueOf(eh.value) })
            val modid = annotationData.annotationData.getOrDefault("modid", mod.modId)
            val busTargetHolder = annotationData.annotationData.getOrDefault("bus", ModAnnotation.EnumHolder(null, "FORGE")) as ModAnnotation.EnumHolder
            val busTarget = Mod.EventBusSubscriber.Bus.valueOf(busTargetHolder.value)

            if (mod.modId == modid && DIST in sides) {
                val kClass = Class.forName(annotationData.classType.className, true, classLoader).kotlin

                var ktObject: Any?

                try {
                    ktObject = kClass.objectInstance
                } catch (unsupported: UnsupportedOperationException) {
                    if (unsupported.message?.contains("file facades") == false) {
                        throw unsupported
                    } else {
                        LOGGER.debug(Logging.LOADING, "Auto-subscribing kotlin file ${annotationData.classType.className} to $busTarget")
                        registerTo(kClass.java, busTarget)
                        continue
                    }
                }

                if (ktObject != null) {
                    try {
                        LOGGER.debug(Logging.LOADING, "Auto-subscribing kotlin object ${annotationData.classType.className} to $busTarget")

                        registerTo(ktObject, busTarget)
                    } catch (e: Throwable) {
                        LOGGER.fatal(Logging.LOADING, "Failed to load mod class ${annotationData.classType} for @EventBusSubscriber annotation", e)
                        throw RuntimeException(e)
                    }
                }
            }
        }
    }

    private fun registerTo(any: Any, target: Mod.EventBusSubscriber.Bus) {
        if (target == Mod.EventBusSubscriber.Bus.MOD) {
            MOD_BUS.register(any)
        } else {
            FORGE_BUS.register(any)
        }
    }
}