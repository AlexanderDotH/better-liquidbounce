/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.ccbluex.liquidbounce.common

import net.ccbluex.liquidbounce.utils.client.GitInfo as PublicGitInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ClientBuildMetadataTest {
    @Test
    fun `neutral metadata mirrors the immutable git backed client identity`() {
        assertEquals("LiquidBounce", ClientBuildMetadata.NAME)
        assertEquals(GitInfo.version(), ClientBuildMetadata.version)
        assertEquals(GitInfo.branch(), ClientBuildMetadata.branch)
        assertEquals(GitInfo.get("git.commit.id.abbrev")?.let { "git-$it" } ?: "unknown", ClientBuildMetadata.commit)
    }

    @Test
    fun `public git info facade preserves every neutral lookup contract`() {
        assertEquals(GitInfo.version(), PublicGitInfo.version())
        assertEquals(GitInfo.branch(), PublicGitInfo.branch())
        assertEquals(GitInfo.get("git.commit.id.abbrev"), PublicGitInfo.get("git.commit.id.abbrev"))
        assertEquals(GitInfo.get("missing.contract.key"), PublicGitInfo.get("missing.contract.key"))
        assertEquals(
            GitInfo.getOrDefault("missing.contract.key", "fallback"),
            PublicGitInfo.getOrDefault("missing.contract.key", "fallback"),
        )
        assertEquals(GitInfo.entries(), PublicGitInfo.entries())
    }
}
