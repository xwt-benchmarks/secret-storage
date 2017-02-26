/*
 *    Copyright 2016 Conor Nosal
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.github.cjnosal.secret_storage.keymanager;

import android.content.Context;
import android.support.test.InstrumentationRegistry;

import com.github.cjnosal.secret_storage.keymanager.crypto.SecurityAlgorithms;
import com.github.cjnosal.secret_storage.keymanager.defaults.DefaultSpecs;
import com.github.cjnosal.secret_storage.storage.DataStorage;
import com.github.cjnosal.secret_storage.storage.PreferenceStorage;

import org.junit.Before;
import org.junit.Test;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.security.auth.login.LoginException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class PasswordKeyWrapperTest {

    private Context context;
    private DataStorage configStorage;
    private DataStorage keyStorage;
    private PasswordKeyWrapper subject;
    private KeyGenerator keyGenerator;
    private SecretKey enc;
    private SecretKey sig;

    @Before
    public void setup() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        configStorage = new PreferenceStorage(context, "testConfig");
        configStorage.clear();
        keyStorage = new PreferenceStorage(context, "testKeys");
        keyStorage.clear();

        subject = new PasswordKeyWrapper(
                DefaultSpecs.getPasswordDerivationSpec(),
                DefaultSpecs.getPasswordBasedKeyProtectionSpec(),
                configStorage,
                keyStorage
        );

        keyGenerator = KeyGenerator.getInstance(SecurityAlgorithms.KeyGenerator_AES);
        keyGenerator.init(SecurityAlgorithms.KEY_SIZE_AES_256);
        enc = keyGenerator.generateKey();
        sig = keyGenerator.generateKey();
    }

    @Test
    public void storeAndLoad() throws Exception {
        subject.setPassword("id", "password");

        subject.storeDataEncryptionKey("id", enc);
        assertTrue(configStorage.exists("id::KEY_PROTECTION"));
        assertTrue(keyStorage.exists("id::WRAPPED_ENCRYPTION_KEY"));

        subject.storeDataSigningKey("id", sig);
        assertTrue(keyStorage.exists("id::WRAPPED_SIGNING_KEY"));

        assertTrue(configStorage.exists("id::ENC_SALT"));
        assertTrue(configStorage.exists("id::VERIFICATION"));

        subject = new PasswordKeyWrapper(
                DefaultSpecs.getPasswordDerivationSpec(),
                DefaultSpecs.getPasswordBasedKeyProtectionSpec(),
                configStorage,
                keyStorage
        );
        subject.unlock("id", "password");

        SecretKey unwrappedEnc = subject.loadDataEncryptionKey("id", SecurityAlgorithms.KeyGenerator_AES);
        assertEquals(enc, unwrappedEnc);

        SecretKey unwrappedSig = subject.loadDataSigningKey("id", SecurityAlgorithms.KeyGenerator_AES);
        assertEquals(sig, unwrappedSig);
    }

    @Test
    public void eraseConfig() throws Exception {
        subject.setPassword("id", "password");

        subject.storeDataEncryptionKey("id", enc);
        subject.storeDataSigningKey("id", sig);

        subject.eraseConfig("id");

        assertFalse(configStorage.exists("id::ENC_SALT"));
        assertFalse(configStorage.exists("id::VERIFICATION"));
        assertFalse(configStorage.exists("id::KEY_PROTECTION"));
        assertFalse(keyStorage.exists("id::WRAPPED_ENCRYPTION_KEY"));
        assertFalse(keyStorage.exists("id::WRAPPED_SIGNING_KEY"));
    }

    @Test
    public void eraseKeys() throws Exception {
        subject.setPassword("id", "password");

        subject.storeDataEncryptionKey("id", enc);
        subject.storeDataSigningKey("id", sig);

        subject.eraseKeys("id");

        assertFalse(keyStorage.exists("id::WRAPPED_ENCRYPTION_KEY"));
        assertFalse(keyStorage.exists("id::WRAPPED_SIGNING_KEY"));
    }

    @Test
    public void keysExist() throws Exception {
        subject.setPassword("id", "password");
        assertFalse(subject.dataKeysExist("id"));

        subject.storeDataEncryptionKey("id", enc);
        subject.storeDataSigningKey("id", sig);
        assertTrue(subject.dataKeysExist("id"));
    }

    @Test
    public void getEditor_noPassword_setPassword() throws Exception {
        PasswordKeyWrapper.PasswordEditor editor = subject.getEditor("id", SecurityAlgorithms.KeyGenerator_AES, SecurityAlgorithms.KeyGenerator_AES);
        assertFalse(editor.isPasswordSet());
        assertFalse(editor.isUnlocked());

        editor.setPassword("password");

        assertTrue(editor.isPasswordSet());
        assertTrue(editor.isUnlocked());
    }

    @Test
    public void getEditor_withPassword_setPasswordFails() throws Exception {
        PasswordKeyWrapper.PasswordEditor editor = subject.getEditor("id", SecurityAlgorithms.KeyGenerator_AES, SecurityAlgorithms.KeyGenerator_AES);
        editor.setPassword("password");

        try {
            editor.setPassword("password2");
            fail("Password already set");
        } catch (LoginException expected) {}
    }

    @Test
    public void getEditor_verifyPassword() throws Exception {
        PasswordKeyWrapper.PasswordEditor editor = subject.getEditor("id", SecurityAlgorithms.KeyGenerator_AES, SecurityAlgorithms.KeyGenerator_AES);

        try {
            editor.verifyPassword("password");
            fail("Password not set");
        } catch (LoginException expected) {}

        editor.setPassword("password");
        editor.lock();

        assertFalse(editor.verifyPassword("1234"));
        assertTrue(editor.verifyPassword("password"));
        assertFalse(editor.isUnlocked());
    }

    @Test
    public void getEditor_lock() throws Exception {
        PasswordKeyWrapper.PasswordEditor editor = subject.getEditor("id", SecurityAlgorithms.KeyGenerator_AES, SecurityAlgorithms.KeyGenerator_AES);
        editor.setPassword("password");

        editor.lock();
        assertTrue(editor.isPasswordSet());
        assertFalse(editor.isUnlocked());
    }

    @Test
    public void getEditor_unlock() throws Exception {
        PasswordKeyWrapper.PasswordEditor editor = subject.getEditor("id", SecurityAlgorithms.KeyGenerator_AES, SecurityAlgorithms.KeyGenerator_AES);

        try {
            editor.unlock("password");
            fail("Password not set");
        } catch (LoginException expected) {}

        editor.setPassword("password");
        editor.lock();

        try {
            editor.unlock("password2");
            fail("Wrong password");
        } catch (LoginException expected) {}

        editor.unlock("password");
        assertTrue(editor.isUnlocked());
    }

    @Test
    public void getEditor_changePassword() throws Exception {
        PasswordKeyWrapper.PasswordEditor editor = subject.getEditor("id", SecurityAlgorithms.KeyGenerator_AES, SecurityAlgorithms.KeyGenerator_AES);

        try {
            editor.changePassword(null, "password");
            fail("Password not set");
        } catch (LoginException expected) {}

        editor.setPassword("password");
        editor.lock();

        try {
            editor.changePassword("1234", "password2");
            fail("Wrong password");
        } catch (LoginException expected) {}

        editor.changePassword("password", "password2");
        assertTrue(editor.isUnlocked());
        assertTrue(editor.isPasswordSet());
    }


}