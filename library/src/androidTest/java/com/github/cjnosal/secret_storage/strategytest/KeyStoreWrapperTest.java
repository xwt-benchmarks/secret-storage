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

package com.github.cjnosal.secret_storage.strategytest;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.support.test.InstrumentationRegistry;

import com.github.cjnosal.secret_storage.keymanager.KeyManager;
import com.github.cjnosal.secret_storage.keymanager.KeyStoreWrapper;
import com.github.cjnosal.secret_storage.keymanager.KeyWrapper;
import com.github.cjnosal.secret_storage.keymanager.crypto.AndroidCrypto;
import com.github.cjnosal.secret_storage.keymanager.crypto.Crypto;
import com.github.cjnosal.secret_storage.keymanager.defaults.DefaultSpecs;
import com.github.cjnosal.secret_storage.keymanager.strategy.ProtectionStrategy;
import com.github.cjnosal.secret_storage.keymanager.strategy.cipher.CipherStrategy;
import com.github.cjnosal.secret_storage.keymanager.strategy.cipher.KeyStoreCipherSpec;
import com.github.cjnosal.secret_storage.keymanager.strategy.cipher.asymmetric.AsymmetricCipherStrategy;
import com.github.cjnosal.secret_storage.keymanager.strategy.cipher.symmetric.SymmetricCipherStrategy;
import com.github.cjnosal.secret_storage.keymanager.strategy.integrity.IntegrityStrategy;
import com.github.cjnosal.secret_storage.keymanager.strategy.integrity.KeyStoreIntegritySpec;
import com.github.cjnosal.secret_storage.keymanager.strategy.integrity.mac.MacStrategy;
import com.github.cjnosal.secret_storage.keymanager.strategy.integrity.signature.SignatureStrategy;
import com.github.cjnosal.secret_storage.storage.DataStorage;
import com.github.cjnosal.secret_storage.storage.FileStorage;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.security.GeneralSecurityException;

import static org.junit.Assert.assertEquals;

@TargetApi(Build.VERSION_CODES.M)
public class KeyStoreWrapperTest {

    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    Crypto crypto;
    AndroidCrypto androidCrypto;
    DataStorage keyStorage;

    @Before
    public void setup() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        crypto = new Crypto();
        androidCrypto = new AndroidCrypto();
        androidCrypto.clear();
        keyStorage = new FileStorage(context.getFilesDir() + "/testData");
        keyStorage.clear();
    }

    @Test
    public void testSSSS() throws Exception {
        KeyManager strat = createManager(
                new SymmetricCipherStrategy(crypto, getSymmetricCipherSpec()),
                new MacStrategy(crypto, getSymmetricIntegritySpec()),
                new SymmetricCipherStrategy(crypto, getSymmetricCipherSpec()),
                new MacStrategy(crypto, getSymmetricIntegritySpec())
        );

        byte[] cipher = strat.encrypt("Hello world".getBytes());
        String plain = new String(strat.decrypt(cipher));

        assertEquals(plain, "Hello world");
    }

    @Test
    public void testSSSA() throws Exception {
        KeyManager strat = createManager(
                new SymmetricCipherStrategy(crypto, getSymmetricCipherSpec()),
                new MacStrategy(crypto, getSymmetricIntegritySpec()),
                new SymmetricCipherStrategy(crypto, getSymmetricCipherSpec()),
                new SignatureStrategy(crypto, getAsymmetricIntegritySpec())
        );

        byte[] cipher = strat.encrypt("Hello world".getBytes());
        String plain = new String(strat.decrypt(cipher));

        assertEquals(plain, "Hello world");
    }

    @Test
    public void testSSAS() throws Exception {
        KeyManager strat = createManager(
                new SymmetricCipherStrategy(crypto, getSymmetricCipherSpec()),
                new MacStrategy(crypto, getSymmetricIntegritySpec()),
                new AsymmetricCipherStrategy(crypto, getAsymmetricCipherSpec()),
                new MacStrategy(crypto, getSymmetricIntegritySpec())
        );

        byte[] cipher = strat.encrypt("Hello world".getBytes());
        String plain = new String(strat.decrypt(cipher));

        assertEquals(plain, "Hello world");
    }

    @Test
    public void testSSAA() throws Exception {
        KeyManager strat = createManager(
                new SymmetricCipherStrategy(crypto, getSymmetricCipherSpec()),
                new MacStrategy(crypto, getSymmetricIntegritySpec()),
                new AsymmetricCipherStrategy(crypto, getAsymmetricCipherSpec()),
                new SignatureStrategy(crypto, getAsymmetricIntegritySpec())
        );

        byte[] cipher = strat.encrypt("Hello world".getBytes());
        String plain = new String(strat.decrypt(cipher));

        assertEquals(plain, "Hello world");
    }

    private KeyManager createManager(CipherStrategy dataCipher, IntegrityStrategy dataIntegrity,
                                     CipherStrategy keyCipher, IntegrityStrategy keyIntegrity) throws IOException, GeneralSecurityException {

        KeyWrapper wrapper =  new KeyStoreWrapper(
                androidCrypto,
                "test",
                new ProtectionStrategy(
                        keyCipher,
                        keyIntegrity
                )
        );
        return new KeyManager(
                "test",
                new ProtectionStrategy(
                        dataCipher,
                        dataIntegrity
                ),
                crypto,
                keyStorage,
                wrapper
        );
    }

    private static KeyStoreCipherSpec getSymmetricCipherSpec() {
        return DefaultSpecs.getKeyStoreAesCbcPkcs7CipherSpec();
    }

    private static KeyStoreCipherSpec getAsymmetricCipherSpec() {
        return DefaultSpecs.getKeyStoreRsaPkcs1CipherSpec();
    }

    private static KeyStoreIntegritySpec getSymmetricIntegritySpec() {
        return DefaultSpecs.getKeyStoreHmacShaIntegritySpec();
    }

    private static KeyStoreIntegritySpec getAsymmetricIntegritySpec() {
        return DefaultSpecs.getKeyStoreShaRsaPssIntegritySpec();
    }


}