package com.vidyo.vidyoconnector;

import android.annotation.TargetApi;

import org.apache.commons.codec.android.binary.Base64;
import org.apache.commons.codec.android.digest.HmacAlgorithms;
import org.apache.commons.codec.android.digest.HmacUtils;

import java.util.Arrays;
import java.util.List;
import java.util.StringJoiner;


public class GenerateToken {

    private static final String PROVISION_TOKEN = "provision";
    private static final long EPOCH_SECONDS = 62167219200l;
    private static final String DELIM = "\0";





    static private String generateProvisionToken(String key, String jid, String expires, String vcard) throws NumberFormatException {

       List<String> items = Arrays.asList(PROVISION_TOKEN,jid,calculateExpiry(expires),vcard);
       String payload = join(items,DELIM);

       List<String> item = Arrays.asList(payload,HmacUtils.hmacSha384Hex(key, payload));//new HmacUtils(HmacAlgorithms.HMAC_SHA_384,key).hmacHex(payload)
       return new String(Base64.encodeBase64(join(item,DELIM).getBytes()));

    }

    static private String join(List<String> list, String Delim)
    {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String item : list)
        {
            if (first)
                first = false;
            else
                sb.append(Delim);
            sb.append(item);
        }
        return sb.toString();
    }

     static private String calculateExpiry(String expires) throws NumberFormatException {
        long expiresLong = 0l;
        long currentUnixTimestamp = System.currentTimeMillis() / 1000;
        expiresLong = Long.parseLong(expires);
        return ""+(EPOCH_SECONDS + currentUnixTimestamp + expiresLong);
    }

    public static void Generate(String Username,TokenSuccess _token) {

        String token = generateProvisionToken("ba6c969e39b547178fc1a36c35ef63b9",Username +"@"+"8e6c51.vidyo.io","1000","");
       _token.Token(token);

    }


}
