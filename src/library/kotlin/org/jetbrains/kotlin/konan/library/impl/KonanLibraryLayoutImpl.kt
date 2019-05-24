package org.jetbrains.kotlin.konan.library.impl

import org.jetbrains.kotlin.konan.file.File
import org.jetbrains.kotlin.konan.file.file
import org.jetbrains.kotlin.konan.file.withZipFileSystem
import org.jetbrains.kotlin.konan.library.*
import org.jetbrains.kotlin.konan.target.KonanTarget
import org.jetbrains.kotlin.konan.util.removeSuffixIfPresent
import java.nio.file.FileSystem

open class KotlinLibraryLayoutImpl(val klib: File) : KotlinLibraryLayout {
    val isZipped = klib.isFile

    init {
        if (isZipped) zippedKotlinLibraryChecks(klib)
    }

    override val libDir = if (isZipped) File("/") else klib

    override val libraryName
        get() =
            if (isZipped)
                klib.path.removeSuffixIfPresent(KLIB_FILE_EXTENSION_WITH_DOT)
            else
                libDir.path

    open val extractingToTemp: KotlinLibraryLayout by lazy {
        ExtractingBaseLibraryImpl(this)
    }

    open fun directlyFromZip(zipFileSystem: FileSystem): KotlinLibraryLayout =
        FromZipBaseLibraryImpl(this, zipFileSystem)

}

class MetadataLibraryLayoutImpl(klib: File) : KotlinLibraryLayoutImpl(klib), MetadataKotlinLibraryLayout {

    override val extractingToTemp: MetadataKotlinLibraryLayout by lazy {
        ExtractingMetadataLibraryImpl(this)
    }

    override fun directlyFromZip(zipFileSystem: FileSystem): MetadataKotlinLibraryLayout =
        FromZipMetadataLibraryImpl(this, zipFileSystem)
}

class IrLibraryLayoutImpl(klib: File) : KotlinLibraryLayoutImpl(klib), IrKotlinLibraryLayout {

    override val extractingToTemp: IrKotlinLibraryLayout by lazy {
        ExtractingIrLibraryImpl(this)
    }

    override fun directlyFromZip(zipFileSystem: FileSystem): IrKotlinLibraryLayout =
        FromZipIrLibraryImpl(this, zipFileSystem)
}

open class TargetedLibraryLayoutImpl(klib: File, override val target: KonanTarget?) :
    KotlinLibraryLayoutImpl(klib), TargetedKotlinLibraryLayout {

    override val extractingToTemp: TargetedKotlinLibraryLayout by lazy {
        ExtractingTargetedLibraryImpl(this)
    }

    override fun directlyFromZip(zipFileSystem: FileSystem): TargetedKotlinLibraryLayout =
        FromZipTargetedLibraryImpl(this, zipFileSystem)

}

class BitcodeLibraryLayoutImpl(klib: File, target: KonanTarget?) : TargetedLibraryLayoutImpl(klib, target),
    BitcodeKotlinLibraryLayout {
    override val extractingToTemp: BitcodeKotlinLibraryLayout by lazy {
        ExtractingBitcodeLibraryImpl(this)
    }

    override fun directlyFromZip(zipFileSystem: FileSystem): BitcodeKotlinLibraryLayout =
        FromZipBitcodeLibraryImpl(this, zipFileSystem)

}

open class BaseLibraryAccess<L : KotlinLibraryLayout>(val klib: File) {
    open val layout = KotlinLibraryLayoutImpl(klib)

    fun <T> realFiles(action: (L) -> T): T =
        if (layout.isZipped)
            action(layout.extractingToTemp as L)
        else
            action(layout as L)

    fun <T> inPlace(action: (L) -> T): T =
        if (layout.isZipped)
            layout.klib.withZipFileSystem { zipFileSystem ->
                action(layout.directlyFromZip(zipFileSystem) as L)
            }
        else
            action(layout as L)
}


open class MetadataLibraryAccess<L : KotlinLibraryLayout>(klib: File) : BaseLibraryAccess<L>(klib) {
    override val layout = MetadataLibraryLayoutImpl(klib)
}

open class IrLibraryAccess<L : KotlinLibraryLayout>(klib: File) : BaseLibraryAccess<L>(klib) {
    override val layout = IrLibraryLayoutImpl(klib)
}

open class TargetedLibraryAccess<L : KotlinLibraryLayout>(klib: File, val target: KonanTarget?) :
    BaseLibraryAccess<L>(klib) {
    override val layout = TargetedLibraryLayoutImpl(klib, target)
}

