package com.tecnopc.admin;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import static org.junit.jupiter.api.Assertions.*;

class PublicRequestFilterTest {
    @Test void bothCheckoutRoutesShareTheSameRateBudget() throws Exception {
        var filter=new PublicRequestFilter();
        for(int i=0;i<12;i++){
            var request=new MockHttpServletRequest("POST",i%2==0?"/api/public/pagos":"/api/public/v2/pagos");
            request.setRemoteAddr("client");request.setContentType("application/json");request.setContent("{}".getBytes());
            var response=new MockHttpServletResponse();filter.doFilter(request,response,(req,res)->{});
            assertEquals(i<10?200:429,response.getStatus());
        }
    }
    @Test void arbitraryUnknownPathsCannotFillTheQuotaMapAndBlockOtherVisitors() throws Exception {
        var filter=new PublicRequestFilter();var passed=new AtomicInteger();
        for(int i=0;i<9000;i++){
            var request=new MockHttpServletRequest("GET","/api/public/random-"+i);request.setRemoteAddr("attacker");
            var response=new MockHttpServletResponse();filter.doFilter(request,response,(req,res)->passed.incrementAndGet());
            assertEquals(i<60?200:429,response.getStatus());
        }
        assertEquals(60,passed.get());
        var legitimate=new MockHttpServletRequest("GET","/api/public/productos");legitimate.setRemoteAddr("customer");
        var response=new MockHttpServletResponse();filter.doFilter(legitimate,response,(req,res)->res.getWriter().write("ok"));
        assertEquals("ok",response.getContentAsString());
    }
}
