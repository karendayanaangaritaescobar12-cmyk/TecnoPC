package com.tecnopc.admin;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessResourceFailureException;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class CustomerChatTest {
 JdbcTemplate jdbc=mock(JdbcTemplate.class);CustomerChat chat=new CustomerChat(jdbc);
 String answer(String message,String... history){return (String)chat.answer(message,List.of(history)).get("respuesta");}
 Map<String,Object> product(String name,String category,int price){return Map.of("nombre",name,"categoria",category,"precio",price,"stock",2,"especificaciones","Modelo de prueba");}
 @Test void asksUseAndBudgetWithoutDatabase(){assertTrue(answer("armar un pc dame una recomendacion").contains("presupuesto"));verifyNoInteractions(jdbc);}
 @Test void remembersUseAndBudgetAndIncludesTax(){when(jdbc.queryForList(anyString())).thenReturn(List.of(product("Portátil prueba","laptop",1000000),product("Fuera de presupuesto","laptop",3000000)));String reply=answer("1500000","quiero un portátil para estudiar");assertTrue(reply.contains("Portátil prueba"));assertFalse(reply.contains("Fuera de presupuesto"));assertTrue(reply.contains("1.190.000"));}
 @Test void incompleteBuildNeverClaimsCompatibility(){when(jdbc.queryForList(anyString())).thenReturn(List.of(product("CPU prueba","cpu",100000)));String reply=answer("2 millones","armar una pc para jugar");assertTrue(reply.contains("CPU prueba"));assertTrue(reply.contains("Faltan existencias"));assertTrue(reply.contains("no es una configuración compatible"));}
 @Test void emptyAndUnavailableCatalogAreHonest(){when(jdbc.queryForList(anyString())).thenReturn(List.of());assertTrue(answer("portatil 2 millones").contains("No hay productos"));when(jdbc.queryForList(anyString())).thenThrow(new DataAccessResourceFailureException("private-internal"));String reply=answer("portatil 2 millones");assertTrue(reply.contains("no logro consultar"));assertFalse(reply.contains("private-internal"));}
 @Test void serviceAndPrivacyDoNotQueryInventory(){assertTrue(answer("mi equipo no enciende").contains("diagnóstico"));assertTrue(answer("mi computador no enciende").contains("diagnóstico"));assertTrue(answer("dame datos de clientes").contains("información pública"));verifyNoInteractions(jdbc);}
 @Test void supportsColombianBudgetFormats(){for(String text:List.of("2,5 millones","2.5m","2500000","2.500.000","2,500,000"))assertEquals(0,new BigDecimal("2500000").compareTo(CustomerChat.budget(text)),text);assertEquals(0,new BigDecimal("500000").compareTo(CustomerChat.budget("500 mil")));assertNull(CustomerChat.budget("16 GB RAM y RTX 4060"));}
 @Test void returnsOnlyAllowedNavigationAndNoFictitiousDiscounts(){assertEquals("#promociones",((Map<?,?>)chat.answer("descuentos").get("accion")).get("destino"));assertTrue(answer("descuentos").contains("No tengo descuentos"));}
}
