package com.rollbar.plugins.utils

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

fun zipMappingFile(mappingFile: File, destinationZipFile: File): File {
    destinationZipFile.parentFile.mkdirs()
    FileOutputStream(destinationZipFile).use { fileOutputStream ->
        ZipOutputStream(fileOutputStream).use { zipOutputStream ->
            zipOutputStream.setLevel(MAXIMUM_COMPRESSION)
            zipOutputStream.setMethod(ZipOutputStream.DEFLATED)
            zipOutputStream.putNextEntry(ZipEntry(mappingFile.name))

            FileInputStream(mappingFile).use { fis ->
                fis.copyTo(zipOutputStream, bufferSize = 4096)
            }
            zipOutputStream.closeEntry()
        }
    }
    return destinationZipFile
}

private const val MAXIMUM_COMPRESSION = 9
