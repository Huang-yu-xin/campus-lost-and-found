package com.campus.lostfound.modules.account.service;

import java.util.Locale;

public final class EmailAddresses {

    private EmailAddresses() {
    }

    /** 邮箱去首尾空白后统一小写，再保存和判重。 */
    public static String normalize(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
