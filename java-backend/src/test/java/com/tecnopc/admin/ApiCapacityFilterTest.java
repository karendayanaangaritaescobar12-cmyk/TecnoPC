package com.tecnopc.admin;

import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import static org.junit.jupiter.api.Assertions.*;

class ApiCapacityFilterTest {
    @Test void rejectsKnownAndChunkedOversizedBodiesOnPrivateRoutes() throws Exception {
        var filter=new ApiCapacityFilter();
        for(boolean unknownLength:new boolean[]{false,true}){
            var request=new MockHttpServletRequest("PUT","/api/admin/productos/1"){
                @Override public long getContentLengthLong(){return unknownLength?-1:super.getContentLengthLong();}
            };
            request.setContent(new byte[65537]);var response=new MockHttpServletResponse();
            filter.doFilter(request,response,(req,res)->fail("Oversized request reached authentication"));
            assertEquals(413,response.getStatus());
        }
    }
    @Test void bodyCanBeReadByTheControllerAndPermitsAreReleasedOnExceptions() throws Exception {
        var filter=new ApiCapacityFilter();var request=new MockHttpServletRequest("PATCH","/api/admin/productos/1");
        request.setContent("{\"nombre\":\"PC\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        filter.doFilter(request,new MockHttpServletResponse(),(req,res)->assertEquals("{\"nombre\":\"PC\"}",req.getReader().readLine()));
        for(int i=0;i<10;i++)assertThrows(IllegalStateException.class,()->filter.doFilter(
                new MockHttpServletRequest("GET","/api/admin/inventario"),new MockHttpServletResponse(),(req,res)->{throw new IllegalStateException();}));
    }
    @Test void publicSaturationDoesNotBlockInternalWebhookOrStaticLanes() throws Exception {
        var filter=new ApiCapacityFilter();var entered=new CountDownLatch(6);var release=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(6)){
            var futures=new ArrayList<Future<?>>();
            for(int i=0;i<6;i++)futures.add(executor.submit(()->{
                try{filter.doFilter(new MockHttpServletRequest("GET","/api/public/productos"),new MockHttpServletResponse(),(req,res)->{
                    entered.countDown();try{assertTrue(release.await(5,TimeUnit.SECONDS));}catch(InterruptedException ex){throw new RuntimeException(ex);}
                });}catch(Exception ex){throw new RuntimeException(ex);}
            }));
            try {
                assertTrue(entered.await(5,TimeUnit.SECONDS));var response=new MockHttpServletResponse();
                filter.doFilter(new MockHttpServletRequest("GET","/api/public/productos"),response,(req,res)->fail("Exceeded lane"));
                assertEquals(503,response.getStatus());assertEquals("2",response.getHeader("Retry-After"));
                for(String path:List.of("/api/admin/inventario","/api/public/pagos/webhook","/images/logo-tecnopc.svg","/livez")){
                    var available=new MockHttpServletResponse();filter.doFilter(new MockHttpServletRequest("GET",path),available,(req,res)->res.getWriter().write("ok"));
                    assertEquals("ok",available.getContentAsString());
                }
            } finally {release.countDown();}
            for(var future:futures)future.get(5,TimeUnit.SECONDS);
        }
    }
}
