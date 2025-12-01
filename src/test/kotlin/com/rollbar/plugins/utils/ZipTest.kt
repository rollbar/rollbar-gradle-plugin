package com.rollbar.plugins.utils

import org.junit.jupiter.api.Assertions.*
import java.io.File
import java.util.zip.ZipInputStream
import kotlin.test.Test

class ZipTest {
    @Test
    fun `zip file contains the original content`() {
        val input = File("build/tmp/mapping.txt").apply {
            parentFile.mkdirs()
            writeText(MAPPING_FILE_CONTENT)
        }

        val output = File("build/tmp/mapping.zip")

        zipMappingFile(input, output)

        assertTrue(output.exists())
        assertTrue(output.length() > 0)

        val unzipResult = unzipFile(output)
        assertEquals(1, unzipResult.size, "Zip should contain exactly one file")
        assertEquals(MAPPING_FILE_CONTENT, unzipResult.values.first(), "Zipped content does not match original")
    }

    private fun unzipFile(zipFile: File): Map<String, String> {
        val result = mutableMapOf<String, String>()
        ZipInputStream(zipFile.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val content = zip.bufferedReader().readText()
                result[entry.name] = content
                entry = zip.nextEntry
            }
        }
        return result
    }

}

private const val MAPPING_FILE_CONTENT = """
            # compiler: R8
            # compiler_version: 8.12.22
            # min_api: 27
            # common_typos_disable
            # {"id":"com.android.tools.r8.mapping","version":"2.2"}
            # pg_map_id: aa175ff649f19227259b5de69a9e1e926ca40a683dcdec41d5c55205874ccc96
            # pg_map_hash: SHA-256 aa175ff649f19227259b5de69a9e1e926ca40a683dcdec41d5c55205874ccc96
            # {"id":"sourceFile","fileName":"CoroutineDebugging.kt"}
            # {"id":"sourceFile","fileName":"CoroutineDebugging.kt"}
            _COROUTINE._BOUNDARY -> a.a:
            # {"id":"sourceFile","fileName":"CoroutineDebugging.kt"}
                0:2:void androidx.core.graphics.TypefaceCompatBaseImpl.<init>():47:47 -> <init>
                3:8:void androidx.core.graphics.TypefaceCompatBaseImpl.<init>():54:54 -> <init>
                0:1:java.lang.Object androidx.compose.ui.semantics.SemanticsConfiguration.getOrElseNullable(androidx.compose.ui.semantics.SemanticsPropertyKey,kotlin.jvm.functions.Function0):54:54 -> A
                0:1:java.lang.Object androidx.compose.ui.semantics.SemanticsConfigurationKt.getOrNull(androidx.compose.ui.semantics.SemanticsConfiguration,androidx.compose.ui.semantics.SemanticsPropertyKey):197 -> A
                  # {"id":"com.android.tools.r8.rewriteFrame","conditions":["throws(Ljava/lang/NullPointerException;)"],"actions":["removeInnerFrames(1)"]}
                  # {"id":"com.android.tools.r8.residualsignature","signature":"(Ll0/g;Ll0/q;)Ljava/lang/Object;"}
                2:9:java.lang.Object androidx.compose.ui.semantics.SemanticsConfiguration.getOrElseNullable(androidx.compose.ui.semantics.SemanticsPropertyKey,kotlin.jvm.functions.Function0):54:54 -> A
                2:9:java.lang.Object androidx.compose.ui.semantics.SemanticsConfigurationKt.getOrNull(androidx.compose.ui.semantics.SemanticsConfiguration,androidx.compose.ui.semantics.SemanticsPropertyKey):197 -> A
                0:9:android.view.ViewParent androidx.core.viewtree.ViewTree.getParentOrViewTreeDisjointParent(android.view.View):68:68 -> B
                10:13:android.view.ViewParent androidx.core.viewtree.ViewTree.getParentOrViewTreeDisjointParent(android.view.View):71:71 -> B
                14:22:android.view.ViewParent androidx.core.viewtree.ViewTree.getParentOrViewTreeDisjointParent(android.view.View):72:72 -> B
                0:13:int androidx.compose.ui.input.key.KeyEvent_androidKt.getType-ZmokQxo(android.view.KeyEvent):68:68 -> C
        """
