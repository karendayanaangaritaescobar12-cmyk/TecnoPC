package com.tecnopc.admin;

import jakarta.servlet.FilterChain;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;

class LoginAttemptFilterTest {
    private final TestClock clock=new TestClock();
    private final LoginAttemptFilter filter=new LoginAttemptFilter(clock);
    private final AtomicInteger verifications=new AtomicInteger();
    private final FilterChain failure=(request,response)->{
        verifications.incrementAndGet();((MockHttpServletResponse)response).setStatus(401);
    };
    private static final class TestClock extends Clock {
        private Instant now=Instant.parse("2026-11-13T18:00:00Z");
        void advance(long seconds){now=now.plusSeconds(seconds);}
        public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId zone){return this;}
        public Instant instant(){return now;}
    }
    @AfterEach void clearContext(){SecurityContextHolder.clearContext();}
    private MockHttpServletRequest request(String ip,String user) {
        var request=new MockHttpServletRequest("GET","/api/admin/inventario");
        request.setRemoteAddr(ip);
        if(user!=null)request.addHeader("Authorization","Basic "+Base64.getEncoder()
                .encodeToString((user+":wrong-password").getBytes(StandardCharsets.UTF_8)));
        return request;
    }
    private MockHttpServletResponse perform(String ip,String user,FilterChain chain) throws Exception {
        var response=new MockHttpServletResponse();filter.doFilter(request(ip,user),response,chain);return response;
    }
    @Test void blocksBeforePasswordVerificationAndUsesProgressiveTemporaryWaits() throws Exception {
        for(int i=0;i<5;i++)assertEquals(401,perform("ip1","admin",failure).getStatus());
        var blocked=perform("ip1","admin",failure);
        assertEquals(429,blocked.getStatus());assertEquals("2",blocked.getHeader("Retry-After"));
        assertEquals(5,verifications.get());assertEquals("no-store",blocked.getHeader("Cache-Control"));
        assertFalse(blocked.getContentAsString().contains("admin"));
        clock.advance(2);assertEquals(401,perform("ip1","admin",failure).getStatus());
        assertEquals("4",perform("ip1","admin",failure).getHeader("Retry-After"));
        clock.advance(601);assertEquals(401,perform("ip1","admin",failure).getStatus());
    }
    @Test void rejectedRequestsDoNotExtendTheWait() throws Exception {
        for(int i=0;i<5;i++)perform("ip1","admin",failure);
        clock.advance(1);
        for(int i=0;i<50;i++)assertEquals("1",perform("ip1","admin",failure).getHeader("Retry-After"));
        clock.advance(1);assertEquals(401,perform("ip1","admin",failure).getStatus());
    }
    @Test void protectsAnAccountAcrossOriginsAndCaseVariantsWithoutPermanentLockout() throws Exception {
        for(int i=0;i<20;i++)assertEquals(401,perform("ip"+i,i%2==0?"Admin":"ADMIN",failure).getStatus());
        assertEquals(429,perform("another-ip","admin",failure).getStatus());
        assertEquals(401,perform("another-ip","other-account",failure).getStatus());
        clock.advance(2);assertEquals(401,perform("another-ip","admin",failure).getStatus());
    }
    @Test void detectsPasswordSprayingAndIgnoresSpoofedForwardingHeaders() throws Exception {
        for(int i=0;i<30;i++)perform("real-ip","user"+i,failure);
        var request=request("real-ip","new-user");
        request.addHeader("X-Forwarded-For","different-ip");request.addHeader("Forwarded","for=different-ip");
        var response=new MockHttpServletResponse();filter.doFilter(request,response,failure);
        assertEquals(429,response.getStatus());assertEquals("60",response.getHeader("Retry-After"));
        assertEquals(401,perform("different-ip","new-user",failure).getStatus());
        clock.advance(60);assertEquals(401,perform("real-ip","new-user",failure).getStatus());
    }
    @Test void successfulAuthenticationResetsItsPairButNotSourceFailures() throws Exception {
        FilterChain success=(request,response)->SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("admin",null,List.of()));
        for(int round=0;round<6;round++){
            for(int i=0;i<4;i++)perform("ip1","admin"+round,failure);
            assertEquals(200,perform("ip1","admin"+round,success).getStatus());SecurityContextHolder.clearContext();
        }
        for(int i=0;i<6;i++)perform("ip1","other"+i,failure);
        assertEquals(429,perform("ip1","fresh-account",failure).getStatus());
    }
    @Test void malformedAndOversizedCredentialsAreRejectedWithoutVerification() throws Exception {
        for(String header:List.of("Basic !!!","Basic "+"a".repeat(1100),"Basic "+Base64.getEncoder()
                .encodeToString(("admin:"+"a".repeat(73)).getBytes(StandardCharsets.UTF_8)))){
            var request=request("ip"+header.length(),"admin");request.removeHeader("Authorization");
            request.addHeader("Authorization",header);var response=new MockHttpServletResponse();
            filter.doFilter(request,response,failure);assertEquals(401,response.getStatus());
        }
        assertEquals(0,verifications.get());
    }
    @Test void publicRoutesWithBasicCredentialsCannotBypassProtection() throws Exception {
        for(int i=0;i<6;i++){
            var request=request("ip1","admin");request.setRequestURI("/api/public/productos");
            var response=new MockHttpServletResponse();filter.doFilter(request,response,failure);
            assertEquals(i<5?401:429,response.getStatus());
        }
        assertEquals(5,verifications.get());
    }
    @Test void anonymousPublicTrafficAndServerErrorsDoNotConsumeFailureBudgets() throws Exception {
        for(int i=0;i<100;i++){
            var request=new MockHttpServletRequest("GET","/images/tecnopc-icon.svg");
            filter.doFilter(request,new MockHttpServletResponse(),(req,res)->res.getWriter().write("svg"));
            assertEquals(500,perform("ip1","admin",(req,res)->((MockHttpServletResponse)res).setStatus(500)).getStatus());
        }
        assertEquals(401,perform("ip1","admin",failure).getStatus());
    }
    @Test void reservesConcurrentAttemptsBeforeAuthenticationAndReleasesAfterErrors() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var executor=Executors.newSingleThreadExecutor()){
            var first=executor.submit(()->perform("ip1","admin",(req,res)->{
                entered.countDown();try{assertTrue(release.await(5,TimeUnit.SECONDS));}
                catch(InterruptedException ex){throw new RuntimeException(ex);}
            }));
            try{assertTrue(entered.await(5,TimeUnit.SECONDS));assertEquals(429,perform("ip1","admin",failure).getStatus());}
            finally{release.countDown();}
            assertEquals(200,first.get(5,TimeUnit.SECONDS).getStatus());
        }
        assertThrows(IllegalStateException.class,()->perform("ip1","admin",(req,res)->{throw new IllegalStateException();}));
        assertEquals(401,perform("ip1","admin",failure).getStatus());
    }
    @Test void boundsMemoryAndRecoversAfterEntriesExpire() throws Exception {
        int accepted=0;
        for(int i=0;i<3000;i++)if(perform("ip"+i,"user"+i,failure).getStatus()==401)accepted++;
        assertTrue(accepted<3000);assertTrue(accepted>1000);
        clock.advance(601);assertEquals(401,perform("fresh-ip","fresh-user",failure).getStatus());
    }
    @Test void releasesAuthenticationSlotsBeforeBusinessLogicAndDoesNotCountController401() throws Exception {
        var complete=filter.authenticationCompletedFilter();
        for(int i=0;i<10;i++){
            perform("ip1","admin",(req,res)->{
                SecurityContextHolder.getContext().setAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated("admin",null,List.of()));
                complete.doFilter(req,res,(businessRequest,businessResponse)->{
                    try{assertEquals(200,perform("ip1","admin",(nestedRequest,nestedResponse)->{}).getStatus());}
                    catch(Exception ex){throw new RuntimeException(ex);}
                    ((MockHttpServletResponse)businessResponse).setStatus(401);
                });
            });
            SecurityContextHolder.clearContext();
        }
        assertEquals(401,perform("ip1","admin",failure).getStatus());
    }
    @Test void blockedOriginCannotExhaustMemoryByRotatingUsernames() throws Exception {
        for(int i=0;i<30;i++)perform("attacker-ip","user"+i,failure);
        for(int i=0;i<9000;i++)assertEquals(429,perform("attacker-ip","random"+i,failure).getStatus());
        assertEquals(401,perform("legitimate-ip","legitimate-user",failure).getStatus());
    }
    @Test void limitsGlobalPasswordVerificationConcurrency() throws Exception {
        var entered=new CountDownLatch(8);var release=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(8)){
            var requests=new java.util.ArrayList<Future<MockHttpServletResponse>>();
            for(int i=0;i<8;i++){
                final String ip="ip"+i;
                requests.add(executor.submit(()->perform(ip,"admin",(req,res)->{
                    entered.countDown();try{assertTrue(release.await(5,TimeUnit.SECONDS));}
                    catch(InterruptedException ex){throw new RuntimeException(ex);}
                })));
            }
            try{assertTrue(entered.await(5,TimeUnit.SECONDS));assertEquals(429,perform("ip-new","other-user",failure).getStatus());}
            finally{release.countDown();}
            for(var request:requests)assertEquals(200,request.get(5,TimeUnit.SECONDS).getStatus());
        }
        assertEquals(401,perform("ip-new","other-user",failure).getStatus());
    }
}
