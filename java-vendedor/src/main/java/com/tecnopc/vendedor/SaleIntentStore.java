package com.tecnopc.vendedor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.prefs.Preferences;

/** Persists only UUID and SHA-256: no customer data, credentials or cart payload. */
final class SaleIntentStore {
    private final Preferences preferences;
    SaleIntentStore(){this(Preferences.userNodeForPackage(SaleIntentStore.class).node("sale-intents"));}
    SaleIntentStore(Preferences preferences){this.preferences=preferences;}
    private String hash(String value){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(Exception ex){throw new IllegalStateException(ex);}
    }
    String prepare(String scope,String payload){
        String storageKey=hash(scope),fingerprint=hash(payload);
        String saved=preferences.get(storageKey,"");
        if(!saved.isEmpty()){
            String[] parts=saved.split(":",-1);
            if(parts.length!=2||!parts[0].matches("[0-9a-f-]{36}")||!parts[1].equals(fingerprint))
                throw new IllegalStateException("Hay una venta sin confirmar con otros datos. Consulta Mis ventas y resuelve el intento anterior antes de cambiarlo.");
            flush();return parts[0];
        }
        String key=UUID.randomUUID().toString();preferences.put(storageKey,key+":"+fingerprint);flush();return key;
    }
    void clear(String scope){preferences.remove(hash(scope));flush();}
    private void flush(){try{preferences.flush();}catch(Exception ex){throw new IllegalStateException("No se pudo guardar el intento. No se enviará otra venta hasta resolver el almacenamiento local.",ex);}}
}
