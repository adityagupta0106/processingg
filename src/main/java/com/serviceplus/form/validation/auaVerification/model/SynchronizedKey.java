package com.serviceplus.form.validation.auaVerification.model;

import java.util.Date;

public class SynchronizedKey {

    byte[] seedSkey;
    String keyIdentifier;
    Date seedCreationDate;

    public SynchronizedKey(byte[] seedSkey, String keyIdentifier,
                           Date seedCreationDate) {
        super();
        this.seedSkey = seedSkey;
        this.keyIdentifier = keyIdentifier;
        this.seedCreationDate = seedCreationDate;
    }

    public String getKeyIdentifier() {
        return keyIdentifier;
    }

    public Date getSeedCreationDate() {
        return seedCreationDate;
    }

    public byte[] getSeedSkey() {
        return seedSkey;
    }
}
