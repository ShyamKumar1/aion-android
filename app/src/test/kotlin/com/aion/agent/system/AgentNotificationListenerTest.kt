package com.aion.agent.system

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

class AgentNotificationListenerTest {

    private lateinit var listener: AgentNotificationListener

    @Before
    fun setUp() {
        // We test only the classify() pure function — the service requires
        // Android framework that Robolectric provides, but we keep this
        // focused on the classification logic.
        listener = AgentNotificationListener()
    }

    @Test
    fun `classify whatsapp package returns message`() {
        val result = listener.classify("com.whatsapp", "New message", "Hello!")
        assertThat(result).isEqualTo("message")
    }

    @Test
    fun `classify telegram package returns message`() {
        val result = listener.classify("org.telegram.messenger", "Hey", "How are you?")
        assertThat(result).isEqualTo("message")
    }

    @Test
    fun `classify gmail package returns message`() {
        val result = listener.classify("com.google.android.gm", "Subject", "Email body")
        assertThat(result).isEqualTo("message")
    }

    @Test
    fun `classify slack package returns message`() {
        val result = listener.classify("com.slack", "New message", "Meeting at 3pm")
        assertThat(result).isEqualTo("message")
    }

    @Test
    fun `classify with spam text returns spam`() {
        val result = listener.classify("com.unknown.app", "You won!", "Click here to claim your prize. Spam.")
        assertThat(result).isEqualTo("spam")
    }

    @Test
    fun `classify with unsubscribe text returns spam`() {
        val result = listener.classify("com.newsletter", "Your subscription", "Click to unsubscribe")
        assertThat(result).isEqualTo("spam")
    }

    @Test
    fun `classify with alert title returns alert`() {
        val result = listener.classify("com.security.app", "Alert", "Suspicious login detected")
        assertThat(result).isEqualTo("alert")
    }

    @Test
    fun `classify with warning text returns alert`() {
        val result = listener.classify("com.antivirus", "Warning", "Threat detected")
        assertThat(result).isEqualTo("alert")
    }

    @Test
    fun `classify update text returns system`() {
        val result = listener.classify("com.system.app", "Update available", "New version 3.0 is ready")
        assertThat(result).isEqualTo("system")
    }

    @Test
    fun `classify generic app returns other`() {
        val result = listener.classify("com.random.app", "Hello", "Just a friendly message")
        assertThat(result).isEqualTo("other")
    }

    @Test
    fun `classify empty text returns other`() {
        val result = listener.classify("com.test", "Title", "")
        assertThat(result).isEqualTo("other")
    }

    @Test
    fun `classify distinguishes message from other app`() {
        // A package like com.whatsapp should be message even with non-message text
        val result = listener.classify("com.whatsapp", "Security alert", "Your account")
        assertThat(result).isEqualTo("message")
    }
}
