package com.antgskds.calendarassistant.platform.xposed

import com.antgskds.calendarassistant.platform.xposed.upstream.LuckMoneyModel
import com.antgskds.calendarassistant.platform.xposed.upstream.dex.Dex
import com.tencent.mm.plugin.luckymoney.model.MatchingPaymentModel
import com.tencent.mm.plugin.luckymoney.model.CallbackOnlyModel
import com.tencent.mm.plugin.luckymoney.model.WrongReturnModel
import org.jf.dexlib2.AccessFlags
import org.jf.dexlib2.Opcodes
import org.jf.dexlib2.immutable.ImmutableClassDef
import org.jf.dexlib2.immutable.ImmutableDexFile
import org.jf.dexlib2.writer.pool.DexPool
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class WechatHookAdaptationTest {
    @get:Rule val temp = TemporaryFolder()

    // Build a real DEX/APK name index; reflection resolves corresponding synthetic JVM fixtures.
    // The upstream scanner matches constructors/methods through the supplied host ClassLoader.
    private fun apk(vararg classes: Class<*>): File {
        val dex = temp.newFile("classes.dex")
        val definitions = classes.map { clazz ->
            ImmutableClassDef(
                "L" + clazz.name.replace('.', '/') + ";",
                AccessFlags.PUBLIC.value, "Ljava/lang/Object;", emptyList(), null,
                emptyList(), emptyList(), emptyList(),
            )
        }
        DexPool.writeTo(dex.absolutePath, ImmutableDexFile(Opcodes.getDefault(), definitions))
        return temp.newFile("host.apk").also { apk ->
            ZipOutputStream(apk.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("classes.dex"))
                dex.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    @Test fun completeSignatureSelectsOnlyTheUpstreamTarget() {
        val file = apk(CallbackOnlyModel::class.java, WrongReturnModel::class.java, MatchingPaymentModel::class.java)
        val result = Dex.findClazz(file.path, javaClass.classLoader!!, listOf(LuckMoneyModel.rule))
        assertEquals(setOf(LuckMoneyModel.rule.name), result.keys)
        assertEquals(MatchingPaymentModel::class.java.name, result.values.single().clazzName)
    }

    @Test fun sameCallbackWithoutConstructorOrWithWrongReturnDoesNotInstall() {
        val file = apk(CallbackOnlyModel::class.java, WrongReturnModel::class.java)
        assertTrue(Dex.findClazz(file.path, javaClass.classLoader!!, listOf(LuckMoneyModel.rule)).isEmpty())
    }

    @Test fun unrelatedClassesAreFilteredBeforeHostClassLoading() {
        val file = apk(String::class.java)
        val rejectingLoader = object : ClassLoader(javaClass.classLoader) {
            override fun loadClass(name: String): Class<*> = error("Unrelated class must not be loaded: " + name)
        }
        assertTrue(Dex.findClazz(file.path, rejectingLoader, listOf(LuckMoneyModel.rule)).isEmpty())
    }
}
