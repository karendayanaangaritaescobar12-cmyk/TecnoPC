package com.tecnopc.vendedor;

import java.util.*;
import java.util.prefs.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SaleIntentStoreTest {
    static class MemoryPreferences extends AbstractPreferences {
        final Map<String,String> values=new HashMap<>();boolean unavailable;
        MemoryPreferences(){super(null,"");}
        protected void putSpi(String key,String value){values.put(key,value);}
        protected String getSpi(String key){return values.get(key);}
        protected void removeSpi(String key){values.remove(key);}
        protected void removeNodeSpi(){values.clear();}
        protected String[] keysSpi(){return values.keySet().toArray(String[]::new);}
        protected String[] childrenNamesSpi(){return new String[0];}
        protected AbstractPreferences childSpi(String name){throw new UnsupportedOperationException();}
        protected void syncSpi(){}
        protected void flushSpi()throws BackingStoreException{if(unavailable)throw new BackingStoreException("test");}
    }
    @Test void restartReusesKeyWithoutSavingCustomerDetails(){
        var preferences=new MemoryPreferences();String payload="{\"cliente\":\"Cliente de prueba\",\"items\":[1]}";
        String first=new SaleIntentStore(preferences).prepare("server|seller",payload);
        assertEquals(first,new SaleIntentStore(preferences).prepare("server|seller",payload));
        assertFalse(preferences.values.toString().contains("Cliente de prueba"));assertFalse(preferences.values.toString().contains("server"));
    }
    @Test void changedDataCannotSilentlyStartAnotherSale(){
        var store=new SaleIntentStore(new MemoryPreferences());store.prepare("server|seller","first");
        assertThrows(IllegalStateException.class,()->store.prepare("server|seller","changed"));
        store.clear("server|seller");assertDoesNotThrow(()->store.prepare("server|seller","changed"));
    }
    @Test void storageFailurePreventsBothInitialAndRepeatedSubmission(){
        var preferences=new MemoryPreferences();preferences.unavailable=true;var store=new SaleIntentStore(preferences);
        assertThrows(IllegalStateException.class,()->store.prepare("server|seller","payload"));
        assertThrows(IllegalStateException.class,()->store.prepare("server|seller","payload"));
    }
    @Test void accountsAndServersKeepSeparateIntents(){
        var store=new SaleIntentStore(new MemoryPreferences());String key=store.prepare("server|seller","payload");
        assertNotEquals(key,store.prepare("other-server|seller","payload"));
        assertNotEquals(key,store.prepare("server|other-seller","payload"));
    }
}
