/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import de.greluc.krt.profit.basetool.android.core.network.ServerClock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.security.KeyPairGenerator
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec

/**
 * The login attempt has to survive the browser, and the browser can outlive the process.
 *
 * That is the whole reason this class exists rather than a field on a ViewModel: while the Custom
 * Tab is in front, Android may kill this app, and on a low-memory phone it does. A field-based
 * implementation works on every developer's device and fails on a member's, once, unreproducibly —
 * so the "survives process death" case is the one worth writing first, and it is expressed here as
 * a second instance reading what the first one wrote.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PendingAuthorizationTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var cipher: FakeSecretCipher
    private lateinit var request: AuthorizationRequest

    @Before
    fun setUp() {
        val file = File(temporaryFolder.newFolder(), "auth.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create { file }
        cipher = FakeSecretCipher()
        request =
            AuthorizationRequestFactory(
                OidcConfiguration(
                    issuer = "https://keycloak.example/realms/iri",
                    clientId = "basetool-android",
                    redirectUri = "https://profit-base.online/app/callback",
                    postLogoutRedirectUri = "https://profit-base.online/app/logout",
                ),
                DpopProofFactory(generateKeyPair(), ServerClock()),
            ).create()
    }

    @Test
    fun `an attempt survives the process that started it`() =
        runTest {
            // The Custom Tab is in front, the app is killed, the redirect brings it back: a new
            // instance over the same file has to find the state, nonce and verifier.
            store().save(request)

            val restored = store().peek()

            assertNotNull("the attempt must survive a process restart", restored)
            assertEquals(request.state, restored!!.state)
            assertEquals(request.nonce, restored.nonce)
            assertEquals(request.pkce.verifier, restored.pkce.verifier)
            assertEquals(request.pkce.challenge, restored.pkce.challenge)
            assertEquals(request.url, restored.url)
        }

    @Test
    fun `taking an attempt consumes it`() =
        runTest {
            // A code can be redeemed exactly once, so a consumed attempt is finished. Leaving it
            // behind would let a stale or replayed redirect be acted on a second time.
            val store = store()
            store.save(request)

            assertNotNull(store.peek())
            // peek() no longer consumes: reading is what an exported activity can be made to do by
            // any installed app, and consuming on read let one of them end a login in flight. The
            // single-use property now belongs to clear(), which the caller runs once the redirect
            // has been judged to be this attempt's.
            assertNotNull("a peek must not consume the attempt", store.peek())
            store.clear()
            assertNull("a second redirect must find nothing", store.peek())
        }

    @Test
    fun `there is nothing to take when no login was started`() =
        runTest {
            assertNull(store().peek())
        }

    @Test
    fun `an unreadable attempt is discarded rather than thrown`() =
        runTest {
            // Same three ordinary states as the stored refresh token — key invalidated by a new
            // biometric enrolment, locked device, blob from elsewhere. All of them mean the login
            // starts over, and none of them is worth a crash on the way back from the browser.
            store().save(request)
            cipher.failDecryption = true

            assertNull(store().peek())
        }

    @Test
    fun `an abandoned login can be forgotten`() =
        runTest {
            val store = store()
            store.save(request)

            store.clear()

            assertNull(store.peek())
        }

    /**
     * Builds a store over the shared file and cipher.
     *
     * A new instance each call on purpose: every test that matters here is about what a *later*
     * process can read, and reusing one object would quietly test a field instead.
     *
     * @return a fresh store over the same DataStore
     */
    private fun store(): PendingAuthorization = PendingAuthorization(dataStore, cipher)

    /**
     * Generates an in-memory P-256 pair standing in for the Keystore one.
     *
     * @return the pair
     */
    private fun generateKeyPair(): DpopKeyPair {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"))
        val pair = generator.generateKeyPair()
        return DpopKeyPair(pair.private as ECPrivateKey, pair.public as ECPublicKey)
    }
}
