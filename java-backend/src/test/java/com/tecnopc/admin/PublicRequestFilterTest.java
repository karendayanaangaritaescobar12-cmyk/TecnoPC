package com.tecnopc.admin;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.Executors;
import java.util.ArrayList;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import static org.junit.jupiter.api.Assertions.*;

class PublicRequestFilterTest {
    private int paymentStatus(PublicRequestFilter filter, String client) throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/public/pagos");
        request.setRemoteAddr(client);
        request.setContentType("application/json");
        request.setContent("{}".getBytes());
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {});
        if (response.getStatus() == 429) assertEquals("60", response.getHeader("Retry-After"));
        return response.getStatus();
    }

    @Test void fullMapAdmitsNewVisitorsAndKeepsRecentlyUsedClientLimits() throws Exception {
        var time = new AtomicLong();
        var filter = new PublicRequestFilter(time::get);
        for (int i = 0; i < 8192; i++) assertEquals(200, paymentStatus(filter, "client-" + i));
        for (int i = 1; i < 10; i++) assertEquals(200, paymentStatus(filter, "client-8191"));
        assertEquals(429, paymentStatus(filter, "client-8191"));
        for (int i = 8192; i < 9000; i++) assertEquals(200, paymentStatus(filter, "client-" + i));
        assertEquals(429, paymentStatus(filter, "client-8191"));
        // The oldest entry was evicted, so its next request starts a new window.
        for (int i = 0; i < 10; i++) assertEquals(200, paymentStatus(filter, "client-0"));
        assertEquals(429, paymentStatus(filter, "client-0"));
    }

    @Test void quotaExpiresAfterOneMinuteWithoutBeingExtendedByRejectedRequests() throws Exception {
        var time = new AtomicLong();
        var filter = new PublicRequestFilter(time::get);
        for (int i = 0; i < 10; i++) assertEquals(200, paymentStatus(filter, "customer"));
        time.set(59_999);
        assertEquals(429, paymentStatus(filter, "customer"));
        time.set(60_000);
        assertEquals(200, paymentStatus(filter, "customer"));
    }

    @Test void expiredEntriesAreRemovedBeforeEvictingAnActiveClient() throws Exception {
        var time = new AtomicLong();
        var filter = new PublicRequestFilter(time::get);
        assertEquals(200, paymentStatus(filter, "expires-first"));
        time.set(10_000);
        for (int i = 0; i < 10; i++) assertEquals(200, paymentStatus(filter, "active-0"));
        for (int i = 1; i < 8191; i++) assertEquals(200, paymentStatus(filter, "active-" + i));
        // Make the expired entry more recently used than active-0.
        time.set(59_999);
        assertEquals(200, paymentStatus(filter, "expires-first"));
        time.set(60_000);
        assertEquals(200, paymentStatus(filter, "new-customer"));
        assertEquals(429, paymentStatus(filter, "active-0"));
    }

    @Test void concurrentRequestsCannotExceedTheSameClientQuota() throws Exception {
        var filter = new PublicRequestFilter(new AtomicLong()::get);
        try (var executor = Executors.newFixedThreadPool(16)) {
            var results = new ArrayList<Future<Integer>>();
            for (int i = 0; i < 64; i++) results.add(executor.submit(() -> paymentStatus(filter, "same-client")));
            int accepted = 0;
            for (var result : results) {
                int status = result.get();
                assertTrue(status == 200 || status == 429);
                if (status == 200) accepted++;
            }
            assertEquals(10, accepted);
        }
    }

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