open class BitcodeLibraryAccess<L : KotlinLibraryLayout>(klib: File, target: KonanTarget?) :
    TargetedLibraryAccess<L>(klib, target) {
    override val layout = BitcodeLibraryLayoutImpl(klib, target)
}

private open class FromZipBaseLibraryImpl(zipped: KotlinLibraryLayoutImpl, zipFileSystem: FileSystem) :
    KotlinLibraryLayout {

    override val libraryName = zipped.libraryName
    override val libDir = zipFileSystem.file(zipped.libDir)
}

private class FromZipMetadataLibraryImpl(zipped: MetadataLibraryLayoutImpl, zipFileSystem: FileSystem) :
    FromZipBaseLibraryImpl(zipped, zipFileSystem), MetadataKotlinLibraryLayout

private class FromZipIrLibraryImpl(zipped: IrLibraryLayoutImpl, zipFileSystem: FileSystem) :
    FromZipBaseLibraryImpl(zipped, zipFileSystem), IrKotlinLibraryLayout

private open class FromZipTargetedLibraryImpl(zipped: TargetedLibraryLayoutImpl, zipFileSystem: FileSystem) :
    FromZipBaseLibraryImpl(zipped, zipFileSystem), TargetedKotlinLibraryLayout

private class FromZipBitcodeLibraryImpl(zipped: BitcodeLibraryLayoutImpl, zipFileSystem: FileSystem) :
    FromZipTargetedLibraryImpl(zipped, zipFileSystem), BitcodeKotlinLibraryLayout

/**
 * This class and its children automatically extracts pieces of the library on first access. Use it if you need
 * to pass extracted files to an external tool. Otherwise, stick to [FromZipBaseLibraryImpl].
 */
open class ExtractingKotlinLibraryAccess(private val zipped: KotlinLibraryLayoutImpl) : KotlinLibraryLayout by zipped {
    fun extract(file: File): File = zipped.klib.withZipFileSystem { zipFileSystem ->
        val temporary = org.jetbrains.kotlin.konan.file.createTempFile(file.name)
        zipFileSystem.file(file).copyTo(temporary)
        temporary.deleteOnExit()
        temporary
    }

    fun extractDir(directory: File): File = zipped.klib.withZipFileSystem { zipFileSystem ->
        val temporary = org.jetbrains.kotlin.konan.file.createTempDir(directory.name)
        zipFileSystem.file(directory).recursiveCopyTo(temporary)
        temporary.deleteOnExitRecursively()
        temporary
    }
}

open class ExtractingBaseLibraryImpl(zipped: KotlinLibraryLayoutImpl) :
    ExtractingKotlinLibraryAccess(zipped),
    KotlinLibraryLayout {

    override val manifestFile: File by lazy { extract(zipped.manifestFile) }
    override val resourcesDir: File by lazy { extractDir(zipped.resourcesDir) }
}

class ExtractingMetadataLibraryImpl(zipped: MetadataLibraryLayoutImpl) :
    ExtractingKotlinLibraryAccess(zipped),
    MetadataKotlinLibraryLayout {

    override val metadataDir: File by lazy { extractDir(zipped.metadataDir) }
}

class ExtractingIrLibraryImpl(zipped: IrLibraryLayoutImpl) : ExtractingKotlinLibraryAccess(zipped),
    IrKotlinLibraryLayout {

    override val irFile: File by lazy { extract(zipped.irFile) }
}

open class ExtractingTargetedLibraryImpl(zipped: TargetedLibraryLayoutImpl) :
    ExtractingKotlinLibraryAccess(zipped),
    TargetedKotlinLibraryLayout {

    override val includedDir: File by lazy { extractDir(zipped.includedDir) }
}

class ExtractingBitcodeLibraryImpl(zipped: BitcodeLibraryLayoutImpl) :
    ExtractingTargetedLibraryImpl(zipped), BitcodeKotlinLibraryLayout {

    override val kotlinDir: File by lazy { extractDir(zipped.kotlinDir) }
    override val nativeDir: File by lazy { extractDir(zipped.nativeDir) }
}

internal fun zippedKotlinLibraryChecks(klibFile: File) {
    check(klibFile.exists) { "Could not find $klibFile." }
    check(klibFile.isFile) { "Expected $klibFile to be a regular file." }

    val extension = klibFile.extension
    check(extension.isEmpty() || extension == KLIB_FILE_EXTENSION) {
        "KLIB path has unexpected extension: $klibFile"
    }
}