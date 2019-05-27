/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.konan

fun String.parseKonanVersion(): KonanVersion = KonanVersion.fromString(this)
/*
// A pseudo-constructor
fun KonanVersionImpl(
    meta: MetaVersion = MetaVersion.DEV,
    major: Int,
    minor: Int,
    maintenance: Int,
    build: Int = -1
): KonanVersion =
    KonanVersionImpl(meta, major, minor, maintenance, build)

 */