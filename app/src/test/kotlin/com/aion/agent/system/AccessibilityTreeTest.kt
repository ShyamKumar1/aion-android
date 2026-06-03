package com.aion.agent.system

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AccessibilityTreeTest {

    private val tree = AccessibilityTree()

    @Test
    fun `isSecurePackage returns true for banking apps`() {
        assertThat(tree.isSecurePackage("com.chase.mobile")).isTrue()
        assertThat(tree.isSecurePackage("com.wellsfargo.wellsfargomobile")).isTrue()
        assertThat(tree.isSecurePackage("com.bankofamerica.mobile")).isTrue()
    }

    @Test
    fun `isSecurePackage returns true for password managers`() {
        assertThat(tree.isSecurePackage("com.bitwarden.mobile")).isTrue()
        assertThat(tree.isSecurePackage("com.lastpass.lpandroid")).isTrue()
        assertThat(tree.isSecurePackage("com.agilebits.onepassword")).isTrue()
    }

    @Test
    fun `isSecurePackage returns true for settings`() {
        assertThat(tree.isSecurePackage("com.android.settings")).isTrue()
    }

    @Test
    fun `isSecurePackage returns false for normal apps`() {
        assertThat(tree.isSecurePackage("com.whatsapp")).isFalse()
        assertThat(tree.isSecurePackage("com.google.android.gm")).isFalse()
        assertThat(tree.isSecurePackage("com.google.chrome")).isFalse()
    }

    @Test
    fun `isSecurePackage returns false for empty string`() {
        assertThat(tree.isSecurePackage("")).isFalse()
    }
}
